Job file specification
======================

The job file must comply to the following ``json`` specifications:

.. code:: json

   {
     "name" : "job_name",
     "fs" : {
       "url" : "/path/to/docs",
       "update_rate" : "5m",
       "includes" : [ "*.doc", "*.xls" ],
       "excludes" : [ "resume.doc" ],
       "json_support" : false,
       "filename_as_id" : true,
       "add_filesize" : true,
       "remove_deleted" : true,
       "add_as_inner_object" : false,
       "store_source" : true,
       "index_content" : true,
       "indexed_chars" : "10000.0",
       "attributes_support" : false,
       "raw_metadata" : true,
       "xml_support" : false,
       "index_folders" : true,
       "lang_detect" : false,
       "continue_on_error" : false,
       "pdf_ocr" : true,
       "ocr" : {
         "language" : "eng",
         "path": "/path/to/tesseract/if/not/available/in/PATH",
         "data_path": "/path/to/tesseract/tessdata/if/needed"
       }
     },
     "server" : {
       "hostname" : "localhost",
       "port" : 22,
       "username" : "dadoonet",
       "password" : "password",
       "protocol" : "SSH",
       "pem_path" : "/path/to/pemfile"
     },
     "elasticsearch" : {
       "nodes" : [ {
         // With Cloud ID
         "cloud_id" : "CLOUD_ID"
       }, {
         // With scheme, host and port
         "host" : "127.0.0.1",
         "port" : 9200,
         "scheme" : "HTTP"
       } ],
       "index" : "docs",
       "bulk_size" : 1000,
       "flush_interval" : "5s",
       "byte_size" : "10mb",
       "username" : "elastic",
       "password" : "password"
     },
     "rest" : {
       "scheme" : "HTTP",
       "host" : "127.0.0.1",
       "port" : 8080,
       "endpoint" : "fscrawler"
     }
   }

Here is a list of existing top level settings:

+-----------------------------------+-------------------------------+
| Name                              | Documentation                 |
+===================================+===============================+
| ``name`` (mandatory field)        | :ref:`simple_crawler`         |
+-----------------------------------+-------------------------------+
| ``fs``                            | :ref:`local-fs-settings`      |
+-----------------------------------+-------------------------------+
| ``elasticsearch``                 | :ref:`elasticsearch-settings` |
+-----------------------------------+-------------------------------+
| ``server``                        | :ref:`ssh-settings`           |
+-----------------------------------+-------------------------------+
| ``rest``                          | :ref:`rest-service`           |
+-----------------------------------+-------------------------------+

