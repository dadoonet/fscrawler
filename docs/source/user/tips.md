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
