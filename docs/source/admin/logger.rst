.. _logger:

Configuring the logger
======================

FSCrawler comes with a default logger configuration which can be found in the
FSCrawler installation dir as ``config/log4j2.xml`` file.

You can modify it to suit your needs. It will be automatically reloaded every 30 seconds.

There are some properties to make your life easier to change the log levels or the log dir:

.. code:: xml

   <Properties>
      <Property name="LOG_LEVEL">info</Property>
      <Property name="DOC_LEVEL">info</Property>
      <Property name="LOG_DIR">logs</Property>
   </Properties>

You can control where FSCrawler will store the logs and the log levels by setting
``LOG_DIR``, ``LOG_LEVEL`` and ``DOC_LEVEL`` Java properties.

.. code:: sh

   FS_JAVA_OPTS="-DLOG_DIR=path/to/logs_dir -DLOG_LEVEL=trace -DDOC_LEVEL=debug" bin/fscrawler

By default, it will log everything in the ``logs`` directory inside the installation folder.

Two log files are generated:

* One is used to log FSCrawler code execution, named ``fscrawler.log``. It's automatically
  rotated every day or after 20mb of logs and gzipped. Logs are removed after 7 days.
* One is used to trace all information about documents, named ``documents.log``. It's automatically
  rotated every day or after 20mb of logs and gzipped. Logs are removed after 7 days.

You can change this strategy by modifying the ``config/log4j2.xml`` file.
Please read `Log4J2 documentation <https://logging.apache.org/log4j/2.x/manual/index.html>`_ on how to configure Log4J.

.. note::

    FSCrawler detects automatically on Linux machines when it's running in background or foreground.
    When in background, the logger configuration file used is ``config/log4j2-file.xml``.

In the Docker context, you can modify the logs level by setting the ``FS_JAVA_OPTS`` environment variable:

.. code:: sh

   docker run -it --rm \
        -v ~/.fscrawler:/root/.fscrawler \
        -v ~/tmp:/tmp/es:ro \
        -v ~/logs:/root/logs \
        -e FS_JAVA_OPTS="-DLOG_LEVEL=debug -DDOC_LEVEL=debug" \
        dadoonet/fscrawler job_name

Then the logs will be readable from the ``~/logs`` directory.

Read :ref:`docker` for more information.

Same for Docker Compose, you can modify your ``docker-compose.yml`` file:

.. code:: yaml

   version: '3'
   services:
     fscrawler:
       image: dadoonet/fscrawler
       volumes:
         - ~/.fscrawler:/root/.fscrawler
         - ~/tmp:/tmp/es:ro
         - ~/logs:/root/logs
       environment:
         - FS_JAVA_OPTS=-DLOG_LEVEL=debug -DDOC_LEVEL=debug
       command: job_name

Read :ref:`docker-compose` for more information.
