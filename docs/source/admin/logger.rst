Configuring an external logger configuration file
=================================================

If you want to define an external ``log4j2.xml`` file, you can use the
``log4j.configurationFile`` JVM parameter which you can define in
``FS_JAVA_OPTS`` variable if you wish:

.. code:: sh

   FS_JAVA_OPTS="-Dlog4j.configurationFile=path/to/log4j2.xml" bin/fscrawler

You can use `the default log4j2.xml
file <https://github.com/dadoonet/fscrawler/blob/master/cli/src/main/resources/log4j2.xml>`__
as an example to start with.

