.. _layout:

Directory layout
================

The directory layout of the project is as follows:

.. code-block:: none

    .
    ├── NOTICE
    ├── LICENSE
    ├── README.md
    ├── bin
    │   ├── fscrawler
    │   └── fscrawler.bat
    ├── config
    │   ├── log4j2.xml
    │   └── log4j2-file.xml
    ├── external
    ├── lib
    └── logs
        ├── documents.log
        └── fscrawler.log

The ``bin`` directory contains the scripts to run FSCrawler.

The ``lib`` directory contains the FSCrawler jar file and all the dependencies.

.. versionadded:: 2.10

The ``config`` directory contains the configuration files. See :ref:`logger`.

The ``external`` directory is for optional JARs (e.g. for JPEG2000 support in PDFs). See :ref:`installation` for
details and how to add libraries such as ``jai-imageio-jpeg2000``.

As this directory is empty by default, you can also mount it when using Docker images:

.. code:: sh

   docker run -it --rm \
        -v ~/.fscrawler:/root/.fscrawler \
        -v ~/tmp:/tmp/es:ro \
        -v "$PWD/external:/usr/share/fscrawler/external" \
        dadoonet/fscrawler

See also :ref:`installation`, :ref:`docker` and :ref:`docker-compose`.

The ``logs`` directory contains the log files. See :ref:`logger`.
