.. _rest-service:

REST service
------------

.. versionadded:: 2.2

FSCrawler can expose a REST service running at http://127.0.0.1:8080/fscrawler.
To activate it, launch FSCrawler with ``--rest`` option.

FSCrawler status
~~~~~~~~~~~~~~~~

To get an overview of the running service, you can call ``GET /``
endpoint:

.. code:: sh

   curl http://127.0.0.1:8080/fscrawler/

It will give you a response similar to:

.. code:: json

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
         "index" : "fscrawler-rest-tests_doc",
         "index_folder" : "fscrawler-rest-tests_folder",
         "bulk_size" : 100,
         "flush_interval" : "5s",
         "byte_size" : "10mb",
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

Uploading a binary document
~~~~~~~~~~~~~~~~~~~~~~~~~~~

To upload a binary, you can call ``POST /_upload`` endpoint:

.. code:: sh

   echo "This is my text" > test.txt
   curl -F "file=@test.txt" "http://127.0.0.1:8080/fscrawler/_upload"

It will give you a response similar to:

.. code:: json

   {
     "ok" : true,
     "filename" : "test.txt",
     "url" : "http://127.0.0.1:9200/fscrawler-rest-tests_doc/doc/dd18bf3a8ea2a3e53e2661c7fb53534"
   }

The ``url`` represents the elasticsearch address of the indexed
document. If you call:

.. code:: sh

   curl http://127.0.0.1:9200/fscrawler-rest-tests_doc/doc/dd18bf3a8ea2a3e53e2661c7fb53534?pretty

You will get back your document as it has been stored by elasticsearch:

.. code:: json

   {
     "_index" : "fscrawler-rest-tests_doc",
     "_type" : "_doc",
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

If you started FSCrawler in debug mode with ``--debug`` or if you pass
``debug=true`` query parameter, then the response will be much more
complete:

.. code:: sh

   echo "This is my text" > test.txt
   curl -F "file=@test.txt" "http://127.0.0.1:8080/fscrawler/_upload?debug=true"

will give

.. code:: json

   {
     "ok" : true,
     "filename" : "test.txt",
     "url" : "http://127.0.0.1:9200/fscrawler-rest-tests_doc/doc/dd18bf3a8ea2a3e53e2661c7fb53534",
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

Simulate Upload
~~~~~~~~~~~~~~~

If you want to get back the extracted content and its metadata but
without indexing into elasticsearch you can use ``simulate=true`` query
parameter:

.. code:: sh

   echo "This is my text" > test.txt
   curl -F "file=@test.txt" "http://127.0.0.1:8080/fscrawler/_upload?debug=true&simulate=true"

Document ID
~~~~~~~~~~~

By default, FSCrawler encodes the filename to generate an id. Which
means that if you send 2 files with the same filename ``test.txt``, the
second one will overwrite the first one because they will both share the
same ID.

You can force any id you wish by adding ``id=YOUR_ID`` in the form data:

.. code:: sh

   echo "This is my text" > test.txt
   curl -F "file=@test.txt" -F "id=my-test" "http://127.0.0.1:8080/fscrawler/_upload"

There is a specific id named ``_auto_`` where the ID will be
autogenerated by elasticsearch. It means that sending twice the same
file will result in 2 different documents indexed.

REST settings
~~~~~~~~~~~~~

Here is a list of REST service settings (under ``rest.`` prefix)`:

+-----------------------+-----------------------+-----------------------+
| Name                  | Default value         | Documentation         |
+=======================+=======================+=======================+
| ``rest.scheme``       | ``http``              | Scheme. Can be either |
|                       |                       | ``http`` or ``https`` |
+-----------------------+-----------------------+-----------------------+
| ``rest.host``         | ``127.0.0.1``         | Bound host            |
+-----------------------+-----------------------+-----------------------+
| ``rest.port``         | ``8080``              | Bound port            |
+-----------------------+-----------------------+-----------------------+
| ``rest.endpoint``     | ``fscrawler``         | Endpoint              |
+-----------------------+-----------------------+-----------------------+

.. tip::

    Most :ref:`local-fs-settings` (under ``fs.*`` in the
    settings file) also affect the REST service, e.g. ``fs.indexed_chars``.
    Local FS settings that do **not** affect the REST service are those such
    as ``url``, ``update_rate``, ``includes``, ``excludes``.

REST service is running at http://127.0.0.1:8080/fscrawler by default.

You can change it using ``rest`` settings:

.. code:: json

   {
     "name" : "test",
     "rest" : {
       "scheme" : "HTTP",
       "host" : "192.168.0.1",
       "port" : 8180,
       "endpoint" : "my_fscrawler"
     }
   }

It also means that if you are running more than one instance of FS
crawler locally, you can (must) change the ``port``.
