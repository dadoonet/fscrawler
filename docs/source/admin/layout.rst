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

The ``config`` directory contains the configuration files. See `Configuring the logger`_.

The ``external`` directory contains the external libraries you could add to FSCrawler. For example, if you want to
add the ``jai-imageio-jpeg2000`` library to add support for JPEG2000 images, you can download it from
`Maven Central <https://central.sonatype.com/search?q=g:com.github.jai-imageio>`_ and put the
``jai-imageio-jpeg2000-1.4.0.jar`` file in the ``external`` directory.

As this directory is empty by default, you can also mount it when using Docker images:

.. code:: sh

   docker run -it --rm \
        -v ~/.fscrawler:/root/.fscrawler \
        -v ~/tmp:/tmp/es:ro \
        -v "$PWD/external:/usr/share/fscrawler/external" \
        dadoonet/fscrawler fscrawler job_name

See also `Using docker`_ and `Using docker compose`_.

The ``lib`` directory contains the FSCrawler jar file and all the dependencies.

The ``logs`` directory contains the log files. See `Configuring the logger`_.
