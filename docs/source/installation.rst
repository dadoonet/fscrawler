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

See :ref:`layout` to know more about the content of the distribution.

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

   docker run -it --rm \
        -v ~/.fscrawler:/root/.fscrawler \
        -v ~/tmp:/tmp/es:ro \
        dadoonet/fscrawler job_name

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

If you need to add a 3rd party library (jar) or your Tika custom jar, you can put it in a ``external`` directory and
mount it as well:

.. code:: sh

   docker run -it --rm \
        -v ~/.fscrawler:/root/.fscrawler \
        -v ~/tmp:/tmp/es:ro \
        -v "$PWD/external:/usr/share/fscrawler/external" \
        dadoonet/fscrawler job_name

If you want to use the :ref:`rest-service`, don't forget to also expose the port:

.. code:: sh

   docker run -it --rm \
        -v ~/.fscrawler:/root/.fscrawler \
        -v ~/tmp:/tmp/es:ro \
        -p 8080:8080 \
        dadoonet/fscrawler job_name

If you want to change the log level for FSCrawler, you can run:

.. code:: sh

   docker run -it --rm \
        -v ~/.fscrawler:/root/.fscrawler \
        -v ~/tmp:/tmp/es:ro \
        -v ~/logs:/root/logs \
        -e FS_JAVA_OPTS="-DLOG_LEVEL=debug -DDOC_LEVEL=debug" \
        dadoonet/fscrawler job_name

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
  ├── config
  │   └── job_name
  │       └── _settings.yaml
  ├── data
  │   └── <your files>
  ├── external
  │   └── <3rd party jars if needed>
  ├── logs
  │   └── <fscrawler logs>
  └── docker-compose.yml

With Elasticsearch
~~~~~~~~~~~~~~~~~~

Here is a typical ``_settings.yaml``, you can use to connect FSCrawler with Elasticsearch when running
with docker compose:

.. code:: yaml

    ---
    name: "idx"
    fs:
      indexed_chars: 100%
      lang_detect: true
      continue_on_error: true
      ocr:
        language: "eng"
        enabled: true
        pdf_strategy: "ocr_and_text"
    elasticsearch:
      nodes:
        - url: "https://elasticsearch:9200"
      username: "elastic"
      password: "changeme"
      ssl_verification: false
    rest :
      url: "http://fscrawler:8080"

.. note::

    The configuration shown above is also meant to start the REST interface. It also activates the full indexation of
    documents, lang detection and ocr using english. You can adapt this example for your needs.

Prepare a ``.env`` file with the following content:

.. code:: sh

    # Chenge the FSCRAWLER_VERSION if needed
    FSCRAWLER_VERSION=2.10-SNAPSHOT
    FSCRAWLER_PORT=8080
    # Optionally, you can change the log level settings
    FS_JAVA_OPTS="-DLOG_LEVEL=debug -DDOC_LEVEL=debug"

    # Chenge the STACK_VERSION if needed
    STACK_VERSION=8.17.3
    ELASTIC_PASSWORD=changeme
    KIBANA_PASSWORD=changeme
    CLUSTER_NAME=docker-cluster
    LICENSE=trial
    ES_PORT=9200
    KIBANA_PORT=5601
    MEM_LIMIT=4294967296
    COMPOSE_PROJECT_NAME=fscrawler


And, prepare the following ``docker-compose.yml``. You will find this example in the
``contrib/docker-compose-example-elasticsearch`` project directory.

