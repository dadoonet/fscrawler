.. _installation:

Download FSCrawler
------------------

.. ifconfig:: release.endswith('-SNAPSHOT')

    Depending on your Elasticsearch cluster version, you can download
    FSCrawler |version| using the following links:

    * |Download_URL_V7|_ for Elasticsearch V7.
    * |Download_URL_V6|_ for Elasticsearch V6.

    The filename ends with ``.zip``.

    .. warning::

        This is a **SNAPSHOT** version.
        You can also download a **stable** version from Maven Central:

        * |Maven_Central_V7|_ for Elasticsearch V7.
        * |Maven_Central_V6|_ for Elasticsearch V6.

.. ifconfig:: release == version

    Depending on your Elasticsearch cluster version, you can download
    FSCrawler |version| using the following links:

    * |Download_URL_V7|_ for Elasticsearch V7.
    * |Download_URL_V6|_ for Elasticsearch V6.

    .. tip::

        This is a **stable** version.
        You can choose another version than |version| from Maven Central:

        * |Maven_Central_V7|_ for Elasticsearch V7.
        * |Maven_Central_V6|_ for Elasticsearch V6.

        You can also download a **SNAPSHOT** version from Sonatype:

        * |Sonatype_V7|_ for Elasticsearch V7.
        * |Sonatype_V6|_ for Elasticsearch V6.

The distribution contains:

::

   $ tree
   .
   ├── LICENSE
   ├── NOTICE
   ├── README.md
   ├── bin
   │   ├── fscrawler
   │   └── fscrawler.bat
   ├── config
   │   └── log4j2.xml
   └── lib
       ├── ... All needed jars

.. _docker:

Using docker
------------

Pull the Docker image:

.. code:: sh

   docker pull dadoonet/fscrawler

.. note::

    This image is very big (1.2+gb) as it contains `Tesseract <https://tesseract-ocr.github.io/tessdoc/>`__ and
    all the `trained language data <https://tesseract-ocr.github.io/tessdoc/Data-Files.html>`__.
    If you don't want to use OCR at all, you can use a smaller image (around 530mb) by pulling instead
    ``dadoonet/fscrawler:noocr``

    .. code:: sh

       docker pull dadoonet/fscrawler:noocr


Let say your documents are located in ``~/tmp`` dir and you want to store your fscrawler jobs in ``~/.fscrawler``.
You can run FSCrawler with:

.. code:: sh

   docker run -it --rm -v ~/.fscrawler:/root/.fscrawler -v ~/tmp:/tmp/es:ro dadoonet/fscrawler fscrawler job_name

On the first run, if the job does not exist yet in ``~/.fscrawler``, FSCrawler will ask you if you want to create it:

::

    10:16:53,880 INFO  [f.p.e.c.f.c.BootstrapChecks] Memory [Free/Total=Percent]: HEAP [67.3mb/876.5mb=7.69%], RAM [2.1gb/3.8gb=55.43%], Swap [1023.9mb/1023.9mb=100.0%].
    10:16:53,899 WARN  [f.p.e.c.f.c.FsCrawlerCli] job [job_name] does not exist
    10:16:53,900 INFO  [f.p.e.c.f.c.FsCrawlerCli] Do you want to create it (Y/N)?
    y
    10:16:56,745 INFO  [f.p.e.c.f.c.FsCrawlerCli] Settings have been created in [/root/.fscrawler/job_name/_settings.yaml]. Please review and edit before relaunch

.. note::

    The configuration file is actually stored on your machine in ``~/.fscrawler/job_name/_settings.yaml``.
    Remember to change the URL of your elasticsearch instance as the container won't be able to see it
    running under the default ``127.0.0.1``. You will need to use the actual IP address of the host.


Using docker compose
--------------------

In this section, the following directory layout is assumed:

.. code-block:: none

  .
  ├── config
  │   └── job_name
  │       └── _settings.yaml
  ├── data
  │   └── <your files>
  ├── logs
  │   └── <fscrawler logs>
  └── docker-compose.yml

