.. _elasticsearch-settings:

Elasticsearch settings
----------------------

Here is a list of Elasticsearch settings (under ``elasticsearch.`` prefix)`:

+----------------------------------+---------------------------+---------------------------------+
| Name                             | Default value             | Documentation                   |
+==================================+===========================+=================================+
| ``elasticsearch.index``          | job name                  | `Index settings for documents`_ |
+----------------------------------+---------------------------+---------------------------------+
| ``elasticsearch.index_folder``   | job name + ``_folder``    | `Index settings for folders`_   |
+----------------------------------+---------------------------+---------------------------------+
| ``elasticsearch.bulk_size``      | ``100``                   | `Bulk settings`_                |
+----------------------------------+---------------------------+---------------------------------+
| ``elasticsearch.flush_interval`` | ``"5s"``                  | `Bulk settings`_                |
+----------------------------------+---------------------------+---------------------------------+
| ``elasticsearch.byte_size``      | ``"10mb"``                | `Bulk settings`_                |
+----------------------------------+---------------------------+---------------------------------+
| ``elasticsearch.pipeline``       | ``null``                  | :ref:`ingest_node`              |
+----------------------------------+---------------------------+---------------------------------+
| ``elasticsearch.nodes``          | ``http://127.0.0.1:9200`` | `Node settings`_                |
+----------------------------------+---------------------------+---------------------------------+
| ``elasticsearch.username``       | ``null``                  | :ref:`credentials`              |
+----------------------------------+---------------------------+---------------------------------+
| ``elasticsearch.password``       | ``null``                  | :ref:`credentials`              |
+----------------------------------+---------------------------+---------------------------------+

Index settings
^^^^^^^^^^^^^^

Index settings for documents
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

By default, FSCrawler will index your data in an index which name is
the same as the crawler name (``name`` property) plus ``_doc`` suffix,
like ``test_doc``. You can change it by setting ``index`` field:

.. code:: json

   {
     "name" : "test",
     "elasticsearch" : {
       "index" : "docs"
     }
   }

Index settings for folders
~~~~~~~~~~~~~~~~~~~~~~~~~~

FSCrawler will also index folders in an index which name is the same as
the crawler name (``name`` property) plus ``_folder`` suffix, like
``test_folder``. You can change it by setting ``index_folder`` field:

.. code:: json

   {
    "name" : "test",
    "elasticsearch" : {
      "index_folder" : "folders"
    }
   }

.. _mappings:

Mappings
~~~~~~~~

When FSCrawler needs to create the doc index, it applies some default
settings and mappings which are read from
``~/.fscrawler/_default/6/_settings.json``. You can read its content
from `the
source <https://github.com/dadoonet/fscrawler/blob/master/settings/src/main/resources/fr/pilato/elasticsearch/crawler/fs/_default/6/_settings.json>`__.

Settings define an analyzer named ``fscrawler_path`` which uses a `path
hierarchy
tokenizer <https://www.elastic.co/guide/en/elasticsearch/reference/current/analysis-pathhierarchy-tokenizer.html>`__.

FSCrawler applies as well a mapping automatically for the folders which can also be
read from `the source <https://github.com/dadoonet/fscrawler/blob/master/settings/src/main/resources/fr/pilato/elasticsearch/crawler/fs/_default/6/_settings_folder.json>`__.

You can also display the index mapping being used with Kibana:

::

   GET docs/_mapping
   GET docs_folder/_mapping

Or fall back to the command line:

.. code:: sh

   curl 'http://localhost:9200/docs/_mapping?pretty'
   curl 'http://localhost:9200/docs_folder/_mapping?pretty'

