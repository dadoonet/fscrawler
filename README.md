# FileSystem Crawler for Elasticsearch

Welcome to the FS Crawler for [Elasticsearch](https://elastic.co/)

This crawler helps to index documents from your local file system and over SSH.
It crawls your file system and index new files, update existing ones and removes old ones.

You need to install a version matching your Elasticsearch version:

| Elasticsearch |  FS Crawler | Released |                                       Docs                                   |
|---------------|-------------|----------|------------------------------------------------------------------------------|
|    es-2.0     | 2.0.0       |2015-10-30|[2.0.0](https://github.com/dadoonet/fscrawler/blob/fscrawler-2.0.0/README.md) |
| 1.x, 2.x, 5.x | **2.1**     |2016-07-26|[2.1](https://github.com/dadoonet/fscrawler/blob/fscrawler-2.1/README.md)     |
| 1.x, 2.x, 5.x | 2.2-SNAPSHOT|          |See below                                                                     |

From FS Crawler 2.1, all elasticsearch versions since 1.0 are supported.

## Build Status

Thanks to Travis for the [build status](https://travis-ci.org/dadoonet/fscrawler): 
[![Build Status](https://travis-ci.org/dadoonet/fscrawler.svg)](https://travis-ci.org/dadoonet/fscrawler)


# Table of content

* [Installation guide](#installation-guide)
    * [Download fscrawler](#download-fscrawler)
    * [Upgrade fscrawler](#upgrade-fscrawler)
* [User guide](#user-guide)
    * [Getting Started](#getting-started)
    * [Searching for docs](#searching-for-docs)
    * [Crawler options](#crawler-options)
    * [Starting with a REST gateway](#starting-with-a-rest-gateway)
    * [Supported formats](#supported-formats)
* [Administration guide](#administration-guide)
    * [CLI options](#cli-options)
    * [JVM Settings](#jvm-settings)
    * [Job file specification](#job-file-specification)
        * [Local FS settings](#local-fs-settings)
        * [SSH settings](#ssh-settings)
        * [Elasticsearch settings](#elasticsearch-settings)
        * [REST service](#rest-service)
* [Tips and tricks](#tips-and-tricks)
* [Developing on fscrawler project](#developing-on-fscrawler-project)
* [License](#license)

# Installation Guide

## Download fscrawler

FS Crawler binary is available on [Maven Central](https://repo1.maven.org/maven2/fr/pilato/elasticsearch/crawler/fscrawler/).
Just download the latest release (or any other specific version you want to try).

The filename ends with `.zip`.

For example, if you wish to download [fscrawler-2.1](https://repo1.maven.org/maven2/fr/pilato/elasticsearch/crawler/fscrawler/2.1/fscrawler-2.1.zip):

```sh
wget https://repo1.maven.org/maven2/fr/pilato/elasticsearch/crawler/fscrawler/2.1/fscrawler-2.1.zip
unzip fscrawler-2.1.zip
```

The distribution contains:

```
$ tree
.
├── LICENSE
├── NOTICE
├── README.md
├── bin
│   ├── fscrawler
│   └── fscrawler.bat
└── lib
    ├── ... All needed jars
```

Note that you can also download a SNAPSHOT version
[from sonatype](https://oss.sonatype.org/content/repositories/snapshots/fr/pilato/elasticsearch/crawler/fscrawler/2.2-SNAPSHOT/)
without needing to build it by yourself.

## Upgrade fscrawler

It can happen that you need to [upgrade a mapping](#upgrading-an-existing-mapping) before starting fscrawler after a
version upgrade.
Read carefully the following update instructions.

To update fscrawler, just download the new version, unzip it in another directory and launch it as usual.
It will still pick up settings from the configuration directory. Of course, you need to stop first the existing
running instances.

### Upgrade to 2.2

* fscrawler comes with new default mappings for files. They have better defaults as they consume less disk space
and CPU at index time. You should remove existing files in `~/.fscrawler/_default/_mappings` before starting the new
version so default mappings will be updated. If you modified manually mapping files, apply the modification you made
on sample files.

* `excludes` is now set by default for new jobs to `["~*"]`. In previous versions, any file or directory containing a
`~` was excluded. Which means that if in your jobs, you are defining any exclusion rule, you need to add `*~*` if
you want to get back the exact previous behavior.

* If you were indexing `json` or `xml` documents with the `filename_as_id` option set, we were previously removing the
suffix of the file name, like indexing `1.json` was indexed as `1`. With this new version, we don't remove anymore the
suffix. So the `_id` for your document will be now `1.json`.


# User Guide

## Getting Started

**You need to have at least Java 1.8.**

```sh
bin/fscrawler job_name
```

FS crawler will read a local file (default to `~/.fscrawler/{job_name}/_settings.json`).
If the file does not exist, FS crawler will propose to create your first job.

```sh
$ bin/fscrawler job_name
18:28:58,174 WARN  [f.p.e.c.f.FsCrawler] job [job_name] does not exist
18:28:58,177 INFO  [f.p.e.c.f.FsCrawler] Do you want to create it (Y/N)?
y
18:29:05,711 INFO  [f.p.e.c.f.FsCrawler] Settings have been created in [~/.fscrawler/job_name/_settings.json]. Please review and edit before relaunch
```

Create a directory named `/tmp/es` or `c:\tmp\es`, add some files you want to index in it and start again:

```sh
$ bin/fscrawler --config_dir ./test job_name
18:30:34,330 INFO  [f.p.e.c.f.FsCrawlerImpl] Starting FS crawler
18:30:34,332 INFO  [f.p.e.c.f.FsCrawlerImpl] FS crawler started in watch mode. It will run unless you stop it with CTRL+C.
18:30:34,682 INFO  [f.p.e.c.f.FsCrawlerImpl] FS crawler started for [job_name] for [/tmp/es] every [15m]
```

If you did not create the directory, FS crawler will complain until you fix it:

```
18:30:34,683 WARN  [f.p.e.c.f.FsCrawlerImpl] Error while indexing content from /tmp/es: /tmp/es doesn't exists.
```

You can also run FS crawler without arguments. It will give you the list of existing jobs and will allow you to
choose one:

```
$ bin/fscrawler
18:33:00,624 INFO  [f.p.e.c.f.FsCrawler] No job specified. Here is the list of existing jobs:
18:33:00,629 INFO  [f.p.e.c.f.FsCrawler] [1] - job_name
18:33:00,629 INFO  [f.p.e.c.f.FsCrawler] Choose your job [1-1]...
1
18:33:06,151 INFO  [f.p.e.c.f.FsCrawlerImpl] Starting FS crawler
```

## Searching for docs

This is a common use case in elasticsearch, we want to search for something! ;-)

```json
GET docs/doc/_search
{
  "query" : {
    "match" : {
        "_all" : "I am searching for something !"
    }
  }
}
```


## Crawler options

By default, FS crawler will read your file from `/tmp/es` every 15 minutes. You can change those settings by
modifying `~/.fscrawler/{job_name}/_settings.json` file where `{job_name}` is the name of the job you just created.

```json
{
  "name" : "job_name",
  "fs" : {
    "url" : "/path/to/data/dir",
    "update_rate" : "15m"
  }
}
```

You can change also `update_rate` to watch more or less frequently for changes.

If you just want FS crawler to run once and exit, run it with `--loop` option:

```sh
$ bin/fscrawler job_name --loop 1
18:47:37,487 INFO  [f.p.e.c.f.FsCrawlerImpl] Starting FS crawler
18:47:37,854 INFO  [f.p.e.c.f.FsCrawlerImpl] FS crawler started for [job_name] for [/tmp/es] every [15m]
...
18:47:37,855 INFO  [f.p.e.c.f.FsCrawlerImpl] FS crawler is stopping after 1 run
18:47:37,959 INFO  [f.p.e.c.f.FsCrawlerImpl] FS crawler [job_name] stopped
```

If you have already ran FS crawler and want to restart (which means reindex existing documents),
use the `--restart` option:

```sh
$ bin/fscrawler job_name --loop 1 --restart
```

You will find more information about settings in the following sections:

* [CLI options](#cli-options)
* [Local FS settings](#local-fs-settings)
* [SSH settings](#ssh-settings)
* [Elasticsearch settings](#elasticsearch-settings)

## Starting with a REST gateway

FS crawler can be a nice gateway to elasticsearch if you want to upload binary documents
and index them into elasticsearch without writing by yourself all the code to extract data
and communicate with elasticsearch.

To start FS crawler with the REST service, use the `--rest` option. A good idea is also to combine it
with `--loop 0` so you won't index local files but only listen to incoming REST requests:

```sh
$ bin/fscrawler job_name --loop 0 --rest
18:55:37,851 INFO  [f.p.e.c.f.FsCrawlerImpl] Starting FS crawler
18:55:39,237 INFO  [f.p.e.c.f.FsCrawlerImpl] FS crawler Rest service started on [http://127.0.0.1:8080/fscrawler]
```

Check the service is working with:

```sh
curl http://127.0.0.1:8080/fscrawler/
```

It will give you back a JSON document.

The you can start uploading your binary files:

```sh
echo "This is my text" > test.txt
curl -F "file=@test.txt" "http://127.0.0.1:8080/fscrawler/_upload"
```

It will index the file into elasticsearch and will give you back the elasticsearch URL
for the created document, like:

```json
{
  "ok" : true,
  "filename" : "test.txt",
  "url" : "http://127.0.0.1:9200/fscrawler-rest-tests/doc/dd18bf3a8ea2a3e53e2661c7fb53534"
}
```

You will find more information about REST settings in the following section:

* [REST settings](#rest-settings)

## Supported formats

FS crawler supports all formats [Tika version 1.14 supports](http://tika.apache.org/1.14/formats.html#Supported_Document_Formats), like:

* HTML
* Microsoft Office
* Open Office
* PDF
* Images
* MP3
* ...




# Administration guide

Once the crawler is running, it will write status information and statistics in:

* `~/.fscrawler/{job_name}/_settings.json`
* `~/.fscrawler/{job_name}/_status.json`

It means that if you stop the job at some point, FS crawler will restart it from where it stops.

FS crawler will also store default mappings and index settings for elasticsearch in `~/.fscrawler/_default/_mappings`:

* `1/doc.json`: for elasticsearch 1.x series document mapping
* `1/folder.json`: for elasticsearch 1.x series folder mapping
* `1/_settings.json`: for elasticsearch 1.x series index settings
* `2/doc.json`: for elasticsearch 2.x series document mapping
* `2/folder.json`: for elasticsearch 2.x series folder mapping
* `2/_settings.json`: for elasticsearch 2.x series index settings
* `5/doc.json`: for elasticsearch 5.x series document mapping
* `5/folder.json`: for elasticsearch 5.x series folder mapping
* `5/_settings.json`: for elasticsearch 5.x series index settings

Read [Mapping](#mapping) for more information.

## CLI options

* `--help` displays help
* `--silent` runs in silent mode. No output is generated.
* `--debug` runs in debug mode.
* `--trace` runs in trace mode (more verbose than debug).
* `--config_dir` defines directory where jobs are stored instead of default `~/.fscrawler`.
* `--username` defines the username to use when using an secured version of elasticsearch cluster. Read
[Using Credentials](#using-credentials). (From 2.2)
* `--upgrade_mapping` tries to upgrade existing mappings for documents and folders. Read
[Upgrading an existing mapping](#upgrading-an-existing-mapping). (From 2.2)
* `--loop x` defines the number of runs we want before exiting  (From 2.2):

    * `X` where X is a negative value means infinite, like `-1` (default)
    * `0` means that we don't run any crawling job (useful when used with rest).
    * `X` where X is a positive value is the number of runs before it stops.

If you want to scan your hard drive only once, run with `--loop 1`.

* `--restart` restart a job from scratch (From 2.2). See below.
* `--rest` starts the REST service (From 2.2):

If you want to run the [REST Service](#rest-service) without scanning your hard drive, launch with:

```sh
bin/fscrawler --rest --loop 0
```

You can tell FS crawler that it must restart from the beginning by using `--restart` option:

```sh
bin/fscrawler job_name --restart
```

In that case, the `{job_name}/_status.json` file will be removed.

## JVM Settings

If you want to provide JVM settings, like defining memory allocated to FS Crawler, you can define
a system property named `FS_JAVA_OPTS`:

```sh
FS_JAVA_OPTS="-Xmx521m -Xms521m" bin/fscrawler
```

## Job file specification

The job file must comply to the following `json` specifications:

```json
{
  "name" : "job_name",
  "fs" : {
    "url" : "/path/to/data/dir",
    "update_rate" : "15m",
    "includes": [
      "*.*"
    ],
    "excludes": [
      "*.json"
    ],
    "json_support" : false,
    "xml_support" : false,
    "ignore_folders" : false,
    "attributes_support" : false,
    "raw_metadata" : false,
    "filename_as_id" : false,
    "add_filesize" : true,
    "remove_deleted" : true,
    "store_source" : false,
    "lang_detect" : false,
    "indexed_chars" : "10000"
  },
  "server" : {
    "hostname" : null,
    "port" : 22,
    "username" : null,
    "password" : null,
    "protocol" : "local",
    "pem_path" : null
  },
  "elasticsearch" : {
    "nodes" : [ {
      "host" : "127.0.0.1",
      "port" : 9200,
      "scheme" : "HTTP"
    } ],
    "index" : "docs",
    "type" : "doc",
    "bulk_size" : 100,
    "flush_interval" : "5s",
    "username" : "username",
    "password" : "password",
    "pipeline" : "pipeline-id-if-any"
  },
  "rest" : {
    "enabled" : false,
    "scheme" : "HTTP",
    "host" : "127.0.0.1",
    "port" : 8080,
    "port" : "fscrawler"
  }
}
```

Here is a list of existing top level settings:

|       Name      |                       Documentation                         |
|-----------------|-------------------------------------------------------------|
| `name`          | [the job name](#the-most-simple-crawler) (mandatory field)  |
| `fs`            | [Local FS settings](#local-fs-settings)                     |
| `elasticsearch` | [Elasticsearch settings](#elasticsearch-settings)           |
| `server`        | [SSH settings](#ssh-settings)                               |
| `rest`          | [REST settings](#rest-settings)                             |

### The most simple crawler

You can define the most simple crawler job by writing a `~/.fscrawler/test/_settings.json` file as follow:

```json
{
  "name" : "test"
}
```

This will scan every 15 minutes all documents available in `/tmp/es` dir and will index them into `test` index with
`doc` type. It will connect to an elasticsearch cluster running on `127.0.0.1`, port `9200`.

**Note**: `name` is a mandatory field.

### Local FS settings

Here is a list of Local FS settings (under `fs.` prefix)`:

|               Name               | Default value |                                 Documentation                                     |
|----------------------------------|---------------|-----------------------------------------------------------------------------------|
| `fs.url`                         | `"/tmp/es"`   | [Root directory](#root-directory)                                                 |
| `fs.update_rate`                 | `"15m"`       | [Update Rate](#update-rate)                                                       |
| `fs.includes`                    | `null`        | [Includes and Excludes](#includes-and-excludes)                                   |
| `fs.excludes`                    | `["~*"]`      | [Includes and Excludes](#includes-and-excludes)                                   |
| `fs.json_support`                | `false`       | [Indexing JSon docs](#indexing-json-docs)                                         |
| `fs.xml_support`                 | `false`       | [Indexing XML docs](#indexing-xml-docs) (from 2.2)                                |
| `fs.add_as_inner_object`         | `false`       | [Add as Inner Object](#add-as-inner-object)                                       |
| `fs.ignore_folders`              | `false`       | [Ignore folders](#iignore-folders) (from 2.2)                                     |
| `fs.attributes_support`          | `false`       | [Adding file attributes](#adding-file-attributes)                                 |
| `fs.raw_metadata`                | `true`        | [Disabling raw metadata](#disabling-raw-metadata)                                 |
| `fs.filename_as_id`              | `false`       | [Using Filename as `_id`](#using-filename-as-elasticsearch-_id)                   |
| `fs.add_filesize`                | `true`        | [Disabling file size field](#disabling-file-size-field)                           |
| `fs.remove_deleted`              | `true`        | [Ignore deleted files](#ignore-deleted-files)                                     |
| `fs.store_source`                | `false`       | [Storing binary source document](#storing-binary-source-document-base64-encoded)  |
| `fs.index_content`               | `true`        | [Ignore content](#ignore-content)                                                 |
| `fs.lang_detect`                 | `false`       | [Language detection](#language-detection) (from 2.2)                              |
| `fs.indexed_chars`               | `100000.0`    | [Extracted characters](#extracted-characters)                                     |
| `fs.checksum`                    | `null`        | [File signature](#file-signature)                                                 |

#### Root directory

Define `fs.url` property in your `~/.fscrawler/test/_settings.json` file:

```json
{
  "name" : "test",
  "fs" : {
    "url" : "/path/to/data/dir"
  }
}
```

For Windows users, use a form like `c:/tmp` or `c:\\tmp`.

#### Update rate

By default, `update_rate` is set to `15m`. You can modify this value using any compatible
[time unit](https://www.elastic.co/guide/en/elasticsearch/reference/current/common-options.html#time-units).

For example, here is a 15 minutes update rate:

```json
{
  "name": "test",
  "fs": {
    "update_rate": "15m"
  }
}
```

Or a 3 hours update rate:

```json
{
  "name": "test",
  "fs": {
    "update_rate": "3h"
  }
}
```

`update_rate` is the pause duration between the last time we read the file system and another run.
Which means that if you set it to `15m`, the next scan will happen on 15 minutes after the end of
the current scan, whatever its duration.

#### Includes and excludes

Let's say you want to index only docs like `*.doc` and `*.pdf` but `resume*`. So `resume_david.pdf` won't be indexed.

Define `fs.includes` and `fs.excludes` properties in your `~/.fscrawler/test/_settings.json` file:

```json
{
  "name" : "test",
  "fs": {
    "includes": [
      "*.doc",
      "*.pdf"
    ],
    "excludes": [
      "resume*"
    ]
  }
}
```

It also applies to directory names. So if you want to ignore `.ignore` dir, just add `.ignore` as an excluded name.
Note that `includes` does not apply to directory names but only to filenames.

By default, FS crawler will exclude files starting with `~`.

#### Indexing JSon docs

If you want to index JSon files directly without parsing with Tika, you can set `json_support` to `true`.
JSon contents will be stored directly under _source. If you need to keep JSon documents synchronized to the index, set option [Add as Inner Object](#add-as-inner-object) which stores additional metadata and the JSon contents under field `object`.
```json
{
  "name" : "test",
  "fs" : {
    "json_support" : true
  }
}
```

Of course, if you did not define a mapping before launching the crawler, Elasticsearch will auto guess the mapping.

#### Indexing XML docs

If you want to index XML files and convert them to JSON, you can set `xml_support` to `true`.
The content of XML files will be added directly under _source. If you need to keep XML documents synchronized to the index, set option [Add as Inner Object](#add-as-inner-object) which stores additional metadata and the XML contents under field `object`.

```json
{
  "name" : "test",
  "fs" : {
    "xml_support" : true
  }
}
```

Of course, if you did not define a mapping before launching the crawler, Elasticsearch will auto guess the mapping.

#### Add as Inner Object

The default settings store the contents of json and xml documents directly onto the _source element of elasticsearch documents. Thereby, there is no metadata about file and path settings, which are necessary to determine if a document is deleted or updated.
New files will however be added to the index, (determined by the file timestamp).

If you need to keep json or xml documents synchronized to elasticsearch, you should set this option.

```json
{
  "name" : "test",
  "fs" : {
    "add_as_inner_object" : true
  }
}
```

#### Ignore folders

By default FS Crawler will index folder names in the index using a specific `folder` type.
If you don't want to index those folders, you can set `ignore_folders` to `true`.

Note that in that case, FS Crawler won't be able to detect removed folders so any document has been indexed
in elasticsearch, it won't be removed when you remove or move the folder.

```json
{
  "name" : "test",
  "fs" : {
    "ignore_folders" : true
  }
}
```

##### Dealing with multiple types and multiple dirs

If you have more than one type, create as many crawlers as types:

`~/.fscrawler/test_type1/_settings.json`:

```json
{
  "name": "test_type1",
  "fs": {
	"url": "/tmp/type1",
	"json_support" : true
  },
  "elasticsearch": {
    "index": "mydocs",
    "type": "type1"
  }
}
```

`~/.fscrawler/test_type2/_settings.json`:

```json
{
  "name": "test_type2",
  "fs": {
	"url": "/tmp/type2",
	"json_support" : true
  },
  "elasticsearch": {
    "index": "mydocs",
    "type": "type2"
  }
}
```

`~/.fscrawler/test_type3/_settings.json`:

```json
{
  "name": "test_type3",
  "fs": {
	"url": "/tmp/type3",
	"xml_support" : true
  },
  "elasticsearch": {
    "index": "mydocs",
    "type": "type3"
  }
}
```

##### Dealing with multiple types within the same dir

You can also index many types from one single dir using two crawlers scanning the same dir and by setting
`includes` parameter:

`~/.fscrawler/test_type1.json`:

```json
{
  "name": "test_type1",
  "fs": {
	"url": "/tmp",
    "includes": [ "type1*.json" ],
	"json_support" : true
  },
  "elasticsearch": {
    "index": "mydocs",
    "type": "type1"
  }
}
```

`~/.fscrawler/test_type2.json`:

```json
{
  "name": "test_type2",
  "fs": {
	"url": "/tmp",
    "includes": [ "type2*.json" ],
	"json_support" : true
  },
  "elasticsearch": {
    "index": "mydocs",
    "type": "type2"
  }
}
```

`~/.fscrawler/test_type3.json`:

```json
{
  "name": "test_type3",
  "fs": {
	"url": "/tmp",
    "includes": [ "*.xml" ],
	"xml_support" : true
  },
  "elasticsearch": {
    "index": "mydocs",
    "type": "type3"
  }
}
```

#### Using filename as elasticsearch `_id`

Please note that the document `_id` is always generated (hash value) from the filename to avoid issues with
special characters in filename.
You can force to use the `_id` to be the filename using `filename_as_id` attribute:

```json
{
  "name" : "test",
  "fs" : {
    "filename_as_id" : true
  }
}
```

#### Adding file attributes

If you want to add file attributes such as `attributes.owner` and `attributes.group`, you can set `attributes_support` to `true`.

```json
{
  "name" : "test",
  "fs" : {
    "attributes_support" : true
  }
}
```

#### Disabling raw metadata

By default, FS Crawler will extract all found metadata within `meta.raw` object.
If you want to disable this feature, you can set `raw_metadata` to `false`.

```json
{
  "name" : "test",
  "fs" : {
    "raw_metadata" : false
  }
}
```

Generated raw metadata depends on the file format itself.

For example, a PDF document could generate:

* `"date" : "2016-07-07T08:37:42Z"`
* `"pdf:PDFVersion" : "1.5"`
* `"xmp:CreatorTool" : "Microsoft Word"`
* `"Keywords" : "keyword1, keyword2"`
* `"access_permission:modify_annotations" : "true"`
* `"access_permission:can_print_degraded" : "true"`
* `"subject" : "Test Tika Object"`
* `"dc:creator" : "David Pilato"`
* `"dcterms:created" : "2016-07-07T08:37:42Z"`
* `"Last-Modified" : "2016-07-07T08:37:42Z"`
* `"dcterms:modified" : "2016-07-07T08:37:42Z"`
* `"dc:format" : "application/pdf; version=1.5"`
* `"title" : "Test Tika title"`
* `"Last-Save-Date" : "2016-07-07T08:37:42Z"`
* `"access_permission:fill_in_form" : "true"`
* `"meta:save-date" : "2016-07-07T08:37:42Z"`
* `"pdf:encrypted" : "false"`
* `"dc:title" : "Test Tika title"`
* `"modified" : "2016-07-07T08:37:42Z"`
* `"cp:subject" : "Test Tika Object"`
* `"Content-Type" : "application/pdf"`
* `"X-Parsed-By" : "org.apache.tika.parser.DefaultParser"`
* `"creator" : "David Pilato"`
* `"meta:author" : "David Pilato"`
* `"dc:subject" : "keyword1, keyword2"`
* `"meta:creation-date" : "2016-07-07T08:37:42Z"`
* `"created" : "Thu Jul 07 10:37:42 CEST 2016"`
* `"access_permission:extract_for_accessibility" : "true"`
* `"access_permission:assemble_document" : "true"`
* `"xmpTPg:NPages" : "2"`
* `"Creation-Date" : "2016-07-07T08:37:42Z"`
* `"access_permission:extract_content" : "true"`
* `"access_permission:can_print" : "true"`
* `"meta:keyword" : "keyword1, keyword2"`
* `"Author" : "David Pilato"`
* `"access_permission:can_modify" : "true"`

Where a MP3 file would generate:

* `"xmpDM:genre" : "Vocal"`
* `"X-Parsed-By" : "org.apache.tika.parser.DefaultParser"`
* `"creator" : "David Pilato"`
* `"xmpDM:album" : "FS Crawler"`
* `"xmpDM:trackNumber" : "1"`
* `"xmpDM:releaseDate" : "2016"`
* `"meta:author" : "David Pilato"`
* `"xmpDM:artist" : "David Pilato"`
* `"dc:creator" : "David Pilato"`
* `"xmpDM:audioCompressor" : "MP3"`
* `"title" : "Test Tika"`
* `"xmpDM:audioChannelType" : "Stereo"`
* `"version" : "MPEG 3 Layer III Version 1"`
* `"xmpDM:logComment" : "Hello but reverted"`
* `"xmpDM:audioSampleRate" : "44100"`
* `"channels" : "2"`
* `"dc:title" : "Test Tika"`
* `"Author" : "David Pilato"`
* `"xmpDM:duration" : "1018.775146484375"`
* `"Content-Type" : "audio/mpeg"`
* `"samplerate" : "44100"`

As elasticsearch will by default to automatically guess the type, you could end up having conflicts between
metadata raw fields: a field which is first detected as a date but is getting for another document a value like
"in the seventies". In such a case, you could imagine forcing the mapping or defining an index mapping template.

Note that dots in metadata names will be replaced by a `:`. For example `PTEX.Fullbanner` will be indexed as
`PTEX:Fullbanner`.

#### Disabling file size field

By default, FS crawler will create a field to store the original file size in octets.
You can disable it using `add_filesize' option:

```json
{
  "name" : "test",
  "fs" : {
    "add_filesize" : false
  }
}
```

#### Ignore deleted files

If you don't want to remove indexed documents when you remove a file or a directory, you can
set `remove_deleted` to `false` (default to `true`):


```json
{
  "name" : "test",
  "fs" : {
    "remove_deleted" : false
  }
}
```

#### Ignore content

If you don't want to extract file content but only index filesystem metadata such as filename, date, size and path,
you can set `index_content` to `false` (default to `true`):


```json
{
  "name" : "test",
  "fs" : {
    "index_content" : false
  }
}
```


#### Language detection

From FS crawler 2.2, you can ask for language detection using `lang_detect` option:

```json
{
  "name" : "test",
  "fs" : {
    "lang_detect" : true
  }
}
```

In that case, a new field named `meta.language` is added to the generated JSon document.

If you are using elasticsearch 5.0 or superior, you can use this value to send your document to a specific index
using a [Node Ingest pipeline](#using-ingest-node-pipeline).

For example, you can define a pipeline named `langdetect` with:

```sh
PUT _ingest/pipeline/langdetect
{
  "description" : "langdetect pipeline",
  "processors" : [
    {
      "set": {
        "field": "_index"
        "value": "myindex-{{meta.language}}"
      }
    }
  ]
}
```

In FS crawler settings, set both `fs.lang_detect` and `elasticsearch.pipeline` options:

```json
{
  "name" : "test",
  "fs" : {
    "lang_detect" : true
  },
  "elasticsearch" : {
    "pipeline" : "langdetect"
  }
}
```

And then, a document containing french text will be sent to `myindex-fr`.
A document containing english text will be sent to `myindex-en`.

You can also imagine changing the field name from `content` to `content-fr` or `content-en`. That will help you
to define the correct analyzer to use.

Language detection might detect more than one language in a given text but only the most accurate will be set.
Which means that if you have a document containing 80% of french and 20% of english, the document will be marked
as `fr`.

Note that language detection is CPU and time consuming.

#### Storing binary source document (BASE64 encoded)

You can store in elasticsearch itself the binary document using `store_source` option:

```json
{
  "name" : "test",
  "fs" : {
    "store_source" : true
  }
}
```

In that case, a new field named `attachment` is added to the generated JSon document. This field is not indexed.
Default mapping for `attachment` field is:

```json
{
  "doc" : {
    "properties" : {
      "attachment" : {
        "type" : "binary",
        "doc_values" : false
      }
      // ... Other properties here
    }
  }
}
```

#### Extracted characters

By default FS crawler will extract only the first 100 000 characters.
But, you can set `indexed_chars` to `5000` in FS crawler settings in order to overwrite this default settings.

```json
{
  "name": "test",
  "fs": {
    "indexed_chars": "5000"
  }
}
```

This number can be either a fixed size, number of characters that is, or a percent using `%` sign.
The percentage value will be applied to the filesize to determine the number of character the crawler needs
to extract.

If you want to index only `80%` of filesize, define `indexed_chars` to `"80%"`.
Of course, if you want to index the full document, you can set this property to `"100%"`. Double values are also
supported so `"0.01%"` is also a correct value.

**Compressed files**: If your file is compressed, you might need to increase `indexed_chars` to more than `"100%"`.
For example, `"150%"`.

If you want to extract the full content, define `indexed_chars` to `"-1"`.

**Note**: Tika requires to allocate in memory a data structure to extract text. Setting `indexed_chars` to a high
number will require more memory!

#### File checksum

If you want FS crawler to generate a checksum for each file, set `checksum` to the algorithm you wish to use
to compute the checksum, such as `MD5` or `SHA-1`.

```json
{
  "name": "test",
  "fs": {
    "checksum": "MD5"
  }
}
```


### SSH settings

You can index files remotely using SSH.

Here is a list of SSH settings (under `server.` prefix)`:

|               Name               | Default value |                     Documentation                        |
|----------------------------------|---------------|----------------------------------------------------------|
| `server.hostname`                | `null`        | Hostname                                                 |
| `server.port`                    | `22`          | Port                                                     |
| `server.username`                | `null`        | [Username](#username--password)                          |
| `server.password`                | `null`        | [Password](#username--password)                          |
| `server.protocol`                | `"local"`     | Set it to `ssh`                                          |
| `server.pem_path`                | `null`        | [Using Username / PEM file](#using-username--pem-file)   |

#### Username / Password

Let's say you want to index from a remote server using SSH:

* FS URL: `/path/to/data/dir/on/server`
* Server: `mynode.mydomain.com`
* Username: `username`
* Password: `password`
* Protocol: `ssh` (default to `local`)
* Port: `22` (default to `22`)


```json
{
  "name" : "test",
  "fs" : {
    "url" : "/path/to/data/dir/on/server"
  },
  "server" : {
    "hostname" : "mynode.mydomain.com",
    "port" : 22,
    "username" : "username",
    "password" : "password",
    "protocol" : "ssh"
  }
}
```

#### Using Username / PEM file

Let's say you want to index from a remote server using SSH:

* FS URL: `/path/to/data/dir/on/server`
* Server: `mynode.mydomain.com`
* Username: `username`
* PEM File: `/path/to/private_key.pem`
* Protocol: `ssh` (default to `local`)
* Port: `22` (default to `22`)

```json
{
  "name" : "test",
  "fs" : {
    "url" : "/path/to/data/dir/on/server"
  },
  "server" : {
    "hostname" : "mynode.mydomain.com",
    "port" : 22,
    "username" : "username",
    "protocol" : "ssh",
	"pem_path": "/path/to/private_key.pem"
  }
}
```


### Elasticsearch settings

Here is a list of Elasticsearch settings (under `elasticsearch.` prefix)`:

|               Name               |    Default value     |                                 Documentation                                     |
|----------------------------------|----------------------|-----------------------------------------------------------------------------------|
| `elasticsearch.index`            | job name             | Index name. See [Index settings](#index-settings)                                 |
| `elasticsearch.type`             | `"doc"`              | Type name. See [Type name and mapping](#type-name-and-mapping)                    |
| `elasticsearch.bulk_size`        | `100`                | [Bulk settings](#bulk-settings)                                                   |
| `elasticsearch.flush_interval`   | `"5s"`               | [Bulk settings](#bulk-settings)                                                   |
| `elasticsearch.pipeline`         | `null`               | [Using Ingest Node Pipeline](#using-ingest-node-pipeline) (from 2.2)              |
| `elasticsearch.nodes`            |http://127.0.0.1:9200 | [Node settings](#node-settings)                                                   |
| `elasticsearch.username`         | `null`               | Username. See [Using credentials (X-Pack)](#using-credentials-x-pack) (from 2.2)  |
| `elasticsearch.password`         | `null`               | Password. See [Using credentials (X-Pack)](#using-credentials-x-pack) (from 2.2)  |

#### Index settings

By default, FS crawler will index your data in an index which name is the same as the crawler name (`name` property).
You can change it by setting `index` field:

```json
{
  "name" : "test",
  "elasticsearch" : {
    "index" : "docs"
  }
}
```

When FS crawler needs to create the index, it applies some default settings which are read from
`~/.fscrawler/_default/5/_settings.json`. You can read its content from
[the source](src/main/resources/fr/pilato/elasticsearch/crawler/fs/_default/5/_settings.json).

Settings define an analyzer named `fscrawler_path` which uses a
[path hierarchy tokenizer](https://www.elastic.co/guide/en/elasticsearch/reference/current/analysis-pathhierarchy-tokenizer.html).


#### Type name and mapping

By default, FS crawler will index your data using `doc` as the type name.
You can change it by setting `type` field:

```json
{
  "name" : "test",
  "elasticsearch" : {
    "type" : "mydocument"
  }
}
```

When the FS crawler detects a new type, it creates automatically a mapping for this type.
The default mapping is read from `~/.fscrawler/_default/5/doc.json` which you can read from
[the source](src/main/resources/fr/pilato/elasticsearch/crawler/fs/_default/5/doc.json).

You can also display the index mapping being used with Kibana

```
GET docs/doc/_mapping
```

or fall back to the command line

```sh
curl 'http://localhost:9200/docs/_mapping?pretty'
```

##### Creating your own mapping (analyzers)

If you want to define your own mapping to set analyzers for example, you can either push the mapping or define a
`~/.fscrawler/_default/5/doc.json` document which contains the mapping you wish **before starting the FS crawler**.

The following example uses a `french` analyzer to index the `content` field.

```json
{
  "properties" : {
    "attachment" : {
      "type" : "binary",
      "doc_values" : false
    },
    "attributes" : {
      "properties" : {
        "group" : {
          "type" : "keyword"
        },
        "owner" : {
          "type" : "keyword"
        }
      }
    },
    "content" : {
      "type" : "text",
      "analyzer" : "french"
    },
    "file" : {
      "properties" : {
        "content_type" : {
          "type" : "keyword"
        },
        "filename" : {
          "type" : "keyword"
        },
        "extension" : {
          "type" : "keyword"
        },
        "filesize" : {
          "type" : "long"
        },
        "indexed_chars" : {
          "type" : "long"
        },
        "indexing_date" : {
          "type" : "date",
          "format" : "dateOptionalTime"
        },
        "last_modified" : {
          "type" : "date",
          "format" : "dateOptionalTime"
        },
        "checksum": {
          "type": "keyword"
        },
        "url" : {
          "type" : "keyword",
          "index" : false
        }
      }
    },
    "meta" : {
      "properties" : {
        "author" : {
          "type" : "text"
        },
        "date" : {
          "type" : "date",
          "format" : "dateOptionalTime"
        },
        "keywords" : {
          "type" : "text"
        },
        "title" : {
          "type" : "text"
        },
        "language" : {
          "type" : "keyword"
        }
      }
    },
    "path" : {
      "properties" : {
        "encoded" : {
          "type" : "keyword"
        },
        "real" : {
          "type" : "keyword",
          "fields": {
            "tree": {
              "type" : "text",
              "analyzer": "fscrawler_path",
              "fielddata": true
            }
          }
        },
        "root" : {
          "type" : "keyword"
        },
        "virtual" : {
          "type" : "keyword",
          "fields": {
            "tree": {
              "type" : "text",
              "analyzer": "fscrawler_path",
              "fielddata": true
            }
          }
        }
      }
    }
  }
}
```

Note that if you want to push manually the mapping to elasticsearch you can use the classic REST calls:

```
# Create index (don't forget to add the fscrawler_path analyzer)
PUT docs
{
  "settings": {
    "analysis": {
      "analyzer": {
        "fscrawler_path": {
          "tokenizer": "fscrawler_path"
        }
      },
      "tokenizer": {
        "fscrawler_path": {
          "type": "path_hierarchy"
        }
      }
    }
  }
}

# Create the mapping
PUT docs/doc/_mapping
{
  // Same mapping as previously seen
}
```

##### Define explicit mapping/settings per job

Let's say you created a job named `job_name` and you are sending documents against an elasticsearch cluster
running version `5.x`.

If you create the following files, they will be picked up at job start time instead of the
[default ones](#autogenerated-mapping):

* `~/.fscrawler/{job_name}/_mappings/5/doc.json`
* `~/.fscrawler/{job_name}/_mappings/5/folder.json`
* `~/.fscrawler/{job_name}/_mappings/5/_settings.json`

You can do the same for other elasticsearch versions with:

* `~/.fscrawler/{job_name}/_mappings/1/doc.json` for 1.x series
* `~/.fscrawler/{job_name}/_mappings/1/folder.json` for 1.x series
* `~/.fscrawler/{job_name}/_mappings/1/_settings.json` for 1.x series
* `~/.fscrawler/{job_name}/_mappings/2/doc.json` for 2.x series
* `~/.fscrawler/{job_name}/_mappings/2/folder.json` for 2.x series
* `~/.fscrawler/{job_name}/_mappings/2/_settings.json` for 1.x series


##### Replace existing mapping

Unfortunately you can not change the mapping on existing data.
Therefore, you'll need first to remove existing index, which means remove all existing data, and then restart FS crawler
with the new mapping.

You might to try [elasticsearch Reindex API](https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-reindex.html) though.

##### Upgrading an existing mapping

It could happen that you would like to add new sub fields or are upgrading from a previous version of FS crawler
which requires that you upgrade the mapping before sending the first documents.

The `--upgrade_mapping` option will help you.

The following command will upgrade the mappings related to the `job_name` job and then will run normally:

```sh
bin/fscrawler job_name --upgrade_mapping
```

If you just want to upgrade the mapping without launching the job itself, you can use the [loop](#crawler-options) option:

```sh
bin/fscrawler job_name --upgrade_mapping --loop 0
```


#### Bulk settings

FS crawler is using bulks to send data to elasticsearch. By default the bulk is executed every 100 operations or 
every 5 seconds. You can change default settings using `bulk_size` and `flush_interval`:

```json
{
  "name" : "test",
  "elasticsearch" : {
    "bulk_size" : 1000,
    "flush_interval" : "2s"
  }
}
```

#### Using Ingest Node Pipeline

If you are using an elasticsearch cluster running a 5.0 or superior version, you
can use an Ingest Node pipeline to transform documents sent by FS crawler before they are actually indexed.

For example, if you have the following pipeline:

```sh
PUT _ingest/pipeline/fscrawler
{
  "description" : "fscrawler pipeline",
  "processors" : [
    {
      "set" : {
        "field": "foo",
        "value": "bar"
      }
    }
  ]
}
```

In FS crawler settings, set the `elasticsearch.pipeline` option:

```json
{
  "name" : "test",
  "elasticsearch" : {
    "pipeline" : "fscrawler"
  }
}
```

Note that this option is available from FS crawler 2.2.

#### Node settings

FS crawler is using elasticsearch REST layer to send data to your running cluster.
By default, it connects to `127.0.0.1` on port `9200` which are the default settings when
running a local node on your machine.

Of course, in production, you would probably change this and connect to a production cluster:

```json
{
  "name" : "test",
  "elasticsearch" : {
    "nodes" : [
      { "host" : "mynode1.mycompany.com", "port" : 9200, "scheme" : "HTTP" }
    ]
  }
}
```

You can define multiple nodes:

```json
{
  "name" : "test",
  "elasticsearch" : {
    "nodes" : [
      { "host" : "mynode1.mycompany.com", "port" : 9200, "scheme" : "HTTP" },
      { "host" : "mynode2.mycompany.com", "port" : 9200, "scheme" : "HTTP" },
      { "host" : "mynode3.mycompany.com", "port" : 9200, "scheme" : "HTTP" }
    ]
  }
}
```

You can use HTTPS instead of default HTTP (from 2.2):

```json
{
  "name" : "test",
  "elasticsearch" : {
    "nodes" : [
      { "host" : "CLUSTERID.eu-west-1.aws.found.io", "port" : 9243, "scheme" : "HTTPS" }
    ]
  }
}
```


#### Using Credentials (X-Pack)

If you secured your elasticsearch cluster with [X-Pack](https://www.elastic.co/downloads/x-pack), you can provide
`username` and `password` to FS crawler:

```json
{
  "name" : "test",
  "elasticsearch" : {
    "username" : "elastic",
    "password" : "changeme"
  }
}
```

**WARNING**: note that for the current version, the elasticsearch password is stored in plain text in
your job setting file.

A better practice is to only set the username or pass it with `--username elastic` option when starting
FS Crawler.

If the password is not defined, you will be prompted when starting the job:

```
22:46:42,528 INFO  [f.p.e.c.f.FsCrawler] Password for elastic:
```

#### Generated fields

FS crawler creates the following fields :

|         Field        |                Description                  |                    Example                  |
|----------------------|---------------------------------------------|---------------------------------------------|
| `content`            | Extracted content                           | `"This is my text!"`                        |
| `attachment`         | BASE64 encoded binary file                  | BASE64 Encoded document                     |
| `meta.author`        | Author if any in document metadata          | `"David Pilato"`                            |
| `meta.title`         | Title if any in document metadata           | `"My document title"`                       |
| `meta.date`          | Document date if any in document metadata   | `"2013-04-04T15:21:35"`                     |
| `meta.keywords`      | Keywords if any in document metadata        | `["river","fs","elasticsearch"]`            |
| `meta.language`      | Detected Language if any                    | `"fr"`                                      |
| `meta.raw`           | An object with all raw metadata             | `"meta.raw.channels" : "2"`                 |
| `file.content_type`  | Content Type                                | `"application/vnd.oasis.opendocument.text"` |
| `file.last_modified` | Last modification date                      | `1386855978000`                             |
| `file.indexing_date` | Indexing date                               | `"2013-12-12T13:50:58.758Z"`                |
| `file.filesize`      | File size in bytes                          | `1256362`                                   |
| `file.indexed_chars` | Extracted chars if `fs.indexed_chars` > 0   | `100000`                                    |
| `file.filename`      | Original file name                          | `"mydocument.pdf"`                          |
| `file.extension`     | Original file name extension (from 2.2)     | `"pdf"`                                     |
| `file.url`           | Original file url                           | `"file://tmp/mydir/otherdir/mydocument.pdf"`|
| `file.checksum`      | Checksum if `fs.checksum` set               | `"c32eafae2587bef4b3b32f73743c3c61"`        |
| `path.encoded`       | MD5 encoded file path (for internal use)    | `"112aed83738239dbfe4485f024cd4ce1"`        |
| `path.virtual`       | Relative path from root path                | `"/mydir/otherdir"`                         |
| `path.root`          | MD5 encoded root path (for internal use)    | `"112aed83738239dbfe4485f024cd4ce1"`        |
| `path.real`          | Actual real path name                       | `"/tmp/mydir/otherdir/mydocument.pdf"`      |
| `attributes.owner`   | Owner name                                  | `"david"`                                   |
| `attributes.group`   | Group name                                  | `"staff"`                                   |


Here is a typical JSON document generated by the crawler:

```json
{
   "file":{
      "filename":"test.odt",
      "extension":"odt",
      "last_modified":1386855978000,
      "indexing_date":"2013-12-12T13:50:58.758Z",
      "content_type":"application/vnd.oasis.opendocument.text",
      "url":"file:///tmp/testfs_metadata/test.odt",
      "indexed_chars":100000,
      "filesize":8355,
      "checksum":"c32eafae2587bef4b3b32f73743c3c61"
   },
   "path":{
      "encoded":"bceb3913f6d793e915beb70a4735592",
      "root":"bceb3913f6d793e915beb70a4735592",
      "virtual":"/",
      "real":"/tmp/testfs_metadata/test.odt"
   },
   "attributes": {
      "owner": "david",
      "group": "staff"
   },
   "meta":{
      "author":"David Pilato",
      "title":"Mon titre",
      "date":"2013-04-04T15:21:35",
      "keywords":[
         "fs",
         "elasticsearch",
         "crawler"
      ],
      "language":"fr"
   },
   "content":"Bonjour David\n\n\n"
}
```

#### Search examples

You can use the content field to perform full-text search on

```
GET docs/doc/_search
{
  "query" : {
    "match" : {
        "content" : "the quick brown fox"
    }
  }
}
```

You can use meta fields to perform search on.

```
GET docs/doc/_search
{
  "query" : {
    "term" : {
        "file.filename" : "mydocument.pdf"
    }
  }
}
```

Or run some aggregations on top of them, like:

```
GET docs/doc/_search
{
  "size": 0,
  "aggs": {
    "by_extension": {
      "terms": {
        "field": "file.extension"
      }
    }
  }
}
```

### REST service

From 2.2, FS crawler comes with a REST service available by default at `http://127.0.0.1:8080/fscrawler`.
To activate it, launch FS Crawler with `--rest` option.

#### FS Crawler status

To get an overview of the running service, you can call `GET /` endpoint:

```sh
curl http://127.0.0.1:8080/fscrawler/
```

It will give you a response similar to:

```json
{
  "ok" : true,
  "version" : "2.2",
  "elasticsearch" : "5.1.1",
  "settings" : {
    "name" : "fscrawler-rest-tests",
    "fs" : {
      "url" : "/tmp/es",
      "update_rate" : "15m",
      "json_support" : false,
      "filename_as_id" : false,
      "add_filesize" : true,
      "remove_deleted" : true,
      "store_source" : false,
      "index_content" : true,
      "attributes_support" : false,
      "raw_metadata" : true,
      "xml_support" : false,
      "index_folders" : true,
      "lang_detect" : false
    },
    "elasticsearch" : {
      "nodes" : [ {
        "host" : "127.0.0.1",
        "port" : 9200,
        "scheme" : "HTTP"
      } ],
      "index" : "fscrawler-rest-tests",
      "type" : "doc",
      "bulk_size" : 100,
      "flush_interval" : "5s",
      "username" : "elastic"
    },
    "rest" : {
      "scheme" : "HTTP",
      "host" : "127.0.0.1",
      "port" : 8080,
      "endpoint" : "fscrawler"
    }
  }
}
```

#### Uploading a binary document

To upload a binary, you can call `POST /_upload` endpoint:

```sh
echo "This is my text" > test.txt
curl -F "file=@test.txt" "http://127.0.0.1:8080/fscrawler/_upload"
```

It will give you a response similar to:

```json
{
  "ok" : true,
  "filename" : "test.txt",
  "url" : "http://127.0.0.1:9200/fscrawler-rest-tests/doc/dd18bf3a8ea2a3e53e2661c7fb53534"
}
```

The `url` represents the elasticsearch address of the indexed document.
If you call:

```sh
curl http://127.0.0.1:9200/fscrawler-rest-tests/doc/dd18bf3a8ea2a3e53e2661c7fb53534?pretty
```

You will get back your document as it has been stored by elasticsearch:

```json
{
  "_index" : "fscrawler-rest-tests",
  "_type" : "doc",
  "_id" : "dd18bf3a8ea2a3e53e2661c7fb53534",
  "_version" : 1,
  "found" : true,
  "_source" : {
    "content" : "This file contains some words.\n",
    "meta" : {
      "raw" : {
        "X-Parsed-By" : "org.apache.tika.parser.DefaultParser",
        "Content-Encoding" : "ISO-8859-1",
        "Content-Type" : "text/plain; charset=ISO-8859-1"
      }
    },
    "file" : {
      "extension" : "txt",
      "content_type" : "text/plain; charset=ISO-8859-1",
      "indexing_date" : "2017-01-04T21:01:08.043",
      "filename" : "test.txt"
    },
    "path" : {
      "virtual" : "test.txt",
      "real" : "test.txt"
    }
  }
}
```


If you started FS crawler in debug mode with `--debug` or if you pass `debug=true` query parameter,
then the response will be much more complete:

```sh
echo "This is my text" > test.txt
curl -F "file=@test.txt" "http://127.0.0.1:8080/fscrawler/_upload?debug=true"
```

will give

```json
{
  "ok" : true,
  "filename" : "test.txt",
  "url" : "http://127.0.0.1:9200/fscrawler-rest-tests/doc/dd18bf3a8ea2a3e53e2661c7fb53534",
  "doc" : {
    "content" : "This file contains some words.\n",
    "meta" : {
      "raw" : {
        "X-Parsed-By" : "org.apache.tika.parser.DefaultParser",
        "Content-Encoding" : "ISO-8859-1",
        "Content-Type" : "text/plain; charset=ISO-8859-1"
      }
    },
    "file" : {
      "extension" : "txt",
      "content_type" : "text/plain; charset=ISO-8859-1",
      "indexing_date" : "2017-01-04T14:05:10.325",
      "filename" : "test.txt"
    },
    "path" : {
      "virtual" : "test.txt",
      "real" : "test.txt"
    }
  }
}
```

#### Simulate Upload

If you want to get back the extracted content and its metadata but without indexing into elasticsearch
you can use `simulate=true` query parameter:

```sh
echo "This is my text" > test.txt
curl -F "file=@test.txt" "http://127.0.0.1:8080/fscrawler/_upload?debug=true&simulate=true"
```

#### Document ID

By default, FS crawler encodes the filename to generate an id. Which means that if you send 2 files
with the same filename `test.txt`, the second one will overwrite the first one because they will both share
the same ID.

You can force any id you wish by adding `id=YOUR_ID` in the form data:

```sh
echo "This is my text" > test.txt
curl -F "file=@test.txt" -F "id=my-test" "http://127.0.0.1:8080/fscrawler/_upload"
```

There is a specific id named `_auto_` where the ID will be autogenerated by elasticsearch.
It means that sending twice the same file will result in 2 different documents indexed.

#### REST settings

Here is a list of REST service settings (under `rest.` prefix)`:

|       Name       | Default value |             Documentation               |
|------------------|---------------|-----------------------------------------|
| `rest.scheme`    | `http`        | Scheme. Can be either `http` or `https` |
| `rest.host`      | `127.0.0.1`   | Bound host                              |
| `rest.port`      | `8080`        | Bound port                              |
| `rest.endpoint`  | `fscrawler`   | Endpoint                                |

REST service is running at `http://127.0.0.1:8080/fscrawler` by default.

You can change it using `rest` settings:

```json
{
  "name" : "test",
  "rest" : {
    "scheme" : "HTTP",
    "host" : "192.168.0.1",
    "port" : 8180,
    "endpoint" : "my_fscrawler"
  }
}
```

It also means that if you are running more than one instance of FS crawler locally, you can (must) change
the `port`.

# Tips and tricks

## Indexing on HDFS

There is no specific support for HDFS in FS crawler. But you can [mount your HDFS on your machine](https://wiki.apache.org/hadoop/MountableHDFS)
and run FS crawler on this mount point. You can also read details about
[HDFS NFS Gateway](http://hadoop.apache.org/docs/stable/hadoop-project-dist/hadoop-hdfs/HdfsNfsGateway.html).

## OCR integration (using Tika and Tesseract)

To deal with images containing text, just [install Tesseract](https://github.com/tesseract-ocr/tesseract/wiki). Tesseract will be auto-detected by Tika.
Then add an image (png, jpg, ...) into your Fscrawler [root directory](#root-directory). After the next index update, the text will be indexed and placed in "_source.content".

## Using docker

To use FS crawler with [docker](https://www.docker.com/), check
[docker-fscrawler](https://github.com/shadiakiki1986/docker-fscrawler) recipe.


# Developing on fscrawler project

If you are thinking of contributing on the project, which is highly appreciated, here are
some tricks which might help.

## Building

The project is built using [Apache Maven](https://maven.apache.org/).
It needs Java >= 1.8.

To build it, just run:

```sh
git clone https://github.com/dadoonet/fscrawler.git
cd fscrawler
mvn clean install
```

You can also skip running tests with:

```sh
mvn clean install -DskipTests
```

## Testing

Tests are now separated between unit tests and integration tests:

* Unit tests are defined in [fr.pilato.elasticsearch.crawler.fs.test.unit](src/test/java/fr/pilato/elasticsearch/crawler/fs/test/unit)
package.
* Integration tests are defined in [fr.pilato.elasticsearch.crawler.fs.test.integration](src/test/java/fr/pilato/elasticsearch/crawler/fs/test/integration)
package.

### Integration tests

Integration tests only run when an elasticsearch cluster is running at [127.0.0.1:9400](http://127.0.0.1:9400).
When running with maven, an elasticsearch instance is automatically downloaded from maven central, installed
and launched before integration tests start and stopped after integration tests.

This local test instance is installed under `target/integration-tests/run`.
If you have any trouble, you can stop any running instance, then run `mvn clean` to clean all left data.

Note that a PID file is generated in `target/integration-tests/run/es.pid`.

For debugging purpose, you can still run integration tests from your IDE.
Just download any version of elasticsearch you wish, and launch it with:

```sh
# elasticsearch 1.x
bin/elasticsearch -Des.http.port=9400 -Des.network.host=127.0.0.1
# elasticsearch 2.x
bin/elasticsearch -Des.http.port=9400
# elasticsearch 5.x
bin/elasticsearch -Ehttp.port=9400
```

Integration tests will detect the running instance and will not ignore anymore those tests.

You can also tell maven to run integration tests by deploying another version of elasticsearch:

```sh
# For elasticsearch 1.x series
mvn install -Pes-1x
mvn install -Pes-2x
```

By default, it will run integration tests against elasticsearch 5.x series cluster.

### Running tests against an external cluster

By default, FS Crawler will run tests against a cluster running at `127.0.0.1` on port `9400`.
It will detect if this cluster is secured with [X-Pack](https://www.elastic.co/downloads/x-pack) and if so, it
will try to authenticate with user `elastic` and password `changeme`.

But, if you want to run the test suite against another cluster, using other credentials, you can use the following
system parameters:

* `tests.cluster.host`: hostname or IP (defaults to `127.0.0.1`)
* `tests.cluster.port`: port (defaults to `9400`)
* `tests.cluster.scheme`: `HTTP` or `HTTPS` (defaults to `HTTP`)
* `tests.cluster.user`: username (defaults to `elastic`)
* `tests.cluster.pass`: password (defaults to `changeme`)

For example, if you have a cluster running on [Elastic Cloud](https://cloud.elastic.co/), you can use:

```sh
mvn clean install -Dtests.cluster.host=CLUSTERID.eu-west-1.aws.found.io -Dtests.cluster.port=9200 -Dtests.cluster.user=elastic -Dtests.cluster.pass=GENERATEDPASSWORD
```

or better:

```sh
mvn clean install -Dtests.cluster.host=CLUSTERID.eu-west-1.aws.found.io -Dtests.cluster.port=9243 -Dtests.cluster.scheme=HTTPS -Dtests.cluster.user=elastic -Dtests.cluster.pass=GENERATEDPASSWORD
```

### Running REST tests using another port

By default, FS crawler will run the integration tests using port `8080` for the REST service.
If you want to change it because this port is already used, you can start your tests with `-Dtests.rest.port=8280` which
will start the REST service on port `8280`


### Randomized testing

FS Crawler uses [Randomized testing framework](https://github.com/randomizedtesting/randomizedtesting).
In case of failure, it will print a line like:

```
REPRODUCE WITH:
mvn test -Dtests.seed=AC6992149EB4B547 -Dtests.class=fr.pilato.elasticsearch.crawler.fs.test.unit.tika.TikaDocParserTest -Dtests.method="testExtractFromRtf" -Dtests.locale=ga-IE -Dtests.timezone=Canada/Saskatchewan
```


It also exposes some parameters you can use at build time:

* `tests.output`: display test output. For example:

```sh
mvn install -Dtests.output=always
mvn install -Dtests.output=onError
```

* `tests.locale`: run the tests using a given Locale. For example:

```sh
mvn install -Dtests.locale=random
mvn install -Dtests.locale=fr-FR
```

* `tests.timezone`: run the tests using a given Timezone. For example:

```sh
mvn install -Dtests.timezone=random
mvn install -Dtests.timezone=CEST
mvn install -Dtests.timezone=-0200
```

* `tests.verbose`: adds running tests details while executing tests

```sh
mvn install -Dtests.verbose
```

* `tests.parallelism`: number of JVMs to start to run tests

```sh
mvn install -Dtests.parallelism=auto
mvn install -Dtests.parallelism=max
mvn install -Dtests.parallelism=1
```

* `tests.seed`: specify the seed to use to run the test suite if you need to reproduce a failure
given a specific test context.

```sh
mvn test -Dtests.seed=E776CE45185A6E7A
```

* `tests.leaveTemporary`: leave temporary files on disk

```sh
mvn test -Dtests.leaveTemporary
```


## Releasing

To release a version, just run:

```sh
./release.sh
```

The release script will:

* Create a release branch
* Replace SNAPSHOT version by the final version number
* Commit the change
* Run tests against elasticsearch 1.x series
* Run tests against elasticsearch 2.x series
* Run tests against elasticsearch 5.x series
* Build the final artifacts using release profile (signing artifacts and generating all needed files)
* Tag the version
* Prepare the announcement email
* Deploy to https://oss.sonatype.org/
* Prepare the next SNAPSHOT version
* Commit the change
* Release the Sonatype staging repository
* Merge the release branch to the branch we started from
* Push the changes to origin
* Announce the version on https://discuss.elastic.co/c/annoucements/community-ecosystem

You will be guided through all the steps.


# License

```
This software is licensed under the Apache 2 license, quoted below.

Copyright 2011-2017 David Pilato

Licensed under the Apache License, Version 2.0 (the "License"); you may not
use this file except in compliance with the License. You may obtain a copy of
the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
License for the specific language governing permissions and limitations under
the License.
```
