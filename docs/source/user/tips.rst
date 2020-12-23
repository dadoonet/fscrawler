Tips and tricks
===============

Moving files to a “watched” directory
-------------------------------------

When moving an existing file to the directory FSCrawler is watching, you
need to explicitly ``touch`` all the files as when moved, the files are
keeping their original date intact:

.. code:: sh

   # single file
   touch file_you_moved

   # all files
   find  -type f  -exec touch {} +

   # all .txt files
   find  -type f  -name "*.txt" -exec touch {} +

Or you need to :ref:`restart <cli-options>` from the
beginning with the ``--restart`` option which will reindex everything.

Indexing from HDFS drive
------------------------

There is no specific support for HDFS in FSCrawler. But you can `mount
your HDFS on your
machine <https://wiki.apache.org/hadoop/MountableHDFS>`__ and run FS
crawler on this mount point. You can also read details about `HDFS NFS
Gateway <http://hadoop.apache.org/docs/stable/hadoop-project-dist/hadoop-hdfs/HdfsNfsGateway.html>`__.


Using docker-compose
--------------------
To standup a full environment you can use docker-compose from the contrib directory.
This environment will setup a node ElasticSearch cluster, a copy of Kibana
for searching and FSCrawler as containers.  No other installs are neeeded, aside form Docker and docker-compose.

Steps:

    1. Download and install `docker <https://docs.docker.com/get-docker/>`__.
    2. Download and install `docker-compose <https://github.com/docker/compose/releases/>`__.
    3. Copy the contrib directory into your home directory.
    4. Edit the docker-compose.yaml
            1. Edit the line (somewhere around 66) that points to the "files to be scanned".
               This is the path on the host machine prior to the colon. (ex: /fs/resume)
            2. In the ./config/ directory exists the name of the index name that FSCrawler will use.
               By default, it's set to 'idx'.  You can change it by renaming this directory, and changing the _settings.yaml file.
               Check the ./config/idx/_settings.yaml to update any changes you like.
               If you have multiple directories that you like to scan, I would suggest linking them under a single directory and
               changing the "follow_links" option.
    5. Check the Dockerfile-fscrawler file.  This is where the version of the package is determined.  By default I have set to
           download the 'master' branch which is currently producing a es7-2.7-SNAPSHOT version but you can lock this into a
           specific version to make it more reliable.  Update (DO NOT MOVE) the ENV variables to match what you want the build to be.
    6. Issue `docker-compose up -d` in that directory and it'll download and create the containers.  It'll also compile and build a
           custom container for fscrawler.
    7. After the containers are up and running, wait about 30 seconds for everything to start syncing.  You can now access Kibana and
           build your index (just need to do it once).  After that the search will be available via Kibana.

TODO: Build a more robust link to a specific version in the Dockerfile so it's a little more specific about what it downloads and builds.0:w

