# Password-protected documents Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow FSCrawler to extract text from password-protected documents via plugin-based password resolution (`noop` / `disk` / `static` / `chained`) and REST request passwords (#1916).

**Architecture:** New PF4J extension point `FsCrawlerExtensionPasswordProvider` + per-file `PasswordSession`. `TikaDocParser` tries without password first (except REST explicit password), then retries with candidates while reopening/spooling streams. Built-ins ship as four Maven modules under `plugins/`.

**Tech Stack:** Java 17+, Maven multi-module, PF4J, Apache Tika `PasswordProvider`, JUnit Jupiter + RandomizedTesting, gestalt-config settings.

**Spec:** `docs/superpowers/specs/2026-07-23-password-protected-documents-design.md`

## Global Constraints

- TDD: failing test first, confirm red, implement, confirm green (`.claude/rules/testing.md`)
- RandomizedTesting: avoid incidental hardcoded fixture values; use `RandomizedTest.*(…, randomizedRandomForTests)` when the value is not a protocol/format constant
- Commit messages: `type(scope): emoji description` with detail bullets (`.claude/rules/git-workflow.md`)
- Never log password values — only path, provider type, candidate index
- Do **not** rebase #2241; implement on current `master` / feature branch; port fixtures/ideas only
- Built-in providers = one Maven module each under `plugins/`
- Default `passwords.provider` = `noop`
- Spotless before commit when touching Java: `mvn spotless:apply -pl <modules> -am`

---

## File map

| Path | Responsibility |
|---|---|
| `settings/.../Passwords.java` | Top-level `passwords` settings (`provider` + nested provider configs) |
| `settings/.../PasswordProviders.java` | Nested `disk` / `static` / `chained` config holders |
| `settings/.../FsSettings.java` + `FsSettingsLoader.java` | Wire `passwords` |
| `settings/.../FsCrawlerValidator.java` | Structural validation (chained empty/self-ref); merge sidecar excludes |
| `settings/.../fscrawler-default.yaml` | Documented defaults |
| `plugin/.../PasswordSession.java` | Session API |
| `plugin/.../FsCrawlerExtensionPasswordProvider.java` | Extension point |
| `plugin/.../PasswordProviderLookup.java` | Lookup functional interface for `chained` |
| `plugin/.../FsCrawlerPasswordProviderExtensionFinder.java` | SPI finder for password providers (mirror FsProvider finder) |
| `plugin/.../FsCrawlerPluginsManager.java` | Register / `findPasswordProvider` / start with lookup |
| `plugins/password-noop-plugin/**` | `noop` |
| `plugins/password-static-plugin/**` | `static` |
| `plugins/password-disk-plugin/**` | `disk` |
| `plugins/password-chained-plugin/**` | `chained` |
| `tika/.../TikaInstance.java` | Per-call parse context + optional Tika `PasswordProvider` |
| `tika/.../TikaDocParser.java` | Encrypt detect + retry orchestration + spool |
| `core/.../FsParserAbstract.java` | Pass reopenable stream supplier + job password provider |
| `rest/.../DocumentApi.java` | REST `password` court-circuit + spool when multi-candidate |
| `cli/pom.xml`, `plugins/pom.xml`, root `pom.xml`, `integration-tests/pom.xml` | Module wiring |
| `docs/source/admin/fs/passwords.rst` (+ toctree) | User docs |
| `docs/source/admin/fs/rest.rst` | REST password section |
| `test-documents/.../test-protected.pdf` | Port from #2241 if missing |

---

### Task 1: Settings model `passwords.*`

