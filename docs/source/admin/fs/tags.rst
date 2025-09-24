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
| ``tags.staticTags``        | ``null``              | `Static Metadata`_              |
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

.. versionadded:: 2.10

You can define static metadata that will be applied to ALL documents indexed by FSCrawler 
without needing to add individual ``.meta.yml`` files in each directory. This is useful 
for adding consistent metadata like hostname, environment, data source information, or 
other static properties to all your documents.

.. code:: yaml

   tags:
     staticTags:
       external:
         hostname: "server001"
         environment: "production"
       custom:
         category: "documents"
         source: "filesystem"

The static metadata is applied to every document during indexing. It is merged with any
existing external metadata from ``.meta.yml`` files, with file-based metadata taking
precedence over static metadata.

In the above example, every document will have the following additional fields:

- ``external.hostname``: "server001"  
- ``external.environment``: "production"
- ``custom.category``: "documents"
- ``custom.source``: "filesystem"

The static metadata follows `the same rules as external metadata`_ - only supported fields 
can be added to the document, and unsupported fields will be ignored.

.. _the same rules as external metadata: `Meta Filename`_

Precedence Example
""""""""""""""""""

When both static metadata and `.meta.yml` files are present, the file-based metadata 
takes precedence over static metadata for conflicting fields.

For example, with this static configuration:

.. code:: yaml

   tags:
     staticTags:
       external:
         hostname: "server001"
         environment: "production"
       custom:
         category: "documents"

And a `.meta.yml` file containing:

.. code:: yaml

   external:
     environment: "development"
   custom:
     priority: "high"

The resulting document will have:

- ``external.hostname``: "server001" (from static metadata)
- ``external.environment``: "development" (from .meta.yml, overriding static)
- ``custom.category``: "documents" (from static metadata)
- ``custom.priority``: "high" (from .meta.yml, additional field)
