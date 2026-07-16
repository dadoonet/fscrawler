(document-ids)=
# Document IDs

```{contents}
:backlinks: entry
```

```{versionadded} 3.0
```

By default, FSCrawler derives the Elasticsearch document `_id` by hashing the file path
(and similarly hashes folder paths and `path.root` values used for housekeeping queries).
You can control the digest algorithm with `fs.hash_algorithm`, or skip hashing entirely with
`fs.filename_as_id`.

See also {ref}`filename-as-id` and {ref}`rest-document-id`.

## `fs.hash_algorithm`

| Name                 | Environment Variable           | Default when unset | New jobs (`--setup`) |
|----------------------|--------------------------------|--------------------|----------------------|
| `fs.hash_algorithm`  | `FSCRAWLER_FS_HASH_ALGORITHM`  | `MD5`              | `SHA-256`            |

Any algorithm supported by Java `MessageDigest` is accepted (for example `MD5`, `SHA-1`,
`SHA-256`). Invalid names disable the crawler at startup.

```yaml
name: "test"
fs:
  url: "/path/to/data/dir"
  hash_algorithm: "SHA-256"
```

### Defaults and compatibility

* **Existing jobs** that do not set `fs.hash_algorithm` keep **`MD5`**, including the historical
  encoding used for document `_id`s, so upgrading FSCrawler does not rewrite existing ids.
* **New jobs** created with `fscrawler --setup` get an example `_settings.yaml` with
  `hash_algorithm: "SHA-256"`.

`fs.hash_algorithm` is independent from `fs.checksum`. The latter hashes **file content** for the
`file.checksum` field; `fs.hash_algorithm` only affects document and folder `_id`s (and related
`path.root` hashes).

When `fs.filename_as_id` is `true`, the raw filename is used as `_id` and `fs.hash_algorithm` is
ignored.

The same setting applies to the crawler and to the REST `_document` endpoint when an id is not
provided explicitly.

## Changing the algorithm (reindex checklist)

Changing `fs.hash_algorithm` (or switching between hashed ids and `filename_as_id`) produces
**new `_id`s** for the same files. FSCrawler will treat them as new documents unless you start
from a clean index. There is no automatic id-migration tool.

Recommended steps:

1. Stop FSCrawler.
2. Create a **new** Elasticsearch index (or delete / empty the existing documents and folder index).
3. Update `fs.hash_algorithm` in `_settings.yaml` (or remove it to fall back to `MD5`).
4. Optionally point `elasticsearch.index` / `elasticsearch.index_folder` at the new indices.
5. Restart with `--restart` so the checkpoint is cleared and the filesystem is fully re-scanned.
6. After verification, remove the old index or update aliases.
7. Remember that `path.root` and folder documents are hashed with the same algorithm; they must
   stay consistent with file documents.

See also the Elasticsearch [Reindex API](https://www.elastic.co/docs/api/doc/elasticsearch/operation/operation-reindex)
if you need to copy other data between indices.
