Download FSCrawler
------------------

.. ifconfig:: release.endswith('-SNAPSHOT')

    You can download FSCrawler |version| from this link: |Download_URL|_.
    The filename ends with ``.zip``.

    .. warning::

        This is a **SNAPSHOT** version.
        You can also download a **stable** version from |Maven_Central|_.

.. ifconfig:: release == version

    You can download FSCrawler |version| from this link: |Download_URL|_.

    .. tip::

        This is a **stable** version.
        You can choose another version than |version| in |Maven_Central|_.

        You can also download a **SNAPSHOT** version from |Sonatype|_.

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
   └── lib
       ├── ... All needed jars


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
``doc`` type as per elasticsearch 6.0:

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
       "type": "doc"
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

