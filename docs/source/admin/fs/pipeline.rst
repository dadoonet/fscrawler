.. _pipeline-settings:

Pipeline Configuration (v2)
===========================

.. versionadded:: 2.10

FSCrawler 2.10 introduces a new pipeline-based configuration format (v2) that allows you to define
multiple inputs, filters, and outputs with conditional routing. This is similar to the Logstash
pipeline model.

.. contents:: :backlinks: entry

Overview
--------

The pipeline architecture consists of three main components:

- **Inputs**: Define where documents come from (local filesystem, SSH, FTP, S3, HTTP)
- **Filters**: Process and transform documents (Tika parsing, JSON, XML)
- **Outputs**: Define where documents are sent (Elasticsearch)

Each component can have multiple instances, and filters/outputs support conditional routing
using MVEL expressions.

Backward Compatibility
----------------------

The legacy v1 configuration format is still fully supported. When FSCrawler loads a v1 configuration,
it automatically converts it to v2 format internally. A deprecation warning is displayed, and the
suggested v2 configuration is logged.

To migrate your configuration manually, use the ``--migrate`` CLI option (see :ref:`cli-migrate`).

Basic v2 Configuration
----------------------

Here is a simple v2 configuration example:

.. code:: yaml

   version: 2
   name: "my_job"

   inputs:
     - type: local
       id: main_input
       url: "/path/to/docs"
       update_rate: "5m"
       includes:
         - "*.pdf"
         - "*.docx"

   filters:
     - type: tika
       id: main_filter

   outputs:
     - type: elasticsearch
       id: main_output
       urls:
         - "https://127.0.0.1:9200"
       index: "my_docs"

Configuration Format
--------------------

Version Field
^^^^^^^^^^^^^

The ``version`` field indicates the configuration format version:

.. code:: yaml

   version: 2

If omitted, FSCrawler will detect the format based on the presence of ``inputs``/``filters``/``outputs``
fields (v2) or ``fs``/``server`` fields (v1).

Inputs
^^^^^^

Inputs define where documents are read from. Each input has a ``type`` and ``id``.

**Common input properties:**

+-------------------+----------------------------------------------------------+
| Property          | Description                                              |
+===================+==========================================================+
| ``type``          | Input type: ``local``, ``ssh``, ``ftp``, ``s3``, ``http``|
+-------------------+----------------------------------------------------------+
| ``id``            | Unique identifier for this input                         |
+-------------------+----------------------------------------------------------+
| ``update_rate``   | How often to scan for changes (e.g., ``5m``, ``1h``)     |
+-------------------+----------------------------------------------------------+
| ``includes``      | File patterns to include                                 |
+-------------------+----------------------------------------------------------+
| ``excludes``      | File patterns to exclude                                 |
+-------------------+----------------------------------------------------------+
| ``tags``          | Tags to add to documents from this input                 |
+-------------------+----------------------------------------------------------+

**Example with multiple inputs:**

.. code:: yaml

   inputs:
     - type: local
       id: local_docs
       url: "/path/to/local/docs"
       update_rate: "5m"
       tags:
         - "source:local"

     - type: ssh
       id: remote_docs
       hostname: "server.example.com"
       port: 22
       username: "user"
       url: "/remote/path"
       update_rate: "15m"
       tags:
         - "source:remote"

Filters
^^^^^^^

Filters process documents. Available filter types:

+-------------------+----------------------------------------------------------+
| Type              | Description                                              |
+===================+==========================================================+
| ``tika``          | Parse documents using Apache Tika (default)              |
+-------------------+----------------------------------------------------------+
| ``json``          | Parse JSON files                                         |
+-------------------+----------------------------------------------------------+
| ``xml``           | Parse XML files                                          |
+-------------------+----------------------------------------------------------+
| ``none``          | No content parsing (metadata only)                       |
+-------------------+----------------------------------------------------------+

**Filter properties:**

+-------------------+----------------------------------------------------------+
| Property          | Description                                              |
+===================+==========================================================+
| ``type``          | Filter type                                              |
+-------------------+----------------------------------------------------------+
| ``id``            | Unique identifier                                        |
+-------------------+----------------------------------------------------------+
| ``when``          | MVEL condition expression (see :ref:`conditional-routing`)|
+-------------------+----------------------------------------------------------+

**Example:**

.. code:: yaml

   filters:
     - type: tika
       id: default_filter

     - type: json
       id: json_filter
       when: "extension == 'json'"

Outputs
^^^^^^^

Outputs define where processed documents are sent.

**Output properties:**

+-------------------+----------------------------------------------------------+
| Property          | Description                                              |
+===================+==========================================================+
| ``type``          | Output type (currently only ``elasticsearch``)           |
+-------------------+----------------------------------------------------------+
| ``id``            | Unique identifier                                        |
+-------------------+----------------------------------------------------------+
| ``when``          | MVEL condition expression                                |
+-------------------+----------------------------------------------------------+
| ``urls``          | Elasticsearch URLs                                       |
+-------------------+----------------------------------------------------------+
| ``index``         | Target index name                                        |
+-------------------+----------------------------------------------------------+
| ``api_key``       | Elasticsearch API key                                    |
+-------------------+----------------------------------------------------------+

