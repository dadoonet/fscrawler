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

    .. note::

        There's an issue with the download links for SNAPSHOT versions.

        .. hint::

        Due to a bug with the underlying service we rely on to provide SNAPSHOT hosting,
        we've had to temporarily remove browse access for SNAPSHOT releases. You should
        still be able to publish and consume SNAPSHOT releases as usual, but you cannot
        browse them via the UI.

        So you must now download the `maven-metadata.xml <https://central.sonatype.com/repository/maven-snapshots/fr/pilato/elasticsearch/crawler/fscrawler-distribution/2.10-SNAPSHOT/maven-metadata.xml>`__
        file. Check the ``<snapshotVersion>`` tag to find the latest SNAPSHOT version of the ``zip`` file.

        .. code:: xml

            <snapshotVersion>
              <extension>zip</extension>
              <value>2.10-20250801.161301-75</value>
              <updated>20250801161301</updated>
            </snapshotVersion>

        Note the ``value`` tag which contains the version you need to download. And use that value in the following URL:

        https://central.sonatype.com/repository/maven-snapshots/fr/pilato/elasticsearch/crawler/fscrawler-distribution/2.10-SNAPSHOT/fscrawler-distribution-2.10-20250801.161301-75.zip

.. ifconfig:: release == version

    Depending on your Elasticsearch cluster version, you can download
    FSCrawler |version| from |Download_URL|_.

    .. tip::

        This is a **stable** version.
        You can choose another version than |version| from |Maven_Central|_.

        You can also download a **SNAPSHOT** version from |Sonatype|_.

See :ref:`layout` to know more about the content of the distribution.

.. _docker:

Using docker
------------

Pull the Docker image from `Docker Hub <https://hub.docker.com/r/dadoonet/fscrawler>`__:

.. code:: sh

   docker pull dadoonet/fscrawler

.. note::

    This image is very big (500+mb) as it contains `Tesseract <https://tesseract-ocr.github.io/tessdoc/>`__ and
    all the `trained language data <https://tesseract-ocr.github.io/tessdoc/Data-Files.html>`__.
    If you don't want to use OCR at all, you can use a smaller image (around 230mb) by pulling instead
    ``dadoonet/fscrawler:noocr``

    .. code:: sh

       docker pull dadoonet/fscrawler:noocr


Let say your documents are located in ``~/tmp`` dir and you want to store your fscrawler jobs in ``~/.fscrawler``.
You can run FSCrawler with:

.. code:: sh

   docker run -it --rm \
        -v ~/.fscrawler:/root/.fscrawler \
        -v ~/tmp:/tmp/es:ro \
        dadoonet/fscrawler

.. note::

    The configuration file is expected to be stored on your machine in ``~/.fscrawler/fscrawler/_settings.yaml``.
    Remember to change the URL of your elasticsearch instance as the container won't be able to see it
    running under the default ``127.0.0.1``. You will need to use the actual IP address of the host.

    Or use the ``FSCRAWLER_ELASTICSEARCH_URLS`` environment variable to set the elasticsearch URL.
    See :ref:`cli-options` for more information about environment variables.

If you need to add a 3rd party library (jar) or your Tika custom jar, you can put it in a ``external`` directory and
mount it as well:

.. code:: sh

   docker run -it --rm \
        -v ~/.fscrawler:/root/.fscrawler \
        -v ~/tmp:/tmp/es:ro \
        -v "$PWD/external:/usr/share/fscrawler/external" \
        dadoonet/fscrawler

If you want to use the :ref:`rest-service`, don't forget to also expose the port:

.. code:: sh

   docker run -it --rm \
        -v ~/.fscrawler:/root/.fscrawler \
        -v ~/tmp:/tmp/es:ro \
        -p 8080:8080 \
        dadoonet/fscrawler

If you want to change the log level for FSCrawler, you can run:

.. code:: sh

   docker run -it --rm \
        -v ~/.fscrawler:/root/.fscrawler \
        -v ~/tmp:/tmp/es:ro \
        -v ~/logs:/root/logs \
        -e FS_JAVA_OPTS="-DLOG_LEVEL=debug -DDOC_LEVEL=debug" \
        dadoonet/fscrawler

And you can read the logs from the ``~/logs`` directory:

.. code:: sh

   tail -f ~/logs/documents.log

You can pass all the CLI options to the docker container as well:

.. code:: sh

   docker run -it --rm \
        -v ~/.fscrawler:/root/.fscrawler \
        -v ~/tmp:/tmp/es:ro \
        dadoonet/fscrawler job_name --restart --loop 1

See :ref:`cli-options` for more information.


.. _docker-compose:

Using docker compose
--------------------

In this section, the following directory layout is assumed:

.. code-block:: none

  .
  ├── .env
  ├── docs
  │   └── <your PDF, DOC, ... files>
  └── docker-compose.yml

