FileSystem River for Elasticsearch
==================================

Welcome to the FS River Plugin for [Elasticsearch](http://www.elasticsearch.org/)

This river plugin helps to index documents from your local file system.

*WARNING*: If you use this river in a multinode mode on differents servers, you need to ensure that the river can access files on the same mounting point. If not, when a node stop, the other node will _think_ that your local dir is empty and will *erase* all your docs.

*WARNING*: starting from 0.0.3, you need to have the [Attachment Plugin](https://github.com/elasticsearch/elasticsearch-mapper-attachments). It's not included anymore
in the distribution.

Versions
--------

<table>
	<thead>
		<tr>
			<td>FS River Plugin</td>
			<td>ElasticSearch</td>
			<td>Attachment Plugin</td>
			<td>Release date</td>
		</tr>
	</thead>
	<tbody>
		<tr>
			<td>master (0.3.0-SNAPSHOT)</td>
			<td>0.90.0</td>
			<td>1.7.0</td>
			<td>31/05/2013 ?</td>
		</tr>
        <tr>
			<td>master (0.2.0-SNAPSHOT)</td>
			<td>0.90.0</td>
			<td>1.7.0</td>
			<td>30/04/2013</td>
		</tr>
		<tr>
			<td>0.1.0</td>
			<td>0.90.0.Beta1</td>
			<td>1.6.0</td>
			<td>15/03/2013</td>
		</tr>
		<tr>
			<td>0.0.3</td>
			<td>0.20.4</td>
			<td>1.6.0</td>
			<td>12/02/2013</td>
		</tr>
		<tr>
			<td>0.0.2</td>
			<td>0.19.8</td>
			<td>1.4.0</td>
            <td>16/07/2012</td>
		</tr>
		<tr>
			<td>0.0.1</td>
			<td>0.19.4</td>
			<td>1.4.0</td>
			<td>19/06/2012</td>
		</tr>
	</tbody>
</table>


Build Status
------------

Thanks to cloudbees for the [build status](https://buildhive.cloudbees.com/job/dadoonet/job/fsriver/) : 
![build status](https://buildhive.cloudbees.com/job/dadoonet/job/fsriver/badge/icon "Build status")

[![Test trends](https://buildhive.cloudbees.com/job/dadoonet/job/fsriver/test/trend)](https://buildhive.cloudbees.com/job/dadoonet/job/fsriver/)


Getting Started
===============

Installation
------------

Just type :

```sh
bin/plugin -install fr.pilato.elasticsearch.river/fsriver/0.2.0
```

This will do the job...

```
-> Installing fr.pilato.elasticsearch.river/fsriver/0.2.0...
Trying http://download.elasticsearch.org/fr.pilato.elasticsearch.river/fsriver/fsriver-0.2.0.zip...
Trying http://search.maven.org/remotecontent?filepath=fr/pilato/elasticsearch/river/fsriver/0.2.0/fsriver-0.2.0.zip...
Trying https://oss.sonatype.org/service/local/repositories/releases/content/fr/pilato/elasticsearch/river/fsriver/0.2.0/fsriver-0.2.0.zip...
Downloading ......DONE
Installed fsriver
```

Creating a FS river
-------------------

We create first an index to store our *documents* :

```sh
curl -XPUT 'localhost:9200/mydocs/' -d '{}'
```

We create the river with the following properties :

* FS URL : `/tmp` or `c:\\tmp` if you use Microsoft Windows OS
* Update Rate : every 15 minutes (15 * 60 * 1000 = 900000 ms)
* Get only docs like `*.doc` and `*.pdf`
* Don't index `resume*`


```sh
curl -XPUT 'localhost:9200/_river/mydocs/_meta' -d '{
  "type": "fs",
  "fs": {
	"url": "/tmp",
	"update_rate": 900000,
	"includes": "*.doc,*.pdf",
	"excludes": "resume"
  }
}'
```

Adding another FS river
-----------------------

We add another river with the following properties :

* FS URL : `/tmp2`
* Update Rate : every hour (60 * 60 * 1000 = 3600000 ms)
* Get only docs like `*.doc`, `*.xls` and `*.pdf`

By the way, we define to index in the same index/type as the previous one:

* index: `docs`
* type: `doc`

```sh
curl -XPUT 'localhost:9200/_river/mynewriver/_meta' -d '{
  "type": "fs",
  "fs": {
	"url": "/tmp2",
	"update_rate": 3600000,
	"includes": [ "*.doc" , "*.xls", "*.pdf" ]
  },
  "index": {
  	"index": "mydocs",
  	"type": "doc",
  	"bulk_size": 50
  }
}'
```

Searching for docs
------------------

This is a common use case in elasticsearch, we want to search for something ;-)

```sh
curl -XGET http://localhost:9200/docs/doc/_search -d '{
  "query" : {
    "text" : {
        "_all" : "I am searching for something !"
    }
  }
}'
```

Indexing JSon docs (>= 0.0.3)
-----------------------------

If you want to index JSon files directly without parsing them through the attachment mapper plugin, you
can set `json_support` to `true`.

```sh
curl -XPUT 'localhost:9200/_river/mydocs/_meta' -d '{
  "type": "fs",
  "fs": {
	"url": "/tmp",
	"update_rate": 3600000,
	"json_support" : true
  },
  "index": {
    "index": "mydocs",
    "type": "doc",
    "bulk_size": 50
  }
}'
```

Of course, if you did not define a mapping prior creating the river, Elasticsearch will auto guess the mapping.

If you have more than one type, create as many rivers as types:

```sh
curl -XPUT 'localhost:9200/_river/mydocs1/_meta' -d '{
  "type": "fs",
  "fs": {
	"url": "/tmp/type1",
	"update_rate": 3600000,
	"json_support" : true
  },
  "index": {
    "index": "mydocs",
    "type": "type1",
    "bulk_size": 50
  }
}'

curl -XPUT 'localhost:9200/_river/mydocs2/_meta' -d '{
  "type": "fs",
  "fs": {
	"url": "/tmp/type2",
	"update_rate": 3600000,
	"json_support" : true
  },
  "index": {
    "index": "mydocs",
    "type": "type2",
    "bulk_size": 50
  }
}'
```

You can also index many types from one single dir using two rivers on the same dir and by setting
`includes` parameter:

```sh
curl -XPUT 'localhost:9200/_river/mydocs1/_meta' -d '{
  "type": "fs",
  "fs": {
	"url": "/tmp",
	"update_rate": 3600000,
    "includes": [ "type1*.json" ],
	"json_support" : true
  },
  "index": {
    "index": "mydocs",
    "type": "type1",
    "bulk_size": 50
  }
}'

curl -XPUT 'localhost:9200/_river/mydocs2/_meta' -d '{
  "type": "fs",
  "fs": {
	"url": "/tmp",
	"update_rate": 3600000,
    "includes": [ "type2*.json" ],
	"json_support" : true
  },
  "index": {
    "index": "mydocs",
    "type": "type2",
    "bulk_size: 50
  }
}'
```

Please note that the document `_id` is always generated (hash value) from the JSon filename to avoid issues with
special characters in filename.
You can force to use the `_id` to be the filename using `filename_as_id` attribute:

```sh
curl -XPUT 'localhost:9200/_river/mydocs/_meta' -d '{
  "type": "fs",
  "fs": {
	"url": "/tmp",
	"update_rate": 3600000,
	"json_support": true,
	"filename_as_id": true
  },
  "index": {
    "index": "mydocs",
    "type": "doc",
    "bulk_size": 50
  }
}'
```

Disabling file size field (>= 0.2.0)
------------------------------------

By default, FSRiver will create a field to store the original file size in octet.
You can disable it using `add_filesize' option:

```sh
curl -XPUT 'localhost:9200/_river/mydocs/_meta' -d '{
  "type": "fs",
  "fs": {
	"url": "/tmp",
	"add_filesize": false
  }
}'
```

Suspend or restart a file river (>= 0.2.0)
------------------------------------------

If you need to stop a river, you can call the `_stop' endpoint:

```sh
curl 'localhost:9200/_river/mydocs/_stop'
```

To restart the river from the previous point, just call `_start` end point:

```sh
curl 'localhost:9200/_river/mydocs/_start'
```


Advanced
========

Autogenerated mapping
---------------------

When the FSRiver detect a new type, it creates automatically a mapping for this type.

```javascript
{
  "doc" : {
    "properties" : {
      "file" : {
        "type" : "attachment",
        "path" : "full",
        "fields" : {
          "file" : {
            "type" : "string",
            "store" : "yes",
            "term_vector" : "with_positions_offsets"
          },
          "author" : {
            "type" : "string"
          },
          "title" : {
            "type" : "string",
            "store" : "yes"
          },
          "name" : {
            "type" : "string"
          },
          "date" : {
            "type" : "date",
            "format" : "dateOptionalTime"
          },
          "keywords" : {
            "type" : "string"
          },
          "content_type" : {
            "type" : "string",
            "store" : "yes"
          }
        }
      },
      "name" : {
        "type" : "string",
        "analyzer" : "keyword"
      },
      "pathEncoded" : {
        "type" : "string",
        "analyzer" : "keyword"
      },
      "postDate" : {
        "type" : "date",
        "format" : "dateOptionalTime"
      },
      "rootpath" : {
        "type" : "string",
        "analyzer" : "keyword"
      },
      "virtualpath" : {
        "type" : "string",
        "analyzer" : "keyword"
      },
      "filesize" : {
        "type" : "long"
      }
    }
  }
}
```

Creating your own mapping (analyzers)
-------------------------------------

If you want to define your own mapping to set analyzers for example, you can push the mapping before starting the FS River.

```javascript
{
  "doc" : {
    "properties" : {
      "file" : {
        "type" : "attachment",
        "path" : "full",
        "fields" : {
          "file" : {
            "type" : "string",
            "store" : "yes",
            "term_vector" : "with_positions_offsets",
            "analyzer" : "french"
          },
          "author" : {
            "type" : "string"
          },
          "title" : {
            "type" : "string",
            "store" : "yes"
          },
          "name" : {
            "type" : "string"
          },
          "date" : {
            "type" : "date",
            "format" : "dateOptionalTime"
          },
          "keywords" : {
            "type" : "string"
          },
          "content_type" : {
            "type" : "string",
            "store" : "yes"
          }
        }
      },
      "name" : {
        "type" : "string",
        "analyzer" : "keyword"
      },
      "pathEncoded" : {
        "type" : "string",
        "analyzer" : "keyword"
      },
      "postDate" : {
        "type" : "date",
        "format" : "dateOptionalTime"
      },
      "rootpath" : {
        "type" : "string",
        "analyzer" : "keyword"
      },
      "virtualpath" : {
        "type" : "string",
        "analyzer" : "keyword"
      },
      "filesize" : {
        "type" : "long"
      }
    }
  }
}
```

To send mapping to Elasticsearch, refer to the [Put Mapping API](http://www.elasticsearch.org/guide/reference/api/admin-indices-put-mapping.html)

Meta fields
-----------

FS River creates some meta fields :

<table>
	<thead>
		<tr>
			<td>Field</td>
			<td>Description</td>
			<td>Example</td>
		</tr>
	</thead>
	<tbody>
		<tr>
			<td>name</td>
			<td>Original file name</td>
			<td>mydocument.pdf</td>
		</tr>
		<tr>
			<td>pathEncoded</td>
			<td>BASE64 encoded file path (for internal use)</td>
			<td>112aed83738239dbfe4485f024cd4ce1</td>
		</tr>
		<tr>
			<td>postDate</td>
			<td>Indexing date</td>
			<td>1312893360000</td>
		</tr>
		<tr>
			<td>rootpath</td>
			<td>BASE64 encoded root path (for internal use)</td>
			<td>112aed83738239dbfe4485f024cd4ce1</td>
		</tr>
		<tr>
			<td>virtualpath</td>
			<td>Relative path</td>
			<td>mydir/otherdir</td>
		</tr>
		<tr>
			<td>filesize</td>
			<td>File size in octet</td>
			<td>1256362</td>
		</tr>
    </tbody>
</table>

Advanced search
---------------

You can use meta fields to perform search on.

```sh
curl -XGET http://localhost:9200/docs/doc/_search -d '{
  "query" : {
    "term" : {
        "name" : "mydocument.pdf"
    }
  }
}'
```

Disabling _source
-----------------

If you don't need to highlight your search responses nor need to get back the original file from
Elasticsearch, you can think about disabling `_source` field.

In that case, you need to store `name` field. Otherwise, FSRiver won't be able to remove documents when they disappear
from your hard drive.

```javascript
{
  "doc" : {
    "_source" : { "enabled" : false },
    "properties" : {
      "file" : {
        "type" : "attachment",
        "path" : "full",
        "fields" : {
          "file" : {
            "type" : "string",
            "store" : "yes",
            "term_vector" : "with_positions_offsets"
          },
          "author" : {
            "type" : "string"
          },
          "title" : {
            "type" : "string",
            "store" : "yes"
          },
          "name" : {
            "type" : "string"
          },
          "date" : {
            "type" : "date",
            "format" : "dateOptionalTime"
          },
          "keywords" : {
            "type" : "string"
          },
          "content_type" : {
            "type" : "string",
            "store" : "yes"
          }
        }
      },
      "name" : {
        "type" : "string",
        "analyzer" : "keyword",
        "store" : true
      },
      "pathEncoded" : {
        "type" : "string",
        "analyzer" : "keyword"
      },
      "postDate" : {
        "type" : "date",
        "format" : "dateOptionalTime"
      },
      "rootpath" : {
        "type" : "string",
        "analyzer" : "keyword"
      },
      "virtualpath" : {
        "type" : "string",
        "analyzer" : "keyword"
      },
      "filesize" : {
        "type" : "long"
      }
    }
  }
}
```

Extracted characters
--------------------

By default the mapper attachment plugin extracts only a limited size of characters (100000 by default).
Setting `index.mapping.attachment.indexed_chars` property on your node may help to index bigger files.

But, you can also have a finer control and set `indexed_chars` to `1` in FSRiver definition.
That option will add a special field `_indexed_chars` to the document. It will be set to the filesize.
This field is used by mapper attachment plugin to define the number of extracted characters.

* `indexed_chars : 0` (default) will use default mapper attachment settings (`index.mapping.attachment.indexed_chars`)
* `indexed_chars : x` will compute file size, multiply it with x and pass it to Tika using `_indexed_chars` field.

That means that a value of 0.8 will extract 20% less characters than the file size. A value of 1.5 will extract 50% more characters than the filesize (think compressed files).
A value of 1, will extract exactly the filesize.


Get content_type
-------------------------

By default, `content_type` is detected by mapper attachment plugin and stored in documents. So, you can easily access
it:

```sh
curl -XPOST http://localhost:9200/mydocs/doc/_search -d '{
  "fields" : ["file.content_type", "_source"],
  "query":{
    "match_all" : {}
  }
}'
```

gives:

```javascript
{
  "took" : 19,
  "timed_out" : false,
  "_shards" : {
    "total" : 5,
    "successful" : 5,
    "failed" : 0
  },
  "hits" : {
    "total" : 1,
    "max_score" : 1.0,
    "hits" : [ {
      "_index" : "fsrivermetadatatest",
      "_type" : "doc",
      "_id" : "fb6115c44876aa1e94cc4f86b03ba93",
      "_score" : 1.0,
      "fields" : {
        "file.content_type" : "application/vnd.oasis.opendocument.text",
        "_source" : "..."
      }
    } ]
  }
}
```

Storing extracted content
-------------------------

If you need to store and retrieve as is extracted content by the mapper attachment plugin, you simply
have to set `store` to `yes` for your `file` field in your mapping:

```javascript
{
  "doc": {
    "properties": {
      "file": {
        "type": "attachment",
        "path": "full",
        "fields": {
          "file": {
            "type": "string",
            "store": "yes",
            "term_vector": "with_positions_offsets"
          }
        }
      }
    }
  }
}
````

Then, you can extract document content using fields property when searching:

```sh
curl -XPOST http://localhost:9200/mydocs/doc/_search -d '{
  "fields" : ["file"],
  "query":{
    "match_all" : {}
  }
}'
```

gives:

```javascript
{
  "took" : 19,
  "timed_out" : false,
  "_shards" : {
    "total" : 5,
    "successful" : 5,
    "failed" : 0
  },
  "hits" : {
    "total" : 1,
    "max_score" : 1.0,
    "hits" : [ {
      "_index" : "fsrivermetadatatest",
      "_type" : "doc",
      "_id" : "fb6115c44876aa1e94cc4f86b03ba93",
      "_score" : 1.0,
      "fields" : {
        "file" : "Bonjour David\n\n\n"
      }
    } ]
  }
}
```


Behind the scene
================

How it works ?
--------------

TO BE COMPLETED

License
=======

```
This software is licensed under the Apache 2 license, quoted below.

Copyright 2011-2012 David Pilato

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
