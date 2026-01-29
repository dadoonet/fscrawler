Getting Started
---------------

You need to have at least **Java |java_version|** and have properly configured
``JAVA_HOME`` to point to your Java installation directory. For example
on MacOS if you are using sdkman you can define in your ``~/.bash_profile`` file:

.. code:: sh

   export JAVA_HOME="~/.sdkman/candidates/java/current"


Start FSCrawler
^^^^^^^^^^^^^^^

Start FSCrawler with:

.. code:: sh

   bin/fscrawler

FSCrawler will read a local file (default to ``~/.fscrawler/fscrawler/_settings.yaml``). If the file does not exist,
you can ask to create it using the ``--setup`` command.

.. code:: sh

   $ bin/fscrawler --setup
   17:40:33,905 INFO  [f.console] You can edit the settings in [~/.fscrawler/fscrawler/_settings.yaml]. Then, you can run again fscrawler without the --setup option.

Create a directory named ``/tmp/es`` or ``c:\tmp\es``, add some files
you want to index in it and start again:

.. code:: sh

   $ bin/fscrawler
   17:41:45,395 INFO  [f.p.e.c.f.FsCrawlerImpl] FSCrawler is now connected to Elasticsearch version [9.0.0]
   17:41:45,395 INFO  [f.p.e.c.f.FsCrawlerImpl] FSCrawler started in watch mode. It will run unless you stop it with CTRL+C.
   17:41:45,395 INFO  [f.p.e.c.f.FsParserAbstract] FS crawler started for [fscrawler] for [/tmp/es] every [15m]

If you did not create the directory, FSCrawler will complain until you fix it:

::

   17:41:45,396 INFO  [f.p.e.c.f.FsParserAbstract] Run #1: job [fscrawler]: starting...
   17:41:45,397 WARN  [f.p.e.c.f.FsParserAbstract] Error while crawling /tmp/es: /tmp/es doesn't exists.

Searching for docs
^^^^^^^^^^^^^^^^^^

This is a common use case in elasticsearch, we want to search for
something! ;-)

.. code:: json

   // GET docs/doc/_search
   {
     "query" : {
       "query_string": {
         "query": "I am searching for something !"
       }
     }
   }

See :ref:`search-examples` for more examples.

Ignoring folders
^^^^^^^^^^^^^^^^

If you would like to ignore some folders to be scanned, just add a ``.fscrawlerignore`` file in it.
The folder content and all sub folders will be ignored.

For more information, read :ref:`includes_excludes`.