For example, to connect to a docker container named ``elasticsearch``, modify your ``_settings.yaml``.

.. code:: yaml

  name: "job_name"
  elasticsearch:
    nodes:
    - url: "http://elasticsearch:9200"

And, prepare the following ``docker-compose.yml``.

.. code:: yaml

    version: '3'
    services:
      # Elasticsearch Cluster
      elasticsearch:
        image: docker.elastic.co/elasticsearch/elasticsearch:$ELASTIC_VERSION
        container_name: elasticsearch
        environment:
          - bootstrap.memory_lock=true
          - discovery.type=single-node
        restart: always
        ulimits:
          memlock:
            soft: -1
            hard: -1
        volumes:
          - data:/usr/share/elasticsearch/data
        ports:
          - 9200:9200
        networks:
          - fscrawler_net

      # FSCrawler
      fscrawler:
        image: dadoonet/fscrawler:$FSCRAWLER_VERSION
        container_name: fscrawler
        restart: always
        volumes:
          - ${PWD}/config:/root/.fscrawler
          - ${PWD}/logs:/usr/share/fscrawler/logs
          - ../../test-documents/src/main/resources/documents/:/tmp/es:ro
        depends_on:
          - elasticsearch
        command: fscrawler --rest idx
        networks:
          - fscrawler_net

    volumes:
      data:
        driver: local

    networks:
      fscrawler_net:
        driver: bridge

Then, you can run Elasticsearch.

.. code:: sh

    docker-compose up -d elasticsearch
    docker-compose logs -f elasticsearch

Wait for elasticsearch to be started:

::



After starting Elasticsearch, you can run FSCrawler.

.. code:: sh

  docker-compose up fscrawler



Running as a Service on Windows
-------------------------------

Create a ``fscrawlerRunner.bat`` as:

.. code:: sh

   set JAVA_HOME=c:\Program Files\Java\jdk15.0.1
   set FS_JAVA_OPTS=-Xmx2g -Xms2g
   /Elastic/fscrawler/bin/fscrawler.bat --config_dir /Elastic/fscrawler data >> /Elastic/logs/fscrawler.log 2>&1

Then use ``fscrawlerRunner.bat`` to create your windows service.


Upgrade FSCrawler
-----------------

It can happen that you need to upgrade a mapping or reindex an entire
index before starting fscrawler after a version upgrade. Read carefully
the following update instructions.

To update fscrawler, just download the new version, unzip it in another
directory and launch it as usual. It will still pick up settings from
the configuration directory. Of course, you need to stop first the
existing running instances.

Upgrade to 2.2
~~~~~~~~~~~~~~

-  fscrawler comes with new default mappings for files. They have better
   defaults as they consume less disk space and CPU at index time. You
   should remove existing files in ``~/.fscrawler/_default/_mappings``
   before starting the new version so default mappings will be updated.
   If you modified manually mapping files, apply the modification you
   made on sample files.

-  ``excludes`` is now set by default for new jobs to ``["~*"]``. In
   previous versions, any file or directory containing a ``~`` was
   excluded. Which means that if in your jobs, you are defining any
   exclusion rule, you need to add ``*~*`` if you want to get back the
   exact previous behavior.

-  If you were indexing ``json`` or ``xml`` documents with the
   ``filename_as_id`` option set, we were previously removing the suffix
   of the file name, like indexing ``1.json`` was indexed as ``1``. With
   this new version, we don’t remove anymore the suffix. So the ``_id``
   for your document will be now ``1.json``.

.. _upgrade_2.3:

Upgrade to 2.3
~~~~~~~~~~~~~~

-  fscrawler comes with new mapping for folders. The change is really
   tiny so you can skip this step if you wish. We basically removed
   ``name`` field in the folder mapping as it was unused.

