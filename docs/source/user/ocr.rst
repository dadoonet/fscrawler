.. _ocr_integration:

OCR integration
===============

.. versionadded:: 2.3

To deal with images containing text, just `install
Tesseract <https://tesseract-ocr.github.io/tessdoc/>`__.
Tesseract will be auto-detected by Tika or you can explicitly `set the
path to tesseract binary <#ocr-path>`_. Then add an image (png, jpg, …)
into your Fscrawler :ref:`root-directory`. After the next
index update, the text will be indexed and placed in "_source.content".

OCR settings
------------

Here is a list of OCR settings (under ``fs.ocr`` prefix)`:

+-------------------------+------------------+------------------------------------+
| Name                    |   Default value  | Documentation                      |
+=========================+==================+====================================+
| ``fs.ocr.enabled``      | ``true``         | `Disable/Enable OCR`_              |
+-------------------------+------------------+------------------------------------+
| ``fs.ocr.language``     | ``"eng"``        | `OCR Language`_                    |
+-------------------------+------------------+------------------------------------+
| ``fs.ocr.path``         | ``null``         | `OCR Path`_                        |
+-------------------------+------------------+------------------------------------+
| ``fs.ocr.data_path``    | ``null``         | `OCR Data Path`_                   |
+-------------------------+------------------+------------------------------------+
| ``fs.ocr.output_type``  | ``txt``          | `OCR Output Type`_                 |
+-------------------------+------------------+------------------------------------+
| ``fs.ocr.pdf_strategy`` | ``ocr_and_text`` | `OCR PDF Strategy`_                |
+-------------------------+------------------+------------------------------------+

Disable/Enable OCR
------------------

.. versionadded:: 2.7

You can completely disable using OCR by setting ``fs.ocr.enabled`` property in your
``~/.fscrawler/test/_settings.yaml`` file:

.. code:: yaml

   name: "test"
   fs:
     url: "/path/to/data/dir"
     ocr:
       enabled: false

By default, OCR is activated if tesseract can be found on your system.


OCR Language
------------

If you are using the default Docker image (see :ref:`docker`) or if you have installed any of the
`Tesseract Languages <https://tesseract-ocr.github.io/tessdoc/Data-Files.html>`__,
you can use them when parsing your documents by setting ``fs.ocr.language`` property in your
``~/.fscrawler/test/_settings.yaml`` file:

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
--------

If your Tesseract application is not available in default system PATH,
you can define the path to use by setting ``fs.ocr.path`` property in
your ``~/.fscrawler/test/_settings.yaml`` file:

.. code:: yaml

   name: "test"
   fs:
     url: "/path/to/data/dir"
     ocr:
       path: "/path/to/tesseract/bin/"

When you set it, it’s highly recommended to set the `OCR Data Path`_.

OCR Data Path
-------------

Set the path to the ‘tessdata’ folder, which contains language files and
config files if Tesseract can not be automatically detected. You can
define the path to use by setting ``fs.ocr.data_path`` property in your
``~/.fscrawler/test/_settings.yaml`` file:

.. code:: yaml

   name: "test"
   fs:
     url: "/path/to/data/dir"
     ocr:
       path: "/path/to/tesseract/bin/"
       data_path: "/path/to/tesseract/share/tessdata/"

OCR Output Type
---------------

.. versionadded:: 2.5

Set the output type from ocr process. ``fs.ocr.output_type`` property can be defined to
``txt`` or ``hocr`` in your ``~/.fscrawler/test/_settings.yaml`` file:

.. code:: yaml

   name: "test"
   fs:
     url: "/path/to/data/dir"
     ocr:
       output_type: "hocr"

.. note:: When omitted, ``txt`` value is used.


OCR PDF Strategy
----------------

By default, FSCrawler will also try to extract also images from your PDF
documents and run OCR on them. This can be a CPU intensive operation. If
you don’t mean to run OCR on PDF but only on images, you can set
``fs.ocr.pdf_strategy`` to ``"no_ocr"`` or  to ``"auto"``:

.. code:: yaml

   name: "test"
   fs:
     ocr:
       pdf_strategy: "auto"

Supported strategies are:

* ``auto``: No OCR is performed on PDF documents if there is more than 10 characters extracted. See `PDFParser OCR Options <https://cwiki.apache.org/confluence/pages/viewpage.action?pageId=109454066>`__.

* ``no_ocr``: No OCR is performed on PDF documents. OCR might be performed on images though if OCR is not disabled. See `Disable/Enable OCR`_.

* ``ocr_only``: Only OCR is performed.

* ``ocr_and_text``: OCR and text extraction is performed.

.. note:: When omitted, ``ocr_and_text`` value is used. If you have performance issues, it's worth using the ``auto`` option
instead as only documents with barely no text will go through the OCR process.