**Files:**
- Create: `settings/src/main/java/fr/pilato/elasticsearch/crawler/fs/settings/Passwords.java`
- Create: `settings/src/main/java/fr/pilato/elasticsearch/crawler/fs/settings/PasswordProviders.java`
- Create: `settings/src/main/java/fr/pilato/elasticsearch/crawler/fs/settings/DiskPasswordProviderSettings.java`
- Create: `settings/src/main/java/fr/pilato/elasticsearch/crawler/fs/settings/StaticPasswordProviderSettings.java`
- Create: `settings/src/main/java/fr/pilato/elasticsearch/crawler/fs/settings/ChainedPasswordProviderSettings.java`
- Modify: `settings/src/main/java/fr/pilato/elasticsearch/crawler/fs/settings/FsSettings.java`
- Modify: `settings/src/main/java/fr/pilato/elasticsearch/crawler/fs/settings/FsSettingsLoader.java` (add `settings.setPasswords(gestalt.getConfigOptional("passwords", Passwords.class).orElse(null));`)
- Modify: `settings/src/main/resources/fr/pilato/elasticsearch/crawler/fs/settings/fscrawler-default.yaml` (commented `passwords` block)
- Test: `settings/src/test/java/fr/pilato/elasticsearch/crawler/fs/settings/PasswordsSettingsTest.java`
- Test resources: `settings/src/test/resources/config/passwords-static/_settings.yaml`

**Interfaces:**
- Consumes: gestalt `@Config` loading pattern from `Tags` / `Fs`
- Produces: `FsSettings#getPasswords(): Passwords` with `provider` default `"noop"`; nested `providers.disk.url`, `providers.static.values` / `value`, `providers.chained.providers`

- [ ] **Step 1: Write failing load test**

```java
@Test
void passwordsStaticValuesLoaded() throws Exception {
    Path jobDir = rootTmpDir.resolve("config").resolve("passwords-static");
    // write _settings.yaml with:
    // passwords:
    //   provider: static
    //   providers:
    //     static:
    //       values: [alpha, beta]
    FsSettings settings = new FsSettingsLoader().read(jobDir);
    assertThat(settings.getPasswords().getProvider()).isEqualTo("static");
    assertThat(settings.getPasswords().getProviders().getStaticSettings().getValues())
            .containsExactly("alpha", "beta");
}
```

Use the same temp-config pattern as `FsSettingsLoaderTest` (copy fixture under `settings/src/test/resources/config/passwords-static/_settings.yaml`).

- [ ] **Step 2: Run test — expect fail**

```bash
mvn -pl settings test -Dtests.class=fr.pilato.elasticsearch.crawler.fs.settings.PasswordsSettingsTest
```

Expected: FAIL (no `Passwords` / NPE / null passwords)

- [ ] **Step 3: Implement settings classes + loader wiring**

`Passwords.java` (sketch):

```java
public class Passwords {
    @Config(defaultVal = "noop")
    private String provider;

    @Config
    @Nullable
    private PasswordProviders providers;

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public PasswordProviders getProviders() { return providers; }
    public void setProviders(PasswordProviders providers) { this.providers = providers; }
}
```

`PasswordProviders.java`: fields `disk` (`DiskPasswordProviderSettings`), `chained` (`ChainedPasswordProviderSettings`), and for YAML key `static` use:

```java
@Config
@Nullable
private StaticPasswordProviderSettings staticSettings;

// gestalt may need the JavaBean name "static" — if loader fails, use:
// @Config(path = "static") on a field named staticSettings, OR name getter getStatic()/setStatic()
public StaticPasswordProviderSettings getStatic() { return staticSettings; }
public void setStatic(StaticPasswordProviderSettings staticSettings) { this.staticSettings = staticSettings; }
```

`StaticPasswordProviderSettings`: `List<String> values` + optional `String value`; add method `List<String> resolvedValues()` that returns `values` if non-null/non-empty else singleton/empty from `value`.

`DiskPasswordProviderSettings`: `String url` (nullable → defaulted later to `fs.url`).

`ChainedPasswordProviderSettings`: `List<String> providers`.

Wire into `FsSettings` (`getPasswords`/`setPasswords`, equals/hashCode/toString).

- [ ] **Step 4: Re-run test — expect pass**

```bash
mvn -pl settings test -Dtests.class=fr.pilato.elasticsearch.crawler.fs.settings.PasswordsSettingsTest
```

- [ ] **Step 5: Commit**

```bash
git add settings
git commit -m "$(cat <<'EOF'
feat(settings): ✨ add passwords.* configuration model

- Load provider + disk/static/chained nested settings via gestalt
- Default provider is noop when section omitted
EOF
)"
```

---

### Task 2: Settings validation + sidecar excludes