-  The way FSCrawler computes now ``path.virtual`` for docs has changed.
   It now includes the filename. Instead of ``/path/to`` you will now
   get ``/path/to/file.txt``.

-  The way FSCrawler computes now ``virtual`` for folders is now
   consistent with what you can see for folders.

-  ``path.encoded`` in documents and ``encoded`` in folders have been
   removed as not needed by FSCrawler after all.

-  :ref:`ocr_integration` is now properly activated for PDF documents.
   This can be time, cpu and memory consuming though. You can disable
   explicitly it by setting ``fs.pdf_ocr`` to ``false``.

-  All dates are now indexed in elasticsearch in UTC instead of without
   any time zone. For example, we were indexing previously a date like
   ``2017-05-19T13:24:47.000``. Which was producing bad results when you
   were located in a time zone other than UTC. It’s now indexed as
   ``2017-05-19T13:24:47.000+0000``.

-  In order to be compatible with the coming 6.0 elasticsearch version,
   we need to get rid of types as only one type per index is still
   supported. Which means that we now create index named ``job_name``
   and ``job_name_folder`` instead of one index ``job_name`` with two
   types ``doc`` and ``folder``. If you are upgrading from FSCrawler
   2.2, it requires that you reindex your existing data either by
   deleting the old index and running again FSCrawler or by using the
   `reindex
   API <https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-reindex.html>`__
   as follows:

::

   # Create folder index job_name_folder based on existing folder data
   POST _reindex
   {
     "source": {
       "index": "job_name",
       "type": "folder"
     },
     "dest": {
       "index": "job_name_folder"
     }
   }
   # Remove old folder data from job_name index
   POST job_name/folder/_delete_by_query
   {
     "query": {
       "match_all": {}
     }
   }

Note that you will need first to create the right settings and mappings
so you can then run the reindex job. You can do that by launching
``bin/fscrawler job_name --loop 0``.

Better, you can run ``bin/fscrawler job_name --upgrade`` and let
FSCrawler do all that for you. Note that this can take a loooong time.

Also please be aware that some APIs used by the upgrade action are only
available from elasticsearch 2.3 (reindex) or elasticsearch 5.0 (delete
by query). If you are running an older version than 5.0 you need first
to upgrade elasticsearch.

This procedure only applies if you did not set previously
``elasticsearch.type`` setting (default value was ``doc``). If you did,
then you also need to reindex the existing documents to the default
``_doc`` type as per elasticsearch 6.x (or ``doc`` for 5.x series):

::

   # Copy old type doc to the default doc type
   POST _reindex
   {
     "source": {
       "index": "job_name",
       "type": "your_type_here"
     },
     "dest": {
       "index": "job_name",
       "type": "_doc"
     }
   }
   # Remove old type data from job_name index
   POST job_name/your_type_here/_delete_by_query
   {
     "query": {
       "match_all": {}
     }
   }

But note that this last step can take a very loooong time and will
generate a lot of IO on your disk. It might be easier in such case to
restart fscrawler from scratch.

-  As seen in the previous point, we now have 2 indices instead of a
   single one. Which means that ``elasticsearch.index`` setting has been
   split to ``elasticsearch.index`` and ``elasticsearch.index_folder``.
   By default, it’s set to the crawler name and the crawler name plus
   ``_folder``. Note that the ``upgrade`` feature performs that change
   for you.

-  fscrawler has removed now mapping files ``doc.json`` and
   ``folder.json``. Mapping for doc is merged within ``_settings.json``
   file and folder mapping is now part of ``_settings_folder.json``.
   Which means you can remove old files to avoid confusion. You can
   simply remove existing files in ``~/.fscrawler/_default`` before
   starting the new version so default files will be created again.

Upgrade to 2.4
~~~~~~~~~~~~~~

-  No specific step needed. Just note that mapping changed as we support
   more metadata. Might be useful to run similar steps as for 2.2
   upgrade.

Upgrade to 2.5
~~~~~~~~~~~~~~

