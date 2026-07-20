# Tips and tricks

## Moving files to a “watched” directory

When moving an existing file to the directory FSCrawler is watching, you
need to explicitly `touch` all the files as when moved, the files are
keeping their original date intact:

```sh
# single file
touch file_you_moved

# all files
find  -type f  -exec touch {} +

# all .txt files
find  -type f  -name "*.txt" -exec touch {} +
```

Or you need to {ref}`restart  <cli-options>` from the
beginning with the `--restart` option which will reindex everything.

## Workaround for huge temporary files

FSCrawler uses a media library that currently does not clean up their temporary files.
Parsing MP4 files may create very large temporary files in /tmp.
The following commands could be useful e.g. as a cronjob to automatically delete those files once they are old and no longer in use.
Adapt the commands as needed.

```sh
# Check all files in /tmp
find /tmp \( -name 'apache-tika-*.tmp-*' -o -name 'MediaDataBox*' \) -type f -mmin +15 ! -exec fuser -s {} \; -delete

# When using a systemd service with PrivateTMP enabled
find $(find /tmp -maxdepth 1 -type d -name 'systemd-private-*-fscrawler.service-*') \( -name 'apache-tika-*.tmp-*' -o -name 'MediaDataBox*' \) -type f -mmin +15 ! -exec fuser -s {} \; -delete
```

## Indexing from HDFS drive

