Building the project
--------------------

This project is built with `Maven <https://maven.apache.org/>`_.

Build the artifact
^^^^^^^^^^^^^^^^^^

To build the project, run::

    mvn clean package

The final artifact is available in ``distribution/target`` directory.

.. tip::

    To build it faster (without tests), run::

        mvn clean package -DskipTests

Run tests with an external cluster
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

To run the test suite against an elasticsearch instance running locally, just run::

    mvn verify

.. tip::

    If you don't want to rebuild everything (ie. you just touch test classes), run::

        mvn -pl fr.pilato.elasticsearch.crawler:fscrawler-it verify

If elasticsearch is not running yet on ``http://localhost:9200``, FSCrawler project will run a Docker instance before
the tests start.

.. hint::

    If you are using a secured instance, use ``tests.cluster.user``, ``tests.cluster.pass`` and ``tests.cluster.scheme``::

        mvn verify \
            -Dtests.cluster.user=elastic \
            -Dtests.cluster.pass=changeme \
            -Dtests.cluster.scheme=HTTPS \

.. hint::

    To run tests against another instance (ie. running on
    `Elasticsearch service by Elastic <https://www.elastic.co/cloud/elasticsearch-service>`_,
    you can also use ``tests.cluster.host`` and ``tests.cluster.port`` to set where elasticsearch
    is running::

        mvn verify \
            -Dtests.cluster.user=elastic \
            -Dtests.cluster.pass=changeme \
            -Dtests.cluster.scheme=HTTPS \
            -Dtests.cluster.host=XYZ.es.io:9243 \
            -Dtests.cluster.port=9243

Check for vulnerabilities (CVE)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The project is using `OSS Sonatype service <https://ossindex.sonatype.org/>`_ to check for known
vulnerabilities. This is ran during the ``verify`` phase.

Sonatype provides this service but with a anonymous account, you might be limited
by the number of tests you can run during a given period.

If you have an existing account, you can use it to bypass this limit for anonymous users by
setting ``sonatype.username`` and ``sonatype.password``::

        mvn verify -DskipTests \
            -Dsonatype.username=youremail@domain.com \
            -Dsonatype.password=yourverysecuredpassword