.. code:: yaml

    ---
    version: "2.2"

    services:
      setup:
        image: docker.elastic.co/elasticsearch/elasticsearch:${STACK_VERSION}
        volumes:
          - certs:/usr/share/elasticsearch/config/certs
        user: "0"
        command: >
          bash -c '
            if [ x${ELASTIC_PASSWORD} == x ]; then
              echo "Set the ELASTIC_PASSWORD environment variable in the .env file";
              exit 1;
            elif [ x${KIBANA_PASSWORD} == x ]; then
              echo "Set the KIBANA_PASSWORD environment variable in the .env file";
              exit 1;
            fi;
            if [ ! -f certs/ca.zip ]; then
              echo "Creating CA";
              bin/elasticsearch-certutil ca --silent --pem -out config/certs/ca.zip;
              unzip config/certs/ca.zip -d config/certs;
            fi;
            if [ ! -f certs/certs.zip ]; then
              echo "Creating certs";
              echo -ne \
              "instances:\n"\
              "  - name: elasticsearch\n"\
              "    dns:\n"\
              "      - elasticsearch\n"\
              "      - localhost\n"\
              "    ip:\n"\
              "      - 127.0.0.1\n"\
              > config/certs/instances.yml;
              bin/elasticsearch-certutil cert --silent --pem -out config/certs/certs.zip --in config/certs/instances.yml --ca-cert config/certs/ca/ca.crt --ca-key config/certs/ca/ca.key;
              unzip config/certs/certs.zip -d config/certs;
            fi;
            echo "Setting file permissions"
            chown -R root:root config/certs;
            find . -type d -exec chmod 750 \{\} \;;
            find . -type f -exec chmod 640 \{\} \;;
            echo "Waiting for Elasticsearch availability";
            until curl -s --cacert config/certs/ca/ca.crt https://elasticsearch:9200 | grep -q "missing authentication credentials"; do sleep 30; done;
            echo "Setting kibana_system password";
            until curl -s -X POST --cacert config/certs/ca/ca.crt -u elastic:${ELASTIC_PASSWORD} -H "Content-Type: application/json" https://elasticsearch:9200/_security/user/kibana_system/_password -d "{\"password\":\"${KIBANA_PASSWORD}\"}" | grep -q "^{}"; do sleep 10; done;
            echo "All done!";
          '
        healthcheck:
          test: ["CMD-SHELL", "[ -f config/certs/elasticsearch/elasticsearch.crt ]"]
          interval: 1s
          timeout: 5s
          retries: 120

      elasticsearch:
        depends_on:
          setup:
            condition: service_healthy
        image: docker.elastic.co/elasticsearch/elasticsearch:${STACK_VERSION}
        volumes:
          - certs:/usr/share/elasticsearch/config/certs
          - esdata:/usr/share/elasticsearch/data
        ports:
          - ${ES_PORT}:9200
        environment:
          - node.name=elasticsearch
          - cluster.name=${CLUSTER_NAME}
          - cluster.initial_master_nodes=elasticsearch
          - ELASTIC_PASSWORD=${ELASTIC_PASSWORD}
          - bootstrap.memory_lock=true
          - xpack.security.enabled=true
          - xpack.security.http.ssl.enabled=true
          - xpack.security.http.ssl.key=certs/elasticsearch/elasticsearch.key
          - xpack.security.http.ssl.certificate=certs/elasticsearch/elasticsearch.crt
          - xpack.security.http.ssl.certificate_authorities=certs/ca/ca.crt
          - xpack.security.http.ssl.verification_mode=certificate
          - xpack.security.transport.ssl.enabled=true
          - xpack.security.transport.ssl.key=certs/elasticsearch/elasticsearch.key
          - xpack.security.transport.ssl.certificate=certs/elasticsearch/elasticsearch.crt
          - xpack.security.transport.ssl.certificate_authorities=certs/ca/ca.crt
          - xpack.security.transport.ssl.verification_mode=certificate
          - xpack.license.self_generated.type=${LICENSE}
        mem_limit: ${MEM_LIMIT}
        ulimits:
          memlock:
            soft: -1
            hard: -1
        healthcheck:
          test:
            [
              "CMD-SHELL",
              "curl -s --cacert config/certs/ca/ca.crt https://localhost:9200 | grep -q 'missing authentication credentials'",
            ]
          interval: 10s
          timeout: 10s
          retries: 120

      kibana:
        depends_on:
          elasticsearch:
            condition: service_healthy
        image: docker.elastic.co/kibana/kibana:${STACK_VERSION}
        volumes:
          - certs:/usr/share/kibana/config/certs
          - kibanadata:/usr/share/kibana/data
        ports:
          - ${KIBANA_PORT}:5601
        environment:
          - SERVERNAME=kibana
          - ELASTICSEARCH_HOSTS=https://elasticsearch:9200
          - ELASTICSEARCH_USERNAME=kibana_system
          - ELASTICSEARCH_PASSWORD=${KIBANA_PASSWORD}
          - ELASTICSEARCH_SSL_CERTIFICATEAUTHORITIES=config/certs/ca/ca.crt
          - ENTERPRISESEARCH_HOST=http://enterprisesearch:${ENTERPRISE_SEARCH_PORT}
        mem_limit: ${MEM_LIMIT}
        healthcheck:
          test:
            [
              "CMD-SHELL",
              "curl -s -I http://localhost:5601 | grep -q 'HTTP/1.1 302 Found'",
            ]
          interval: 10s
          timeout: 10s
          retries: 120

      # FSCrawler
      fscrawler:
        image: dadoonet/fscrawler:${FSCRAWLER_VERSION}
        container_name: fscrawler
        restart: always
        environment:
          - FS_JAVA_OPTS=${FS_JAVA_OPTS}
        volumes:
          - ../../test-documents/src/main/resources/documents/:/tmp/es:ro
          - ${PWD}/config:/root/.fscrawler
          - ${PWD}/logs:/usr/share/fscrawler/logs
          - ${PWD}/external:/usr/share/fscrawler/external
        depends_on:
          elasticsearch:
            condition: service_healthy
        ports:
          - ${FSCRAWLER_PORT}:8080
        command: idx --restart --rest

    volumes:
      certs:
        driver: local
      esdata:
        driver: local
      kibanadata:
        driver: local

.. note::

    The configuration shown above is also meant to start Kibana. You can skip that part if you don't need it.

Then, you can run the full stack, including FSCrawler.

.. code:: sh

    docker-compose up -d

Then if you need to read the logs from FSCrawler, you can run:

.. code:: sh

    docker-compose logs -f fscrawler

Or just go in the ``logs`` directory to read the logs:

.. code:: sh

    tail -f logs/documents.log

Running as a Service on Windows
-------------------------------

Create a ``fscrawlerRunner.bat`` as:

.. code:: sh

   set JAVA_HOME=c:\Program Files\Java\jdk15.0.1
   set FS_JAVA_OPTS=-Xmx2g -Xms2g
   /Elastic/fscrawler/bin/fscrawler.bat --config_dir /Elastic/fscrawler data >> /Elastic/logs/fscrawler.log 2>&1

Then use ``fscrawlerRunner.bat`` to create your windows service.
