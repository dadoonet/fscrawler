.. _cli-options:

CLI options
===========

-  ``--config_dir`` defines directory where jobs are stored instead of default ``~/.fscrawler``.
-  ``--help`` displays help
-  ``--list`` lists all jobs. See `List`_.
-  ``--loop x`` defines the number of runs we want before exiting. See `Loop`_.
-  ``--restart`` restart a job from scratch. See `Restart`_.
-  ``--rest`` starts the REST service. See `Rest`_.
-  ``--setup`` creates a job configuration. See `Setup`_.
-  ``--silent`` runs in silent mode. No output is generated on the console.

Job settings can also be passed as command line arguments. For example, if you
want to set the ``url`` of a job named ``myjob`` to ``/tmp/test``, you can run:

.. code:: sh

   FS_JAVA_OPTS="-Dfs.url=/tmp/test" bin/fscrawler

A more complete example as follow, runs out of the box the indexation of a the directory
``/tmp/test`` in Elasticsearch running at ``https://elastic.mycompany.com`` with ``API_KEY`` as the API key and it
exits after the first run:

.. code:: sh

   FS_JAVA_OPTS="-Dfs.url=/tmp/test -Delasticsearch.urls=https://elastic.mycompany.com -Delasticsearch.api-key=API_KEY" bin/fscrawler --loop 1

..note::

    You can optionally specify the job name you want to use / run. If not set, the default job name is ``fscrawler``.

Loop
----

``--loop x`` defines the number of runs we want before exiting:

-  ``X`` where X is a negative value means infinite, like ``-1`` (default)
-  ``0`` means that we don’t run any crawling job (useful when used with rest).
-  ``X`` where X is a positive value is the number of runs before it stops.

If you want to scan your hard drive only once, run with ``--loop 1``.


Restart
-------

You can tell FSCrawler that it must restart from the beginning by using ``--restart`` option:

.. code:: sh

   bin/fscrawler --restart

In that case, the ``~/.fscrawler/{job_name}/_checkpoint.json`` file will be removed, 
forcing a fresh scan of the entire filesystem as if it had never been indexed before.

.. note::

   The ``--restart`` option does **not** delete the Elasticsearch indices. It only clears the 
   checkpoint file so FSCrawler will re-scan all files. If you also want to remove the indexed 
   documents, you need to delete the Elasticsearch indices manually.

Rest
----

If you want to run the :ref:`rest-service` without scanning your hard drive, launch with:

.. code:: sh

   bin/fscrawler --rest --loop 0

Setup
-----

If you want to setup a new job, you can use the ``--setup`` option. It will create
a default configuration file named ``~/.fscrawler/fscrawler/_settings.yaml``:

.. code:: sh

   bin/fscrawler --setup

.. note::

    You can also use ``--setup job_name`` to create a job named ``job_name`` instead of the default ``fscrawler``.

List
----

If you want to list all jobs, you can use the ``--list`` option. It will list all the existing jobs in ``~/.fscrawler``:

.. code:: sh

   bin/fscrawler --list
