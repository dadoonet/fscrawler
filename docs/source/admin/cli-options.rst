.. _cli-options:

CLI options
===========

-  ``--config_dir`` defines directory where jobs are stored instead of default ``~/.fscrawler``.
-  ``--help`` displays help
-  ``--list`` lists all jobs. See `List`_.
-  ``--loop x`` defines the number of runs we want before exiting. See `Loop`_.
-  ``--migrate`` migrates a v1 configuration to v2 pipeline format. See `Migrate`_.
-  ``--migrate-output`` specifies output file or directory for migrated configuration. See `Migrate`_.
-  ``--migrate-keep-old-files`` keeps old configuration files after migration. See `Migrate`_.
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

   bin/fscrawler my_job --migrate

The migration is interactive by default:

1. **Preview**: Shows the new configuration files that will be created
2. **Confirmation**: Asks for user confirmation before proceeding
3. **Execution**: Creates new files and removes old ones

Example output:

.. code-block:: none

   === Migration Preview for job [my_job] ===

   Files to be CREATED in [~/.fscrawler/my_job/_settings]:

   --- 00-common.yaml ---
   name: "my_job"
   version: 2

   --- 10-input-local.yaml ---
   inputs[0].type: "local"
   inputs[0].id: "default"
   ...

   Files to be DELETED:
     - _settings.yaml

   =================================
   Do you want to proceed with the migration? (y/N):

Migration Options
^^^^^^^^^^^^^^^^^

``--migrate-output <path>``
   Specifies where to write the migrated configuration.
   
   - Use a filename (e.g., ``_settings_v2.yaml``) for a single file
   - Use a directory with trailing slash (e.g., ``_settings/``) for split files
   - **Default**: ``_settings/`` (split files)

``--migrate-keep-old-files``
   Keeps old configuration files after migration instead of deleting them.

``--silent``
   Skips the preview and confirmation prompts. Use for automated migrations.

Examples:

.. code:: sh

   # Interactive migration (default: creates _settings/ directory)
   bin/fscrawler my_job --migrate

   # Keep old files for backup
   bin/fscrawler my_job --migrate --migrate-keep-old-files

   # Single file output
   bin/fscrawler my_job --migrate --migrate-output _settings_v2.yaml

   # Automated migration (no prompts)
   bin/fscrawler my_job --migrate --silent

Split File Structure
^^^^^^^^^^^^^^^^^^^^

By default, migration creates split files with numeric prefixes for correct loading order.
File names are based on the component type:

.. code-block:: none

   _settings/
     00-common.yaml              # name, version
     10-input-local.yaml         # local filesystem input (or ssh, ftp, etc.)
     20-filter-tika.yaml         # Tika filter (or json, xml, none)
     30-output-elasticsearch.yaml # Elasticsearch output

The split format makes it easy to understand and modify each component separately.

Using with Docker
^^^^^^^^^^^^^^^^^

The ``--migrate`` option works with Docker. Use ``--silent`` for non-interactive mode:

.. code:: sh

   # Interactive migration (requires -it for terminal)
   docker run -it --rm \
        -v ~/.fscrawler:/root/.fscrawler \
        dadoonet/fscrawler my_job --migrate

   # Automated migration (no prompts)
   docker run --rm \
        -v ~/.fscrawler:/root/.fscrawler \
        dadoonet/fscrawler my_job --migrate --silent

   # Keep old files
   docker run --rm \
        -v ~/.fscrawler:/root/.fscrawler \
        dadoonet/fscrawler my_job --migrate --silent --migrate-keep-old-files

.. note::

   Use relative filenames (not absolute paths) to ensure the output files
   are written inside the mounted volume and accessible on the host machine.

For more details about the v2 pipeline format, see :ref:`pipeline-settings`.
