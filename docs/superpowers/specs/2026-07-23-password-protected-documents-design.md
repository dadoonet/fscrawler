# Password-protected documents — design

**Date:** 2026-07-23  
**Related:** [#1916](https://github.com/dadoonet/fscrawler/issues/1916), WIP [#2241](https://github.com/dadoonet/fscrawler/pull/2241)  
**Status:** Draft for review

## Problem

FSCrawler fails to extract text from password-protected Office/PDF documents (`EncryptedDocumentException`). Users need:

- A password supplied on REST upload
- For filesystem crawls, usually **one password per document** (or per subtree), not only one global password
- An extensible way to resolve passwords (disk sidecars, static list, future vault/ES) without hardcoding a single unsafe scheme

Tika already supports this via `org.apache.tika.parser.PasswordProvider`.

## Goals (v1)

- Extract content from encrypted PDF/DOCX (and other Tika-supported encrypted formats) when a correct password is available
- Top-level job settings under `passwords.*`
- Plugin-based password resolution (PF4J), reusable by third parties
- Built-in providers: `noop` (default), `disk`, `static`, `chained`
- REST request `password` short-circuits providers
- Fail soft: wrong/missing password → warn + empty content, do not crash the job
- Never log password values

## Non-goals (v1)

- Elasticsearch / vault password backends (possible later as plugins)
- Caching “which password worked” across files
- Writing or rotating sidecar password files
- Continuing / rebasing PR #2241 as-is (see Delivery)

## Decisions summary

| Topic | Decision |
|---|---|
| Config namespace | Top-level `passwords.*` (not under `fs`) |
| Extensibility | New PF4J extension point (parallel to FsProvider) |
| Active provider | Single `passwords.provider` (default `noop`) |
| Composition | `chained` provider lists other provider types |
| API shape | `PasswordSession open(path)` → `next()` / `close()` |
| Parse order | Try **without** password first (except REST with explicit password) |
| Disk layout | Mirror relative path under `providers.disk.url` (default `fs.url`) |
| Disk cascade | `<rel>.password` then parent `.password` files up to disk root |
| REST password | Court-circuits providers; parse **directly** with that password |
| REST multi-retry | Spool to memory/temp when provider may yield multiple candidates |
| Delivery | **New PR from current `master`**; close #2241 as superseded |

## Architecture

```text
                    passwords.provider
                            │
                            ▼
              FsCrawlerExtensionPasswordProvider
                     open(documentPath)
                            │
                            ▼
                     PasswordSession
                   next() until empty
                            │
                            ▼
         TikaDocParser retry loop (reopen stream + PasswordProvider)
```

### New extension point (`plugin/`)

```java
public interface FsCrawlerExtensionPasswordProvider extends ExtensionPoint, AutoCloseable {
    String getType();
    void start(FsSettings settings);
    PasswordSession open(String documentPath);
}

public interface PasswordSession extends AutoCloseable {
    /** Next password candidate, or empty if this provider is exhausted for the path. */
    Optional<String> next();
}
```

- Discovered like FsProviders: PF4J `@Extension` + SPI under  
  `META-INF/services/fr.pilato.elasticsearch.crawler.plugins.FsCrawlerExtensionPasswordProvider`
- `FsCrawlerPluginsManager` gains `findPasswordProvider(String type)`
- Provider instances are process-scoped (loaded at crawler start). `close()` on the provider is **lifecycle only** (plugin stop), not per document — unlike the REST try-with-resources pattern used for FsProviders
- **Per-file state lives in `PasswordSession`** (safe for parallel crawls). Always `close()` the session after the retry loop

### Built-in providers (v1)

| Type | Role |
|---|---|
| `noop` | Default. `open()` returns a session whose first `next()` is empty |
| `disk` | Sidecar files under a configurable root (path mirror + directory walk-up) |
| `static` | Ordered list from settings |
| `chained` | Opens child providers in order; exhausts N before N+1 |

Third-party JARs dropped into the distribution `plugins/` directory can register new types and be referenced by `passwords.provider` or listed under `chained`.

## Configuration

```yaml
passwords:
  provider: chained          # default: noop
  providers:
    chained:
      providers: [disk, static]
    disk:
      url: /crypted/password # default: same as fs.url
    static:
      values: [shared1, shared2]
      # value: single          # optional alias → values: [single]
```

### Validation (startup)

- Unknown `passwords.provider` → fail fast
- `chained.providers` missing/empty → fail fast
- `chained` must not include itself (reject or ignore self-reference)
- Unknown name inside `chained.providers` → fail fast

### Defaults

- If `passwords` section omitted: `provider: noop` (current behavior for encrypted docs: empty content)
- `disk.url` defaults to `fs.url` when unset
- Auto-exclude password sidecars from crawling by adding `*.password` and `*/.password` to the effective excludes (merged with user `fs.excludes`), so secrets are not indexed as documents

## Disk provider resolution

Given:

- `fs.url = /tmp/es`
- document real path = `/tmp/es/foo/bar.txt`
- `passwords.providers.disk.url = /crypted/password` (or default `/tmp/es`)

Relative path = `foo/bar.txt`.

`PasswordSession.next()` yields passwords from existing readable files, in order:

1. `{disk.url}/foo/bar.txt.password`
2. `{disk.url}/foo/.password`
3. `{disk.url}/.password`

Then exhausted.

**Sidecar format:** plain text; first non-empty line (trimmed) is the password. Missing/empty/unreadable file → skip candidate (warn on IO errors), continue.

**Security note:** production deployments should set `disk.url` **outside** the crawled tree with stricter permissions. Colocating `.password` next to encrypted files is convenient but weak.

The core/orchestrator always calls `open(documentPath)` once with the **document** path. Walk-up semantics are encapsulated inside `disk`; `chained` only composes providers.

## Parse / retry flow

### Filesystem crawl

1. Open file stream via `FileAbstractor`
2. Parse with Tika **without** password
3. If not `EncryptedDocumentException` → done (providers never consulted)
4. If encrypted:
   - `session = passwordProvider.open(realPath)`
   - For each `pwd = session.next()`:
     - **Reopen** a fresh stream (`FileAbstractor.getInputStream`)
     - Parse with Tika `PasswordProvider` returning `pwd`
     - Success → stop
     - `EncryptedDocumentException` → try next candidate
   - `session.close()`
5. If all candidates fail → warn (no secret values), leave content empty; job continues (`continueOnError` unchanged for unrelated errors)

### REST multipart upload

**Request `password` present (form / header / query):**

- Court-circuits job providers
- Parse **directly** with that password (no “try without first”)
- Single stream consumption → **no rewind required**
- Wrong password → empty content + warn

**No request password:**

- Same strategy as crawl using `passwords.provider`
- If the provider may yield multiple candidates: **spool** upload bytes to memory (small) or temp file (large / unknown size) before the retry loop — reuse the existing `TikaDocParser` buffering approach used for checksum / `storeSource`
- `disk` is usually irrelevant for REST (path is typically just the filename); `static` / `chained` remain useful

### REST 3rd-party JSON upload

- Optional query/header password: same court-circuit as multipart
- Otherwise use job provider; prefer re-`readFile()` / reopen from the FsProvider when possible, else spool

## Error handling

| Situation | Behavior |
|---|---|
| Non-encrypted file | One parse; providers unused |
| Encrypted + correct candidate | Indexed with content |
| Encrypted + no / wrong candidates | Warn; document without content |
| Unknown provider at startup | Fail fast |
| Sidecar IO error | Warn; skip that candidate |
| Stream not re-readable and spool impossible | Stop retries; warn |

Do not log password values — only path, provider type, and candidate index if needed.

## Relationship to #2241

PR #2241 already prototyped:

- Instance-based `TikaDocParser` / `TikaInstance` (no global static parser)
- REST `password` (form / header / query)
- Protected PDF/DOCX fixtures and tests

It is **not** a good base to continue:

- GitHub status: conflicting / dirty vs `master`
- Large drift on `master` since the branch tip (including Tika and parser changes)
- Contains unrelated noise (OCR IT refactor, logging cosmetics)

**Delivery choice:** open a **new PR from current `master`**, port only the useful bits, implement this design, then close #2241 as superseded with a link.

## Testing (TDD)

### Unit

- `noop` / `static` / `disk` / `chained` session ordering and exhaustion
- Disk: exact sidecar, parent `.password`, root `.password`, missing files
- `TikaDocParser`: protected PDF/DOCX with good / bad / multiple candidates; unprotected docs never open a password session
- Settings load + validation

### Integration

- Crawl with `disk.url` ≠ `fs.url` plus `static` fallback via `chained`
- REST upload with form password → non-empty content
- REST without password + multi-value `static` → spool + retries
- Sidecars excluded from the index

Reuse protected fixtures from #2241 (`test-protected.pdf`, `test-protected.docx`).

## Documentation

- Admin docs for `passwords` (settings table, disk mirror, static, chained, examples)
- REST section: request password + court-circuit behavior
- Security guidance: external `disk.url`, excludes, no secret logging

## Implementation outline (for later plan)

1. Settings model + defaults + validation  
2. Extension point + plugin manager lookup + `noop`  
3. Parse/retry orchestration in `TikaDocParser` (stream reopen / spool)  
4. REST password court-circuit (port from #2241 as needed)  
5. `static`, then `disk`, then `chained`  
6. Sidecar excludes  
7. Unit + integration tests  
8. User docs  
9. Close #2241 as superseded  

## Open points intentionally deferred

- Future providers (`es`, vault, etc.) — same extension API
- Optional optimization: remember last successful password for a directory (not in v1)
