Crawler options
---------------

By default, FSCrawler will read your file from ``/tmp/es`` every 15
minutes. You can change those settings by modifying
``~/.fscrawler/{job_name}/_settings.yaml`` file where ``{job_name}`` is
the name of the job you just created.

.. code:: yaml

   name: "job_name"
   fs:
     url: "/path/to/data/dir"
     update_rate: "15m"

You can change also ``update_rate`` to watch more or less frequently for
changes.

If you just want FSCrawler to run once and exit, run it with ``--loop``
option:

.. code:: sh

   $ bin/fscrawler --loop 1
   17:41:45,395 INFO  [f.p.e.c.f.FsCrawlerImpl] FSCrawler is now connected to Elasticsearch version [9.0.0]
   17:41:45,395 INFO  [f.p.e.c.f.FsCrawlerImpl] FSCrawler started in watch mode. It will run unless you stop it with CTRL+C.
   17:41:45,395 INFO  [f.p.e.c.f.FsParserAbstract] FS crawler started for [fscrawler] for [/tmp/es] every [15m]
   17:44:57,865 INFO  [f.p.e.c.f.FsParserAbstract] Run #1: job [fscrawler]: starting...
   ...
   17:44:57,866 INFO  [f.p.e.c.f.FsParserAbstract] FS crawler is stopping after 1 run
   17:44:57,972 INFO  [f.p.e.c.f.FsCrawlerImpl] FS crawler [fscrawler] stopped

If you have already ran FSCrawler and want to restart (which means reindex existing documents),
use the ``--restart`` option:

.. code:: sh

   $ bin/fscrawler --loop 1 --restart

You will find more information about settings in the following sections:

-  :ref:`cli-options`
-  :ref:`local-fs-settings`
-  :ref:`ssh-settings`
-  :ref:`ftp-settings`
-  :ref:`elasticsearch-settings`

