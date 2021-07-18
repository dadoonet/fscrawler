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

   $ bin/fscrawler job_name --loop 1
   18:47:37,487 INFO  [f.p.e.c.f.FsCrawlerImpl] Starting FS crawler
   18:47:37,854 INFO  [f.p.e.c.f.FsCrawlerImpl] FS crawler started for [job_name] for [/tmp/es] every [15m]
   ...
   18:47:37,855 INFO  [f.p.e.c.f.FsCrawlerImpl] FS crawler is stopping after 1 run
   18:47:37,959 INFO  [f.p.e.c.f.FsCrawlerImpl] FS crawler [job_name] stopped

If you have already ran FSCrawler and want to restart (which means
reindex existing documents), use the ``--restart`` option:

.. code:: sh

   $ bin/fscrawler job_name --loop 1 --restart

You will find more information about settings in the following sections:

-  :ref:`cli-options`
-  :ref:`local-fs-settings`
-  :ref:`ssh-settings`
-  :ref:`ftp-settings`
-  :ref:`elasticsearch-settings`

