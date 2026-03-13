Testing FSCrawler CLI
=====================

A convenience shell script is provided to spin up the full APM observability
stack and run FSCrawler against the built-in test documents in a single command.

.. contents:: :backlinks: entry

Prerequisites
-------------

* Java 17+
* Maven 3.3+
* Docker and either ``docker compose`` (v2) or ``docker-compose`` (v1)
* ``curl``, ``unzip``

Quick start
-----------

From the project root::

    # Full run: build → start docker stack → crawl with APM
    ./distribution/test-scripts/test-fscrawler-cli.sh

.. note::

    ``distribution/test-scripts/test-fscrawler-cli.sh`` is a **generated file** — the project
    version is injected by Maven resource filtering at build time.
    The source template is ``distribution/src/test/scripts/test-fscrawler-cli.sh``.
    After a version bump, regenerate it with::

        mvn generate-test-resources -pl distribution

The script will:

1. Build the distribution ZIP (``mvn clean package -DskipTests -Ddocker.skip``)
2. Start **Elasticsearch + Kibana + APM Server** via docker-compose
3. Unzip the distribution into ``/tmp/fscrawler-apm-test/``
4. Create a job config pointing to ``test-documents/src/main/resources/documents/``
5. Set ``OTEL_*`` environment variables and launch FSCrawler for one crawl pass (``--loop 1``)

Once the crawl finishes:

* **Elasticsearch** — ``http://localhost:9200/test-apm/_search``
* **Kibana APM** — ``http://localhost:5601`` → *Observability → APM* → service ``fscrawler``

Options
-------

.. list-table::
   :header-rows: 1
   :widths: 30 70

   * - Flag
     - Effect
   * - ``--skip-build``
     - Reuse the existing distribution ZIP (skip Maven build)
   * - ``--skip-docker``
     - Assume the docker-compose stack is already running
   * - ``--no-apm``
     - Disable OTel tracing (``OTEL_SDK_DISABLED=true``); crawl without APM
   * - ``--help``
     - Print usage

Examples::

    # Iterate quickly: keep docker stack running, only rebuild + recrawl
    ./distribution/test-scripts/test-fscrawler-cli.sh --skip-docker

    # Rebuild and crawl, but disable tracing (baseline comparison)
    ./distribution/test-scripts/test-fscrawler-cli.sh --no-apm

    # Fastest iteration: nothing to (re)build, stack already up
    ./distribution/test-scripts/test-fscrawler-cli.sh --skip-build --skip-docker

What to look for in Kibana APM
--------------------------------

After the crawl, open Kibana at http://localhost:5601.
Navigate to **Observability → APM → Services → fscrawler**.

You should see traces containing the following spans in a waterfall view:

.. code-block:: text

   fscrawler.crawl                      ← one span per run
   └─ fscrawler.directory.traverse
      └─ fscrawler.directory.process    ← one per directory
         └─ fscrawler.file.index        ← one per file
            └─ fscrawler.tika.extract   ← Tika text extraction
   fscrawler.es.bulk                    ← Elasticsearch bulk calls

Auto-instrumented spans (HTTP, ES client, etc.) also appear as children of the
root trace thanks to the ``elastic-otel-javaagent``.

Stopping the stack
------------------

When you're done, stop the docker-compose services::

    docker compose -f contrib/docker-compose-example-apm/docker-compose.yml down

Or to also remove the volumes (wipes Elasticsearch data)::

    docker compose -f contrib/docker-compose-example-apm/docker-compose.yml down -v