**Example with multiple outputs:**

.. code:: yaml

   outputs:
     - type: elasticsearch
       id: main_output
       urls:
         - "https://127.0.0.1:9200"
       index: "documents"

     - type: elasticsearch
       id: archive_output
       urls:
         - "https://archive.example.com:9200"
       index: "archive"
       when: "tags.contains('archive')"

.. _conditional-routing:

Conditional Routing
-------------------

Filters and outputs can use MVEL expressions in the ``when`` field to conditionally
process documents. If the condition evaluates to ``true``, the filter/output is applied.

Available Variables
^^^^^^^^^^^^^^^^^^^

+-------------------+----------------------------------------------------------+
| Variable          | Description                                              |
+===================+==========================================================+
| ``filename``      | The file name (e.g., ``report.pdf``)                     |
+-------------------+----------------------------------------------------------+
| ``extension``     | The file extension without dot (e.g., ``pdf``)           |
+-------------------+----------------------------------------------------------+
| ``path``          | Full path to the file                                    |
+-------------------+----------------------------------------------------------+
| ``size``          | File size in bytes                                       |
+-------------------+----------------------------------------------------------+
| ``inputId``       | ID of the input that produced this document              |
+-------------------+----------------------------------------------------------+
| ``mimeType``      | Detected MIME type                                       |
+-------------------+----------------------------------------------------------+
| ``tags``          | Set of tags associated with the document                 |
+-------------------+----------------------------------------------------------+
| ``metadata``      | Map of additional metadata                               |
+-------------------+----------------------------------------------------------+

Expression Examples
^^^^^^^^^^^^^^^^^^^

**Simple comparisons:**

.. code:: yaml

   # Match PDF files
   when: "extension == 'pdf'"

   # Match files larger than 1MB
   when: "size > 1048576"

   # Match specific input
   when: "inputId == 'local_docs'"

**String operations:**

.. code:: yaml

   # Files starting with "report_"
   when: "filename.startsWith('report_')"

   # Files containing "2024"
   when: "filename.contains('2024')"

   # Path contains "finance"
   when: "path.contains('/finance/')"

**Tags and metadata:**

.. code:: yaml

   # Documents with "important" tag
   when: "tags.contains('important')"

   # Check metadata
   when: "metadata['department'] == 'HR'"

**Logical operators:**

.. code:: yaml

   # AND condition
   when: "extension == 'pdf' && size > 100000"

   # OR condition
   when: "extension == 'pdf' || extension == 'docx'"

   # NOT condition
   when: "!(extension == 'tmp')"

**Regular expressions:**

.. code:: yaml

   # Regex matching (using ~= operator)
   when: "filename ~= '.*_2024_.*'"

Complex Example
---------------

Here is a complete example with multiple inputs, conditional filters, and outputs:

.. code:: yaml

   version: 2
   name: "multi_source_crawler"

   inputs:
     - type: local
       id: finance_docs
       url: "/data/finance"
       update_rate: "5m"
       includes:
         - "*.pdf"
         - "*.xlsx"
       tags:
         - "department:finance"

     - type: local
       id: hr_docs
       url: "/data/hr"
       update_rate: "15m"
       includes:
         - "*.pdf"
         - "*.docx"
       tags:
         - "department:hr"

     - type: ssh
       id: remote_reports
       hostname: "reports.example.com"
       username: "crawler"
       url: "/reports"
       update_rate: "1h"
       tags:
         - "source:remote"

   filters:
     - type: tika
       id: default_parser

     - type: json
       id: json_parser
       when: "extension == 'json'"

   outputs:
     - type: elasticsearch
       id: main_index
       urls:
         - "https://es.example.com:9200"
       index: "documents"

     - type: elasticsearch
       id: finance_index
       urls:
         - "https://es.example.com:9200"
       index: "finance_docs"
       when: "tags.contains('department:finance')"

     - type: elasticsearch
       id: archive
       urls:
         - "https://archive.example.com:9200"
       index: "archive"
       when: "size > 10485760"  # Files > 10MB go to archive

Singular vs Plural Syntax
-------------------------

For simple configurations with a single input/filter/output, you can use the singular
form (``input``, ``filter``, ``output``) instead of lists:

.. code:: yaml

   version: 2
   name: "simple_job"

   input:
     type: local
     id: main
     url: "/path/to/docs"

   filter:
     type: tika
     id: main

   output:
     type: elasticsearch
     id: main
     urls:
       - "https://127.0.0.1:9200"

This is equivalent to using lists with a single element.

.. _cli-migrate:

Migrating from v1 to v2
-----------------------

To migrate an existing v1 configuration to v2 format, use the ``--migrate`` CLI option:

.. code:: sh

   # Display the migrated configuration
   bin/fscrawler my_job --migrate

   # Save to a file
   bin/fscrawler my_job --migrate --migrate-output _settings_v2.yaml

The migration tool will:

1. Detect the input type from the ``server.protocol`` setting
2. Determine the filter type based on ``json_support``, ``xml_support``, and ``index_content``
3. Create an Elasticsearch output from the existing settings
4. Preserve all legacy settings for backward compatibility

After migration, review the generated configuration and replace your ``_settings.yaml`` file.
