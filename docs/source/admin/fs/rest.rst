.. _rest-service:

REST service
------------

.. versionadded:: 2.2

FSCrawler can expose a REST service running at http://127.0.0.1:8080/fscrawler.
To activate it, launch FSCrawler with ``--rest`` option.

.. contents:: :backlinks: entry

General settings
~~~~~~~~~~~~~~~~

.. versionadded:: 2.10

For all the APIs on this page, you can pass parameters in different ways.

You can use a query string parameter:

.. code:: sh

   curl "http://127.0.0.1:8080/fscrawler/API?param1=foo&param2=bar"

You can use a header parameter:

.. code:: sh

   curl -H "param1=foo" -H "param2=bar" "http://127.0.0.1:8080/fscrawler/API"

The rest of this documentation will assume using a query string parameter unless stated otherwise.

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
           "url" : "http://127.0.0.1:9200"
         } ],
         "index" : "fscrawler-rest-tests_doc",
         "index_folder" : "fscrawler-rest-tests_folder",
         "bulk_size" : 100,
         "flush_interval" : "5s",
         "byte_size" : "10mb",
         "username" : "elastic"
       },
       "rest" : {
         "url" : "http://127.0.0.1:8080/fscrawler",
         "enable_cors": false
       }
     }
   }

Uploading a binary document
~~~~~~~~~~~~~~~~~~~~~~~~~~~

To upload a binary, you can call ``POST /_document`` endpoint:

.. code:: sh

   echo "This is my text" > test.txt
   curl -F "file=@test.txt" "http://127.0.0.1:8080/fscrawler/_document"

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
   curl -F "file=@test.txt" "http://127.0.0.1:8080/fscrawler/_document?debug=true"

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
   curl -F "file=@test.txt" "http://127.0.0.1:8080/fscrawler/_document?debug=true&simulate=true"

Document ID
~~~~~~~~~~~

By default, FSCrawler encodes the filename to generate an id. Which
means that if you send 2 files with the same filename ``test.txt``, the
second one will overwrite the first one because they will both share the
same ID.

You can force any id you wish by adding ``id=YOUR_ID`` as a parameter:

.. code:: sh

   echo "This is my text" > test.txt
   curl -F "file=@test.txt" "http://127.0.0.1:8080/fscrawler/_document?id=my-test"

You can pass the ``id`` parameter within the form data:

.. code:: sh

   echo "This is my text" > test.txt
   curl -F "file=@test.txt" -F "id=my-test" "http://127.0.0.1:8080/fscrawler/_document"

There is a specific id named ``_auto_`` where the ID will be
autogenerated by elasticsearch. It means that sending twice the same
file will result in 2 different documents indexed.

Additional tags
~~~~~~~~~~~~~~~

Add custom tags to the document. In case you want to do filtering on those
tags (examples are ``projectId`` or ``tenantId``).
These tags can be assigned to an ``external`` object field. As you can see
in the json, you are able to overwrite the ``content`` field.
``meta``, ``file`` and ``path`` fields can be overwritten as well.
To upload a binary with additional tags, you can call ``POST /_document`` endpoint:

.. code:: json

    {
      "content": "OVERWRITE CONTENT",
      "external": {
        "tenantId": 23,
        "projectId": 34,
        "description": "these are additional tags"
      }
    }

.. code:: sh

    echo "This is my text" > test.txt
    echo "{\"content\":\"OVERWRITE CONTENT\",\"external\":{\"tenantId\": 23,\"projectId\": 34,\"description\":\"these are additional tags\"}}" > tags.txt
    curl -F "file=@test.txt" -F "tags=@tags.txt" "http://127.0.0.1:8080/fscrawler/_document"

The field ``external`` doesn't necessarily be a flat structure. This is a more advanced example:

.. code:: json

    {
      "external": {
        "tenantId" : 23,
        "company": "shoe company",
        "projectId": 34,
        "project": "business development",
        "daysOpen": [
          "Mon",
          "Tue",
          "Wed",
          "Thu",
          "Fri"
        ],
        "products": [
          {
            "brand": "nike",
            "size": 41,
            "sub": "Air MAX"
          },
          {
            "brand": "reebok",
            "size": 43,
            "sub": "Pump"
          }
        ]
      }
    }

