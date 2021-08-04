Building the project
--------------------

This project is built with `Maven <https://maven.apache.org/>`_. It needs Java >= 1.11.
Source code is available on `GitHub <https://github.com/dadoonet/fscrawler/>`_.
Thanks to `JetBrains <https://www.jetbrains.com/?from=FSCrawler>`_ for the IntelliJ IDEA License!

.. image:: /_static/jetbrains.png
    :scale: 10
    :alt: Jet Brains
    :target: https://www.jetbrains.com/?from=FSCrawler

.. contents:: :backlinks: entry

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

When running from the command line with ``mvn`` integration tests are ran against all supported versions.
This is done by running a Docker instance of elasticsearch using the expected version.

A HTTP server is also started on port 8080 during the integration tests, alternatively the assigned port can be set with `-Dtests.rest.port=8090` argument.

Run tests from your IDE
"""""""""""""""""""""""

To run integration tests from your IDE, you need to start tests in ``fscrawler-it-common`` module.
But you need first to specify the Maven profile to use and rebuild the project.

* ``es-7x`` for Elasticsearch 7.x
* ``es-6x`` for Elasticsearch 6.x

Run a specific test from your Terminal
""""""""""""""""""""""""""""""""""""""

To run a specific integration test, just run::

    mvn verify -am -Dtests.class=fr.pilato.elasticsearch.crawler.fs.test.integration.CLASS_NAME -Dtests.method="METHOD_NAME"

Run tests with an external cluster
""""""""""""""""""""""""""""""""""

Launching the docker containers might take some time so if to want to run the test suite against an already running
cluster, you need to provide a ``tests.cluster.url`` value. This will skip launching the docker instances.

To run the test suite against an elasticsearch instance running locally, just run::

    mvn verify -pl fr.pilato.elasticsearch.crawler:fscrawler-it-v7 -Dtests.cluster.url=http://localhost:9200

.. tip::

    If you want to run against a version 6, run::

        mvn verify -pl fr.pilato.elasticsearch.crawler:fscrawler-it-v6 -Dtests.cluster.url=http://localhost:9200

.. hint::

    If you are using a secured instance, use ``tests.cluster.user``, ``tests.cluster.pass`` and ``tests.cluster.url``::

        mvn verify -pl fr.pilato.elasticsearch.crawler:fscrawler-it-v7 \
            -Dtests.cluster.user=elastic \
            -Dtests.cluster.pass=changeme \
            -Dtests.cluster.url=http://127.0.0.1:9200 \

