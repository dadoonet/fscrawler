Starting with a REST gateway
----------------------------

.. versionadded:: 2.2

FSCrawler can be a nice gateway to elasticsearch if you want to upload
binary documents and index them into elasticsearch without writing by
yourself all the code to extract data and communicate with
elasticsearch.

To start FSCrawler with the REST service, use the ``--rest`` option. A
good idea is also to combine it with ``--loop 0`` so you won’t index
local files but only listen to incoming REST requests:

.. code:: sh

   $ bin/fscrawler job_name --loop 0 --rest
   18:55:37,851 INFO  [f.p.e.c.f.FsCrawlerImpl] Starting FS Crawler
   18:55:39,237 INFO  [f.p.e.c.f.FsCrawlerImpl] FS crawler Rest service started on [http://127.0.0.1:8080/fscrawler]

Check the service is working with:

.. code:: sh

   curl http://127.0.0.1:8080/fscrawler/

It will give you back a JSON document.

Then you can start uploading your binary files:

.. code:: sh

   echo "This is my text" > test.txt
   curl -F "file=@test.txt" "http://127.0.0.1:8080/fscrawler/_upload"

It will index the file into elasticsearch and will give you back the
elasticsearch URL for the created document, like:

.. code:: json

   {
     "ok" : true,
     "filename" : "test.txt",
     "url" : "http://127.0.0.1:9200/fscrawler-rest-tests_doc/doc/dd18bf3a8ea2a3e53e2661c7fb53534"
   }

To enable CORS (Cross-Origin Request Sharing) functionality you will
need to set ``enable_cors: true`` in your job settings.

Read the :ref:`rest-service` chapter for more information.
