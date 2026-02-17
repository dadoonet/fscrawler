.. _rest-service:

REST service
------------

FSCrawler can expose a REST service running at http://127.0.0.1:8080/.
To activate it, launch FSCrawler with ``--rest`` option.

.. contents:: :backlinks: entry

General settings
^^^^^^^^^^^^^^^^

.. versionadded:: 2.10

For all the APIs on this page, you can pass parameters in different ways.

You can use a query string parameter:

.. code:: sh

   curl "http://127.0.0.1:8080/API?param1=foo&param2=bar"

You can use a header parameter:

.. code:: sh

   curl -H "param1=foo" -H "param2=bar" "http://127.0.0.1:8080/API"

The rest of this documentation will assume using a query string parameter unless stated otherwise.

FSCrawler status
^^^^^^^^^^^^^^^^

To get an overview of the running service, you can call ``GET /``
endpoint:

.. code:: sh

   curl http://127.0.0.1:8080/

It will give you a response similar to:

.. code-block:: json
   :substitutions:

   {
     "ok" : true,
     "version" : "|FSCrawler_version|",
     "elasticsearch" : "|ES_stack_version|",
     "settings" : {
       "name" : "fscrawler",
       "fs" : {
         "url" : "/tmp/es",
         "update_rate" : "15m",
         "excludes" : [ "*/~*" ],
         "json_support" : false,
         "add_as_inner_object" : false,
         "xml_support" : false,
         "follow_symlinks" : false,
         "remove_deleted" : true,
         "continue_on_error" : false,
         "filename_as_id" : false,
         "add_filesize" : true,
         "attributes_support" : false,
         "store_source" : false,
         "index_content" : true,
         "acl_support" : false,
         "raw_metadata" : true,
         "index_folders" : true,
         "lang_detect" : false,
         "ocr" : {
           "enabled" : true,
           "language" : "eng",
           "output_type" : "txt",
           "pdf_strategy" : "ocr_and_text",
           "page_seg_mode" : 1,
           "preserve_interword_spacing" : false
         }
       },
       "server" : {
         "port" : 0,
         "protocol" : "local"
       },
       "elasticsearch" : {
         "urls" : [ "http://es-fscrawler:9200" ],
         "index" : "fscrawler_docs",
         "index_folder" : "fscrawler_folder",
         "bulk_size" : 100,
         "flush_interval" : "5s",
         "byte_size" : "10mb",
         "username" : "elastic",
         "ssl_verification" : true,
         "push_templates" : true,
         "semantic_search" : true
       },
       "rest" : {
         "url" : "http://127.0.0.1:8080",
         "enable_cors" : false
       },
       "tags" : {
         "meta_filename" : ".meta.yml"
       }
     }
   }

Uploading a binary document
^^^^^^^^^^^^^^^^^^^^^^^^^^^

To upload a binary, you can call ``POST /_document`` endpoint:

.. code:: sh

   echo "This is my text" > test.txt
   curl -F "file=@test.txt" "http://127.0.0.1:8080/_document"

It will give you a response similar to:

.. code:: json

   {
     "ok" : true,
     "filename" : "test.txt",
     "url" : "http://127.0.0.1:9200/fscrawler-rest-tests_doc/_doc/dd18bf3a8ea2a3e53e2661c7fb53534"
   }

The ``url`` represents the elasticsearch address of the indexed
document. If you call:

.. code:: sh

   curl http://127.0.0.1:9200/fscrawler-rest-tests_doc/_doc/dd18bf3a8ea2a3e53e2661c7fb53534?pretty

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

If you started FSCrawler in debug mode or if you pass
``debug=true`` query parameter, then the response will be much more
complete:

.. code:: sh

   echo "This is my text" > test.txt
   curl -F "file=@test.txt" "http://127.0.0.1:8080/_document?debug=true"

will give

.. code:: json

   {
     "ok" : true,
     "filename" : "test.txt",
     "url" : "http://127.0.0.1:9200/fscrawler-rest-tests_doc/_doc/dd18bf3a8ea2a3e53e2661c7fb53534",
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

Uploading a binary document from a 3rd party service
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

.. versionadded:: 2.10

You can also ask FSCrawler to fetch a document from a 3rd party service and index
it into Elasticsearch. FSCrawler supports so far the following services:

* ``local``: reads a file from the server where FSCrawler is running (a local file)
* ``http``: reads a file from a URL
* ``s3``: reads a file from an S3 compatible service
* ``ssh``: reads a file from an SSH/SFTP server
* ``ftp``: reads a file from an FTP server

To upload a binary from a 3rd party service, you can call ``POST /_document`` endpoint and pass
a JSON document which describes the service settings:

.. code:: sh

    curl -XPOST http://127.0.0.1:8080/_document -H 'Content-Type: application/json' -d '{
      "type": "<TYPE>",
      "<TYPE>": {
        // Settings for the <TYPE>
      }
    }'

