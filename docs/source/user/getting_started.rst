Getting Started
---------------

You need to have at least **Java 11** and have properly configured
``JAVA_HOME`` to point to your Java installation directory. For example
on MacOS if you are using sdkman you can define in your ``~/.bash_profile`` file:

.. code:: sh

   export JAVA_HOME="~/.sdkman/candidates/java/current"


Start FSCrawler
^^^^^^^^^^^^^^^

Start FSCrawler with:

.. code:: sh

   bin/fscrawler job_name

FSCrawler will read a local file (default to
``~/.fscrawler/{job_name}/_settings.yaml``). If the file does not exist,
FSCrawler will propose to create your first job.

.. code:: sh

   $ bin/fscrawler job_name
   18:28:58,174 WARN  [f.p.e.c.f.FsCrawler] job [job_name] does not exist
   18:28:58,177 INFO  [f.p.e.c.f.FsCrawler] Do you want to create it (Y/N)?
   y
   18:29:05,711 INFO  [f.p.e.c.f.FsCrawler] Settings have been created in [~/.fscrawler/job_name/_settings.yaml]. Please review and edit before relaunch

Create a directory named ``/tmp/es`` or ``c:\tmp\es``, add some files
you want to index in it and start again:

.. code:: sh

   $ bin/fscrawler --config_dir ./test job_name
   18:30:34,330 INFO  [f.p.e.c.f.FsCrawlerImpl] Starting FS crawler
   18:30:34,332 INFO  [f.p.e.c.f.FsCrawlerImpl] FS crawler started in watch mode. It will run unless you stop it with CTRL+C.
   18:30:34,682 INFO  [f.p.e.c.f.FsCrawlerImpl] FS crawler started for [job_name] for [/tmp/es] every [15m]

If you did not create the directory, FSCrawler will complain until you
fix it:

::

   18:30:34,683 WARN  [f.p.e.c.f.FsCrawlerImpl] Error while indexing content from /tmp/es: /tmp/es doesn't exists.

You can also run FSCrawler without arguments. It will give you the list
of existing jobs and will allow you to choose one:

::

   $ bin/fscrawler
   18:33:00,624 INFO  [f.p.e.c.f.FsCrawler] No job specified. Here is the list of existing jobs:
   18:33:00,629 INFO  [f.p.e.c.f.FsCrawler] [1] - job_name
   18:33:00,629 INFO  [f.p.e.c.f.FsCrawler] Choose your job [1-1]...
   1
   18:33:06,151 INFO  [f.p.e.c.f.FsCrawlerImpl] Starting FS crawler

Searching for docs
^^^^^^^^^^^^^^^^^^

This is a common use case in elasticsearch, we want to search for
something! ;-)

.. code:: json

   GET docs/doc/_search
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

