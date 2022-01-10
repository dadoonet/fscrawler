Example job file specification
==============================

The job file (``~/.fscrawler/test/_settings.yaml``) for the job name ``test`` must comply to the following ``yaml`` specifications:

.. code:: yaml

   # required
   name: "test"

   # required
   fs:

     # define a "local" file path crawler, if running inside a docker container this must be the path INSIDE the container
     url: "/path/to/docs"
     follow_symlink: false
     remove_deleted: true
     continue_on_error: false

     # scan every 5 minutes for changes in url defined above
     update_rate: "5m"

     # opional: define includes and excludes, "~" files are excluded by default if not defined below
     includes:
     - "*.doc"
     - "*.xls"
     excludes:
     - "resume.doc"

     # optional: do not send big files to TIKA
     ignore_above: "512mb"

     # special handling of JSON files, should only be used if ALL files are JSON
     json_support: false
     add_as_inner_object: false

     # special handling of XML files, should only be used if ALL files are XML
     xml_support: false

     # use MD5 from filename (instead of filename) if set to false
     filename_as_id: true

     # include size ot file in index
     add_filesize: true

     # inlcude user/group of file only if needed
     attributes_support: false

     # do you REALLY want to store every file as a copy in the index ? Then set this to true
     store_source: false

     # you may want to store (partial) content of the file (see indexed_chars)	 
     index_content: true

     # how much data from the content of the file should be indexed (and stored inside the index), set to 0 if you need checksum, but no content at all to be indexed
     #indexed_chars: "0"
     indexed_chars: "10000.0"

     # usually file metadata will be stored in separate fields, if you want to keep the original set, set this to true
     raw_metadata: false

     # optional: add checksum meta (requires index_content to be set to true)
     checksum: "MD5"

     # recommmended, but will create another index
     index_folders: true

     lang_detect: false

     ocr.pdf_strategy: noocr
     #ocr:
     #  language: "eng"
     #  path: "/path/to/tesseract/if/not/available/in/PATH"
     #  data_path: "/path/to/tesseract/tessdata/if/needed"

   # optional: only required if you want to SSH to another server to index documents from there
   server:
     hostname: "localhost"
     port: 22
     username: "dadoonet"
     password: "password"
     protocol: "SSH"
     pem_path: "/path/to/pemfile"

   # required
   elasticsearch:
     nodes:
     # With Cloud ID
     - cloud_id: "CLOUD_ID"
     # With URL
     - url: "http://127.0.0.1:9200"
     bulk_size: 1000
     flush_interval: "5s"
     byte_size: "10mb"
     username: "elastic"
     password: "password"
     # optional, defaults to "docs"
     index: "test_docs"
     # optional, defaults to "test_folders", used when es.index_folders is set to true
     index_folder: "test_fold"
   rest:
     # only is started with --rest option
     url: "http://127.0.0.1:8080/fscrawler"

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

.. versionadded:: 2.7

You can define your job settings either in ``_settings.yaml`` (using ``.yaml`` extension) or
in ``_settings.json`` (using ``.json`` extension).