**Files:**
- Modify: `settings/src/main/java/fr/pilato/elasticsearch/crawler/fs/settings/FsCrawlerValidator.java`
- Test: `settings/src/test/java/fr/pilato/elasticsearch/crawler/fs/settings/FsCrawlerValidatorTest.java` (extend)

**Interfaces:**
- Consumes: `Passwords` / `ChainedPasswordProviderSettings`
- Produces: fail-fast on empty/`chained` self-ref; merges excludes for `*.password` and files named `.password`

- [ ] **Step 1: Write failing validation tests**

```java
@Test
void chainedWithoutProvidersIsFatal() {
    FsSettings settings = FsSettingsLoader.load();
    settings.getPasswords().setProvider("chained");
    settings.getPasswords().setProviders(new PasswordProviders());
    settings.getPasswords().getProviders().setChained(new ChainedPasswordProviderSettings());
    // providers list null/empty
    assertThat(FsCrawlerValidator.validateSettings(logger, settings)).isTrue();
}

@Test
void passwordSidecarExcludesMerged() {
    FsSettings settings = FsSettingsLoader.load();
    assertThat(FsCrawlerValidator.validateSettings(logger, settings)).isFalse();
    assertThat(settings.getFs().getExcludes())
            .anyMatch(e -> e.contains(".password") || e.endsWith("*.password"));
}
```

Pick exclude patterns that match existing `FsCrawlerUtil.isMatching` semantics (same style as default `*/~*`). Recommended defaults to append if missing:

- `*.password` (matches `foo.txt.password`)
- `*/.password` and/or `.password` as needed so directory sidecars are skipped — verify with a tiny unit assert on `FsCrawlerUtil.isExcluded("foo/.password", excludes)` etc.

- [ ] **Step 2: Run — expect fail**

```bash
mvn -pl settings test -Dtests.class=fr.pilato.elasticsearch.crawler.fs.settings.FsCrawlerValidatorTest
```

- [ ] **Step 3: Implement validation**

In `validateSettings`:

1. If `passwords.provider` is `chained`: require non-empty `providers.chained.providers`; reject if list contains `"chained"`.
2. Always ensure sidecar exclude patterns are present on `fs.excludes` (mutable copy if unmodifiable).
3. Do **not** validate unknown provider type here (needs plugin registry — Task 4 / crawler start).

- [ ] **Step 4: Run — expect pass**

- [ ] **Step 5: Commit**

```bash
git commit -m "$(cat <<'EOF'
feat(settings): ✨ validate passwords config and exclude sidecars

- Reject empty/self-referential chained provider lists
- Merge *.password / .password excludes so secrets are not indexed
EOF
)"
```

---

### Task 3: Password provider extension API + plugin manager

**Files:**
- Create: `plugin/src/main/java/fr/pilato/elasticsearch/crawler/plugins/PasswordSession.java`
- Create: `plugin/src/main/java/fr/pilato/elasticsearch/crawler/plugins/FsCrawlerExtensionPasswordProvider.java`
- Create: `plugin/src/main/java/fr/pilato/elasticsearch/crawler/plugins/PasswordProviderLookup.java`
- Create: `plugin/src/main/java/fr/pilato/elasticsearch/crawler/plugins/FsCrawlerPasswordProviderExtensionFinder.java`
- Modify: `plugin/src/main/java/fr/pilato/elasticsearch/crawler/plugins/FsCrawlerPluginsManager.java`
- Test: `plugin/src/test/java/fr/pilato/elasticsearch/crawler/plugins/FsCrawlerPluginsManagerPasswordProviderTest.java` (can use a test-only `@Extension` stub in test sources)

**Interfaces:**
- Consumes: PF4J patterns from `FsCrawlerExtensionFsProvider` / `FsCrawlerServiceProviderExtensionFinder`
- Produces:

```java
public interface PasswordSession extends AutoCloseable {
    Optional<String> next();
}

@FunctionalInterface
public interface PasswordProviderLookup {
    FsCrawlerExtensionPasswordProvider get(String type);
}

public interface FsCrawlerExtensionPasswordProvider extends ExtensionPoint, AutoCloseable {
    String getType();
    void start(FsSettings settings, PasswordProviderLookup lookup);
    PasswordSession open(String documentPath);
}
```