.. note::

    FSCrawler is actually applying default index settings depending on the
    elasticsearch version it is connected to.
    The default settings definitions are stored in ``~/.fscrawler/_default/_mappings``:

    -  ``2/_settings.json``: for elasticsearch 2.x series document index settings
    -  ``2/_settings_folder.json``: for elasticsearch 2.x series folder index settings
    -  ``5/_settings.json``: for elasticsearch 5.x series document index settings
    -  ``5/_settings_folder.json``: for elasticsearch 5.x series folder index settings
    -  ``6/_settings.json``: for elasticsearch 6.x series document index settings
    -  ``6/_settings_folder.json``: for elasticsearch 6.x series folder index settings

Creating your own mapping (analyzers)
"""""""""""""""""""""""""""""""""""""

If you want to define your own index settings and mapping to set
analyzers for example, you can either create the index and push the
mapping or define a ``~/.fscrawler/_default/6/_settings.json`` document
which contains the index settings and mappings you wish **before
starting the FSCrawler**.

The following example uses a ``french`` analyzer to index the
``content`` field.

.. code:: json

   {
     "settings": {
       "index.mapping.total_fields.limit": 2000,
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
     },
     "mappings": {
       "doc": {
         "properties" : {
           "attachment" : {
             "type" : "binary",
             "doc_values": false
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
           "object" : {
             "type" : "object"
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
               },
               "format" : {
                 "type" : "text"
               },
               "identifier" : {
                 "type" : "text"
               },
               "contributor" : {
                 "type" : "text"
               },
               "coverage" : {
                 "type" : "text"
               },
               "modifier" : {
                 "type" : "text"
               },
               "creator_tool" : {
                 "type" : "keyword"
               },
               "publisher" : {
                 "type" : "text"
               },
               "relation" : {
                 "type" : "text"
               },
               "rights" : {
                 "type" : "text"
               },
               "source" : {
                 "type" : "text"
               },
               "type" : {
                 "type" : "text"
               },
               "description" : {
                 "type" : "text"
               },
               "created" : {
                 "type" : "date",
                 "format" : "dateOptionalTime"
               },
               "print_date" : {
                 "type" : "date",
                 "format" : "dateOptionalTime"
               },
               "metadata_date" : {
                 "type" : "date",
                 "format" : "dateOptionalTime"
               },
               "latitude" : {
                 "type" : "text"
               },
               "longitude" : {
                 "type" : "text"
               },
               "altitude" : {
                 "type" : "text"
               },
               "rating" : {
                 "type" : "keyword"
               },
               "comments" : {
                 "type" : "text"
               }
             }
           },
           "path" : {
             "properties" : {
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
     }
   }

Note that if you want to push manually the mapping to elasticsearch you
can use the classic REST calls:

::

   # Create index (don't forget to add the fscrawler_path analyzer)
   PUT docs
   {
     // Same index settings as previously seen
   }

Define explicit mapping/settings per job
""""""""""""""""""""""""""""""""""""""""

Let’s say you created a job named ``job_name`` and you are sending
documents against an elasticsearch cluster running version ``6.x``.

If you create the following files, they will be picked up at job start
time instead of the :ref:`default ones <mappings>`:

-  ``~/.fscrawler/{job_name}/_mappings/6/_settings.json``
-  ``~/.fscrawler/{job_name}/_mappings/6/_settings_folder.json``

.. tip::
    You can do the same for other elasticsearch versions with:

    -  ``~/.fscrawler/{job_name}/_mappings/2/_settings.json`` for 2.x series (deprecated)
    -  ``~/.fscrawler/{job_name}/_mappings/2/_settings_folder.json`` for 2.x series (deprecated)
    -  ``~/.fscrawler/{job_name}/_mappings/5/_settings.json`` for 5.x series
    -  ``~/.fscrawler/{job_name}/_mappings/5/_settings_folder.json`` for 5.x series

Replace existing mapping
""""""""""""""""""""""""

Unfortunately you can not change the mapping on existing data.
Therefore, you’ll need first to remove existing index, which means
remove all existing data, and then restart FSCrawler with the new
mapping.

You might to try `elasticsearch Reindex
API <https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-reindex.html>`__
though.

Bulk settings
^^^^^^^^^^^^^

FSCrawler is using bulks to send data to elasticsearch. By default the
bulk is executed every 100 operations or every 5 seconds or every 10 megabytes. You can change
default settings using ``bulk_size``, ``byte_size`` and ``flush_interval``:

.. code:: json

   {
     "name" : "test",
     "elasticsearch" : {
       "bulk_size" : 1000,
       "byte_size" : "500kb",
       "flush_interval" : "2s"
     }
   }

.. tip::

    Elasticsearch has a default limit of ``100mb`` per HTTP request as per
    `elasticsearch HTTP Module <https://www.elastic.co/guide/en/elasticsearch/reference/current/modules-http.html>`__
    documentation.

    Which means that if you are indexing a massive bulk of documents, you
    might hit that limit and FSCrawler will throw an error like
    ``entity content is too long [xxx] for the configured buffer limit [104857600]``.

    You can either change this limit on elasticsearch side by setting
    ``http.max_content_length`` to a higher value but please be aware that
    this will consume much more memory on elasticsearch side.

    Or you can decrease the ``bulk_size`` or ``byte_size`` setting to a smaller value.

.. _ingest_node:

Using Ingest Node Pipeline
^^^^^^^^^^^^^^^^^^^^^^^^^^

.. versionadded:: 2.2

If you are using an elasticsearch cluster running a 5.0 or superior
version, you can use an Ingest Node pipeline to transform documents sent
by FSCrawler before they are actually indexed.

For example, if you have the following pipeline:

.. code:: sh

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

In FSCrawler settings, set the ``elasticsearch.pipeline`` option:

.. code:: json

   {
     "name" : "test",
     "elasticsearch" : {
       "pipeline" : "fscrawler"
     }
   }

.. note::
    Folder objects are not sent through the pipeline as they are more
    internal objects.

Node settings
^^^^^^^^^^^^^

FSCrawler is using elasticsearch REST layer to send data to your
running cluster. By default, it connects to ``127.0.0.1`` on port
``9200`` which are the default settings when running a local node on
your machine.

Of course, in production, you would probably change this and connect to
a production cluster:

.. code:: json

   {
     "name" : "test",
     "elasticsearch" : {
       "nodes" : [
         { "host" : "mynode1.mycompany.com", "port" : 9200, "scheme" : "HTTP" }
       ]
     }
   }

You can define multiple nodes:

.. code:: json

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

.. note::
    .. versionadded:: 2.2 you can use HTTPS instead of default HTTP.

    .. code:: json

       {
         "name" : "test",
         "elasticsearch" : {
           "nodes" : [
             { "host" : "CLUSTERID.eu-west-1.aws.found.io", "port" : 9243, "scheme" : "HTTPS" }
           ]
         }
       }

    For more information, read :ref:`ssl`.

.. _credentials:

Using Credentials (X-Pack)
^^^^^^^^^^^^^^^^^^^^^^^^^^

.. versionadded:: 2.2

If you secured your elasticsearch cluster with
`X-Pack <https://www.elastic.co/downloads/x-pack>`__, you can provide
``username`` and ``password`` to FSCrawler:

.. code:: json

   {
     "name" : "test",
     "elasticsearch" : {
       "username" : "elastic",
       "password" : "changeme"
     }
   }

.. warning::
    For the current version, the elasticsearch password is stored in
    plain text in your job setting file.

    A better practice is to only set the username or pass it with
    ``--username elastic`` option when starting FSCrawler.

    If the password is not defined, you will be prompted when starting the job:

    ::

       22:46:42,528 INFO  [f.p.e.c.f.FsCrawler] Password for elastic:

.. _ssl:

SSL Configuration
^^^^^^^^^^^^^^^^^

In order to ingest documents to Elasticsearch over HTTPS based connection, you need to perform additional configuration
steps:

.. important::

    Prerequisite: you need to have root CA chain certificate or Elasticsearch server certificate
    in DER format. DER format files have a ``.cer`` extension.

1. Logon to server (or client machine) where FSCrawler is running
2. Run:

.. code:: sh

    keytool -import -alias <alias name> -keystore " <JAVA_HOME>\lib\security\cacerts" -file <Path of Elasticsearch Server certificate or Root certificate>

It will prompt you for the password. Enter the certificate password like ``changeit``.

3. Make changes to FSCrawler ``_settings.json`` file to connect to your Elasticsearch server over HTTPS:

.. code:: json

    {
      "name" : "test",
      "elasticsearch" : {
        "nodes" : [
          {"host" : "localhost", "port" : 9243, "scheme" : "HTTPS" }
        ]
      }
    }

.. tip::

    If you can not find ``keytool``, it probably means that you did not add your ``JAVA_HOME/bin`` directory to your path.


Generated fields
^^^^^^^^^^^^^^^^

FSCrawler creates the following fields :

+------------------------+----------------------+----------------------------------------------+---------------------------------------------------------------------+
| Field                  | Description          | Example                                      | Javadoc                                                             |
+========================+======================+==============================================+=====================================================================+
| ``content``            | Extracted content    | ``"This is my text!"``                       |                                                                     |
+------------------------+----------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``attachment``         | BASE64 encoded       | BASE64 Encoded document                      |                                                                     |
|                        | binary file          |                                              |                                                                     |
+------------------------+----------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.author``        | Author if any in     | ``"David Pilato"``                           | `CREATOR <https://tika.apache.org/1.18/api/org/apache/tika/         |
|                        |                      |                                              | metadata/TikaCoreProperties.html#CREATOR>`__                        |
+------------------------+----------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.title``         | Title if any in      | ``"My document title"``                      | `TITLE <https://tika.apache.org/1.18/api/org/apache/tika/           |
|                        | document metadata    |                                              | metadata/TikaCoreProperties.html#TITLE>`__                          |
+------------------------+----------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.date``          | Last modified date   | ``"2013-04-04T15:21:35"``                    | `MODIFIED <https://tika.apache.org/1.18/api/org/apache/tika/        |
|                        |                      |                                              | metadata/TikaCoreProperties.html#MODIFIED>`__                       |
+------------------------+----------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.keywords``      | Keywords if any in   | ``["fs","elasticsearch"]``                   | `KEYWORDS <https://tika.apache.org/1.18/api/org/apache/tika/        |
|                        | document metadata    |                                              | metadata/TikaCoreProperties.html#KEYWORDS>`__                       |
+------------------------+----------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.language``      | Language (can be     | ``"fr"``                                     | `LANGUAGE <https://tika.apache.org/1.18/api/org/apache/tika/        |
|                        | detected)            |                                              | metadata/TikaCoreProperties.html#LANGUAGE>`__                       |
+------------------------+----------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.format``        | Format of the media  | ``"application/pdf; version=1.6"``           | `FORMAT <https://tika.apache.org/1.18/api/org/apache/tika/          |
|                        |                      |                                              | metadata/TikaCoreProperties.html#FORMAT>`__                         |
+------------------------+----------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.identifier``    | URL/DOI/ISBN for     | ``"FOOBAR"``                                 | `IDENTIFIER <https://tika.apache.org/1.18/api/org/apache/tika/      |
|                        | example              |                                              | metadata/TikaCoreProperties.html#IDENTIFIER>`__                     |
+------------------------+----------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.contributor``   | Contributor          | ``"foo bar"``                                | `CONTRIBUTOR <https://tika.apache.org/1.18/api/org/apache/tika/     |
|                        |                      |                                              | metadata/TikaCoreProperties.html#CONTRIBUTOR>`__                    |
+------------------------+----------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.coverage``      | Coverage             | ``"FOOBAR"``                                 | `COVERAGE <https://tika.apache.org/1.18/api/org/apache/tika/        |
|                        |                      |                                              | metadata/TikaCoreProperties.html#COVERAGE>`__                       |
+------------------------+----------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.modifier``      | Last author          | ``"David Pilato"``                           | `MODIFIER <https://tika.apache.org/1.18/api/org/apache/tika/        |
|                        |                      |                                              | metadata/TikaCoreProperties.html#MODIFIER>`__                       |
+------------------------+----------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.creator_tool``  | Tool used to create  | ``"HTML2PDF- TCPDF"``                        | `CREATOR_TOOL <https://tika.apache.org/1.18/api/org/apache/tika/    |
|                        | the resource         |                                              | metadata/TikaCoreProperties.html#CREATOR_TOOL>`__                   |
+------------------------+----------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.publisher``     | Publisher: person,   | ``"elastic"``                                | `PUBLISHER <https://tika.apache.org/1.18/api/org/apache/tika/       |
|                        | organisation, service|                                              | metadata/TikaCoreProperties.html#PUBLISHER>`__                      |
+------------------------+----------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.relation``      | Related resource     | ``"FOOBAR"``                                 | `RELATION <https://tika.apache.org/1.18/api/org/apache/tika/        |
|                        |                      |                                              | metadata/TikaCoreProperties.html#RELATION>`__                       |
+------------------------+----------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.rights``        | Information about    | ``"CC-BY-ND"``                               | `RIGHTS <https://tika.apache.org/1.18/api/org/apache/tika/          |
|                        | rights               |                                              | metadata/TikaCoreProperties.html#RIGHTS>`__                         |
+------------------------+----------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.source``        | Source for the       | ``"FOOBAR"``                                 | `SOURCE <https://tika.apache.org/1.18/api/org/apache/tika/          |
|                        | current document     |                                              | metadata/TikaCoreProperties.html#SOURCE>`__                         |
|                        | (derivated)          |                                              |                                                                     |
+------------------------+----------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.type``          | Nature or genre of   | ``"Image"``                                  | `TYPE <https://tika.apache.org/1.18/api/org/apache/tika/            |
|                        | the content          |                                              | metadata/TikaCoreProperties.html#TYPE>`__                           |
+------------------------+----------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.description``   | An account of the    | ``"This is a description"``                  | `DESCRIPTION <https://tika.apache.org/1.18/api/org/apache/tika/     |
|                        | content              |                                              | metadata/TikaCoreProperties.html#DESCRIPTION>`__                    |
+------------------------+----------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.created``       | Date of creation     | ``"2013-04-04T15:21:35"``                    | `CREATED <https://tika.apache.org/1.18/api/org/apache/tika/         |
|                        |                      |                                              | metadata/TikaCoreProperties.html#CREATED>`__                        |
+------------------------+----------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.print_date``    | When was the doc     | ``"2013-04-04T15:21:35"``                    | `PRINT_DATE <https://tika.apache.org/1.18/api/org/apache/tika/      |
|                        | last printed?        |                                              | metadata/TikaCoreProperties.html#PRINT_DATE>`__                     |
+------------------------+----------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.metadata_date`` | Last modification of | ``"2013-04-04T15:21:35"``                    | `METADATA_DATE <https://tika.apache.org/1.18/api/org/apache/tika/   |
|                        | metadata             |                                              | metadata/TikaCoreProperties.html#METADATA_DATE>`__                  |
+------------------------+----------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.latitude``      | The WGS84 Latitude   | ``"N 48° 51' 45.81''"``                      | `LATITUDE <https://tika.apache.org/1.18/api/org/apache/tika/        |
|                        | of the Point         |                                              | metadata/TikaCoreProperties.html#LATITUDE>`__                       |
+------------------------+----------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.longitude``     | The WGS84 Longitude  | ``"E 2° 17'15.331''"``                       | `LONGITUDE <https://tika.apache.org/1.18/api/org/apache/tika/       |
|                        | of the Point         |                                              | metadata/TikaCoreProperties.html#LONGITUDE>`__                      |
+------------------------+----------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.altitude``      | The WGS84 Altitude   | ``""``                                       | `ALTITUDE <https://tika.apache.org/1.18/api/org/apache/tika/        |
|                        | of the Point         |                                              | metadata/TikaCoreProperties.html#ALTITUDE>`__                       |
+------------------------+----------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.rating``        | A user-assigned      | ``0``                                        | `RATING <https://tika.apache.org/1.18/api/org/apache/tika/          |
|                        | rating -1, [0..5]    |                                              | metadata/TikaCoreProperties.html#RATING>`__                         |
+------------------------+----------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.comments``      | Comments             | ``"Comments"``                               | `COMMENTS <https://tika.apache.org/1.18/api/org/apache/tika/        |
|                        |                      |                                              | metadata/TikaCoreProperties.html#COMMENTS>`__                       |
+------------------------+----------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.raw``           | An object with all   | ``"meta.raw.channels": "2"``                 |                                                                     |
|                        | raw metadata         |                                              |                                                                     |
+------------------------+----------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``file.content_type``  | Content Type         | ``"application/vnd.oasis.opendocument.text"``|                                                                     |
+------------------------+----------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``file.last_modified`` | Last modification    | ``1386855978000``                            |                                                                     |
|                        | date                 |                                              |                                                                     |
+------------------------+----------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``file.indexing_date`` | Indexing date        | ``"2013-12-12T13:50:58.758Z"``               |                                                                     |
+------------------------+----------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``file.filesize``      | File size in bytes   | ``1256362``                                  |                                                                     |
+------------------------+----------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``file.indexed_chars`` | Extracted chars if   | ``100000``                                   |                                                                     |
|                        | ``fs.indexed_chars`` |                                              |                                                                     |
|                        | > 0                  |                                              |                                                                     |
+------------------------+----------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``file.filename``      | Original file name   | ``"mydocument.pdf"``                         |                                                                     |
+------------------------+----------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``file.extension``     | Original file name   | ``"pdf"``                                    |                                                                     |
|                        | extension (from 2.2) |                                              |                                                                     |
+------------------------+----------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``file.url``           | Original file url    | ``"file://tmp/otherdir/mydocument.pdf"``     |                                                                     |
+------------------------+----------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``file.checksum``      | Checksum if          | ``"c32eafae2587bef4b3b32f73743c3c61"``       |                                                                     |
|                        | ``fs.checksum`` set  |                                              |                                                                     |
+------------------------+----------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``path.virtual``       | Relative path from   | ``"/otherdir/mydocument.pdf"``               |                                                                     |
+------------------------+----------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``path.root``          | MD5 encoded parent   | ``"112aed83738239dbfe4485f024cd4ce1"``       |                                                                     |
|                        | path (internal use)  |                                              |                                                                     |
+------------------------+----------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``path.real``          | Real path name       | ``"/tmp/otherdir/mydocument.pdf"``           |                                                                     |
+------------------------+----------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``attributes.owner``   | Owner name           | ``"david"``                                  |                                                                     |
+------------------------+----------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``attributes.group``   | Group name           | ``"staff"``                                  |                                                                     |
+------------------------+----------------------+----------------------------------------------+---------------------------------------------------------------------+

For more information about meta data, please read the `TikaCoreProperties <https://tika.apache.org/1.18/api/org/apache/tika/metadata/TikaCoreProperties.html>`__.

Here is a typical JSON document generated by the crawler:

.. code:: json

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
         "root":"bceb3913f6d793e915beb70a4735592",
         "virtual":"/test.odt",
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

.. _search-examples:

Search examples
^^^^^^^^^^^^^^^

You can use the content field to perform full-text search on

::

   GET docs/_search
   {
     "query" : {
       "match" : {
           "content" : "the quick brown fox"
       }
     }
   }

You can use meta fields to perform search on.

::

   GET docs/_search
   {
     "query" : {
       "term" : {
           "file.filename" : "mydocument.pdf"
       }
     }
   }

Or run some aggregations on top of them, like:

::

   GET docs/_search
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

