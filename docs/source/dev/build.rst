Building the project
--------------------

This project is built with `Maven <https://maven.apache.org/>`_.
Source code is available on `GitHub <https://github.com/dadoonet/fscrawler/>`_.
Thanks to `JetBrains <https://www.jetbrains.com/?from=FSCrawler>`_ for the IntelliJ IDEA License!

.. image:: /_static/jetbrains.png
    :scale: 10
    :alt: Jet Brains
    :target: https://www.jetbrains.com/?from=FSCrawler

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


Run tests with an external cluster
""""""""""""""""""""""""""""""""""

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
            -Dtests.cluster.url=https://127.0.0.1:9200 \

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

Integration tests are run by default against a standard Elasticsearch cluster, which means
with no security feature activated.

.. versionadded:: 2.7

You can run all the integration tests against a secured cluster by using the ``security`` profile::

    mvn verify -Psecurity

Note that secured tests are using by default ``changeme`` as the password.
You can change this by using ``tests.cluster.pass`` option::

    mvn verify -Psecurity -Dtests.cluster.pass=mystrongpassword


Testing Workplace Search connector
""""""""""""""""""""""""""""""""""

To test the Workplace Search connector, some manual steps needs to be performed as you need to start
Enterprise Search and create manually the custom source as there is no API yet to do that.

.. versionadded:: 2.7

* Run the following steps::

    cd contrib/docker-compose-workplacesearch
    docker-compose up

* Wait for it to start and open http://localhost:3002/ws.
* Enter ``enterprise_search`` as the login and ``changeme`` as the password.
* Click on "Add sources" button and choose `Custom API <http://localhost:3002/ws/org/sources#/add/custom>`_.
* Name it ``fscrawler`` and click on "Create Custom API Source" button.
* Copy the "Access Token" value. We will mention it as ``ACCESS_TOKEN`` for the rest of this documentation.
* Copy the "Key" value. We will mention it as ``KEY`` for the rest of this documentation.

.. image:: /_static/wpsearch/fscrawler-custom-source.png

* You can now run in another terminal::

    mvn verify -pl fr.pilato.elasticsearch.crawler:fscrawler-it-v7 \
        -Dtests.workplace.access_token=ACCESS_TOKEN \
        -Dtests.workplace.key=KEY

* Then you should be able to see the documents in http://localhost:3002/ws/search

.. hint::

    If you want to modify the look, go to the source and choose "Display Settings".
    Adapt the settings accordingly.

    .. image:: /_static/wpsearch/fscrawler-display-settings-1.png

    In "Result Detail" tab, add the missing fields. And click on "Save".

    .. image:: /_static/wpsearch/fscrawler-display-settings-2.png



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

  mvn install -rf :fscrawler-it -Dtests.output=always

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

