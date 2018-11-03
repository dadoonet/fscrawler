Building the project
--------------------

This project is built with `Maven <https://maven.apache.org/>`_.
Source code is available on `GitHub <https://github.com/dadoonet/fscrawler/>`_.

Clone the project
^^^^^^^^^^^^^^^^^

Use git to clone the project locally::

    git clone git@github.com:dadoonet/fscrawler.git
    cd fscrawler

Build the artifact
^^^^^^^^^^^^^^^^^^

To build the project, run::

    mvn clean package

The final artifacts are available in ``distribution/esX/target`` directory where ``X`` is the
elasticsearch major version target.

.. tip::

    To build it faster (without tests), run::

        mvn clean package -DskipTests

Integration tests
^^^^^^^^^^^^^^^^^

To run integration tests, you need to specify the Maven profile to use,
which means the elasticsearch major version you want to use.

* ``es-6x`` for Elasticsearch 6.x
* ``es-5x`` for Elasticsearch 5.x

For example::

    mvn verify -Pes-6x

Run tests with an external cluster
""""""""""""""""""""""""""""""""""

To run the test suite against an elasticsearch instance running locally, just run::

    mvn verify

.. tip::

    If you don't want to rebuild everything (ie. you just touch test classes), run::

        mvn -pl fr.pilato.elasticsearch.crawler:fscrawler-it verify -Pes-6x

If elasticsearch is not running yet on ``http://localhost:9200``, FSCrawler project will run a Docker instance before
the tests start.

.. hint::

    If you are using a secured instance, use ``tests.cluster.user``, ``tests.cluster.pass`` and ``tests.cluster.scheme``::

        mvn verify -Pes-6x \
            -Dtests.cluster.user=elastic \
            -Dtests.cluster.pass=changeme \
            -Dtests.cluster.scheme=HTTPS \

.. hint::

    To run tests against another instance (ie. running on
    `Elasticsearch service by Elastic <https://www.elastic.co/cloud/elasticsearch-service>`_,
    you can also use ``tests.cluster.host`` and ``tests.cluster.port`` to set where elasticsearch
    is running::

        mvn verify -Pes-6x \
            -Dtests.cluster.user=elastic \
            -Dtests.cluster.pass=changeme \
            -Dtests.cluster.scheme=HTTPS \
            -Dtests.cluster.host=XYZ.es.io:9243 \
            -Dtests.cluster.port=9243

    Or even easier, you can use the ``Cloud ID`` available on you Cloud Console::

        mvn verify -Pes-6x \
            -Dtests.cluster.user=elastic \
            -Dtests.cluster.pass=changeme \
            -Dtests.cluster.cloud_id=fscrawler:ZXVyb3BlLXdlc3QxLmdjcC5jbG91ZC5lcy5pbyQxZDFlYTk5Njg4Nzc0NWE2YTJiN2NiNzkzMTUzNDhhMyQyOTk1MDI3MzZmZGQ0OTI5OTE5M2UzNjdlOTk3ZmU3Nw==

Tests options
"""""""""""""

Some options are available from the command line when running the tests:

* ``tests.leaveTemporary`` leaves temporary files after tests. ``false`` by default.
* ``tests.parallelism`` how many JVM to launch in parallel for tests. Set to ``auto`` by default
    which means that it depends on the number of processors you have.
* ``tests.output`` what should be displayed to the console while running tests. By default it is set to
    ``onError`` but can be set to ``always``
* ``tests.verbose`` ``false`` by default
* ``tests.seed`` if you need to reproduce a specific failure using the exact same random seed
* ``tests.timeoutSuite`` how long a single can run. It's set by default to ``600000`` which means 5 minutes.
* ``tests.locale`` by default it's set to ``random`` but you can force the locale to use.
* ``tests.timezone`` by default it's set to ``random`` but you can force the timezone to use.

For example::

  mvn install -rf :fscrawler-it -Pes-6x -Dtests.output=always

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

If you want to skip the check, you can run with ``-Dossindex.fail=false``::

        mvn clean install -Dossindex.fail=false

