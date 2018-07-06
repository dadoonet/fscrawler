Welcome to FSCrawler's documentation!
=====================================

.. ifconfig:: release.endswith('-SNAPSHOT')

    .. warning::

        This documentation is for the version of FSCrawler currently under development.
        Were you looking for the `documentation of the latest stable version <//fscrawler.readthedocs.io/en/stable/>`_?

Welcome to the FS Crawler for |ES|_.

This crawler helps to index binary documents such as PDF, Open Office, MS Office.

**Main features**:

* Local file system (or a mounted drive) crawling and index new files, update existing ones and removes old ones.
* Remote file system over SSH crawling.
* REST interface to let you "upload" your binary documents to elasticsearch.

.. note::

    FS Crawler |release| is using |Tika_version|_ and |ESHL_version|_.

.. toctree::
   :caption: Installation Guide
   :maxdepth: 2

   installation

.. toctree::
   :caption: User Guide
   :maxdepth: 3

   user/getting_started
   user/options
   user/rest
   user/formats
   user/tips


.. toctree::
   :caption: Administration Guide
   :maxdepth: 3

   admin/status
   admin/cli-options
   admin/jvm-settings
   admin/logger
   admin/fs/index
   admin/fs/simple
   admin/fs/local-fs
   admin/fs/ssh
   admin/fs/elasticsearch
   admin/fs/rest




License
=======

.. note::

   This software is licensed under the Apache 2 license, quoted below.

   Copyright 2011-|year| David Pilato

   Licensed under the Apache License, Version 2.0 (the "License"); you may not
   use this file except in compliance with the License. You may obtain a copy of
   the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
   WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
   License for the specific language governing permissions and limitations under
   the License.

Incompatible 3rd party library licenses
=======================================

Some libraries are not Apache2 compatible. Therefore they are not
packaged with FSCrawler so you need to download and add manually them to
the ``lib`` directory:

-  for JBIG2 images, you need to add |Levigo_version|_ library
-  for TIFF images, you need to add |Tiff_version|_ library
-  for JPEG 2000 (JPX) images, you need to add |JPEG2000_version|_ library

See
`pdfbox documentation <https://pdfbox.apache.org/2.0/dependencies.html#jai-image-io>`__
for more details.

