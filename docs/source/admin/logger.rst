Configuring the logger
======================

FSCrawler comes with a default logger configuration which can be found in the
FSCrawler installation dir as ``config/log4j2.xml`` file.

You can modify it to suit your needs.

You can control where FSCrawler will store the logs by setting the ``LOG_DIR`` Java property.

.. code:: sh

   FS_JAVA_OPTS="-DLOG_DIR=path/to/logs_dir" bin/fscrawler

By default, it will log everything in the ``logs`` directory inside the installation folder.

Two log files are generated:

* One is used to log FSCrawler code execution, named ``fscrawler.log``. It's automatically
rotated every day or after 20mb of logs and gzipped. Logs are removed after 7 days.
* One is used to trace all information about documents, named ``documents.log``. It's automatically
rotated every day or after 20mb of logs and gzipped. Logs are removed after 7 days.

You can change this strategy by modifying the ``config/log4j2.xml`` file.
Please read `Log4J2 documentation <https://logging.apache.org/log4j/2.x/manual/index.html>`_ on how to configure Log4J.