The ``.env`` file looks like this:

.. code-block:: sh
   :substitutions:

   # Password for the 'elastic' user (at least 6 characters)
   ES_LOCAL_PASSWORD=changeme

   # Version of Elastic products
   ES_LOCAL_VERSION=|ES_stack_version|

   # Set the ES container name
   ES_LOCAL_CONTAINER_NAME=es-fscrawler

   # Set to 'basic' or 'trial' to automatically start the 30-day trial
   ES_LOCAL_LICENSE=basic
   #ES_LOCAL_LICENSE=trial

   # Port to expose Elasticsearch HTTP API to the host
   ES_LOCAL_PORT=9200
   ES_LOCAL_DISK_SPACE_REQUIRED=1gb
   ES_LOCAL_JAVA_OPTS="-XX:UseSVE=0 -Xms128m -Xmx2g"

   # Project namespace (defaults to the current folder name if not set)
   COMPOSE_PROJECT_NAME=fscrawler

   # FSCrawler Settings
   FSCRAWLER_VERSION=|FSCrawler_version|
   FSCRAWLER_PORT=8080

   # Optionally, you can change the log level settings
   FS_JAVA_OPTS="-DLOG_LEVEL=debug -DDOC_LEVEL=debug"


And, the ``docker-compose.yml`` file looks like this:

.. code:: yaml

   ---
   services:
     elasticsearch:
       image: docker.elastic.co/elasticsearch/elasticsearch:${ES_LOCAL_VERSION}
       container_name: ${ES_LOCAL_CONTAINER_NAME}
       volumes:
         - dev-elasticsearch:/usr/share/elasticsearch/data
       ports:
         - 127.0.0.1:${ES_LOCAL_PORT}:9200
       environment:
         - discovery.type=single-node
         - ELASTIC_PASSWORD=${ES_LOCAL_PASSWORD}
         - xpack.security.enabled=true
         - xpack.security.http.ssl.enabled=false
         - xpack.license.self_generated.type=${ES_LOCAL_LICENSE}
         - xpack.ml.use_auto_machine_memory_percent=true
         - ES_JAVA_OPTS=${ES_LOCAL_JAVA_OPTS}
         - cluster.routing.allocation.disk.watermark.low=${ES_LOCAL_DISK_SPACE_REQUIRED}
         - cluster.routing.allocation.disk.watermark.high=${ES_LOCAL_DISK_SPACE_REQUIRED}
         - cluster.routing.allocation.disk.watermark.flood_stage=${ES_LOCAL_DISK_SPACE_REQUIRED}
       ulimits:
         memlock:
           soft: -1
           hard: -1
       healthcheck:
         test:
           [
             "CMD-SHELL",
             "curl --output /dev/null --silent --head --fail -u elastic:${ES_LOCAL_PASSWORD} http://elasticsearch:9200",
           ]
         interval: 10s
         timeout: 10s
         retries: 30

     # FSCrawler
     fscrawler:
       image: dadoonet/fscrawler:${FSCRAWLER_VERSION}
       container_name: fscrawler
       restart: always
       environment:
         - FS_JAVA_OPTS=${FS_JAVA_OPTS}
         - FSCRAWLER_ELASTICSEARCH_URLS=http://${ES_LOCAL_CONTAINER_NAME}:9200
         - FSCRAWLER_ELASTICSEARCH_USERNAME=elastic
         - FSCRAWLER_ELASTICSEARCH_PASSWORD=${ES_LOCAL_PASSWORD}
         - FSCRAWLER_REST_URL=http://fscrawler:${FSCRAWLER_PORT}
       volumes:
         - ${PWD}/docs:/tmp/es:ro
       depends_on:
         elasticsearch:
           condition: service_healthy
       ports:
         - ${FSCRAWLER_PORT}:8080
       command: --rest

   volumes:
     dev-elasticsearch:

Copy your pdf/doc files into the ``docs`` directory and run the full stack, including FSCrawler with:

.. code:: sh

    docker-compose up

When the job has finished indexing, you can check your documents in Elasticsearch with:

.. code:: sh

   curl -u elastic:changeme "http://localhost:9200/fscrawler/_search"

.. note::

  You will find this example in the ``contrib/docker-compose-example-elasticsearch`` project directory.

Running as a Service on Windows
-------------------------------

Create a ``fscrawlerRunner.bat`` as:

.. code:: sh

   set JAVA_HOME=c:\Program Files\Java\jdk15.0.1
   set FS_JAVA_OPTS=-Xmx2g -Xms2g
   /Elastic/fscrawler/bin/fscrawler.bat --config_dir /Elastic/fscrawler data >> /Elastic/logs/fscrawler.log 2>&1

Then use ``fscrawlerRunner.bat`` to create your windows service.