`FsCrawlerPluginsManager`:
- `HashMap<String, FsCrawlerExtensionPasswordProvider> passwordProviders`
- In `startPlugins()`, also `getExtensions(FsCrawlerExtensionPasswordProvider.class)`, then `provider.start(settingsPlaceholder?, lookup)` — **note:** today FsProviders are started later with job settings. Mirror that: add `startPasswordProviders(FsSettings settings)` called from `FsCrawlerImpl` / CLI when job settings are known, OR start inside `findPasswordProvider` lazily once. Prefer explicit `startPasswordProviders(FsSettings settings)` called once after settings load (same place FsProvider crawl `start` happens).
- `findPasswordProvider(String type)` throws `FsCrawlerIllegalConfigurationException` if missing
- `close()` also closes password providers (lifecycle)

SPI finder: copy `FsCrawlerServiceProviderExtensionFinder` → `FsCrawlerPasswordProviderExtensionFinder` with  
`SERVICE_RESOURCE = "META-INF/services/" + FsCrawlerExtensionPasswordProvider.class.getName()`  
Register both finders in `DefaultExtensionFinder.add(...)`.

- [ ] **Step 1: Write failing manager test** with a test stub extension of type `"test-pwd"` registered via SPI in `plugin/src/test/resources/...` OR direct map injection if easier; assert `findPasswordProvider("test-pwd")` works after start.

- [ ] **Step 2: Run — expect fail**

```bash
mvn -pl plugin test -Dtests.class=fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPluginsManagerPasswordProviderTest
```

- [ ] **Step 3: Implement interfaces + manager + finder**

- [ ] **Step 4: Run — expect pass**

- [ ] **Step 5: Commit**

```bash
git commit -m "$(cat <<'EOF'
feat(plugin): ✨ add PasswordProvider extension point

- PasswordSession + FsCrawlerExtensionPasswordProvider + lookup
- SPI finder and PluginsManager registration/lookup
EOF
)"
```

---

### Task 4: Maven module `password-noop-plugin`

**Files:**
- Create module: `plugins/password-noop-plugin/` (pom, MANIFEST.MF, SPI, plugin class)
- Modify: `plugins/pom.xml` (add module)
- Modify: root `pom.xml` `dependencyManagement`
- Modify: `cli/pom.xml` + `integration-tests/pom.xml` dependencies
- Test: `plugins/password-noop-plugin/src/test/java/.../PasswordNoopPluginTest.java`

**Interfaces:**
- Consumes: `FsCrawlerExtensionPasswordProvider`
- Produces: type `"noop"`; `open(path).next()` always empty

Scaffold like `plugins/fs-local-plugin`:

- `pom.xml` artifact `fscrawler-password-noop-plugin`
- `META-INF/MANIFEST.MF` `Plugin-Class: ...PasswordNoopPlugin`
- SPI file listing the `@Extension` inner class FQCN
- Outer `extends FsCrawlerPlugin`, `getName()` → `"password-noop"`

```java
@Extension
public static class Provider implements FsCrawlerExtensionPasswordProvider {
    @Override public String getType() { return "noop"; }
    @Override public void start(FsSettings settings, PasswordProviderLookup lookup) {}
    @Override public PasswordSession open(String documentPath) {
        return Optional::empty; // or session whose next() returns Optional.empty()
    }
    @Override public void close() {}
}
```

- [ ] **Step 1: Failing unit test** — start provider, `open("any").next()` is empty

- [ ] **Step 2: Run — expect fail** (module missing)

```bash
mvn -pl plugins/password-noop-plugin test
```

- [ ] **Step 3: Create module + implement + wire poms**

- [ ] **Step 4: Run — expect pass**

```bash
mvn -pl plugins/password-noop-plugin,cli -am test -DskipIntegTests -Dtests.class=*PasswordNoop*
```

Also smoke: construct `FsCrawlerPluginsManager`, `loadPlugins`/`startPlugins`, `startPasswordProviders(settings)`, `findPasswordProvider("noop")`.

- [ ] **Step 5: Commit**

