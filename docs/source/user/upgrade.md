(upgrade-from-2.9)=
# Upgrading from 2.9

FSCrawler 3.0 is a new major version. **There is no in-place upgrade from 2.9.**

Install 3.0, recreate each job with `--setup`, and reindex. Do not start 3.0 against a 2.9
configuration directory and do not copy a 2.9 `_settings.yaml` as-is.

2.9 indices, mappings, and document `_id`s are not migrated. Keep the 2.9 deployment until you have
verified 3.0, then remove the old indices when you no longer need them.

## What to do

1. Stop every FSCrawler 2.9 process.
2. Install 3.0 (ZIP or Docker). See {ref}`installation`.
3. Create jobs with `bin/fscrawler --setup` (or `--setup job_name`).
4. Edit the generated `_settings.yaml`: `fs.url`, `elasticsearch.urls`, and an API key.
5. Reindex from the filesystem (or re-upload through the REST `_document` endpoint).

The fastest path for a new 3.0 install is the {ref}`tutorial`.

## Why a 2.9 job file will not work

A 2.9 `_settings.yaml` uses settings and defaults that 3.0 does not accept or no longer means the
same thing. The main ones:

| 2.9 | 3.0 |
|-----|-----|
| `elasticsearch.nodes` / `elasticsearch.nodes.url` | `elasticsearch.urls` (a list). Unknown keys are ignored and FSCrawler falls back to `https://127.0.0.1:9200`. |
| Job folder created on first run | `fscrawler --setup` |
| No job name lists existing jobs | `fscrawler --list` |
| REST `_upload` | `_document` |
| REST base path `/fscrawler/` | `/` |
| Docker command includes `fscrawler` binary | image entrypoint; pass the job name only |
| Tika XML config | Tika JSON config |
| `server.protocol` | `fs.provider` (`local`, `ftp`, `ssh`) |
| `fs.ocr.pdf_strategy` default `ocr_and_text` | default `auto` |
| Basic auth | API keys (basic auth still works, but is deprecated) |
| One distribution per Elasticsearch version | a single distribution |

New jobs created with `--setup` set `fs.hash_algorithm` to `SHA-256`. 2.9 document `_id`s used MD5.
Reindexing with 3.0 produces new ids.

The default `fs.ocr.pdf_strategy` is now `auto`: OCR is skipped on PDF pages that already contain
text. Set `ocr_and_text` if you need OCR on every page.

Apache Tika 4 renamed several `meta.raw.*` keys. Queries, aggregations, and templates that depend on
those names must be rewritten. See {ref}`release-notes-3.0`.

The full list of changes is in {ref}`release-notes-3.0`.
