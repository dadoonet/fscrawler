.. _otel:

OTel Tracing / EDOT Collector
==============================

FSCrawler ships with built-in distributed tracing support via the
`Elastic OpenTelemetry Java agent <https://github.com/elastic/elastic-otel-java>`_.
When the agent is present in the ``external/`` directory, FSCrawler automatically
loads it on startup and exports traces to any OpenTelemetry-compatible backend
(EDOT Collector, Jaeger, Zipkin, …).

.. note::

   OTel tracing is **enabled by default** when using the standard FSCrawler distribution.
   The agent JAR is bundled in ``external/``.  No configuration is required to
   start collecting traces — just point the exporter at your EDOT Collector.

How it works
-------------

FSCrawler uses a hybrid instrumentation approach:

* **Auto-instrumentation**: The ``elastic-otel-javaagent`` covers HTTP clients,
  the Elasticsearch Java client, and other standard libraries automatically.
* **Manual instrumentation**: Key FSCrawler pipeline stages are instrumented
  with named spans so you can identify bottlenecks:

.. list-table:: FSCrawler custom spans
   :header-rows: 1
   :widths: 30 30 40

   * - Span name
     - Attributes
     - Description
   * - ``fscrawler.crawl``
     - ``job.name``, ``fs.provider``
     - One span per crawler run
   * - ``fscrawler.directory.traverse``
     - ``scan.id``
     - Entire directory traversal for a run
   * - ``fscrawler.directory.process``
     - ``fs.path``
     - Processing of a single directory
   * - ``fscrawler.file.index``
     - ``fs.path``, ``file.size``
     - Indexing of a single file
   * - ``fscrawler.tika.extract``
     - ``file.size``, ``tika.content_type``, ``tika.indexed_chars``
     - Apache Tika text extraction
   * - ``fscrawler.es.bulk``
     - ``es.bulk.actions``
     - Elasticsearch bulk indexing request

Disabling OTel tracing
-----------------------

Remove the agent JAR to disable tracing entirely::

    rm $FS_HOME/external/elastic-otel-javaagent-*.jar

Alternatively, keep the JAR but disable the SDK at runtime::

    export OTEL_SDK_DISABLED=true
    ./bin/fscrawler my-job

Configuring the OTel exporter
-------------------------------

Use standard OpenTelemetry environment variables before starting FSCrawler:

.. code-block:: bash

   # Send traces to an EDOT Collector (OTLP HTTP)
   export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318
   export OTEL_SERVICE_NAME=fscrawler
   export OTEL_RESOURCE_ATTRIBUTES=deployment.environment=production,service.version=2.10

   ./bin/fscrawler my-job

Common variables:

.. list-table::
   :header-rows: 1
   :widths: 40 60

   * - Variable
     - Description
   * - ``OTEL_EXPORTER_OTLP_ENDPOINT``
     - OTLP endpoint (default: ``http://localhost:4317``)
   * - ``OTEL_SERVICE_NAME``
     - Service name shown in Kibana APM (default: ``fscrawler``)
   * - ``OTEL_RESOURCE_ATTRIBUTES``
     - Comma-separated ``key=value`` resource attributes
   * - ``OTEL_EXPORTER_OTLP_HEADERS``
     - Auth headers, e.g. ``Authorization=Bearer <token>``
   * - ``OTEL_SDK_DISABLED``
     - Set to ``true`` to disable without removing the agent
   * - ``OTEL_EXPORTER_OTLP_TIMEOUT``
     - Export timeout in ms (e.g. ``1000``); reduce if the collector is unavailable
   * - ``OTEL_METRIC_EXPORT_INTERVAL``
     - Metric flush interval in ms (default: ``60000``); set to ``5000`` for short-lived runs
   * - ``OTEL_INSTRUMENTATION_RUNTIME_TELEMETRY_JAVA17_ENABLED``
     - Set to ``true`` to enable Java 17+ JVM metrics (``jvm.cpu.recent_utilization``, etc.)

Using with Elastic Cloud (managed EDOT)
-----------------------------------------

.. code-block:: bash

   export OTEL_EXPORTER_OTLP_ENDPOINT=https://<your-otel-endpoint>:443
   export OTEL_EXPORTER_OTLP_HEADERS="Authorization=Bearer <api-key>"
   export OTEL_SERVICE_NAME=fscrawler
   ./bin/fscrawler my-job

You can find your OTel endpoint and API key in Kibana under
**Observability → Add data → Monitor with OpenTelemetry**.

Behavior without a collector
------------------------------

If the agent is present but no EDOT Collector is reachable, the OTLP exporter
will log a ``WARN`` message on each failed export attempt and retry with
exponential back-off.  FSCrawler continues to run normally — tracing failures
are non-blocking.

To suppress the warnings when no collector is available::

    export OTEL_SDK_DISABLED=true

or reduce the export timeout::

    export OTEL_EXPORTER_OTLP_TIMEOUT=1000

Using with other OTel backends (Jaeger, Zipkin, …)
----------------------------------------------------

FSCrawler uses the standard OTel Java SDK, so any compatible backend works.
Example for Jaeger (OTLP/gRPC):

.. code-block:: bash

   export OTEL_EXPORTER_OTLP_ENDPOINT=http://jaeger-host:4317
   export OTEL_SERVICE_NAME=fscrawler
   ./bin/fscrawler my-job

Docker example with EDOT Collector
-------------------------------------

A complete ``docker-compose`` stack (Elasticsearch + Kibana + EDOT Collector + FSCrawler)
is available under ``contrib/docker-compose-example-edot/``.

.. code-block:: yaml

   services:
     fscrawler:
       image: dadoonet/fscrawler:latest
       environment:
         - OTEL_EXPORTER_OTLP_ENDPOINT=http://edot-collector:4318
         - OTEL_SERVICE_NAME=fscrawler
         - OTEL_RESOURCE_ATTRIBUTES=deployment.environment=docker

To disable OTel tracing in Docker without rebuilding the image::

    environment:
      - OTEL_SDK_DISABLED=true

Windows
--------

The ``bin\fscrawler.bat`` launcher applies the same auto-detection logic.
Set environment variables in the same shell before running::

    set OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318
    set OTEL_SERVICE_NAME=fscrawler
    bin\fscrawler.bat my-job
