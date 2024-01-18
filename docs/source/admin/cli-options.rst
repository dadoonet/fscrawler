.. _cli-options:

CLI options
===========

-  ``--help`` displays help
-  ``--silent`` runs in silent mode. No output is generated on the console.
-  ``--config_dir`` defines directory where jobs are stored instead of
   default ``~/.fscrawler``.
-  ``--api_key`` defines the Elasticsearch Api Key to use. Do not use with ``--username`` or ``--access_token``. Read :ref:`credentials`.
-  ``--access_token`` defines the Elasticsearch Access Token to use. Do not use with ``--username`` or ``--api_key``. Read :ref:`credentials`.
-  ``--username`` defines the username to use (Deprecated). Do not use with ``--api_key`` or ``--access_token``. Read :ref:`credentials`.
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
