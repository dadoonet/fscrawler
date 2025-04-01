JVM Settings
============

If you want to provide JVM settings, like defining memory allocated to
FSCrawler, you can define a system property named ``FS_JAVA_OPTS``:

.. code:: sh

   FS_JAVA_OPTS="-Xmx521m -Xms521m" bin/fscrawler

You can also use the ``-D`` option to pass some jobs specific settings:

.. code:: sh

   FS_JAVA_OPTS="-Dfs.url=/tmp/test" bin/fscrawler job_name