Local plugin
~~~~~~~~~~~~

The ``local`` plugin reads a file from the server where FSCrawler is running (a local file).
It needs the following parameter:

* ``url``: link to the local file

For example, we can read the file ``bar.txt`` from the ``/path/to/foo`` directory with:

.. code:: sh

    curl -XPOST http://127.0.0.1:8080/_document -H 'Content-Type: application/json' -d '{
      "type": "local",
      "local": {
        "url": "/path/to/foo/bar.txt"
      }
    }'

.. note::

    For security reasons, the ``local`` plugin can only read files
    which are under the path defined in the job settings file under
    ``fs.url``.

HTTP plugin
~~~~~~~~~~~

The ``http`` plugin reads a file from a given URL.
It needs the following parameter:

* ``url``: link to the file

For example, we can read the file ``robots.txt`` from the ``https://www.elastic.co/`` website with:

.. code:: sh

    curl -XPOST http://127.0.0.1:8080/_document -H 'Content-Type: application/json' -d '{
      "type": "http",
      "http": {
        "url": "https://www.elastic.co/robots.txt"
      }
    }'

S3 plugin
~~~~~~~~~

The ``s3`` plugin reads a file from an S3 compatible service.
It needs the following parameters:

* ``url``: url for the S3 Service
* ``bucket``: bucket name
* ``object``: object to read from the bucket
* ``access_key``: access key (or login)
* ``secret_key``: secret key (or password)

For example, we can read the file ``foo.txt`` from the bucket ``foo`` running on ``https://s3.amazonaws.com/`` with:

.. code:: sh

    curl -XPOST http://127.0.0.1:8080/_document -H 'Content-Type: application/json' -d '{
      "type": "s3",
      "s3": {
        "url": "https://s3.amazonaws.com",
        "bucket": "foo",
        "object": "foo.txt",
        "access_key": "ACCESS",
        "secret_key": "SECRET"
      }
    }'

If you are using Minio, you can use:

.. code:: sh

    curl -XPOST http://127.0.0.1:8080/_document -H 'Content-Type: application/json' -d '{
      "type": "s3",
      "s3": {
        "url": "http://localhost:9000",
        "bucket": "foo",
        "object": "foo.txt",
        "access_key": "minioadmin",
        "secret_key": "minioadmin"
      }
    }'

SSH plugin
~~~~~~~~~~

The ``ssh`` plugin reads a file from an SSH/SFTP server.
It accepts the following parameters:

* ``path`` (required): path to the file on the remote server
* ``hostname`` (optional): SSH server hostname. If not provided, uses the ``server.hostname`` from job settings.
* ``port`` (optional): SSH server port. If not provided, uses the ``server.port`` from job settings.
* ``username`` (optional): SSH username. If not provided, uses the ``server.username`` from job settings.
* ``password`` (optional): SSH password. If not provided, uses the ``server.password`` from job settings.
* ``pem_path`` (optional): path to the PEM key file for key-based authentication. If not provided, uses the ``server.pem_path`` from job settings.

For example, we can read the file ``document.pdf`` from an SSH server with:

.. code:: sh

    curl -XPOST http://127.0.0.1:8080/_document -H 'Content-Type: application/json' -d '{
      "type": "ssh",
      "ssh": {
        "hostname": "my-ssh-server.example.com",
        "port": 22,
        "username": "myuser",
        "password": "mypassword",
        "path": "/home/myuser/documents/document.pdf"
      }
    }'

If you have already configured the SSH server settings in your job ``_settings.yaml`` file:

.. code:: yaml

    name: "my_job"
    server:
      hostname: "my-ssh-server.example.com"
      port: 22
      username: "myuser"
      password: "mypassword"
      protocol: "SSH"

You can simplify the REST call by only providing the ``path``:

.. code:: sh

    curl -XPOST http://127.0.0.1:8080/_document -H 'Content-Type: application/json' -d '{
      "type": "ssh",
      "ssh": {
        "path": "/home/myuser/documents/document.pdf"
      }
    }'

FTP plugin
~~~~~~~~~~

The ``ftp`` plugin reads a file from an FTP server.
It accepts the following parameters:

* ``path`` (required): path to the file on the remote server
* ``hostname`` (optional): FTP server hostname. If not provided, uses the ``server.hostname`` from job settings.
* ``port`` (optional): FTP server port. If not provided, uses the ``server.port`` from job settings.
* ``username`` (optional): FTP username. If not provided, uses the ``server.username`` from job settings.
* ``password`` (optional): FTP password. If not provided, uses the ``server.password`` from job settings.

For example, we can read the file ``document.pdf`` from an FTP server with:

.. code:: sh

    curl -XPOST http://127.0.0.1:8080/_document -H 'Content-Type: application/json' -d '{
      "type": "ftp",
      "ftp": {
        "hostname": "ftp.example.com",
        "port": 21,
        "username": "myuser",
        "password": "mypassword",
        "path": "/documents/document.pdf"
      }
    }'

If you have already configured the FTP server settings in your job ``_settings.yaml`` file:

.. code:: yaml

    name: "my_job"
    server:
      hostname: "ftp.example.com"
      port: 21
      username: "myuser"
      password: "mypassword"
      protocol: "FTP"

You can simplify the REST call by only providing the ``path``:

.. code:: sh

    curl -XPOST http://127.0.0.1:8080/_document -H 'Content-Type: application/json' -d '{
      "type": "ftp",
      "ftp": {
        "path": "/documents/document.pdf"
      }
    }'

Simulate Upload
^^^^^^^^^^^^^^^

If you want to get back the extracted content and its metadata but
without indexing into elasticsearch you can use ``simulate=true`` query
parameter:

.. code:: sh

   echo "This is my text" > test.txt
   curl -F "file=@test.txt" "http://127.0.0.1:8080/_document?debug=true&simulate=true"

Document ID
^^^^^^^^^^^

By default, FSCrawler encodes the filename to generate an id. Which
means that if you send 2 files with the same filename ``test.txt``, the
second one will overwrite the first one because they will both share the
same ID.

You can force any id you wish by adding ``id=YOUR_ID`` as a parameter:

.. code:: sh

   echo "This is my text" > test.txt
   curl -F "file=@test.txt" "http://127.0.0.1:8080/_document?id=my-test"

You can pass the ``id`` parameter within the form data:

.. code:: sh

   echo "This is my text" > test.txt
   curl -F "file=@test.txt" -F "id=my-test" "http://127.0.0.1:8080/_document"

There is a specific id named ``_auto_`` where the ID will be
autogenerated by elasticsearch. It means that sending twice the same
file will result in 2 different documents indexed.

Additional tags
^^^^^^^^^^^^^^^

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
    curl -F "file=@test.txt" -F "tags=@tags.txt" "http://127.0.0.1:8080/_document"

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

You can use this technique to add for example the filesize of the file your are uploading::

.. code:: sh

    echo "This is my text" > test.txt
    curl -F "file=@test.txt" \
      -F "tags={\"file\":{\"filesize\":$(ls -l test.txt | awk '{print $5}')}}" \
      "http://127.0.0.1:8080/_document"

.. attention:: Only standard :ref:`FSCrawler fields <generated_fields>` can be set outside ``external`` field name.

Remove a document
^^^^^^^^^^^^^^^^^

.. versionadded:: 2.10

To remove a document, you can call ``DELETE /_document`` endpoint.

If you only know the filename, you can pass it to FSCrawler using the ``filename`` field:

.. code:: sh

   curl -X DELETE "http://127.0.0.1:8080/_document?filename=test.txt"

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

   curl -X DELETE "http://127.0.0.1:8080/_document/dd18bf3a8ea2a3e53e2661c7fb53534"

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
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

By default, fscrawler creates document in the index defined in the ``_settings.yaml`` file.
However, using the REST service, it is possible to require fscrawler to use different indexes, by setting the ``index``
parameter:

.. code:: sh

   echo "This is my text" > test.txt
   curl -F "file=@test.txt" "http://127.0.0.1:8080/_document?index=my-index"
   curl -X DELETE "http://127.0.0.1:8080/_document?filename=test.txt&index=my-index"

When uploading, you can pass the ``id`` parameter within the form data:

.. code:: sh

   echo "This is my text" > test.txt
   curl -F "file=@test.txt" -F "index=my-index" "http://127.0.0.1:8080/_document"


Enabling CORS
^^^^^^^^^^^^^

To enable Cross-Origin Request Sharing you will need to set ``enable_cors: true``
under ``rest`` in your job settings. Doing so will enable the relevant access headers
on all REST service resource responses (for example ``/`` and ``/_document``).

You can check if CORS is enabled with:

.. code:: sh

   curl -I http://127.0.0.1:8080/

The response header should contain ``Access-Control-Allow-*`` parameters like:
::

   Access-Control-Allow-Origin: *
   Access-Control-Allow-Headers: origin, content-type, accept, authorization
   Access-Control-Allow-Credentials: true
   Access-Control-Allow-Methods: GET, POST, PUT, PATCH, DELETE, OPTIONS, HEAD

REST settings
^^^^^^^^^^^^^

