# Job file specifications

```{contents}
:backlinks: entry
```

## Expected files

FSCrawler expects to find a job directory in the `~/.fscrawler` directory or in the directory
you defined with the `--config_dir` CLI option (see {ref}`cli-options`). The job file could be either:

* a `yaml` file named `_settings.yaml`
* a `json` file named `_settings.json`
* a list of files within a directory named `_settings`

When using a directory, FSCrawler will merge all files found in the directory. Meaning that you can split your settings
in multiple files, like:

* `my_job_fs.yaml` which contains the file system settings
* `my_job_elasticsearch.yaml` which contains the elasticsearch settings

## Using placeholders

```{versionadded} 3.0
```

FSCrawler supports placeholders in the job file. This is useful when you want to use environment variables in your job file.
For example, you can define the following job file:

```yaml
fs:
  url: "${HOME}/docs"
elasticsearch:
  urls:
  - "${ES_NODE1:=https://127.0.0.1:9200}"
  api_key: "${ES_API_KEY}"
```

When running FSCrawler, it will replace `${HOME}`, `${ES_NODE1}` and `${ES_API_KEY}`
by their respective values which will be read from environment variables and java system properties if not found.

If no value is found, it will use the default value after the `:=` if any, or it will fail starting if no default value.
In the previous example, both `${HOME}` and `${ES_API_KEY}` are mandatory but `${ES_NODE1}` is optional and will
be set to `https://127.0.0.1:9200` if not set.

FSCrawler is using the gestalt-config project to handle placeholders. You can read more about String substitution in the
[gestalt-config documentation](https://github.com/gestalt-config/gestalt#string-substitution).

## Default placeholders

FSCrawler supports a set of default placeholders that you can define using environment variables.
The form of those placeholders is the prefix `FSCRAWLER_` and the setting name. For example,
`fs.url` can be set using the environment variable `FSCRAWLER_FS_URL` or the system property `-Dfs.url`.

As an example, you can run:

```sh
FSCRAWLER_NAME=foo \
FSCRAWLER_FS_URL=/tmp/test \
FSCRAWLER_ELASTICSEARCH_API_KEY=VnVhQ2ZHY0JDZGJrUW0tZTVhT3g6dWkybHAyYXhUTm1zeWFrdzl0dk5udw== \
bin/fscrawler test
```

or:

```sh
FS_JAVA_OPTS="-Dname=foo -Dfs.url=/tmp/test -Delasticsearch.api-key=VnVhQ2ZHY0JDZGJrUW0tZTVhT3g6dWkybHAyYXhUTm1zeWFrdzl0dk5udw==" \
bin/fscrawler test
```

```{note}

 If you define as well some settings in the job file, the settings in the job file will override the
 environment variables and system properties.
```

## Example job file specification

`bin/fscrawler --setup` writes a fully commented example to
`~/.fscrawler/{job}/_settings.yaml`. The job file for the job name `test`
(`~/.fscrawler/test/_settings.yaml`) looks like:

```yaml
# optional: the name of the crawler. Defaults to the job directory name.
name: "test"

fs:
  # available providers: local (default), ftp, ssh
  provider: "local"
  # inside Docker this must be the path INSIDE the container (/tmp/es)
  url: "/path/to/docs"
  follow_symlinks: false
  remove_deleted: true
  continue_on_error: false
  update_rate: "15m"

  # optional: "~" and ".DS_Store" files are excluded by default if not defined
  includes:
    - "*.doc"
    - "*.xls"
  excludes:
    - "resume.doc"

  ignore_above: "512mb"
  json_support: false
  add_as_inner_object: false
  xml_support: false

  # when true, the filename is used as _id (hash_algorithm is ignored)
  filename_as_id: false
  # new jobs from --setup use SHA-256; unset keeps MD5 for existing jobs
  hash_algorithm: "SHA-256"

  add_filesize: true
  attributes_support: false
  acl_support: false
  store_source: false
  index_content: true
  indexed_chars: "10000.0"
  raw_metadata: false
  # optional: hash of file content (independent from hash_algorithm)
  checksum: "MD5"
  index_folders: true
  lang_detect: false

  ocr:
    enabled: true
    language: "eng"
    # default is auto: skip OCR on PDF pages that already contain text
    pdf_strategy: auto

# optional: add static metadata tags to documents
tags:
  metaFilename: ".meta.yml"
  staticMetaFilename: "/path/to/static_metadata.yml"

# optional: configure password lookup for protected documents
passwords:
  provider: "disk"
  providers:
    disk:
      url: "/path/to/password-sidecars"

# optional: only required for SSH/FTP
server:
  hostname: "localhost"
  port: 22
  username: "dadoonet"
  password: "password"
  pem_path: "/path/to/pemfile"

elasticsearch:
  urls:
    - "https://127.0.0.1:9200"
  bulk_size: 1000
  flush_interval: "5s"
  byte_size: "10mb"
  api_key: "VnVhQ2ZHY0JDZGJrUW0tZTVhT3g6dWkybHAyYXhUTm1zeWFrdzl0dk5udw=="
  # username/password is deprecated; prefer api_key
  # username: "elastic"
  # password: "password"
  # optional, defaults to <name>_docs — do not set this to the job name
  # index: "test_docs"
  # optional, defaults to <name>_folder
  # index_folder: "test_folder"
  push_templates: true
  semantic_search: true

kibana:
  url: "http://127.0.0.1:5601"
  push_dashboard: true

# only used when started with --rest option
rest:
  url: "http://127.0.0.1:8080"
```

Here is a list of existing top level settings:

| Name                     | Documentation                 |
|--------------------------|-------------------------------|
| `name` (mandatory field) | {ref}`simple_crawler`         |
| `fs`                     | {ref}`local-fs-settings`      |
| `tags`                   | {ref}`tags`                   |
| `passwords`              | {ref}`password-settings`      |
| `elasticsearch`          | {ref}`elasticsearch-settings` |
| `kibana`                 | {ref}`kibana-settings`        |
| `server`                 | {ref}`ssh-settings`           |
| `rest`                   | {ref}`rest-service`           |


You can define your job settings either in `_settings.yaml` (using `.yaml` extension) or
in `_settings.json` (using `.json` extension).
