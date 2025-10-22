.. _tags:

External Tags
-------------

.. contents:: :backlinks: entry

.. versionadded:: 2.10

The goal of this feature is to allow users to provide additional metadata when
crawling files. Whenever a directory is crawled, FSCrawler checks if a file named
``.meta.yml`` is present in the directory. If it is, the content of this file is
used to enrich the document.

For example, if you have a file named ``.meta.yml`` in the directory
``/path/to/data/dir``:

.. code:: yaml

   external:
     myTitle: "My document title"

Then the document indexed will have a new field named ``external.myTitle`` with the value
``My document title``.

Only supported fields can be added to the document. If you try to add a field
which is not supported, it will be ignored.

For example, if you have the ``.meta.yml`` file contains:

.. code:: yaml

   foo: "bar"
   external:
     myTitle: "My document title"

The document indexed will have a new field named ``external.myTitle`` with the value
``My document title``. The field ``foo`` will be ignored.

If you really want to add a field named ``foo``, you need to add it first as an external tag:

.. code:: yaml

   external:
     foo: "bar"
     myTitle: "My document title"

and then use an ingest pipeline to rename the ``external.foo`` field to ``foo``. See :ref:`ingest_node`.

The ``.meta.yml`` file can also overwrite existing fields. For example, if you have the following
``.meta.yml`` file:

.. code:: yaml

   content: "HIDDEN"

Then the ``content`` field will be replaced by ``HIDDEN`` even though something else is extracted.

.. note::

    The ``.meta.yml`` file is not indexed. It is only used to enrich the document.


Here is a list of Tags settings (under ``tags.`` prefix)`:

+----------------------------+-----------------------+---------------------------------+
| Name                       | Default value         | Documentation                   |
+============================+=======================+=================================+
| ``tags.metaFilename``      | ``".meta.yml"``       | `Meta Filename`_                |
+----------------------------+-----------------------+---------------------------------+
| ``tags.staticMetadata``    | ``null``              | `Static Metadata`_              |
+----------------------------+-----------------------+---------------------------------+

Meta Filename
^^^^^^^^^^^^^

You can use another filename for the external tags file. For example, if you want to use
``meta_tags.json`` instead of ``.meta.yml``, you can set:

.. code:: yaml

   tags:
     metaFilename: "meta_tags.json"

.. note::

    Only json and yaml files are supported.

Static Metadata
^^^^^^^^^^^^^^^

.. versionadded:: 3.0

You can define static metadata that will be applied to all documents indexed by FSCrawler.
This is useful when you want to add the same metadata to every document without needing
to create a ``.meta.yml`` file in every directory.

For example, if you want to add a hostname and environment to all documents:

.. code:: yaml

   fs:
     url: "/path/to/docs"
   tags:
     staticMetadata:
       external:
         hostname: "server001"
         environment: "production"

All documents indexed will have the fields ``external.hostname`` and ``external.environment``
with the values ``server001`` and ``production`` respectively.

You can add complex nested structures:

.. code:: yaml

   tags:
     staticMetadata:
       external:
         tenantId: 42
         company: "my company"
         region: "us-west-2"
       custom:
         projectId: 123
         department: "engineering"

.. note::

    Static metadata is merged with per-directory metadata files. If both static metadata
    and a ``.meta.yml`` file define the same field, the value from the ``.meta.yml`` file
    takes precedence.

.. tip::

    Use static metadata for configuration-level metadata that applies to all documents,
    and use per-directory ``.meta.yml`` files for metadata specific to certain directories
    or files.