Here is a list of REST service settings (under ``rest.`` prefix)`:

+----------------------+---------------------------+-------------------------------------------------------+
| Name                 | Default value             | Documentation                                         |
+======================+===========================+=======================================================+
| ``rest.url``         | ``http://127.0.0.1:8080`` | Rest Service URL                                      |
+----------------------+---------------------------+-------------------------------------------------------+
| ``rest.enable_cors`` | ``false``                 | Enables or disables Cross-Origin Resource Sharing     |
|                      |                           | globally for all resources                            |
+----------------------+---------------------------+-------------------------------------------------------+

.. tip::

    Most :ref:`local-fs-settings` (under ``fs.*`` in the
    settings file) also affect the REST service, e.g. ``fs.indexed_chars``.
    Local FS settings that do **not** affect the REST service are those such
    as ``url``, ``update_rate``, ``includes``, ``excludes``.

REST service is running at http://127.0.0.1:8080/ by default.

You can change it using ``rest`` settings:

.. code:: yaml

   name: "test"
   rest:
     url: "http://192.168.0.1:8180/my_fscrawler"

It also means that if you are running more than one instance of FScrawler locally, you can (must) change the port as
it will conflict.

Crawler control
^^^^^^^^^^^^^^^

.. versionadded:: 2.10

FSCrawler provides REST endpoints to control the crawler, allowing you to pause, resume, 
and monitor the crawling process. This is particularly useful for large file systems 
where crawling may take a long time and you want to be able to stop and resume later.

Getting crawler status
~~~~~~~~~~~~~~~~~~~~~~

To get the current status of the crawler, you can call ``GET /_crawler/status``:

.. code:: sh

   curl http://127.0.0.1:8080/_crawler/status

It will give you a response similar to:

.. code:: json

   {
     "state" : "RUNNING",
     "scanId" : "abc123-def456",
     "currentPath" : "/data/documents/subfolder",
     "pendingDirectories" : 42,
     "completedDirectories" : 158,
     "filesProcessed" : 1523,
     "filesDeleted" : 12,
     "scanStartTime" : "2024-01-15T10:30:00",
     "scanEndTime" : null,
     "nextCheck" : null,
     "elapsedTime" : "15m 32s",
     "retryCount" : 0,
     "lastError" : null
   }

When a scan is completed, the response will also include the ``scanEndTime`` and ``nextCheck`` fields:

.. code:: json

   {
     "state" : "COMPLETED",
     "filesProcessed" : 2500,
     "filesDeleted" : 25,
     "scanStartTime" : "2024-01-15T10:30:00",
     "scanEndTime" : "2024-01-15T11:45:00",
     "nextCheck" : "2024-01-15T12:00:00",
     "elapsedTime" : "1h 15m"
   }

The possible states are:

* ``RUNNING``: The crawler is actively processing files
* ``PAUSED``: The crawler has been paused and is waiting for a resume command
* ``STOPPED``: The crawler is not running
* ``COMPLETED``: The crawler has finished its scan successfully
* ``ERROR``: The crawler encountered an error and stopped

Pausing the crawler
~~~~~~~~~~~~~~~~~~~

To pause the crawler, call ``POST /_crawler/pause``:

.. code:: sh

   curl -X POST http://127.0.0.1:8080/_crawler/pause

The crawler will save its current progress (checkpoint) and pause. You can then safely 
stop FSCrawler if needed. When you restart FSCrawler, it will automatically resume 
from where it left off.

Response:

.. code:: json

   {
     "ok" : true,
     "message" : "Crawler paused. Checkpoint saved."
   }

Resuming the crawler
~~~~~~~~~~~~~~~~~~~~

To resume a paused crawler, call ``POST /_crawler/resume``:

.. code:: sh

   curl -X POST http://127.0.0.1:8080/_crawler/resume

Response:

.. code:: json

   {
     "ok" : true,
     "message" : "Crawler resumed"
   }

Clearing the checkpoint
~~~~~~~~~~~~~~~~~~~~~~~

If you want to force a fresh start and ignore any saved progress, you can clear 
the checkpoint file. The crawler must be paused or stopped first:

.. code:: sh

   curl -X DELETE http://127.0.0.1:8080/_crawler/checkpoint

Response:

.. code:: json

   {
     "ok" : true,
     "message" : "Checkpoint cleared"
   }

.. note::

   You can also clear the checkpoint using the ``--restart`` command line option 
   when starting FSCrawler. See :ref:`cli-options` for more details.

Automatic resume after crash
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

If FSCrawler crashes or is forcefully terminated during a crawl, it will automatically 
resume from the last saved checkpoint when restarted. The checkpoint is saved periodically 
(every 100 files by default) and whenever the crawler state changes.

Network error recovery
~~~~~~~~~~~~~~~~~~~~~~

FSCrawler automatically handles network errors with exponential backoff retry. If a network 
error occurs, it will:

1. Save the current checkpoint
2. Wait with exponential backoff (starting from 1 second, doubling each retry)
3. Attempt to reconnect
4. Resume from the failed directory

After 10 consecutive failures, the crawler will stop with an error state. You can then 
fix the network issue and restart FSCrawler to resume from the checkpoint.
