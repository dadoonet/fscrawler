.. _cli-options:

CLI options
===========

-  ``--config_dir`` defines directory where jobs are stored instead of default ``~/.fscrawler``.
-  ``--help`` displays help
-  ``--list`` lists all jobs. See `List`_.
-  ``--loop x`` defines the number of runs we want before exiting. See `Loop`_.
-  ``--migrate`` migrates a v1 configuration to v2 pipeline format. See `Migrate`_.
-  ``--migrate-output`` specifies output file for migrated configuration. See `Migrate`_.
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

In that case, the ``~/.fscrawler/fscrawler/_status.json`` file will be removed.

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

Migrate
-------

.. versionadded:: 2.10

If you want to migrate an existing v1 configuration to the new v2 pipeline format,
use the ``--migrate`` option:

.. code:: sh

   # Display the migrated configuration on console
   bin/fscrawler my_job --migrate

   # Save the migrated configuration to a single file
   bin/fscrawler my_job --migrate --migrate-output _settings_v2.yaml

   # Save as split files (recommended for complex configurations)
   bin/fscrawler my_job --migrate --migrate-output _settings/

The migration tool will:

1. Read your existing v1 configuration (including split configurations from ``_settings/`` directory)
2. Convert it to the new v2 pipeline format
3. Display or save the result

Single File vs Split Output
^^^^^^^^^^^^^^^^^^^^^^^^^^^

When using ``--migrate-output``, you can choose between two output formats:

**Single file** (default): Use a filename like ``_settings_v2.yaml``

.. code:: sh

   bin/fscrawler my_job --migrate --migrate-output _settings_v2.yaml

**Split files** (recommended for complex configurations): Use ``_settings/`` as output

.. code:: sh

   bin/fscrawler my_job --migrate --migrate-output _settings/

This creates multiple files with numeric prefixes to ensure correct loading order:

.. code-block:: none

   _settings/
     00-common.yaml        # name, version
     10-input-default.yaml # input configuration
     20-filter-default.yaml # filter configuration
     30-output-default.yaml # output configuration

The split format is useful when you have multiple inputs, filters, or outputs,
as each component gets its own file.

Using with Docker
^^^^^^^^^^^^^^^^^

The ``--migrate`` option works with Docker. The output file is written relative
to the configuration directory (which is typically mounted):

.. code:: sh

   # Display on console
   docker run -it --rm \
        -v ~/.fscrawler:/root/.fscrawler \
        dadoonet/fscrawler my_job --migrate

   # Save to a single file (will be in ~/.fscrawler/my_job/_settings_v2.yaml)
   docker run -it --rm \
        -v ~/.fscrawler:/root/.fscrawler \
        dadoonet/fscrawler my_job --migrate --migrate-output _settings_v2.yaml

   # Save as split files (will be in ~/.fscrawler/my_job/_settings/)
   docker run -it --rm \
        -v ~/.fscrawler:/root/.fscrawler \
        dadoonet/fscrawler my_job --migrate --migrate-output _settings/

.. note::

   Use relative filenames (not absolute paths) to ensure the output files
   are written inside the mounted volume and accessible on the host machine.

After migration, you should:

1. Review the generated configuration
2. Backup your current ``_settings.yaml``
3. Replace it with the migrated version (or remove ``_settings.yaml`` if using split files)

For more details about the v2 pipeline format, see :ref:`pipeline-settings`.
