Getting Started
---------------

You need to have at least **Java 1.8.** and have properly configured
``JAVA_HOME`` to point to your Java installation directory. For example
on MacOS you can define in your ``~/.bash_profile`` file:

.. code:: sh

   export JAVA_HOME=`/usr/libexec/java_home -v 1.8`


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

Using docker
^^^^^^^^^^^^^

You can also run FSCrawler using docker. `The docker image is here <https://hub.docker.com/r/toto1310/fscrawler>`__.

The following command let FSCrawler read its configuration files from ``/root/.fscrawler`` (i.e. ``--config_dir``) and its target files from ``/tmp/es`` (i.e. ``fs.url``).

.. code:: sh

  docker run -it --rm -v ${PWD}/config:/root/.fscrawler -v ${PWD}/data:/tmp/es:ro toto1310/fscrawler fscrawler job_name

Using with Elasticsearch installed by Docker
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

If you `installing Elasticsearch with docker <https://www.elastic.co/guide/en/elasticsearch/reference/current/docker.html>`__, you need to communicate between each container.

In the following, the following directory arrangement is assumed.

.. code-block:: none

  .
  ├── config
  │   └── job_name
  │       └── _settings.yaml
  ├── data
  │   └── <your files>
  └── docker-compose.yml

For example, to connect to a docker container named ``elasticsearch``, modify your ``_settings.yaml`` (or ``_settings.json``).

.. code:: yaml

  name: "test"
  elasticsearch:
    nodes:
    - url: "http://elasticsearch:9200"

And, prepare the following ``docker-compose.yml``.

.. code:: yaml

  version: '2.2'
  services:
    # FSCrawler 
    fscrawler:
      image: toto1310/fscrawler
      container_name: fscrawler
      volumes:
        - ${PWD}/config:/root/.fscrawler
        - ${PWD}/data:/tmp/es
      networks: 
        - esnet
      command: fscrawler job_name

    # Elasticsearch Cluster
    elasticsearch:
      image: docker.elastic.co/elasticsearch/elasticsearch:7.3.2
      container_name: elasticsearch
      environment:
        - node.name=elasticsearch
        - discovery.seed_hosts=elasticsearch2
        - cluster.initial_master_nodes=elasticsearch,elasticsearch2
        - cluster.name=docker-cluster
        - bootstrap.memory_lock=true
        - "ES_JAVA_OPTS=-Xms512m -Xmx512m"
      ulimits:
        memlock:
          soft: -1
          hard: -1
      volumes:
        - esdata01:/usr/share/elasticsearch/data
      ports:
        - 9200:9200
      networks:
        - esnet
    elasticsearch2:
      image: docker.elastic.co/elasticsearch/elasticsearch:7.3.2
      container_name: elasticsearch2
      environment:
        - node.name=elasticsearch2
        - discovery.seed_hosts=elasticsearch
        - cluster.initial_master_nodes=elasticsearch,elasticsearch2
        - cluster.name=docker-cluster
        - bootstrap.memory_lock=true
        - "ES_JAVA_OPTS=-Xms512m -Xmx512m"
      ulimits:
        memlock:
          soft: -1
          hard: -1
      volumes:
        - esdata02:/usr/share/elasticsearch/data
      networks:
        - esnet

  volumes:
    esdata01:
      driver: local
    esdata02:
      driver: local

  networks:
    esnet:

Then, you can run Elasticsearch.

.. code:: sh

  docker-compose up -d elasticsearch elasticsearch2

After starting Elasticsearch, you can run FSCrawler.

.. code:: sh

  docker-compose up fscrawler