There is no specific support for HDFS in FSCrawler. But you can [mount your HDFS on your machine](https://wiki.apache.org/hadoop/MountableHDFS) 
and run FS crawler on this mount point. You can also read details about [HDFS NFS Gateway](http://hadoop.apache.org/docs/stable/hadoop-project-dist/hadoop-hdfs/HdfsNfsGateway.html).

## Using docker

See {ref}`docker`.

(multi-machine)=
## Running FSCrawler on multiple machines

```{versionadded} 3.0
```

If you run FSCrawler on several hosts against the **same Elasticsearch cluster**, and those
hosts crawl paths that look identical (for example both watch `/data/docs`), document `_id`s
can collide.

By default, the `_id` is derived from the file path (see {ref}`document-ids`). The same path on
`machine1` and `machine2` therefore produces the **same** `_id`. The last writer wins and
silently overwrites the other machine's document.

Do **not** point several crawlers at the same physical document index unless every path (and
thus every `_id`) is guaranteed unique across machines.

### Recommended pattern: one index per machine + a search alias

Give each machine its own document and folder indices, then use the shared alias for search.
Optionally tag every document with the machine hostname via {ref}`tags` static metadata, so you can
filter or aggregate by host when searching on the shared alias.

**On machine1**, create a static metadata file (for example
`~/.fscrawler/fscrawler/static_metadata.yml`):

```yaml
external:
  hostname: "machine1"
```

And the job settings (`~/.fscrawler/fscrawler/_settings.yaml`):

```yaml
name: "fscrawler"
fs:
  url: "/data/docs"
elasticsearch:
  index: "fscrawler_machine1_docs"
  index_folder: "fscrawler_machine1_folder"
tags:
  staticMetaFilename: "/home/user/.fscrawler/fscrawler/static_metadata.yml"
```

**On machine2**, create a static metadata file (for example
`~/.fscrawler/fscrawler/static_metadata.yml`):

```yaml
external:
  hostname: "machine2"
```

And the job settings (`~/.fscrawler/fscrawler/_settings.yaml`):

```yaml
name: "fscrawler"
fs:
  url: "/data/docs"
elasticsearch:
  index: "fscrawler_machine2_docs"
  index_folder: "fscrawler_machine2_folder"
tags:
  staticMetaFilename: "/home/user/.fscrawler/fscrawler/static_metadata.yml"
```

By default, FSCrawler creates an index alias named after the job `name` (here `fscrawler`) that points to
the documents index. That means that both `fscrawler_machine1_docs` and `fscrawler_machine2_docs` are aliased to
`fscrawler`. You can then use the alias for search on `fscrawler`. Each crawler writes only to its own index, so:

* `_id` collisions between machines no longer overwrite each other
* `fs.remove_deleted` only removes documents that belong to that machine's index
* folder housekeeping stays isolated as the folder index is also per machine
* when static metadata is set, documents carry `external.hostname` (and any other fields you defined)
  so you can tell which machine indexed them even when searching through the shared alias

### Alternatives

* **Different `fs.url` layouts** that never collide in the hashed path (for example mount points
  that include the hostname) can share one index, but that is brittle and hard to reason about.
* **`fs.filename_as_id: true`** does **not** fix multi-machine collisions: identical filenames
  still share the same `_id`.

### See also

* {ref}`mappings` — how the job-name alias is created via index templates
* {ref}`document-ids` — how `_id`s are generated and what happens when you change the algorithm
* {ref}`elasticsearch-settings` — `elasticsearch.index` / `elasticsearch.index_folder`
* {ref}`tags` — static metadata such as `external.hostname`
* Discussion: [Running FSCrawler on several servers](https://github.com/dadoonet/fscrawler/discussions/2256)

## Deduplicate documents with a content-based `_id`

By default, FSCrawler derives the Elasticsearch document `_id` from the **file path**
(see {ref}`document-ids`). Two identical files under different paths therefore become
two distinct documents.

If you want to index only **one copy** of duplicate files, you can set the document `_id`
from a content fingerprint via an Elasticsearch {ref}`ingest pipeline <ingest_node>`.

### Option 1: binary checksum (`fs.checksum`)

Enable {ref}`file checksum <file-checksum>` so FSCrawler stores a hash of the **binary file**
in `file.checksum`:

```yaml
name: "test"
fs:
  index_content: true
  # indexed_chars: 0   # optional: checksum only, no extracted text
  checksum: "SHA-256"
elasticsearch:
  pipeline: "set-id-from-checksum"
```

Create the pipeline that copies `file.checksum` into `_id`:

```none
PUT _ingest/pipeline/set-id-from-checksum
{
  "description": "Set the _id from file.checksum",
  "processors": [
    {
      "set": {
        "field": "_id",
        "value": "{{{file.checksum}}}"
      }
    }
  ]
}
```

Identical binary files then share the same `_id`: the last indexed copy wins and overwrites
the previous document. That behaviour is useful when you update a file and want the index
to reflect the latest path or metadata for that content.

```{note}
The checksum is computed from the **binary file**, not from the extracted text.
`fs.checksum` is independent from `fs.hash_algorithm` (the latter only affects path-based ids).
```

### Option 2: fingerprint of the extracted text

If you prefer to deduplicate on the **extracted text** (`content`) instead of the binary,
use the Elasticsearch
[fingerprint ingest processor](https://www.elastic.co/docs/reference/ingest-processor/fingerprint-processor):

```none
PUT _ingest/pipeline/content-fingerprint-id
{
  "description": "Compute a fingerprint from content and set it as the document _id",
  "processors": [
    {
      "fingerprint": {
        "fields": ["content"],
        "target_field": "_tmp_fingerprint",
        "method": "SHA-256"
      }
    },
    {
      "set": {
        "field": "_id",
        "value": "{{{_tmp_fingerprint}}}"
      }
    },
    {
      "remove": {
        "field": "_tmp_fingerprint",
        "ignore_missing": true
      }
    }
  ]
}
```

Then set `elasticsearch.pipeline: "content-fingerprint-id"` in your job settings.

```{warning}
This option overwrites documents that have **no extracted text** or the **exact same text**.
Binary-identical files with different extracted text are **not** treated as duplicates.
Conversely, different files that yield the same extracted text **are** treated as duplicates.
```

### Caveats

* **Last writer wins**: when several paths share the same fingerprint, only one document remains.
  FSCrawler uses the Elasticsearch bulk `index` action, which replaces an existing document
  with the same `_id`.
* **`fs.remove_deleted`**: deleting one of the duplicate files on disk can remove the shared
  document from Elasticsearch even if another copy still exists.
* **Folder documents** are not sent through the ingest pipeline (see {ref}`ingest_node`).
* Changing to a content-based `_id` produces new ids; reindex from a clean index (or with
  `--restart`) as described in {ref}`document-ids`.

### See also

* {ref}`file-checksum` — `fs.checksum` / `file.checksum`
* {ref}`ingest_node` — `elasticsearch.pipeline`
* {ref}`document-ids` — default path-based `_id` generation