-   A bug was causing a lot of data going over the wire each time
    FSCrawler was running. To fix this issue, we changed the default
    mapping and we set ``store: true`` on field ``file.filename``. If
    this field is not stored and ``remove_deleted`` is ``true``
    (default), FSCrawler will fail while crawling your documents. You
    need to create the new mapping accordingly and reindex your existing
    data either by deleting the old index and running again FSCrawler or
    by using the `reindex
    API <https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-reindex.html>`__
    as follows:

    ::

       # Backup old index data
       POST _reindex
       {
         "source": {
           "index": "job_name"
         },
         "dest": {
           "index": "job_name_backup"
         }
       }
       # Remove job_name index
       DELETE job_name

    Restart FSCrawler with the following command. It will just create the
    right mapping again.

    .. code:: sh

       $ bin/fscrawler job_name --loop 0

    Then restore old data:

    ::

       POST _reindex
       {
         "source": {
           "index": "job_name_backup"
         },
         "dest": {
           "index": "job_name"
         }
       }
       # Remove backup index
       DELETE job_name_backup

    The default mapping changed for FSCrawler for ``meta.raw.*`` fields.
    Might be better to reindex your data.

-   The ``excludes`` parameter is also used for directory names. But this
    new implementation also brings a breaking change if you were using ``excludes``
    previously. In the previous implementation, the regular expression was only applied
    to the filename. It's now applied to the full virtual path name.

    For example if you have a ``/tmp`` dir as follows:

    .. code::

        /tmp
        └── folder
            ├── foo.txt
            └── bar.txt

    Previously excluding ``foo.txt`` was excluding the virtual file ``/folder/foo.txt``.
    If you still want to exclude any file named ``foo.txt`` whatever its directory
    you now need to specify ``*/foo.txt``:

    .. code:: json

       {
         "name" : "test",
         "fs": {
           "excludes": [
             "*/foo.txt"
           ]
         }
       }

    For more information, read :ref:`includes_excludes`.

- For new indices, FSCrawler now uses ``_doc`` as the default type name for clusters
  running elasticsearch 6.x or superior.

Upgrade to 2.6
~~~~~~~~~~~~~~

- FSCrawler comes now with multiple distributions, depending on the elasticsearch
  cluster you're targeting to run.

- ``elasticsearch.nodes`` settings using ``host``, ``port`` or ``scheme`` have been replaced by
  an easier notation using ``url`` setting like ``http://127.0.0.1:9200``. You will need to modify
  your existing settings and use the new notation if warned.

Upgrade to 2.7
~~~~~~~~~~~~~~

- FSCrawler comes now with an elasticsearch 7.x implementation.
- FSCrawler supports Workplace Search 7.x.
- FSCrawler also supports YAML format for jobs (default).
- The elasticsearch 6.x implementation does not support elasticsearch versions prior to 6.7.
  If you are using an older version, it's better to upgrade or you need to "hack" the distribution
  and replace all elasticsearch/lucene jars to the 6.6 version.
- FSCrawler does not follow symbolic links anymore. You need to set explicitly ``fs.follow_symlink``
  to ``true`` if you wish revert to the previous behavior.
- The mapping for elasticsearch 6.x can not contain anymore the type name.
- We removed the Elasticsearch V5 compatibility as it's not maintained anymore by elastic.
- You need to use a recent JVM to run FSCrawler (Java 11 as a minimum. Java 15+ recommended)
- The mapping for the folders changed and is now consistent with the mapping for documents. If you are already using
  FSCrawler, you will need to first remove the existing ``*_folder`` indices and remove or edit the default
  settings files in ``~/_default/7/_settings_folder.json`` and ``~/_default/6/_settings_folder.json`` or any job
  specific setting file like ``~/.fscrawler/{job_name}/_mappings/7/_settings_folder.json`` or
  ``~/.fscrawler/{job_name}/_mappings/6/_settings_folder.json``.
