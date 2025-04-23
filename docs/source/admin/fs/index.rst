Job file specifications
=======================

.. contents:: :backlinks: entry

Expected files
--------------

FSCrawler expects to find a job directory in the ``~/.fscrawler`` directory or in the directory
you defined with the ``-config_dir`` CLI option (see :ref:`cli-options`). The job file could be either:

* a ``yaml`` file named ``_settings.yaml``
* a ``json`` file named ``_settings.json``
* a list of files within a directory named ``_settings``

When using a directory, FSCrawler will merge all files found in the directory. Meaning that you can split your settings
in multiple files, like:

* ``my_job_fs.yaml`` which contains the file system settings
* ``my_job_elasticsearch.yaml`` which contains the elasticsearch settings

Using placeholders
------------------

.. versionadded:: 2.10

FSCrawler supports placeholders in the job file. This is useful when you want to use environment variables in your job file.
For example, you can define the following job file:

.. code:: yaml

   fs:
     url: "${HOME}/docs"
   elasticsearch:
     nodes:
     - url: "${ES_NODE1:=https://127.0.0.1:9200}"
     api_key: "${ES_API_KEY}"

When running FSCrawler, it will replace ``${HOME}``, ``${ES_NODE1}`` and ``${ES_API_KEY}``
by their respective values which will be read from environment variables and java system properties if not found.

If no value is found, it will use the default value after the ``:=`` if any, or it will fail starting if no default value.
In the previous example, both ``${HOME}`` and ``${ES_API_KEY}`` are mandatory but ``${ES_NODE1}`` is optional and will
be set to ``https://127.0.0.1:9200`` if not set.

FSCrawler is using the gestalt-config project to handle placeholders. You can read more about String substitution in the
`gestalt-config documentation <https://github.com/gestalt-config/gestalt#string-substitution>`_.

Default placeholders
--------------------

FSCrawler supports a set of default placeholders that you can define using environment variables.
The form of those placeholders is the prefix ``FSCRAWLER_`` and the setting name. For example,
``fs.url`` can be set using the environment variable ``FSCRAWLER_FS_URL`` or the system property ``-Dfs.url``.

As an example, you can run:

.. code:: sh

   FSCRAWLER_NAME=foo \
   FSCRAWLER_FS_URL=/tmp/test \
   FSCRAWLER_ELASTICSEARCH_API-KEY=VnVhQ2ZHY0JDZGJrUW0tZTVhT3g6dWkybHAyYXhUTm1zeWFrdzl0dk5udw== \
   bin/fscrawler test

or:

.. code:: sh

   FS_JAVA_OPTS="-Dname=foo -Dfs.url=/tmp/test -Delasticsearch.api-key=VnVhQ2ZHY0JDZGJrUW0tZTVhT3g6dWkybHAyYXhUTm1zeWFrdzl0dk5udw==" \
   bin/fscrawler test

.. note::

    If you define as well some settings in the job file, the settings in the job file will override the
    environment variables and system properties.

Example job file specification
------------------------------

The job file (``~/.fscrawler/test/_settings.yaml``) for the job name ``test`` must comply to the following ``yaml`` specifications:

.. code:: yaml

   # optional: the name of the crawler. Defaults to the job directory name.
   name: "test"

   # required
   fs:

     # define a "local" file path crawler, if running inside a docker container this must be the path INSIDE the container (/tmp/es)
     url: "/path/to/docs"
     follow_symlinks: false
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

   # optional: only needed if you want to change the default settings
    tags:
      metaFilename: "meta_tags.json" # default is ".meta.yml"

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
     - url: "https://127.0.0.1:9200"
     bulk_size: 1000
     flush_interval: "5s"
     byte_size: "10mb"
     # choose one of the 2 following options:
     # 1 - Using Api Key
     api_key: "VnVhQ2ZHY0JDZGJrUW0tZTVhT3g6dWkybHAyYXhUTm1zeWFrdzl0dk5udw=="
     # 2 - Using username/password (not recommended / deprecated)
     username: "elastic"
     password: "password"
     # optional, defaults to ``name``-property
     index: "test_docs"
     # optional, defaults to "test_folders", used when es.index_folders is set to true
     index_folder: "test_fold"
     # optional, defaults to "true"
     push_templates: "true"
     # optional, defaults to "true", used with Elasticsearch 8.17+ with a trial or enterprise license
     semantic_search: "true"
   # only used when started with --rest option
   rest:
     url: "http://127.0.0.1:8080/fscrawler"

Here is a list of existing top level settings:

+-----------------------------------+-------------------------------+
| Name                              | Documentation                 |
+===================================+===============================+
| ``name`` (mandatory field)        | :ref:`simple_crawler`         |
+-----------------------------------+-------------------------------+
| ``fs``                            | :ref:`local-fs-settings`      |
+-----------------------------------+-------------------------------+
| ``tags``                          | :ref:`tags`                   |
+-----------------------------------+-------------------------------+
| ``elasticsearch``                 | :ref:`elasticsearch-settings` |
+-----------------------------------+-------------------------------+
| ``server``                        | :ref:`ssh-settings`           |
+-----------------------------------+-------------------------------+
| ``rest``                          | :ref:`rest-service`           |
+-----------------------------------+-------------------------------+

You can define your job settings either in ``_settings.yaml`` (using ``.yaml`` extension) or
in ``_settings.json`` (using ``.json`` extension).
