Tips and tricks
===============

Moving files to a “watched” directory
-------------------------------------

When moving an existing file to the directory FSCrawler is watching, you
need to explicitly ``touch`` all the files as when moved, the files are
keeping their original date intact:

.. code:: sh

   # single file
   touch file_you_moved

   # all files
   find  -type f  -exec touch {} +

   # all .txt files
   find  -type f  -name "*.txt" -exec touch {} +

Or you need to :ref:`restart <cli-options>` from the
beginning with the ``--restart`` option which will reindex everything.

Indexing from HDFS drive
------------------------

There is no specific support for HDFS in FSCrawler. But you can `mount
your HDFS on your
machine <https://wiki.apache.org/hadoop/MountableHDFS>`__ and run FS
crawler on this mount point. You can also read details about `HDFS NFS
Gateway <http://hadoop.apache.org/docs/stable/hadoop-project-dist/hadoop-hdfs/HdfsNfsGateway.html>`__.


.. _ocr_integration:

OCR integration
---------------

.. versionadded:: 2.3

To deal with images containing text, just `install
Tesseract <https://github.com/tesseract-ocr/tesseract/wiki>`__.
Tesseract will be auto-detected by Tika or you can explicitly `set the
path to tesseract binary <OCR Path>`_. Then add an image (png, jpg, …)
into your Fscrawler :ref:`root-directory`. After the next
index update, the text will be indexed and placed in "_source.content".

By default, FSCrawler will try to extract also images from your PDF
documents and run OCR on them. This can be a CPU intensive operation. If
you don’t mean to run OCR on PDF but only on images, you can set
``fs.pdf_ocr`` to ``false``:

.. code:: yaml

   name: "test"
   fs:
     pdf_ocr: false

OCR settings
^^^^^^^^^^^^

Here is a list of OCR settings (under ``fs.ocr`` prefix)`:

+------------------------+---------------+------------------------------------+
| Name                   | Default value | Documentation                      |
+========================+===============+====================================+
| ``fs.ocr.language``    | ``"eng"``     | `OCR Language`_                    |
+------------------------+---------------+------------------------------------+
| ``fs.ocr.path``        | ``null``      | `OCR Path`_                        |
+------------------------+---------------+------------------------------------+
| ``fs.ocr.data_path``   | ``null``      | `OCR Data Path`_                   |
+------------------------+---------------+------------------------------------+
| ``fs.ocr.output_type`` | ``txt``       | `OCR Output Type`_                 |
+------------------------+---------------+------------------------------------+

OCR Language
^^^^^^^^^^^^

If you have installed a `Tesseract Language
pack <https://wiki.apache.org/tika/TikaOCR>`__, you can use it when
parsing your documents by setting ``fs.ocr.language`` property in your
``~/.fscrawler/test/_settings.yml`` file:

.. code:: yaml

   name: "test"
   fs:
     url: "/path/to/data/dir"
     ocr:
       language: "eng"

.. note::

    You can define multiple languages by using ``+`` sign as a separator:

    .. code:: yaml

       name: "test"
       fs:
         url: "/path/to/data/dir"
         ocr:
           language: "eng+fas+fra"

OCR Path
^^^^^^^^

If your Tesseract application is not available in default system PATH,
you can define the path to use by setting ``fs.ocr.path`` property in
your ``~/.fscrawler/test/_settings.yml`` file:

.. code:: yaml

   name: "test"
   fs:
     url: "/path/to/data/dir"
     ocr:
       path: "/path/to/tesseract/executable"

When you set it, it’s highly recommended to set the `OCR Data Path`_.

OCR Data Path
^^^^^^^^^^^^^

Set the path to the ‘tessdata’ folder, which contains language files and
config files if Tesseract can not be automatically detected. You can
define the path to use by setting ``fs.ocr.data_path`` property in your
``~/.fscrawler/test/_settings.yml`` file:

.. code:: yaml

   name: "test"
   fs:
     url: "/path/to/data/dir"
     ocr:
       path: "/path/to/tesseract/executable"
       data_path: "/path/to/tesseract/tessdata"

OCR Output Type
^^^^^^^^^^^^^^^

.. versionadded:: 2.5

Set the output type from ocr process. ``fs.ocr.output_type`` property can be defined to
``txt`` or ``hocr`` in your ``~/.fscrawler/test/_settings.yml`` file:

.. code:: yaml

   name: "test"
   fs:
     url: "/path/to/data/dir"
     ocr:
       output_type: "hocr"

.. note:: When omitted, ``txt`` value is used.

Using docker
------------

To use FSCrawler with `docker <https://www.docker.com/>`__, check
`docker-fscrawler <https://github.com/shadiakiki1986/docker-fscrawler>`__
recipe.

