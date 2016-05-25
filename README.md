# FileSystem Crawler for Elasticsearch

Welcome to the FS Crawler for [Elasticsearch](https://elastic.co/)

This crawler helps to index documents from your local file system and over SSH.
It crawls your file system and index new files, update existing ones and removes old ones.

You need to install a version matching your Elasticsearch version:

| Elasticsearch |  FS Crawler |                   Docs               |
|---------------|-------------|--------------------------------------|
|    es-2.0     | 2.0.0       | See below                            |


## Build Status

Thanks to Travis for the [build status](https://travis-ci.org/dadoonet/fscrawler): 
[![Build Status](https://travis-ci.org/dadoonet/fscrawler.svg)](https://travis-ci.org/dadoonet/fscrawler)


# Download fscrawler

FS Crawler binary is available on [Maven Central](https://repo1.maven.org/maven2/fr/pilato/elasticsearch/crawler/fscrawler/).
Just download the latest release (or any other specific version you want to try).

The filename ends with `.zip`.

For example, if you wish to download [fscrawler-2.0.0](https://repo1.maven.org/maven2/fr/pilato/elasticsearch/crawler/fscrawler/2.0.0/fscrawler-2.0.0.zip):

```sh
wget https://repo1.maven.org/maven2/fr/pilato/elasticsearch/crawler/fscrawler/2.0.0/fscrawler-2.0.0.zip
unzip fscrawler-2.0.0.zip
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

# Getting Started

**You need to have at least Java 1.8.**

```sh
bin/fscrawler job_name
```

FS crawler will read a local file (default to `~/.fscrawler/{job_name}.json`).
 If the file does not exist, FS crawler will propose to create your first job.

Once the crawler is running, it will write status information and statistics in:

* `~/.fscrawler/{job_name}.json`
* `~/.fscrawler/{job_name}_status.json`
* `~/.fscrawler/{job_name}_stats.json`

It means that if you stop the job at some point, FS crawler will restart it from where it stops.
If needed, you can manually edit / remove those files to restart.


You can also run:

```sh
bin/fscrawler
```

It will give you the list of existing jobs and will allow you to choose one.

## Crawler options

`--help` displays help
`--silent` runs in silent mode. No output is generated.
`--debug` runs in debug mode.
`--trace` runs in trace mode (more verbose than debug).
`--config_dir` defines directory where jobs are stored instead of default `~/.fscrawler`.

## Job file specification

The job file must comply to the following `json` specifications:

```json
{
  "name" : "job_name",
  "fs" : {
    "url" : "/path/to/data/dir",
    "update_rate" : "5s",
    "includes": [
      "*.*"
    ],
    "excludes": [
      "*.json"
    ],
    "json_support" : false,
    "filename_as_id" : false,
    "add_filesize" : true,
    "remove_deleted" : true,
    "store_source" : false,
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
      "port" : 9200
    } ],
    "index" : "docs",
    "type" : "doc",
    "bulk_size" : 100,
    "flush_interval" : "5s"
  }
}
```

## Settings list

Here is a full list of existing settings:

|               Name               | Default value |                                 Documentation                                     |
|----------------------------------|---------------|-----------------------------------------------------------------------------------|
| `name`                           |               | [the job name](#the-most-simple-crawler) (mandatory field)                        |
| `fs.url`                         | `"/tmp/es"`   | [Root directory](#root-directory)                                                 |
| `fs.update_rate`                 | `"15m"`       | [Update Rate](#update-rate)                                                       |
| `fs.includes`                    | `null`        | [Includes and Excludes](#includes-and-excludes)                                   |
| `fs.excludes`                    | `null`        | [Includes and Excludes](#includes-and-excludes)                                   |
| `fs.json_support`                | `false`       | [Indexing JSon docs](#indexing-json-docs)                                         |
| `fs.filename_as_id`              | `false`       | [Using Filename as `_id`](#using-filename-as-elasticsearch-_id)                   |
| `fs.add_filesize`                | `true`        | [Disabling file size field](#disabling-file-size-field)                           |
| `fs.remove_deleted`              | `true`        | [Ignore deleted files](#ignore-deleted-files)                                     |
| `fs.store_source`                | `false`       | [Storing binary source document](#storing-binary-source-document-base64-encoded)  |
| `fs.indexed_chars`               | `0.0`         | [Extracted characters](#extracted-characters)                                     |
| `server.hostname`                | `null`        | [Indexing using SSH](#indexing-using-ssh)                                         |
| `server.port`                    | `22`          | [Indexing using SSH](#indexing-using-ssh)                                         |
| `server.username`                | `null`        | [Indexing using SSH](#indexing-using-ssh)                                         |
| `server.password`                | `null`        | [Indexing using SSH](#username--password)                                         |
| `server.protocol`                | `"local"`     | [Indexing using SSH](#indexing-using-ssh)                                         |
| `server.pem_path`                | `null`        | [Using Username / PEM file](#using-username--pem-file)                            |
| `elasticsearch.index`            | job name      | [Index Name](#index-name)                                                         |
| `elasticsearch.type`             | `"doc"`       | [Type Name](#type-name)                                                           |
| `elasticsearch.bulk_size`        | `100`         | [Bulk settings](#bulk-settings)                                                   |
| `elasticsearch.flush_interval`   | `"5s"`        | [Bulk settings](#bulk-settings)                                                   |
| `elasticsearch.nodes`            |127.0.0.1:9200 | [Node settings](#node-settings)                                                   |


### The most simple crawler

You can define the most simple crawler job by writing a `~/.fscrawler/test.json` file as follow:

```json
{
  "name" : "test"
}
```

This will scan every 15 minutes all documents available in `/tmp/es` dir and will index them into `test` index with
`doc` type. It will connect to an elasticsearch cluster running on `127.0.0.1`, port `9200`.

**Note**: `name` is a mandatory field.

### Root directory

Define `fs.url` property in your `~/.fscrawler/test.json` file:

```json
{
  "name" : "test",
  "fs" : {
    "url" : "/path/to/data/dir"
  }
}
```

For Windows users, use a form like `c:/tmp` or `c:\\tmp`.

### Includes and excludes

Let's say you want to index only docs like `*.doc` and `*.pdf` but `resume*`. So `resume_david.pdf` won't be indexed.

Define `fs.includes` and `fs.excludes` properties in your `~/.fscrawler/test.json` file:

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


### Update rate

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

### Indexing using SSH

You can index files remotely using SSH.

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

# Searching for docs

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

# Indexing JSon docs

If you want to index JSon files directly without parsing with Tika, you can set `json_support` to `true`.

```json
{
  "name" : "test",
  "fs" : {
    "json_support" : true
  }
}
```

Of course, if you did not define a mapping before launching the crawler, Elasticsearch will auto guess the mapping.

## Dealing with multiple types and multiple dirs

If you have more than one type, create as many crawlers as types:

`~/.fscrawler/test_type1.json`:

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

`~/.fscrawler/test_type2.json`:

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

## Dealing with multiple types within the same dir

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

## Using filename as elasticsearch `_id`

Please note that the document `_id` is always generated (hash value) from the JSon filename to avoid issues with
special characters in filename.
You can force to use the `_id` to be the filename using `filename_as_id` attribute:

```json
{
  "name" : "test",
  "fs" : {
    "json_support" : true,
    "filename_as_id" : true
  }
}
```

# Disabling file size field

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

# Ignore deleted files

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

# Ignore content

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

# Advanced

## Autogenerated mapping

When the FS crawler detects a new type, it creates automatically a mapping for this type:

```json
{
  "doc" : {
    "properties" : {
      "content" : {
        "type" : "string",
        "store" : "yes"
      },
      "meta" : {
        "properties" : {
          "author" : {
              "type" : "string",
              "store" : "yes"
          },
          "title" : {
              "type" : "string",
              "store" : "yes"
          },
          "date" : {
              "type" : "date",
              "format" : "dateOptionalTime",
              "store" : "yes"
          },
          "keywords" : {
              "type" : "string",
              "store" : "yes"
          }
        }
      },
      "file" : {
        "properties" : {
          "content_type" : {
              "type" : "string",
              "analyzer" : "not_analyzed",
              "store" : "yes"
          },
          "last_modified" : {
              "type" : "date",
              "format" : "dateOptionalTime",
              "store" : "yes"
          },
          "indexing_date" : {
              "type" : "date",
              "format" : "dateOptionalTime",
              "store" : "yes"
          },
          "filesize" : {
              "type" : "long",
              "store" : "yes"
          },
          "indexed_chars" : {
              "type" : "long",
              "store" : "yes"
          },
          "filename" : {
              "type" : "string",
              "analyzer" : "not_analyzed",
              "store" : "yes"
          },
          "url" : {
              "type" : "string",
              "store" : "yes",
              "index" : "no"
          }
        }
      },
      "path" : {
        "properties" : {
          "encoded" : {
              "type" : "string",
              "store" : "yes",
              "index" : "not_analyzed"
          },
          "virtual" : {
              "type" : "string",
              "store" : "yes",
              "index" : "not_analyzed"
          },
          "root" : {
              "type" : "string",
              "store" : "yes",
              "index" : "not_analyzed"
          },
          "real" : {
              "type" : "string",
              "store" : "yes",
              "index" : "not_analyzed"
          }
        }
      }
    }
  }
}
```

## Creating your own mapping (analyzers)

If you want to define your own mapping to set analyzers for example, you can push the mapping **before** starting the 
FS crawler.

```
# Create index
PUT docs

# Create the mapping
PUT docs/doc/_mapping
{
  "doc" : {
    "properties" : {
      "content" : {
        "type" : "string",
        "store" : "yes",
        "analyzer" : "french"
      },
      "meta" : {
        "properties" : {
          "author" : {
              "type" : "string",
              "store" : "yes"
          },
          "title" : {
              "type" : "string",
              "store" : "yes"
          },
          "date" : {
              "type" : "date",
              "format" : "dateOptionalTime",
              "store" : "yes"
          },
          "keywords" : {
              "type" : "string",
              "store" : "yes"
          }
        }
      },
      "file" : {
        "properties" : {
          "content_type" : {
              "type" : "string",
              "analyzer" : "not_analyzed",
              "store" : "yes"
          },
          "last_modified" : {
              "type" : "date",
              "format" : "dateOptionalTime",
              "store" : "yes"
          },
          "indexing_date" : {
              "type" : "date",
              "format" : "dateOptionalTime",
              "store" : "yes"
          },
          "filesize" : {
              "type" : "long",
              "store" : "yes"
          },
          "indexed_chars" : {
              "type" : "long",
              "store" : "yes"
          },
          "filename" : {
              "type" : "string",
              "analyzer" : "not_analyzed",
              "store" : "yes"
          },
          "url" : {
              "type" : "string",
              "store" : "yes",
              "index" : "no"
          }
        }
      },
      "path" : {
        "properties" : {
          "encoded" : {
              "type" : "string",
              "store" : "yes",
              "index" : "not_analyzed"
          },
          "virtual" : {
              "type" : "string",
              "store" : "yes",
              "index" : "not_analyzed"
          },
          "root" : {
              "type" : "string",
              "store" : "yes",
              "index" : "not_analyzed"
          },
          "real" : {
              "type" : "string",
              "store" : "yes",
              "index" : "not_analyzed"
          }
        }
      }
    }
  }
}
```

## Generated fields

FS crawler creates the following fields :

|         Field        |                Description                  |                    Example                  |
|----------------------|---------------------------------------------|---------------------------------------------|
| `content`            | Extracted content                           | `"This is my text!"`                        |
| `attachment`         | BASE64 encoded binary file                  | BASE64 Encoded document                     |
| `meta.author`        | Author if any in document metadata          | `"David Pilato"`                            |
| `meta.title`         | Title if any in document metadata           | `"My document title"`                       |
| `meta.date`          | Document date if any in document metadata   | `"2013-04-04T15:21:35"`                     |
| `meta.keywords`      | Keywords if any in document metadata        | `["river","fs","elasticsearch"]`            |
| `file.content_type`  | Content Type                                | `"application/vnd.oasis.opendocument.text"` |
| `file.last_modified` | Last modification date                      | `1386855978000`                             |
| `file.indexing_date` | Indexing date                               | `"2013-12-12T13:50:58.758Z"`                |
| `file.filesize`      | File size in bytes                          | `1256362`                                   |
| `file.indexed_chars` | Extracted chars if `fs.indexed_chars` > 0   | `100000`                                    |
| `file.filename`      | Original file name                          | `"mydocument.pdf"`                          |
| `file.url`           | Original file url                           | `"file://tmp/mydir/otherdir/mydocument.pdf"`|
| `path.encoded`       | MD5 encoded file path (for internal use)    | `"112aed83738239dbfe4485f024cd4ce1"`        |
| `path.virtual`       | Relative path from root path                | `"mydir/otherdir"`                          |
| `path.root`          | MD5 encoded root path (for internal use)    | `"112aed83738239dbfe4485f024cd4ce1"`        |
| `path.real`          | Actual real path name                       | `"/tmp/mydir/otherdir/mydocument.pdf"`      |

Here is a typical JSON document generated by the crawler:

```json
{
   "file":{
      "filename":"test.odt",
      "last_modified":1386855978000,
      "indexing_date":"2013-12-12T13:50:58.758Z",
      "content_type":"application/vnd.oasis.opendocument.text",
      "url":"file:///tmp/testfs_metadata/test.odt",
      "indexed_chars":100000,
      "filesize":8355
   },
   "path":{
      "encoded":"bceb3913f6d793e915beb70a4735592",
      "root":"bceb3913f6d793e915beb70a4735592",
      "virtual":"",
      "real":"/tmp/testfs_metadata/test.odt"
   },
   "meta":{
      "author":"David Pilato",
      "title":"Mon titre",
      "date":"2013-04-04T15:21:35",
      "keywords":[
         "fs",
         "elasticsearch",
         "crawler"
      ]
   },
   "content":"Bonjour David\n\n\n"
}
```


## Advanced search

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

## Storing binary source document (BASE64 encoded)

You can store in elasticsearch itself the binary document using `store_source` option:

```json
{
  "name" : "test",
  "fs" : {
    "store_source" : true
  }
}
```

In that case, a new stored field named `attachment` is added to the generated JSon document.
If you let FS crawler generates the mapping, FS crawler will exclude `attachment` field from
`_source` to save some disk space.

That means you need to ask for field `attachment` when querying:

```
GET mydocs/doc/_search
{
  "fields" : ["attachment", "_source"],
  "query":{
    "match_all" : {}
  }
}
```

Default generated mapping in this case is:

```json
{
  "doc" : {
    "_source" : {
      "excludes" : [ "attachment" ]
    },
    "properties" : {
      "attachment" : {
        "type" : "binary"
      }
      // ... Other properties here
    }
  }
}
```

You can force not to store `attachment` field and keep `attachment` in `_source`:

```
# Create index
PUT docs

# Create the mapping
PUT docs/doc/_mapping
{
  "doc" : {
    "properties" : {
      "attachment" : {
        "type" : "binary",
        "store" : "no"
      }
      // ... Other properties here
    }
  }
}
```

## Extracted characters

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

# Elasticsearch settings

You can change elasticsearch settings within `elasticsearch` settings object.

## Index name

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

## Type name

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

## Bulk settings

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

## Node settings

FS crawler is using elasticsearch transport client to send data to your running cluster.
By default, it connects to `127.0.0.1` on port `9300` which are the default settings when
running a local node on your machine.

Of course, in production, you would probably change this and connect to a production cluster:

```json
{
  "name" : "test",
  "elasticsearch" : {
    "nodes" : [
      { "host" : "mynode1.mycompany.com", "port" : 9300 }
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
      { "host" : "mynode1.mycompany.com", "port" : 9300 },
      { "host" : "mynode2.mycompany.com", "port" : 9300 },
      { "host" : "mynode3.mycompany.com", "port" : 9300 }
    ]
  }
}
```

**Note:** the `cluster.name` does not have to be set as it's ignored.


# License

```
This software is licensed under the Apache 2 license, quoted below.

Copyright 2011-2015 David Pilato

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
