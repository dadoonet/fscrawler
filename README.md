FileSystem River for Elasticsearch
==================================

Welcome to the FS River Plugin for [Elasticsearch](http://www.elasticsearch.org/)

This river plugin helps to index documents from your local file system and using SSH.

**WARNING**: If you use this river in a multinode mode on different servers without SSH, you need to ensure that the river
can access files on the same mounting point. If not, when a node stop, the other node will _think_ that your
local dir is empty and will **erase** all your docs.

In order to install the plugin, run: 

```sh
bin/plugin -install fr.pilato.elasticsearch.river/fsriver/1.2.0
```

You need to install a version matching your Elasticsearch version:

|       Elasticsearch    |  FS River Plugin  |                                                            Docs                                                                    |
|------------------------|-------------------|------------------------------------------------------------------------------------------------------------------------------------|
|    master              | Build from source | See below                                                                                                                          |
|    es-1.x              | Build from source | [1.4.0-SNAPSHOT](https://github.com/dadoonet/fsriver/tree/es-1.x/#version-140-snapshot-for-elasticsearch-1x)                       |
|    es-1.3              | Build from source | [1.3.0-SNAPSHOT](https://github.com/dadoonet/fsriver/tree/es-1.3/#version-130-snapshot-for-elasticsearch-13)                       |
|    es-1.2              |     1.2.0         | [1.2.0](https://github.com/dadoonet/fsriver/tree/v1.2.0/#version-120-for-elasticsearch-12)                  |
|    es-1.0              |     1.0.0         | [1.0.0](https://github.com/dadoonet/fsriver/tree/v1.0.0/#filesystem-river-for-elasticsearch)                                       |
|    es-0.90             |     0.5.0         | [0.5.0](https://github.com/dadoonet/fsriver/tree/v0.5.0/#filesystem-river-for-elasticsearch)                                       |

To build a `SNAPSHOT` version, you need to build it with Maven:

```bash
mvn clean install
plugin --install fsriver \ 
       --url file:target/releases/fsriver-X.X.X-SNAPSHOT.zip
```


Build Status
------------

Thanks to cloudbees for the [build status](https://buildhive.cloudbees.com/job/dadoonet/job/fsriver/) : 
![build status](https://buildhive.cloudbees.com/job/dadoonet/job/fsriver/badge/icon "Build status")

Getting Started
===============

Creating a FS river
-------------------

You can create the most simple river as follow:

```
PUT _river/mydocs/_meta
{
  "type" : "fs"
}
```

This will scan every 15 minutes all documents available in `/esdir` dir will index them into `mydocs` index using 
`doc` type.

Choose scanned directory
------------------------

We create the river with the following properties :

* FS URL: `/tmp` or `c:\\tmp` if you use Microsoft Windows OS
* Get only docs like `*.doc` and `*.pdf`
* Don't index `resume*`


```
PUT _river/mydocs/_meta
{
  "type": "fs",
  "fs": {
	"url": "/tmp",
	"includes": "*.doc,*.pdf",
	"excludes": "resume"
  }
}
```

Setting update rate
-------------------

By default, `update_rate` is set to `15m`. You can modify this value using any compatible 
[time unit](http://www.elasticsearch.org/guide/en/elasticsearch/reference/current/common-options.html#time-units).

For example, here is a 15 minutes update rate.

```
PUT _river/mydocs/_meta
{
  "type": "fs",
  "fs": {
	"update_rate": "15m"
  }
}
```

Or a 3 hours update rate.

```
PUT _river/mydocs/_meta
{
  "type": "fs",
  "fs": {
	"update_rate": "3h"
  }
}
```

Adding another local FS river
-----------------------------

We add another river with the following properties :

* FS URL: `/tmp2`
* Update Rate: every hour
* Get only docs like `*.doc`, `*.xls` and `*.pdf`

By the way, we define to index in the same index/type as the previous one
(see [Bulk settings](#bulk-settings) for details):

* index: `docs`
* type: `doc`

```
PUT _river/mynewriver/_meta
{
  "type": "fs",
  "fs": {
	"url": "/tmp2",
	"update_rate": "1h",
	"includes": [ "*.doc" , "*.xls", "*.pdf" ]
  },
  "index": {
  	"index": "docs",
  	"type": "doc",
  	"bulk_size": 50
  }
}
```

Indexing using SSH
------------------

You can now index files remotely using SSH.

### Username / Password

* FS URL: `/tmp3`
* Server: `mynode.mydomain.com`
* Username: `username`
* Password: `password`
* Protocol: `ssh` (default to `local`)
* Port: `22` (default to `22`)
* Update Rate: every hour 
* Get only docs like `*.doc`, `*.xls` and `*.pdf`

```
PUT _river/mysshriver/_meta
{
  "type": "fs",
  "fs": {
	"url": "/tmp3",
	"server": "mynode.mydomain.com",
	"port": 22,
	"username": "username",
	"password": "password",
	"protocol": "ssh",
	"update_rate": "1h",
	"includes": [ "*.doc" , "*.xls", "*.pdf" ]
  }
}
```

### Using Username / PEM file


* FS URL: `/tmp3`
* Server: `mynode.mydomain.com`
* Username: `username`
* PEM File: `/path/to/private_key.pem`
* Protocol: `ssh` (default to `local`)
* Port: `22` (default to `22`)
* Update Rate: every hour
* Get only docs like `*.doc`, `*.xls` and `*.pdf`

```
PUT _river/mysshriver/_meta
{
  "type": "fs",
  "fs": {
	"url": "/tmp3",
	"server": "mynode.mydomain.com",
	"port": 22,
	"username": "username",
	"pem_path": "/path/to/private_key.pem",
	"protocol": "ssh",
	"update_rate": "1h",
	"includes": [ "*.doc" , "*.xls", "*.pdf" ]
  }
}
```

Searching for docs
------------------

This is a common use case in elasticsearch, we want to search for something ;-)

```
GET docs/doc/_search
{
  "query" : {
    "match" : {
        "_all" : "I am searching for something !"
    }
  }
}
```

Indexing JSon docs
------------------

If you want to index JSon files directly without parsing them through the attachment mapper plugin, you
can set `json_support` to `true`.

```
PUT _river/mydocs/_meta
{
  "type": "fs",
  "fs": {
	"url": "/tmp",
	"update_rate": "1h",
	"json_support" : true
  }
}
```

Of course, if you did not define a mapping prior creating the river, Elasticsearch will auto guess the mapping.

If you have more than one type, create as many rivers as types:

```
PUT _river/mydocs1/_meta
{
  "type": "fs",
  "fs": {
	"url": "/tmp/type1",
	"update_rate": "1h",
	"json_support" : true
  },
  "index": {
    "index": "mydocs",
    "type": "type1"
  }
}

PUT _river/mydocs2/_meta
{
  "type": "fs",
  "fs": {
	"url": "/tmp/type2",
	"update_rate": "1h",
	"json_support" : true
  },
  "index": {
    "index": "mydocs",
    "type": "type2"
  }
}
```

You can also index many types from one single dir using two rivers on the same dir and by setting
`includes` parameter:

```
PUT _river/mydocs1/_meta
{
  "type": "fs",
  "fs": {
	"url": "/tmp",
	"update_rate": "1h",
    "includes": [ "type1*.json" ],
	"json_support" : true
  },
  "index": {
    "index": "mydocs",
    "type": "type1"
  }
}

PUT _river/mydocs2/_meta
{
  "type": "fs",
  "fs": {
	"url": "/tmp",
	"update_rate": "1h",
    "includes": [ "type2*.json" ],
	"json_support" : true
  },
  "index": {
    "index": "mydocs",
    "type": "type2"
  }
}
```

Please note that the document `_id` is always generated (hash value) from the JSon filename to avoid issues with
special characters in filename.
You can force to use the `_id` to be the filename using `filename_as_id` attribute:

```
PUT _river/mydocs/_meta
{
  "type": "fs",
  "fs": {
	"url": "/tmp",
	"update_rate": "1h",
	"json_support": true,
	"filename_as_id": true
  }
}
```

Disabling file size field
-------------------------

By default, FSRiver will create a field to store the original file size in octet.
You can disable it using `add_filesize' option:

```
PUT _river/mydocs/_meta
{
  "type": "fs",
  "fs": {
	"url": "/tmp",
	"add_filesize": false
  }
}
```

Ignore deleted files
--------------------

If you don't want to remove indexed documents when you remove a file or a directory, you can
set `remove_deleted` to `false` (default to `true`):


```
PUT _river/mydocs/_meta
{
  "type": "fs",
  "fs": {
	"url": "/tmp",
	"remove_deleted": false
  }
}
```

Advanced
========

Suspend or restart a file river
-------------------------------

If you need to stop a river, you can call the `_stop' endpoint:

```
GET _river/mydocs/_stop
```

To restart the river from the previous point, just call `_start` end point:

```
GET _river/mydocs/_start
```

Autogenerated mapping
---------------------

When the FSRiver detect a new type, it creates automatically a mapping for this type.

```javascript
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

Creating your own mapping (analyzers)
-------------------------------------

If you want to define your own mapping to set analyzers for example, you can push the mapping **before** starting the FS River.

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

Generated fields
----------------

FS River creates the following fields :

|   Field (>= 0.4.0)   |   Field (< 0.4.0)    |                Description                  |                    Example                  |
|----------------------|----------------------|---------------------------------------------|---------------------------------------------|
| `content`            | `file.file`          | Extracted content                           | `"This is my text!"`                        |
| `attachment`         | `file`               | BASE64 encoded binary file                  | BASE64 Encoded document                     |
| `meta.author`        | `file.author`        | Author if any in document metadata          | `"David Pilato"`                            |
| `meta.title`         | `file.title`         | Title if any in document metadata           | `"My document title"`                       |
| `meta.date`          |                      | Document date if any in document metadata   | `"2013-04-04T15:21:35"`                     |
| `meta.keywords`      |                      | Keywords if any in document metadata        | `["river","fs","elasticsearch"]`            |
| `file.content_type`  | `file.content_type`  | Content Type                                | `"application/vnd.oasis.opendocument.text"` |
| `file.last_modified` |                      | Last modification date                      | `1386855978000`                             |
| `file.indexing_date` | `postDate`           | Indexing date                               | `"2013-12-12T13:50:58.758Z"`                |
| `file.filesize`      | `filesize`           | File size in bytes                          | `1256362`                                   |
| `file.indexed_chars` | `file.indexed_chars` | Extracted chars if `fs.indexed_chars` > 0   | `100000`                                    |
| `file.filename`      | `name`               | Original file name                          | `"mydocument.pdf"`                          |
| `file.url`           |                      | Original file url                           | `"file://tmp/mydir/otherdir/mydocument.pdf"`|
| `path.encoded`       | `pathEncoded`        | BASE64 encoded file path (for internal use) | `"112aed83738239dbfe4485f024cd4ce1"`        |
| `path.virtual`       | `virtualpath`        | Relative path from root path                | `"mydir/otherdir"`                          |
| `path.root`          | `rootpath`           | BASE64 encoded root path (for internal use) | `"112aed83738239dbfe4485f024cd4ce1"`        |
| `path.real`          |                      | Actual real path name                       | `"/tmp/mydir/otherdir/mydocument.pdf"`      |

Here is a typical JSON document generated by the river:

```javascript
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
         "river"
      ]
   },
   "content":"Bonjour David\n\n\n"
}
```


Advanced search
---------------

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

Disabling _source
-----------------

If you don't need to highlight your search responses nor need to get back the original file from
Elasticsearch, you can think about disabling `_source` field.

In that case, you need to store `file.filename` field. Otherwise, FSRiver won't be able to remove documents when
they disappear from your hard drive.

```javascript
{
  "doc" : {
    "_source" : { "enabled" : false },
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

Storing binary source document (BASE64 encoded)
-----------------------------------------------

You can store in elasticsearch itself the binary document using `store_source` option:

```
PUT _river/mydocs/_meta
{
  "type": "fs",
  "fs": {
	"url": "/tmp",
	"update_rate": "1h",
	"store_source": true
  }
}
```

In that case, a new stored field named `attachment` is added to the generated JSon document.
If you let FSRiver generates the mapping, FSRiver will exclude `attachment` field from
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

```javascript
{
  "doc" : {
    "_source" : {
      "excludes" : [ "attachment" ]
    },
    "properties" : {
      "attachment" : {
        "type" : "binary"
      },
      ... // Other properties here
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
      },
      ... // Other properties here
    }
  }
}
```

Extracted characters
--------------------

By default FSRiver will extract only a limited size of characters (100000).
But, you can set `indexed_chars` to `1` in FSRiver definition.

```
PUT _river/mydocs/_meta
{
  "type": "fs",
  "fs": {
    "url": "/tmp",
    "indexed_chars": 1
  }
}
```

That option will add a special field `_indexed_chars` to the document. It will be set to the filesize.
This field is used by mapper attachment plugin to define the number of extracted characters.

Setting `indexed_chars : x` will compute file size, multiply it with x and pass it to Tika using `_indexed_chars` field.

That means that a value of 0.8 will extract 20% less characters than the file size. A value of 1.5 will extract 50% more
characters than the filesize (think compressed files). A value of 1, will extract exactly the filesize.

Note that Tika requires to allocate in memory a data structure to extract text. Setting `indexed_chars` to a high
number will require more memory!

Bulk settings
=============

You can change some indexing settings:

* `index.index` sets the index name where your documents will be indexed (default to river name)
* `index.type` sets the type name for your documents (default to `doc`)
* `index.bulk_size` set the maximum number of documents per bulk before a bulk is sent to elasticsearch (default to `100`)
* `index.flush_interval` set the bulk flush interval frequency (default to `5s`). It will be use to process bulk even if
bulk is not fill with `bulk_size` documents.

For example:

```
PUT _river/myriver/_meta
{
  "type": "fs",
  "fs": {
	"url": "/sales"
  },
  "index": {
  	"index": "acme",
  	"type": "sales",
  	"bulk_size": 10,
  	"flush_interval": "30s"
  }
}
```


Migrating from version < 0.4.0
==============================

Some important changes have been done in FSRiver 0.4.0:

* You don't have to add attachment plugin anymore as we directly rely on Apache Tika.
* Fields have changed. You should look at [Generated Fields](#generated-fields) section
to know how the old fields have been renamed.


Settings list
=============

Here is a full list of existing settings:

|               Name               |                                  Documentation                                    |
|----------------------------------|-----------------------------------------------------------------------------------|
| `fs.url`                         | [Creating a Local FS river](#creating-a-local-fs-river)                           |
| `fs.update_rate`                 | [Creating a Local FS river](#creating-a-local-fs-river)                           |
| `fs.includes`                    | [Creating a Local FS river](#creating-a-local-fs-river)                           |
| `fs.excludes`                    | [Creating a Local FS river](#creating-a-local-fs-river)                           |
| `fs.server`                      | [Indexing using SSH](#indexing-using-ssh)                                         |
| `fs.port`                        | [Indexing using SSH](#indexing-using-ssh)                                         |
| `fs.username`                    | [Indexing using SSH](#indexing-using-ssh)                                         |
| `fs.password`                    | [Indexing using SSH](#indexing-using-ssh)                                         |
| `fs.protocol`                    | [Indexing using SSH](#indexing-using-ssh)                                         |
| `fs.pem_path`                    | [Indexing using SSH](#indexing-using-ssh)                                         |
| `fs.json_support`                | [Indexing JSon docs](#indexing-json-docs)                                         |
| `fs.filename_as_id`              | [Indexing JSon docs](#indexing-json-docs)                                         |
| `fs.add_filesize`                | [Disabling file size field](#disabling-file-size-field)                           |
| `fs.remove_deleted`              | [Ignore deleted files](#ignore-deleted-files)                                     |
| `fs.indexed_chars`               | [Extracted characters](#extracted-characters)                                     |
| `fs.store_source`                | [Storing binary source document](#storing-binary-source-document-base64-encoded)  |
| `index.index`                    | [Bulk settings](#bulk-settings)                                                   |
| `index.type`                     | [Bulk settings](#bulk-settings)                                                   |
| `index.bulk_size`                | [Bulk settings](#bulk-settings)                                                   |
| `index.flush_interval`           | [Bulk settings](#bulk-settings)                                                   |


Debug mode
==========

To activate traces (`DEBUG` or `TRACE` level), you need to modify `config/logging.yml` and set 
`fr.pilato.elasticsearch.river.fs` to the desired log level:


```yaml
es.logger.level: INFO
rootLogger: ${es.logger.level}, console, file
logger:
  # TRACE fsriver
  fr.pilato.elasticsearch.river.fs: TRACE
```


License
=======

```
This software is licensed under the Apache 2 license, quoted below.

Copyright 2011-2014 David Pilato

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