```bash
git commit -m "$(cat <<'EOF'
feat(password-noop): ✨ add noop password provider plugin

- Default provider that never yields password candidates
- Wire module into plugins parent, cli, and dependencyManagement
EOF
)"
```

---

### Task 5: Tika password-aware extract + retry loop

**Files:**
- Modify: `tika/src/main/java/fr/pilato/elasticsearch/crawler/fs/tika/TikaInstance.java`
- Modify: `tika/src/main/java/fr/pilato/elasticsearch/crawler/fs/tika/TikaDocParser.java`
- Modify: `tika/pom.xml` (add `fscrawler-plugin` dependency)
- Test: `tika/src/test/java/fr/pilato/elasticsearch/crawler/fs/tika/TikaDocParserTest.java`
- Fixture: ensure `test-protected.docx` used; **port** `test-protected.pdf` from PR branch `1916-password` into `test-documents/src/main/resources/documents/` if absent

**Interfaces:**
- Consumes: `FsCrawlerExtensionPasswordProvider` / `PasswordSession`
- Produces: `TikaDocParser.generate(...)` overloads that can decrypt

Important: today `extractParsedContent` swallows all `Throwable` and returns null — encrypted docs look like empty content. Change extraction so `EncryptedDocumentException` is distinguishable.

Recommended approach:

1. `TikaInstance.extractText(..., String password)` clones/builds a **per-call** `ParseContext` from the instance baseline and, if `password != null`, `context.set(PasswordProvider.class, metadata -> password)`. Do not mutate a shared context (concurrency).
2. Add package-private or public result type, e.g. enum `ExtractStatus { OK, ENCRYPTED, FAILED }` or throw a small `EncryptedDocumentSignal` only for control flow — prefer a result object to avoid using exceptions for happy-path control in the outer loop.
3. `TikaDocParser.generate(InputStream in, Doc doc, long size)` keeps current behavior (single pass, no provider).
4. New overload:

```java
public void generate(
        InputStreamSupplier reopen,
        Doc doc,
        long filesize,
        String explicitPassword, // non-null => REST court-circuit: parse WITH this password only
        FsCrawlerExtensionPasswordProvider provider // may be null / noop
) throws IOException
```

Algorithm:

```
if (explicitPassword != null) {
  try (InputStream in = reopen.open()) { parse with explicitPassword }
  return
}
try (InputStream in = reopen.open()) { parse without password }
if not encrypted -> return
if provider == null -> return (empty)
try (PasswordSession session = provider.open(doc.getPath().getReal())) {
  Optional<String> pwd;
  while ((pwd = session.next()).isPresent()) {
    try (InputStream in = reopen.open()) {
      parse with pwd.get()
      if success -> return
      if encrypted -> continue
      else -> return (other failure already logged)
    }
  }
}
warn exhausted candidates (no secret values)
```

For single-shot streams (legacy callers): `() -> { throw ... }` not used — wrap `ByteArrayInputStream` supplier after spooling when needed.

Spool: when `explicitPassword == null` and provider is not noop, if the supplied stream is not reopenable, spool to memory/temp first (reuse existing checksum/`storeSource` threshold logic / `IN_MEMORY_THRESHOLD`), then `reopen` from buffer/temp.

- [ ] **Step 1: Failing tests in `TikaDocParserTest`**

```java
@Test
void protectedDocxWithPasswordExtractsContent() throws Exception {
    FsSettings settings = FsSettingsLoader.load();
    Doc doc = new Doc();
    doc.getPath().setReal("test-protected.docx");
    doc.getFile().setFilename("test-protected.docx");
    TikaDocParser parser = new TikaDocParser(settings);
    parser.generate(
            () -> getBinaryContent("test-protected.docx"),
            doc,
            0,
            "david",
            null);
    assertThat(doc.getContent()).contains("sample text"); // adjust to real fixture text
}

@Test
void protectedDocxWithoutPasswordYieldsEmptyContent() throws Exception {
    // generate single-pass or with noop provider — content null/empty
}
```

