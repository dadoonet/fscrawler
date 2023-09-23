.. _logger:

Configuring the logger
======================

In addition to the :ref:`cli-options`, FSCrawler comes with a default logger configuration which can be found in the
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
