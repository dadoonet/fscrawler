.. _cli-options:

CLI options
===========

-  ``--help`` displays help
-  ``--silent`` runs in silent mode. No output is generated on the console.
-  ``--debug`` runs in debug mode. This applies to log files only. See also :ref:`logger`.
-  ``--trace`` runs in trace mode (more verbose than debug). This applies to log files only. See also :ref:`logger`.
-  ``--config_dir`` defines directory where jobs are stored instead of
   default ``~/.fscrawler``.
-  ``--username`` defines the username to use when using an secured
   version of elasticsearch cluster. Read :ref:`credentials`.
-  ``--loop x`` defines the number of runs we want before exiting. See `Loop`_.
-  ``--restart`` restart a job from scratch. See `Restart`_.
-  ``--rest`` starts the REST service. See `Rest`_.


Loop
----

.. versionadded:: 2.2

``--loop x`` defines the number of runs we want before exiting:

-  ``X`` where X is a negative value means infinite, like ``-1`` (default)
-  ``0`` means that we don’t run any crawling job (useful when used with rest).
-  ``X`` where X is a positive value is the number of runs before it stops.

If you want to scan your hard drive only once, run with ``--loop 1``.


Restart
-------

.. versionadded:: 2.2

You can tell FSCrawler that it must restart from the beginning by using
``--restart`` option:

.. code:: sh

   bin/fscrawler job_name --restart

In that case, the ``{job_name}/_status.json`` file will be removed.

Rest
----

.. versionadded:: 2.3

If you want to run the :ref:`rest-service` without scanning
your hard drive, launch with:

.. code:: sh

   bin/fscrawler --rest --loop 0
