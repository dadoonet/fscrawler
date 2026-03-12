Supported formats
-----------------

FSCrawler supports all formats |Tika_format|_ supports,
like:

-  HTML
-  Microsoft Office
-  Open Office
-  PDF
-  Images
-  MP3
-  ...

Apple Keynote (``.key``)
~~~~~~~~~~~~~~~~~~~~~~~~

Apple Keynote files are supported. To extract the **text content** from slides,
you need to enable :ref:`OCR <ocr_integration>`. Without OCR, FSCrawler only indexes the
package structure (e.g. embedded image file paths), not the actual slide text.
