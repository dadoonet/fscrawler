.. _installation:

Download FSCrawler
------------------

.. ifconfig:: release.endswith('-SNAPSHOT')

    Depending on your Elasticsearch cluster version, you can download
    FSCrawler |version| using the following links from |Download_URL|_.

    The filename ends with ``.zip``.

    .. warning::

        This is a **SNAPSHOT** version.
        You can also download a **stable** version from |Maven_Central|_.

.. ifconfig:: release == version

    Depending on your Elasticsearch cluster version, you can download
    FSCrawler |version| from |Download_URL|_.

    .. tip::

        This is a **stable** version.
        You can choose another version than |version| from |Maven_Central|_.

        You can also download a **SNAPSHOT** version from |Sonatype|_.

The distribution contains:

::

   $ tree
   .
   ├── LICENSE
   ├── NOTICE
   ├── README.md
   ├── bin
   │   ├── fscrawler
   │   └── fscrawler.bat
   ├── config
   │   └── log4j2.xml
   └── lib
       ├── ... All needed jars

.. _docker:

Using docker
------------

Pull the Docker image:

.. code:: sh

   docker pull dadoonet/fscrawler

.. note::

    This image is very big (1.2+gb) as it contains `Tesseract <https://tesseract-ocr.github.io/tessdoc/>`__ and
    all the `trained language data <https://tesseract-ocr.github.io/tessdoc/Data-Files.html>`__.
    If you don't want to use OCR at all, you can use a smaller image (around 530mb) by pulling instead
    ``dadoonet/fscrawler:noocr``

    .. code:: sh

       docker pull dadoonet/fscrawler:noocr


Let say your documents are located in ``~/tmp`` dir and you want to store your fscrawler jobs in ``~/.fscrawler``.
You can run FSCrawler with:

.. code:: sh

   docker run -it --rm -v ~/.fscrawler:/root/.fscrawler -v ~/tmp:/tmp/es:ro dadoonet/fscrawler fscrawler job_name

On the first run, if the job does not exist yet in ``~/.fscrawler``, FSCrawler will ask you if you want to create it:

::

    10:16:53,880 INFO  [f.p.e.c.f.c.BootstrapChecks] Memory [Free/Total=Percent]: HEAP [67.3mb/876.5mb=7.69%], RAM [2.1gb/3.8gb=55.43%], Swap [1023.9mb/1023.9mb=100.0%].
    10:16:53,899 WARN  [f.p.e.c.f.c.FsCrawlerCli] job [job_name] does not exist
    10:16:53,900 INFO  [f.p.e.c.f.c.FsCrawlerCli] Do you want to create it (Y/N)?
    y
    10:16:56,745 INFO  [f.p.e.c.f.c.FsCrawlerCli] Settings have been created in [/root/.fscrawler/job_name/_settings.yaml]. Please review and edit before relaunch

.. note::

    The configuration file is actually stored on your machine in ``~/.fscrawler/job_name/_settings.yaml``.
    Remember to change the URL of your elasticsearch instance as the container won't be able to see it
    running under the default ``127.0.0.1``. You will need to use the actual IP address of the host.


Using docker compose
--------------------

In this section, the following directory layout is assumed:

.. code-block:: none

  .
  ├── config
  │   └── job_name
  │       └── _settings.yaml
  ├── data
  │   └── <your files>
  ├── logs
  │   └── <fscrawler logs>
  └── docker-compose.yml

For example, to connect to a docker container named ``elasticsearch``, modify your ``_settings.yaml``.

.. code:: yaml

  name: "job_name"
  elasticsearch:
    nodes:
    - url: "http://elasticsearch:9200"

And, prepare the following ``docker-compose.yml``.

.. code:: yaml

    version: '3'
    services:
      # Elasticsearch Cluster
      elasticsearch:
        image: docker.elastic.co/elasticsearch/elasticsearch:$ELASTIC_VERSION
        container_name: elasticsearch
        environment:
          - bootstrap.memory_lock=true
          - discovery.type=single-node
        restart: always
        ulimits:
          memlock:
            soft: -1
            hard: -1
        volumes:
          - data:/usr/share/elasticsearch/data
        ports:
          - 9200:9200
        networks:
          - fscrawler_net

      # FSCrawler
      fscrawler:
        image: dadoonet/fscrawler:$FSCRAWLER_VERSION
        container_name: fscrawler
        restart: always
        volumes:
          - ${PWD}/config:/root/.fscrawler
          - ${PWD}/logs:/usr/share/fscrawler/logs
          - ../../test-documents/src/main/resources/documents/:/tmp/es:ro
        depends_on:
          - elasticsearch
        command: fscrawler --rest idx
        networks:
          - fscrawler_net

    volumes:
      data:
        driver: local

    networks:
      fscrawler_net:
        driver: bridge

Then, you can run Elasticsearch.

.. code:: sh

    docker-compose up -d elasticsearch
    docker-compose logs -f elasticsearch

Wait for elasticsearch to be started:

::



After starting Elasticsearch, you can run FSCrawler.

.. code:: sh

  docker-compose up fscrawler



Running as a Service on Windows
-------------------------------

Create a ``fscrawlerRunner.bat`` as:

.. code:: sh

   set JAVA_HOME=c:\Program Files\Java\jdk15.0.1
   set FS_JAVA_OPTS=-Xmx2g -Xms2g
   /Elastic/fscrawler/bin/fscrawler.bat --config_dir /Elastic/fscrawler data >> /Elastic/logs/fscrawler.log 2>&1

Then use ``fscrawlerRunner.bat`` to create your windows service.