.. attention:: Only standard :ref:`FSCrawler fields <generated_fields>` can be set outside ``external`` field name.

Remove a document
~~~~~~~~~~~~~~~~~

.. versionadded:: 2.10

To remove a document, you can call ``DELETE /_document`` endpoint.

If you only know the filename, you can pass it to FSCrawler using the ``filename`` field:

.. code:: sh

   curl -X DELETE "http://127.0.0.1:8080/fscrawler/_document?filename=test.txt"

It will give you a response similar to:

.. code:: json

    {
      "ok": true,
      "filename": "test.txt",
      "index": "rest",
      "id": "dd18bf3a8ea2a3e53e2661c7fb53534"
    }

If you know the document id, you can pass it to FSCrawler within the url:

.. code:: sh

   curl -X DELETE "http://127.0.0.1:8080/fscrawler/_document/dd18bf3a8ea2a3e53e2661c7fb53534"

If the document does not exist, you will get the following response:

.. code:: json

    {
      "ok": false,
      "message": "Can not remove document [rest/test.txt]: Can not remove document rest/dd18bf3a8ea2a3e53e2661c7fb53534 cause: NOT_FOUND",
      "filename": "test.txt",
      "index": "rest",
      "id": "dd18bf3a8ea2a3e53e2661c7fb53534"
    }

Specifying an elasticsearch index
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

By default, fscrawler creates document in the index defined in the ``_settings.yaml`` file.
However, using the REST service, it is possible to require fscrawler to use different indexes, by setting the ``index``
parameter:

.. code:: sh

   echo "This is my text" > test.txt
   curl -F "file=@test.txt" "http://127.0.0.1:8080/fscrawler/_document?index=my-index"
   curl -X DELETE "http://127.0.0.1:8080/fscrawler/_document?filename=test.txt&index=my-index"

When uploading, you can pass the ``id`` parameter within the form data:

.. code:: sh

   echo "This is my text" > test.txt
   curl -F "file=@test.txt" -F "index=my-index" "http://127.0.0.1:8080/fscrawler/_document"


Enabling CORS
~~~~~~~~~~~~~
To enable Cross-Origin Request Sharing you will need to set ``enable_cors: true``
under ``rest`` in your job settings. Doing so will enable the relevant access headers
on all REST service resource responses (for example ``/fscrawler`` and ``/fscrawler/_document``).

You can check if CORS is enabled with:

.. code:: sh

   curl -I http://127.0.0.1:8080/fscrawler/

The response header should contain ``Access-Control-Allow-*`` parameters like:
::

   Access-Control-Allow-Origin: *
   Access-Control-Allow-Headers: origin, content-type, accept, authorization
   Access-Control-Allow-Credentials: true
   Access-Control-Allow-Methods: GET, POST, PUT, PATCH, DELETE, OPTIONS, HEAD

REST settings
~~~~~~~~~~~~~

Here is a list of REST service settings (under ``rest.`` prefix)`:

+----------------------+-------------------------------------+-------------------------------------------------------+
| Name                 | Default value                       | Documentation                                         |
+======================+=====================================+=======================================================+
| ``rest.url``         | ``http://127.0.0.1:8080/fscrawler`` | Rest Service URL                                      |
+----------------------+-------------------------------------+-------------------------------------------------------+
| ``rest.enable_cors`` | ``false``                           | Enables or disables Cross-Origin Resource Sharing     |
|                      |                                     | globally for all resources                            |
+----------------------+-------------------------------------+-------------------------------------------------------+

.. tip::

    Most :ref:`local-fs-settings` (under ``fs.*`` in the
    settings file) also affect the REST service, e.g. ``fs.indexed_chars``.
    Local FS settings that do **not** affect the REST service are those such
    as ``url``, ``update_rate``, ``includes``, ``excludes``.

REST service is running at http://127.0.0.1:8080/fscrawler by default.

You can change it using ``rest`` settings:

.. code:: yaml

   name: "test"
   rest:
     url: "http://192.168.0.1:8180/my_fscrawler"

It also means that if you are running more than one instance of FS
crawler locally, you can (must) change the port as it will conflict.