Password for fixtures (from #2241): docx `david`, pdf `pdfpassword`.

- [ ] **Step 2: Run — expect fail**

```bash
mvn -pl tika,test-documents -am test -Dtests.class=fr.pilato.elasticsearch.crawler.fs.tika.TikaDocParserTest -Dtests.method=protectedDocx*
```

- [ ] **Step 3: Implement TikaInstance + TikaDocParser changes; port PDF fixture**

```bash
git show origin/1916-password:test-documents/src/main/resources/documents/test-protected.pdf \
  > test-documents/src/main/resources/documents/test-protected.pdf
```

(or `gh`/fetch from that ref)

- [ ] **Step 4: Run — expect pass**

- [ ] **Step 5: Commit**

```bash
git commit -m "$(cat <<'EOF'
feat(tika): ✨ retry extraction with document passwords

- Per-call Tika PasswordProvider without mutating shared ParseContext
- Distinguish encrypted docs and retry via PasswordSession candidates
- Port test-protected.pdf fixture for coverage
EOF
)"
```

---

### Task 6: Wire crawl path (`FsParserAbstract`)

**Files:**
- Modify: `core/src/main/java/fr/pilato/elasticsearch/crawler/fs/FsParserAbstract.java`
- Modify: `core/src/main/java/fr/pilato/elasticsearch/crawler/fs/FsCrawlerImpl.java` (start password providers with job settings; resolve provider once)
- Test: prefer IT in Task 11; optional unit if stream supplier wiring is extractable

**Interfaces:**
- Consumes: `pluginsManager.findPasswordProvider(settings.getPasswords().getProvider())` after `startPasswordProviders`
- Produces: crawl calls `tikaDocParser.generate(() -> fileAbstractor.getInputStream(child), doc, size, null, passwordProvider)`

- [ ] **Step 1: Locate `indexFile` / `TikaDocParser.generate` call site; write a focused test if feasible, else rely on IT and do a characterization run**

- [ ] **Step 2: Implement wiring**

- On crawler start: `pluginsManager.startPasswordProviders(fsSettings)` then `passwordProvider = pluginsManager.findPasswordProvider(fsSettings.getPasswords().getProvider())` (null-safe default `"noop"` if passwords section null — treat as noop).
- Unknown provider → fail start with clear error (fail fast).
- Pass provider into parser field (like existing `tikaDocParser`).

- [ ] **Step 3: Compile module**

```bash
mvn -pl core -am test -DskipTests
```

- [ ] **Step 4: Commit**

```bash
git commit -m "$(cat <<'EOF'
feat(core): ✨ use password provider during filesystem crawl

- Start password providers with job settings
- Reopen file streams for each decrypt candidate
EOF
)"
```

---

### Task 7: `password-static-plugin`

**Files:**
- Create: `plugins/password-static-plugin/**` (same packaging pattern as noop)
- Wire poms (`plugins/pom.xml`, root dependencyManagement, `cli`, `integration-tests`)
- Test: `PasswordStaticPluginTest`

**Interfaces:**
- Consumes: `settings.getPasswords().getProviders().getStatic().resolvedValues()`
- Produces: session yielding each value in order

- [ ] **Step 1: Failing test** — values `[a,b]` → `next()` a, b, empty

- [ ] **Step 2: Run — expect fail**

- [ ] **Step 3: Implement + wire**

- [ ] **Step 4: Run — expect pass**

- [ ] **Step 5: Commit**

```bash
git commit -m "$(cat <<'EOF'
feat(password-static): ✨ add static password provider plugin

- Yield configured passwords.values (or single value) in order
EOF
)"
```

---

### Task 8: `password-disk-plugin`

**Files:**
- Create: `plugins/password-disk-plugin/**`
- Wire poms
- Test: `PasswordDiskPluginTest` with temp dirs (RandomizedTesting temp paths)

**Interfaces:**
- Consumes: `disk.url` defaulting to `fs.url`; document absolute/real path
- Produces: session walking candidates:
  1. `{diskUrl}/{relative}.password`
  2. parent dirs `.password` up to disk root

Relative path = path of document relative to `fs.url` (use `Paths` relativize; if not under `fs.url`, fall back to filename-only or skip with warn — document choice: **warn + filename-only under diskUrl**).

Sidecar read: UTF-8, first non-empty trimmed line; missing/empty skip; IO warn + skip.

- [ ] **Step 1: Failing tests**

```java
@Test
void yieldsExactSidecarThenParentThenRoot() throws Exception {
    // fs.url = root/es
    // doc = root/es/foo/bar.txt
    // disk.url = root/pwd
    // files: root/pwd/foo/bar.txt.password, root/pwd/foo/.password, root/pwd/.password
    // assert next order
}
```

Use `RandomizedTest.randomAsciiLettersOfLength(...)` for password contents where incidental.

- [ ] **Step 2: Run — expect fail**

- [ ] **Step 3: Implement**

- [ ] **Step 4: Run — expect pass**

- [ ] **Step 5: Commit**

```bash
git commit -m "$(cat <<'EOF'
feat(password-disk): ✨ add disk sidecar password provider

- Mirror relative paths under passwords.providers.disk.url
- Walk up .password files after exact *.password sidecar
EOF
)"
```

---

### Task 9: `password-chained-plugin`

**Files:**
- Create: `plugins/password-chained-plugin/**`
- Wire poms
- Test: `PasswordChainedPluginTest` with stub child providers via lookup lambda (no need for real disk/static JARs in unit test)

**Interfaces:**
- Consumes: `PasswordProviderLookup` + `providers.chained.providers` list
- Produces: session that exhausts child sessions in order

```java
// open():
List<String> types = settings.getPasswords().getProviders().getChained().getProviders();
// session keeps index + current child PasswordSession
// next(): pull from current child; on empty close child and open next type via lookup.get(type).open(path)
```

- [ ] **Step 1: Failing test** — lookup returns stub A then B; assert concatenated candidates

- [ ] **Step 2: Run — expect fail**

- [ ] **Step 3: Implement**

- [ ] **Step 4: Run — expect pass**

- [ ] **Step 5: Commit**

```bash
git commit -m "$(cat <<'EOF'
feat(password-chained): ✨ add chained password provider plugin

- Compose child providers by type via PasswordProviderLookup
EOF
)"
```

---

### Task 10: REST password court-circuit + spool

**Files:**
- Modify: `rest/src/main/java/fr/pilato/elasticsearch/crawler/fs/rest/DocumentApi.java`
- Modify: `integration-tests/.../AbstractRestITCase.java` (`uploadFileUsingApi` password overload — port idea from #2241)
- Test: unit if possible; IT in Task 11

**Interfaces:**
- Consumes: form/header/query `password`; job `passwordProvider`
- Produces: `explicitPassword != null` → parse with that password only (no provider session); else provider + spool/reopen

- [ ] **Step 1: Add failing REST IT method stub or unit test around `enrichDoc`**

Prefer IT in Task 11; here add compile-level API:

```java
@FormDataParam("password") String formDocumentPassword,
@HeaderParam("password") String headerDocumentPassword,
@QueryParam("password") String queryParamDocumentPassword,
```

Resolve: form > header > query (same precedence style as id/index in #2241).

- [ ] **Step 2: Implement DocumentApi wiring to `tikaDocParser.generate(reopen, doc, size, password, provider)`**

For multipart without request password: spool to byte[]/temp then reopen supplier (because Jersey stream is one-shot).

For 3rd-party JSON: accept query/header password; prefer re-`provider.readFile()` as reopen; else spool.

- [ ] **Step 3: `mvn -pl rest -am test -DskipIntegTests`

- [ ] **Step 4: Commit**

```bash
git commit -m "$(cat <<'EOF'
feat(rest): ✨ accept document password on upload

- Form/header/query password court-circuits job providers
- Spool uploads when multi-candidate providers may retry
EOF
)"
```

---

### Task 11: Integration tests

**Files:**
- Create: `integration-tests/src/test/java/.../elasticsearch/FsCrawlerTestPasswordIT.java`
- Modify: `integration-tests/src/test/java/.../elasticsearch/FsCrawlerRestIT.java` (protected upload case)
- Modify: `integration-tests/pom.xml` if password plugins not already added
- Test resources under IT temp copy: create sidecars in test setup

**Interfaces:**
- Consumes: all providers + REST

Cases:

1. **Crawl disk+static chained** — copy `test-protected.pdf` into crawl dir; put password in external `disk.url` mirror; assert content searchable
2. **REST with form password** — upload protected docx with `david`; assert `$.content` non-empty
3. **REST static multi-value** — job `passwords.provider=static` with wrong then right password; upload without request password; assert content
4. **Sidecar not indexed** — place `foo.txt.password` in crawl tree; assert not present as a document

Docker required for IT (see `AGENTS.md`).

- [ ] **Step 1: Write failing ITs**

- [ ] **Step 2: Run one IT — expect fail**

```bash
# ensure dockerd running per AGENTS.md
mvn verify -pl integration-tests -am -DskipUnitTests -Dit.test=FsCrawlerTestPasswordIT
```

- [ ] **Step 3: Fix gaps until green**

- [ ] **Step 4: Commit**

```bash
git commit -m "$(cat <<'EOF'
test(integration): 🧪 cover password-protected crawl and REST

- Disk mirror + chained static fallback
- REST explicit password and static multi-candidate retries
- Ensure password sidecars are not indexed
EOF
)"
```

---

### Task 12: User documentation

**Files:**
- Create: `docs/source/admin/fs/passwords.rst`
- Modify: `docs/source/admin/fs/index.rst` (toctree entry)
- Modify: `docs/source/admin/fs/rest.rst` (Document password section)
- Optionally mention in `docs/source/admin/fs/local-fs.md` / settings index tables if there is a central settings list

**Content must include:**

- Settings table: `passwords.provider`, `passwords.providers.disk.url`, `passwords.providers.static.values`, `passwords.providers.chained.providers`
- Disk mirror examples (default `fs.url` vs external root)
- Security note (external root, no logging, sidecars excluded)
- REST examples (form / header / query) + court-circuit behavior
- Chained example YAML from the spec

- [ ] **Step 1: Write docs**

- [ ] **Step 2: Quick RST sanity (optional)**

```bash
# if docs env available; otherwise review RST manually
```

- [ ] **Step 3: Commit**

```bash
git commit -m "$(cat <<'EOF'
docs(passwords): 📝 document password providers and REST password

- Add admin passwords page and REST upload examples
- Security guidance for disk sidecars
EOF
)"
```

---

### Task 13: Close loop on #2241 / #1916

**Files:** none (GitHub)

- [ ] **Step 1: Ensure implementation PR description links design + plan + closes #1916**

- [ ] **Step 2: Comment on #2241 that it is superseded by the new PR; close #2241** (human/maintainer may prefer to close — agent: comment + close if permitted)

- [ ] **Step 3: Final verification**

```bash
mvn spotless:check
mvn test -DskipIntegTests
mvn verify -pl integration-tests -am -DskipUnitTests -Dit.test=FsCrawlerTestPasswordIT,FsCrawlerRestIT
```

- [ ] **Step 4: Commit any leftover fixes; push; update PR body**

---

## Spec coverage checklist

| Spec requirement | Task |
|---|---|
| Top-level `passwords.*` | 1 |
| Default `noop` | 1, 4 |
| Validation chained empty/self-ref | 2 |
| Auto-exclude sidecars | 2 |
| Extension API + session | 3 |
| SPI / PluginsManager | 3 |
| One module per provider | 4, 7, 8, 9 |
| Try without password first | 5 |
| REST explicit password court-circuit + direct parse | 5, 10 |
| Reopen/spool streams | 5, 6, 10 |
| Disk mirror + walk-up | 8 |
| Static list | 7 |
| Chained composition | 9 |
| Crawl wiring | 6 |
| ITs | 11 |
| Docs + security notes | 12 |
| New PR not rebase #2241 | 13 (+ delivery) |
| No password logging | 5–10 (code review assert) |
| No es/vault in v1 | — (intentionally omitted) |

## Plan self-review notes

- No TBD placeholders left for v1 scope; third-party gestalt config beyond typed built-ins is deferred (plugins can still ignore settings or use env until a later enhancement).
- `start(FsSettings, PasswordProviderLookup)` is the chosen injection style so `chained` stays a normal plugin without compile deps on siblings.
- Tika shared `ParseContext` must not be mutated per call — called out in Task 5.