.. hint::

    To run tests against another instance (ie. running on
    `Elasticsearch service by Elastic <https://www.elastic.co/cloud/elasticsearch-service>`_,
    you can also use ``tests.cluster.url`` to set where elasticsearch is running::

        mvn verify -pl fr.pilato.elasticsearch.crawler:fscrawler-it-v7 \
            -Dtests.cluster.user=elastic \
            -Dtests.cluster.pass=changeme \
            -Dtests.cluster.url=https://XYZ.es.io:9243

    Or even easier, you can use the ``Cloud ID`` available on you Cloud Console::

        mvn verify -pl fr.pilato.elasticsearch.crawler:fscrawler-it-v7 \
            -Dtests.cluster.user=elastic \
            -Dtests.cluster.pass=changeme \
            -Dtests.cluster.cloud_id=fscrawler:ZXVyb3BlLXdlc3QxLmdjcC5jbG91ZC5lcy5pbyQxZDFlYTk5Njg4Nzc0NWE2YTJiN2NiNzkzMTUzNDhhMyQyOTk1MDI3MzZmZGQ0OTI5OTE5M2UzNjdlOTk3ZmU3Nw==

Using security feature
""""""""""""""""""""""

Integration tests are run by default against a secured Elasticsearch cluster.

.. versionadded:: 2.7

Secured tests are using by default ``changeme`` as the password.
You can change this by using ``tests.cluster.pass`` option::

    mvn verify -Dtests.cluster.pass=mystrongpassword


Testing Workplace Search connector
""""""""""""""""""""""""""""""""""

.. versionadded:: 2.7

The Workplace Search integration is automatically tested when running the integration tests.
The maven process will start both elasticsearch and enterprise search nodes. Note that this
could take several minutes before to have it up and running.

To test the Workplace Search connector against an existing cluster, you can provide the ``tests.cluster.url`` setting.
This will skip launching the containers and all the test suite will run against this external cluster::

    mvn verify -pl fr.pilato.elasticsearch.crawler:fscrawler-it-v7 \
        -Dtests.cluster.url=http://localhost:9200 \
        -Dtests.cluster.user=elastic \
        -Dtests.cluster.pass=changeme \
        -Dtests.workplace.url=http://localhost:3002

.. note::

    By default, ``tests.workplace.user`` and ``tests.workplace.pass`` are using the same values as for
    ``tests.cluster.user`` and ``tests.cluster.pass``. But if you want to use another username and password
    to connect to workplace search, you can override the settings::

        mvn verify -pl fr.pilato.elasticsearch.crawler:fscrawler-it-v7 \
            -Dtests.cluster.url=http://localhost:9200 \
            -Dtests.cluster.user=elastic \
            -Dtests.cluster.pass=changeme \
            -Dtests.workplace.url=http://localhost:3002
            -Dtests.workplace.user=enterprise_search \
            -Dtests.workplace.pass=changeme

To run Workplace Search tests against the
`Enterprise Search service by Elastic <https://www.elastic.co/workplace-search>`_,
you can also use something like::

    mvn verify -pl fr.pilato.elasticsearch.crawler:fscrawler-it-v7 \
        -Dtests.cluster.url=https://ALIAS.es.eu-west-3.aws.elastic-cloud.com:9243 \
        -Dtests.cluster.user=elastic \
        -Dtests.cluster.pass=changeme \
        -Dtests.workplace.url=https://ALIAS.ent.eu-west-3.aws.elastic-cloud.com \
        -Dtests.workplace.user=enterprise_search \
        -Dtests.workplace.pass=changeme

Changing the REST port
""""""""""""""""""""""

By default, FS crawler will run the integration tests using port ``8080`` for the REST service.
You can change this by using ``tests.rest.port`` option::

    mvn verify -Dtests.rest.port=8280

Randomized testing
""""""""""""""""""

FS Crawler uses the `randomized testing framework <https://github.com/randomizedtesting/randomizedtesting>`_.
In case of failure, it will print a line like::

    REPRODUCE WITH:
    mvn test -Dtests.seed=AC6992149EB4B547 -Dtests.class=fr.pilato.elasticsearch.crawler.fs.test.unit.tika.TikaDocParserTest -Dtests.method="testExtractFromRtf" -Dtests.locale=ga-IE -Dtests.timezone=Canada/Saskatchewan

You can just run the test again using the same seed to make sure you always run the test in the same context as before.

Tests options
"""""""""""""

Some options are available from the command line when running the tests:

* ``tests.leaveTemporary`` leaves temporary files after tests. ``false`` by default.
* ``tests.parallelism`` how many JVM to launch in parallel for tests. ``auto`` by default which means that it depends on the number of processors you have. It can be set to ``max`` if you want to use all the available processors, or a given value like ``1`` to use that exact number of JVMs.
* ``tests.output`` what should be displayed to the console while running tests. By default it is set to ``onError`` but can be set to ``always``
* ``tests.verbose`` ``false`` by default
* ``tests.seed`` if you need to reproduce a specific failure using the exact same random seed
* ``tests.timeoutSuite`` how long a single can run. It's set by default to ``600000`` which means 5 minutes.
* ``tests.locale`` by default it's set to ``random`` but you can force the locale to use.
* ``tests.timezone`` by default it's set to ``random`` but you can force the timezone to use, like ``CEST`` or ``-0200``.

For example::

  mvn install -rf :fscrawler-it \
    -Dtests.output=always \
    -Dtests.locale=fr-FR \
    -Dtests.timezone=CEST \
    -Dtests.verbose \
    -Dtests.leaveTemporary \
    -Dtests.seed=E776CE45185A6E7A

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

Docker build
^^^^^^^^^^^^

The docker images build is ran when calling the maven ``package`` phase. If you want to skip the build of the images,
you can manually use the ``docker.skip`` option::

        mvn package -Ddocker.skip

DockerHub publication
^^^^^^^^^^^^^^^^^^^^^

To publish the latest build to `DockerHub <https://hub.docker.com/r/dadoonet/fscrawler/>`_ you can manually
call ``docker:push`` maven task and provide credentials ``docker.push.username`` and ``docker.push.password``::

        mvn -f distribution/pom.xml docker:push \
            -Ddocker.push.username=yourdockerhubaccount \
            -Ddocker.push.password=yourverysecuredpassword

Otherwise, if you call the maven ``deploy`` phase, it will be done automatically.
Note that it will still require that you provide the credentials ``docker.push.username`` and ``docker.push.password``::

        mvn deploy \
            -Ddocker.push.username=yourdockerhubaccount \
            -Ddocker.push.password=yourverysecuredpassword

You can also provide the settings as environment variables:

*  ``env.DOCKER_USERNAME`` or ``DOCKER_USERNAME``
*  ``env.DOCKER_PASSWORD`` or ``DOCKER_PASSWORD``
