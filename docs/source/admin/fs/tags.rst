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
| ``tags.staticMetaFilename``| ``null``              | `Static Metadata`_              |
+----------------------------+-----------------------+---------------------------------+

Meta Filename
^^^^^^^^^^^^^

You can use another filename for the external tags file. For example, if you want to use
``meta_tags.json`` instead of ``.meta.yml``, you can set:

.. code:: yaml

   fs:
     tags:
       metaFilename: "meta_tags.json"

.. note::

    Only json and yaml files are supported.

Static Metadata
^^^^^^^^^^^^^^^

.. versionadded:: 2.10

You can define static metadata that will be applied to all documents indexed by FSCrawler.
This is useful when you want to add the same metadata to every document without needing
to create a ``.meta.yml`` file in every directory.

For example, if you want to add a ``hostname`` and ``environment`` field to all documents. Create a file
named ``/path/to/static_metadata.yml`` with the following content:

.. code:: yaml

    external:
      hostname: "server001"
      environment: "production"

Then, configure FSCrawler to use this static metadata file using the ``tags.staticMetaFilename`` setting:

.. code:: yaml

   fs:
     url: "/path/to/docs"
   tags:
     staticMetaFilename: "/path/to/static_metadata.yml"

All documents indexed will have the fields ``external.hostname`` and ``external.environment``
with the values ``server001`` and ``production`` respectively.

.. note::

    Static metadata is merged first and then the content within a ``.meta.yml`` is applied.
    If you are overwriting the tags within the ``.meta.yml`` file, then that
    takes precedence.

    Example: If the static metadata file contains:

    .. code:: yaml
         external:
            category: "general"
            owner: "team-a"

    And the ``.meta.yml`` file contains:

    .. code:: yaml
         external:
            owner: "team-b"
            priority: "high"

    The resulting document will have:

    .. code:: yaml
         external:
            category: "general"
            owner: "team-b"
            priority: "high"

.. tip::

    Use static metadata for configuration-level metadata that applies to all documents,
    and use per-directory ``.meta.yml`` files for metadata specific to certain directories
    or files.
