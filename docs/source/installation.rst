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
        image: dadoonet/fscrawler:$FSCRAWLER_VERSION
        container_name: fscrawler
        restart: always
        volumes:
          - ../../test-documents/src/main/resources/documents/:/tmp/es:ro
          - ${PWD}/config:/root/.fscrawler
          - ${PWD}/logs:/usr/share/fscrawler/logs
        depends_on:
          elasticsearch:
            condition: service_healthy
        ports:
          - ${FSCRAWLER_PORT}:8080
        command: fscrawler idx --restart --rest

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

With Enterprise Search (Workplace Search)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Here is a typical ``_settings.yaml``, you can use to connect FSCrawler with Workplace Search when running
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
    workplace_search:
      server: "http://enterprisesearch:3002"

And, prepare the following ``docker-compose.yml``. You will find this example in the
``contrib/docker-compose-example-workplace`` project directory.

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

      enterprisesearch:
        depends_on:
          elasticsearch:
            condition: service_healthy
          kibana:
            condition: service_healthy
        image: docker.elastic.co/enterprise-search/enterprise-search:${STACK_VERSION}
        volumes:
          - certs:/usr/share/enterprise-search/config/certs
          - enterprisesearchdata:/usr/share/enterprise-search/config
        ports:
          - ${ENTERPRISE_SEARCH_PORT}:3002
        environment:
          - SERVERNAME=enterprisesearch
          - secret_management.encryption_keys=[${ENCRYPTION_KEYS}]
          - allow_es_settings_modification=true
          - elasticsearch.host=https://elasticsearch:9200
          - elasticsearch.username=elastic
          - elasticsearch.password=${ELASTIC_PASSWORD}
          - elasticsearch.ssl.enabled=true
          - elasticsearch.ssl.certificate_authority=/usr/share/enterprise-search/config/certs/ca/ca.crt
          - kibana.external_url=http://kibana:5601
        mem_limit: ${MEM_LIMIT}
        healthcheck:
          test:
            [
              "CMD-SHELL",
              "curl -s -I http://localhost:3002 | grep -q 'HTTP/1.1 302 Found'",
            ]
          interval: 10s
          timeout: 10s
          retries: 120

      # Apache Httpd service (to serve local files)
      httpd:
        image: httpd:2.4
        restart: on-failure
        volumes:
          - ../../test-documents/src/main/resources/documents/:/usr/local/apache2/htdocs/:ro
        ports:
          - 80:80
        healthcheck:
          test:
            [
              "CMD-SHELL",
              "curl -s -I http://localhost | grep -q 'HTTP/1.1 302 Found'",
            ]
          interval: 10s
          timeout: 10s
          retries: 120

      # FSCrawler
      fscrawler:
        image: dadoonet/fscrawler:$FSCRAWLER_VERSION
        container_name: fscrawler
        restart: on-failure
        volumes:
          - ../../test-documents/src/main/resources/documents/:/tmp/es:ro
          - ${PWD}/config:/root/.fscrawler
          - ${PWD}/logs:/usr/share/fscrawler/logs
        depends_on:
          enterprisesearch:
            condition: service_healthy
        command: fscrawler idx --restart

    volumes:
      certs:
        driver: local
      enterprisesearchdata:
        driver: local
      esdata:
        driver: local
      kibanadata:
        driver: local

.. note::

    The configuration shown above is also meant to start a local HTTP server which will serve your local files when you
    click on a result from the Workplace Search interface.

Then, you can run the full stack, including FSCrawler and the HTTP Web Server.

.. code:: sh

    docker-compose up -d

FSCrawler will index all the documents and then exit.

When the FSCrawler container has stopped, you can just open `the search interface <http://0.0.0.0:3002/ws/search/>`__
and start to search for your local documents. You might need to be authenticated first in Kibana.
You can also open `Kibana to access the Workplace Search configuration <http://0.0.0.0:5601/app/enterprise_search/workplace_search/sources>`
and modify the source which has been created by FSCrawler.

Running as a Service on Windows
-------------------------------

Create a ``fscrawlerRunner.bat`` as:

.. code:: sh

   set JAVA_HOME=c:\Program Files\Java\jdk15.0.1
   set FS_JAVA_OPTS=-Xmx2g -Xms2g
   /Elastic/fscrawler/bin/fscrawler.bat --config_dir /Elastic/fscrawler data >> /Elastic/logs/fscrawler.log 2>&1

Then use ``fscrawlerRunner.bat`` to create your windows service.
