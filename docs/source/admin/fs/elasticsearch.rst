.. _elasticsearch-settings:

Elasticsearch settings
----------------------

.. contents:: :backlinks: entry

Here is a list of Elasticsearch settings (under ``elasticsearch.`` prefix)`:

+----------------------------------------+---------------------------+---------------------------------+
| Name                                   | Default value             | Documentation                   |
+========================================+===========================+=================================+
| ``elasticsearch.index``                | job name + ``_docs``      | `Index settings for documents`_ |
+----------------------------------------+---------------------------+---------------------------------+
| ``elasticsearch.index_folder``         | job name + ``_folder``    | `Index settings for folders`_   |
+----------------------------------------+---------------------------+---------------------------------+
| ``elasticsearch.push_templates``       | ``true``                  | :ref:`mappings`                 |
+----------------------------------------+---------------------------+---------------------------------+
| ``elasticsearch.force_push_templates`` | ``false``                 | :ref:`mappings`                 |
+----------------------------------------+---------------------------+---------------------------------+
| ``elasticsearch.bulk_size``            | ``100``                   | `Bulk settings`_                |
+----------------------------------------+---------------------------+---------------------------------+
| ``elasticsearch.flush_interval``       | ``"5s"``                  | `Bulk settings`_                |
+----------------------------------------+---------------------------+---------------------------------+
| ``elasticsearch.byte_size``            | ``"10mb"``                | `Bulk settings`_                |
+----------------------------------------+---------------------------+---------------------------------+
| ``elasticsearch.pipeline``             | ``null``                  | :ref:`ingest_node`              |
+----------------------------------------+---------------------------+---------------------------------+
| ``elasticsearch.semantic_search``      | ``true``                  | :ref:`semantic_search`          |
+----------------------------------------+---------------------------+---------------------------------+
| ``elasticsearch.urls``                 | ``https://127.0.0.1:9200``| `Node settings`_                |
+----------------------------------------+---------------------------+---------------------------------+
| ``elasticsearch.path_prefix``          | ``null``                  | `Path prefix`_                  |
+----------------------------------------+---------------------------+---------------------------------+
| ``elasticsearch.api_key``              | ``null``                  | `API Key`_                      |
+----------------------------------------+---------------------------+---------------------------------+
| ``elasticsearch.username``             | ``null``                  | :ref:`credentials`              |
+----------------------------------------+---------------------------+---------------------------------+
| ``elasticsearch.password``             | ``null``                  | :ref:`credentials`              |
+----------------------------------------+---------------------------+---------------------------------+
| ``elasticsearch.ssl_verification``     | ``true``                  | :ref:`ssl`                      |
+----------------------------------------+---------------------------+---------------------------------+
| ``elasticsearch.ca_certificate``       | ``null``                  | :ref:`ssl`                      |
+----------------------------------------+---------------------------+---------------------------------+

Index settings
^^^^^^^^^^^^^^

Index settings for documents
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

By default, FSCrawler will index your data in an index which name is
the same as the crawler name (``name`` property) plus ``_docs`` suffix, like
``test_docs``. You can change it by setting ``index`` field:

.. code:: yaml

   name: "test"
   elasticsearch:
     index: "docs"

Index settings for folders
~~~~~~~~~~~~~~~~~~~~~~~~~~

FSCrawler will also index folders in an index which name is the same as
the crawler name (``name`` property) plus ``_folder`` suffix, like
``test_folder``. You can change it by setting ``index_folder`` field:

.. code:: yaml

  name: "test"
  elasticsearch:
    index_folder: "folders"

.. _mappings:

Mappings
~~~~~~~~

.. versionadded:: 2.10

FSCrawler defines the following `Component Templates <https://www.elastic.co/guide/en/elasticsearch/reference/current/index-templates.html>`__
to define the index settings and mappings (replace ``INDEX`` with the index name):

- ``fscrawler_INDEX_alias``: defines the alias which name is the same as the crawler name (``name`` property) so you can search using this alias.
- ``fscrawler_INDEX_settings_total_fields``: defines the maximum number of fields for the index.
- ``fscrawler_INDEX_mapping_attributes``: defines the mapping for the ``attributes`` field.
- ``fscrawler_INDEX_mapping_file``: defines the mapping for the ``file`` field.
- ``fscrawler_INDEX_mapping_path``: defines an define an analyzer named ``fscrawler_path`` which uses a
  `path hierarchy tokenizer <https://www.elastic.co/guide/en/elasticsearch/reference/current/analysis-pathhierarchy-tokenizer.html>`__
  and the mapping for the ``path`` field.

- ``fscrawler_INDEX_mapping_attachment``: defines the mapping for the ``attachment`` field.
- ``fscrawler_INDEX_mapping_content_semantic``: defines the mapping for the ``content`` field when using semantic search.
It also creates a ``semantic_text`` field named ``content_semantic``. Please read the :ref:`semantic_search` section.

- ``fscrawler_INDEX_mapping_content``: defines the mapping for the ``content`` field when semantic search is not available.
- ``fscrawler_INDEX_mapping_meta``: defines the mapping for the ``meta`` field.

You can see the content of those templates by running:

::

   GET _component_template/fscrawler*

Then, FSCrawler applies those templates to the indices being created.

By default, FSCrawler will check if the index template already exists before creating templates.
If the index template exists, FSCrawler will skip the templates management, preserving any
custom component templates you may have defined in advance.

This means you can create your own component template before starting FSCrawler. FSCrawler will then create all the
missing component templates if any (but not the ones you already defined) and create the index template.

You can stop FSCrawler creating/updating the index templates for you
by setting ``push_templates`` to ``false``:

.. code:: yaml

   name: "test"
   elasticsearch:
     push_templates: false

If you want to force FSCrawler to push all templates (overwriting any existing ones),
you can set ``force_push_templates`` to ``true``:

.. code:: yaml

   name: "test"
   elasticsearch:
     force_push_templates: true

If you want to know what are the component templates and index templates
that will be created, you can get them from `the source <https://github.com/dadoonet/fscrawler/blob/master/elasticsearch-client/src/main/resources/fr/pilato/elasticsearch/crawler/fs/client/9>`__.

Creating your own mapping (analyzers)
"""""""""""""""""""""""""""""""""""""

If you want to define your own index settings and mapping to set
analyzers for example, you can create the needed component template
**before starting FSCrawler**.

FSCrawler will detect that the component template already exists and will not override it.
It will only create the missing component templates and the index template.

For example, you can define in advance your own component template ``fscrawler_fscrawler_mapping_content``:

.. code:: json

    PUT _component_template/fscrawler_fscrawler_mapping_content
    {
      "template": {
        "mappings": {
          "properties": {
            "content": {
              "type": "text",
              "analyzer": "french"
            }
          }
        }
      }
    }

Then start FSCrawler. It will create all the component templates but ``fscrawler_fscrawler_mapping_content``
(which you already defined) and create the index template.

.. note::

    If someone wants to force pushing all the templates again (for example after an upgrade),
    they can use ``force_push_templates: true``. In the above example, the custom
    ``fscrawler_fscrawler_mapping_content`` component template would be overridden.

The following example uses a ``french`` analyzer to index the
``content`` field and still allow using semantic search.

.. code:: json

    PUT _component_template/fscrawler_fscrawler_mapping_content_semantic
    {
      "template": {
        "mappings": {
          "properties": {
            "content": {
              "type": "text",
              "analyzer": "french",
              "copy_to": "content_semantic"
            },
            "content_semantic": {
              "type": "semantic_text"
            }
          }
        }
      }
    }

The following example uses a ``french`` analyzer to index the
``content`` field.

.. code:: json

    PUT _component_template/fscrawler_fscrawler_mapping_content
    {
      "template": {
        "mappings": {
          "properties": {
            "content": {
              "type": "text",
              "analyzer": "french"
            }
          }
        }
      }
    }

.. tip::

    You can launch FSCrawler with ``--loop 0`` to see what component templates and index templates
    would be created without indexing any document. Then you can create your own custom component
    templates and restart FSCrawler. Your custom templates will be preserved.

Replace existing mapping
""""""""""""""""""""""""

Unfortunately you can not change the mapping on existing data.
Therefore, you’ll need first to remove existing index, which means
remove all existing data, and then restart FSCrawler with the new
mapping.

You might to try `elasticsearch Reindex
API <https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-reindex.html>`__
though.

.. _semantic_search:

Semantic search
"""""""""""""""

.. versionadded:: 2.10

FSCrawler can use `semantic search <https://www.elastic.co/guide/en/elasticsearch/reference/current/semantic-search.html>`__
to improve the search results.

.. note::

    Semantic search is available starting from Elasticsearch 8.17.0 and requires a trial or enterprise license.

Semantic search is enabled by default when an Elasticsearch 8.17.0 or above and a trial or enterprise license are
detected. But you can disable it by setting ``semantic_search`` to ``false``:

.. code:: yaml

   name: "test"
   elasticsearch:
     semantic_search: false

When activated, the ``content`` field is indexed as usual but a new field named ``content_semantic``
is created and uses the `semantic_text <https://www.elastic.co/guide/en/elasticsearch/reference/current/semantic-text.html>`__
field type. This field type is used to store the semantic information extracted from the content by using the defined
inference API (defaults to `Elser model <https://www.elastic.co/guide/en/machine-learning/current/ml-nlp-elser.html>`__).

You can change the model to use by changing the component template. For example, a recommended model when you have only
english content is the Elastic `multilingual-e5-small <https://www.elastic.co/guide/en/machine-learning/current/ml-nlp-multilingual-e5-small.html>`__:

.. code:: json

    PUT _component_template/fscrawler_fscrawler_mapping_content_semantic
    {
      "template": {
        "mappings": {
          "properties": {
            "content": {
              "type": "text",
              "copy_to": "content_semantic"
            },
            "content_semantic": {
              "type": "semantic_text",
              "inference_id": ".multilingual-e5-small-elasticsearch"
            }
          }
        }
      }
    }


Bulk settings
^^^^^^^^^^^^^

FSCrawler is using bulks to send data to elasticsearch. By default the
bulk is executed every 100 operations or every 5 seconds or every 10 megabytes. You can change
default settings using ``bulk_size``, ``byte_size`` and ``flush_interval``:

.. code:: yaml

  name: "test"
  elasticsearch:
    bulk_size: 1000
    byte_size: "500kb"
    flush_interval: "2s"

.. tip::

    Elasticsearch has a default limit of ``100mb`` per HTTP request as per
    `elasticsearch HTTP Module <https://www.elastic.co/guide/en/elasticsearch/reference/current/modules-http.html>`__
    documentation.

    Which means that if you are indexing a massive bulk of documents, you
    might hit that limit and FSCrawler will throw an error like
    ``entity content is too long [xxx] for the configured buffer limit [104857600]``.

    You can either change this limit on elasticsearch side by setting
    ``http.max_content_length`` to a higher value but please be aware that
    this will consume much more memory on elasticsearch side.

    Or you can decrease the ``bulk_size`` or ``byte_size`` setting to a smaller value.

.. _ingest_node:

Using Ingest Node Pipeline
^^^^^^^^^^^^^^^^^^^^^^^^^^

If you are using an elasticsearch cluster running a 5.0 or superior
version, you can use an Ingest Node pipeline to transform documents sent
by FSCrawler before they are actually indexed.

For example, if you have the following pipeline:

.. code:: sh

   PUT _ingest/pipeline/fscrawler
   {
     "description" : "fscrawler pipeline",
     "processors" : [
       {
         "set" : {
           "field": "foo",
           "value": "bar"
         }
       }
     ]
   }

In FSCrawler settings, set the ``elasticsearch.pipeline`` option:

.. code:: yaml

   name: "test"
   elasticsearch:
     pipeline: "fscrawler"

.. note::
    Folder objects are not sent through the pipeline as they are more
    internal objects.

Node settings
^^^^^^^^^^^^^

FSCrawler is using elasticsearch REST layer to send data to your
running cluster. By default, it connects to ``https://127.0.0.1:9200``
which is the default when running a local node on your machine. 
Note that using ``https`` requires SSL Configuration set up.
For more information, read  :ref:`ssl`.

FSCrawler supports all kind of Elasticsearch deployments:

- `Self managed deployments <https://www.elastic.co/guide/en/elasticsearch/reference/current/install-elasticsearch.html>`_
- `Hosted deployments <https://ela.st/dedicated-deployment-usage-info>`_
- `Serverless projects <https://ela.st/serverless-learn-more>`_

Of course, in production, you would probably change this and connect to
a production cluster:

.. code:: yaml

   name: "test"
   elasticsearch:
     urls:
     - "https://mynode1.mycompany.com:9200"

You can define multiple nodes:

.. code:: yaml

   name: "test"
   elasticsearch:
     urls:
     - "https://mynode1.mycompany.com:9200"
     - "https://mynode2.mycompany.com:9200"
     - "https://mynode3.mycompany.com:9200"

.. note::

    If you are using `Elastic Cloud <https://www.elastic.co/cloud>`_, you can just use the ``Elasticsearch Endpoint``.

.. note::

    If you are using `Start Local <https://www.elastic.co/guide/en/elasticsearch/reference/current/run-elasticsearch-locally.html>`_:

    .. code:: sh

       curl -fsSL https://elastic.co/start-local | sh

    The url to use is ``http://localhost:9200`` and the API key to use is available in the ``.env`` generated file.

Path prefix
^^^^^^^^^^^

If your elasticsearch is running behind a proxy with url rewriting,
you might have to specify a path prefix. This can be done with ``path_prefix`` setting:

.. code:: yaml

   name: "test"
   elasticsearch:
     urls:
     - "http://mynode1.mycompany.com:9200"
     path_prefix: "/path/to/elasticsearch"

.. note::

    The same ``path_prefix`` applies to all nodes.

.. _credentials:

Using Credentials (Security)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^

If you have a secured cluster, you can use several methods to connect
to it:

- `API Key <https://www.elastic.co/guide/en/elasticsearch/reference/current/security-api-create-api-key.html>`__
- `Basic Authentication <https://www.elastic.co/guide/en/elasticsearch/reference/current/security-api-authenticate.html>`__ (not recommended / deprecated)

API Key
~~~~~~~

.. versionadded:: 2.10

Let's create an API Key named ``fscrawler``:

.. code:: json

    POST /_security/api_key
    {
      "name": "fscrawler"
    }

This gives something like:

.. code:: json

    {
      "id": "VuaCfGcBCdbkQm-e5aOx",
      "name": "fscrawler",
      "expiration": 1544068612110,
      "api_key": "ui2lp2axTNmsyakw9tvNnw",
      "encoded": "VnVhQ2ZHY0JDZGJrUW0tZTVhT3g6dWkybHAyYXhUTm1zeWFrdzl0dk5udw=="
    }

Then you can use the encoded API Key in FSCrawler settings:

.. code:: yaml

   name: "test"
   elasticsearch:
     api_key: "VnVhQ2ZHY0JDZGJrUW0tZTVhT3g6dWkybHAyYXhUTm1zeWFrdzl0dk5udw=="

Basic Authentication (deprecated)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The best practice is to use `API Key`_. But if you have no other choice, you can still use Basic Authentication.

You can provide the ``username`` and ``password`` to FSCrawler:

.. code:: yaml

   name: "test"
   elasticsearch:
     username: "elastic"
     password: "changeme"

.. warning::
    Be aware that the elasticsearch password is stored in plain text in your job setting file.

    A better practice is to only set the username or pass it with
    ``--username elastic`` option when starting FSCrawler.

    If the password is not defined, you will be prompted when starting the job:

    ::

       22:46:42,528 INFO  [f.p.e.c.f.FsCrawler] Password for elastic:


User permissions
~~~~~~~~~~~~~~~~

If you want to use another user than the default ``elastic`` (which is admin), you will need to give him some permissions:

* ``cluster:monitor``
* ``indices:fsc/all``
* ``indices:fsc_folder/all``

where ``fsc`` is the FSCrawler index name as defined in `Index settings for documents`_.

This can be done by defining the following role:

.. code:: sh

    PUT /_security/role/fscrawler
    {
      "cluster" : [ "monitor" ],
      "indices" : [ {
          "names" : [ "fsc", "fsc_folder" ],
          "privileges" : [ "all" ]
      } ]
    }

This also can be done using the Kibana Stack Management Interface.

.. image:: /_static/elasticsearch/fscrawler-roles.png

Then, you can assign this role to the user who will be defined within the ``username`` setting.

.. _ssl:

SSL Configuration
^^^^^^^^^^^^^^^^^

In order to ingest documents to Elasticsearch over HTTPS based connection, you obviously need to set the URL
to ``https://your-server-address``. If your server is using a certificate that has been signed
by a Certificate Authority, then you're good to go. For example, that's the case if you are running Elasticsearch
from cloud.elastic.co.

But if you are using a self signed certificate, which is the case in development mode, you need to either
ignore the ssl check (not recommended) or provide the certificate to the Elasticsearch client.

To bypass the SSL Certificate verification, you can use the ``ssl_verification`` option:

.. code:: yaml

   name: "test"
   elasticsearch:
     api_key: "VnVhQ2ZHY0JDZGJrUW0tZTVhT3g6dWkybHAyYXhUTm1zeWFrdzl0dk5udw=="
     ssl_verification: false

If you are running Elasticsearch from a Docker container, you can copy the self-signed certificate
generated in ``/usr/share/elasticsearch/config/certs/http_ca.crt`` to your local machine:

.. code:: sh

    docker cp CONTAINER_NAME:/usr/share/elasticsearch/config/certs/http_ca.crt /path/to/certificate

And then, you can specify this file in the ``elasticsearch.ca_certificate`` option:

.. code:: yaml

   name: "test"
   elasticsearch:
     api_key: "VnVhQ2ZHY0JDZGJrUW0tZTVhT3g6dWkybHAyYXhUTm1zeWFrdzl0dk5udw=="
     ca_certificate: /path/to/certificate/http_ca.crt

.. note::

    You can also import your certificate into ``<JAVA_HOME>\lib\security\cacerts``.

    For example, if you have a root CA chain certificate or Elasticsearch server certificate
    in DER format (it's a binary format using a ``.cer`` extension), you need to:

    1. Logon to server (or client machine) where FSCrawler is running
    2. Run:

    .. code:: sh

        keytool -import -alias <alias name> -keystore "<JAVA_HOME>\lib\security\cacerts" -file <Path of Elasticsearch Server certificate or Root certificate>

    It will prompt you for the password. Enter the certificate password like ``changeit``.

    3. Make changes to FSCrawler ``_settings.json`` file to connect to your Elasticsearch server over HTTPS:

    .. code:: yaml

        name: "test"
        elasticsearch:
         api_key: "VnVhQ2ZHY0JDZGJrUW0tZTVhT3g6dWkybHAyYXhUTm1zeWFrdzl0dk5udw=="
         urls:
         - "https://localhost:9243"

    .. tip::

        If you can not find ``keytool``, it probably means that you did not add your ``JAVA_HOME/bin`` directory to your path.

.. _generated_fields:

Generated fields
^^^^^^^^^^^^^^^^

FSCrawler may create the following fields depending on configuration and available data:

+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| Field                      | Description                            | Example                                      | Javadoc                                                             |
+============================+========================================+==============================================+=====================================================================+
| ``content``                | Extracted content                      | ``"This is my text!"``                       |                                                                     |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``content_semantic``       | Semantic version for the extracted     | ``"This is my text!"``                       |                                                                     |
|                            | content                                |                                              |                                                                     |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``attachment``             | BASE64 encoded binary file             | BASE64 Encoded document                      |                                                                     |
|                            |                                        |                                              |                                                                     |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.author``            | Author if any in                       | ``"David Pilato"``                           | `CREATOR <https://tika.apache.org/2.9.1/api/org/apache/tika/         |
|                            |                                        |                                              | metadata/TikaCoreProperties.html#CREATOR>`__                        |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.title``             | Title if any in document metadata      | ``"My document title"``                      | `TITLE <https://tika.apache.org/2.9.1/api/org/apache/tika/           |
|                            |                                        |                                              | metadata/TikaCoreProperties.html#TITLE>`__                          |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.date``              | Last modified date                     | ``"2013-04-04T15:21:35"``                    | `MODIFIED <https://tika.apache.org/2.9.1/api/org/apache/tika/        |
|                            |                                        |                                              | metadata/TikaCoreProperties.html#MODIFIED>`__                       |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.keywords``          | Keywords if any in document metadata   | ``["fs","elasticsearch"]``                   | `KEYWORDS <https://tika.apache.org/2.9.1/api/org/apache/tika/        |
|                            |                                        |                                              | metadata/TikaCoreProperties.html#KEYWORDS>`__                       |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.language``          | Language (can be detected)             | ``"fr"``                                     | `LANGUAGE <https://tika.apache.org/2.9.1/api/org/apache/tika/        |
|                            |                                        |                                              | metadata/TikaCoreProperties.html#LANGUAGE>`__                       |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.format``            | Format of the media                    | ``"application/pdf; version=1.6"``           | `FORMAT <https://tika.apache.org/2.9.1/api/org/apache/tika/          |
|                            |                                        |                                              | metadata/TikaCoreProperties.html#FORMAT>`__                         |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.identifier``        | URL/DOI/ISBN for example               | ``"FOOBAR"``                                 | `IDENTIFIER <https://tika.apache.org/2.9.1/api/org/apache/tika/      |
|                            |                                        |                                              | metadata/TikaCoreProperties.html#IDENTIFIER>`__                     |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.contributor``       | Contributor                            | ``"foo bar"``                                | `CONTRIBUTOR <https://tika.apache.org/2.9.1/api/org/apache/tika/     |
|                            |                                        |                                              | metadata/TikaCoreProperties.html#CONTRIBUTOR>`__                    |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.coverage``          | Coverage                               | ``"FOOBAR"``                                 | `COVERAGE <https://tika.apache.org/2.9.1/api/org/apache/tika/        |
|                            |                                        |                                              | metadata/TikaCoreProperties.html#COVERAGE>`__                       |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.modifier``          | Last author                            | ``"David Pilato"``                           | `MODIFIER <https://tika.apache.org/2.9.1/api/org/apache/tika/        |
|                            |                                        |                                              | metadata/TikaCoreProperties.html#MODIFIER>`__                       |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.creator_tool``      | Tool used to create the resource       | ``"HTML2PDF- TCPDF"``                        | `CREATOR_TOOL <https://tika.apache.org/2.9.1/api/org/apache/tika/    |
|                            |                                        |                                              | metadata/TikaCoreProperties.html#CREATOR_TOOL>`__                   |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.publisher``         | Publisher: person, organisation,       | ``"elastic"``                                | `PUBLISHER <https://tika.apache.org/2.9.1/api/org/apache/tika/       |
|                            | service                                |                                              | metadata/TikaCoreProperties.html#PUBLISHER>`__                      |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.relation``          | Related resource                       | ``"FOOBAR"``                                 | `RELATION <https://tika.apache.org/2.9.1/api/org/apache/tika/        |
|                            |                                        |                                              | metadata/TikaCoreProperties.html#RELATION>`__                       |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.rights``            | Information about rights               | ``"CC-BY-ND"``                               | `RIGHTS <https://tika.apache.org/2.9.1/api/org/apache/tika/          |
|                            |                                        |                                              | metadata/TikaCoreProperties.html#RIGHTS>`__                         |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.source``            | Source for the current document        | ``"FOOBAR"``                                 | `SOURCE <https://tika.apache.org/2.9.1/api/org/apache/tika/          |
|                            | (derivated)                            |                                              | metadata/TikaCoreProperties.html#SOURCE>`__                         |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.type``              | Nature or genre of the content         | ``"Image"``                                  | `TYPE <https://tika.apache.org/2.9.1/api/org/apache/tika/            |
|                            |                                        |                                              | metadata/TikaCoreProperties.html#TYPE>`__                           |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.description``       | An account of the content              | ``"This is a description"``                  | `DESCRIPTION <https://tika.apache.org/2.9.1/api/org/apache/tika/     |
|                            |                                        |                                              | metadata/TikaCoreProperties.html#DESCRIPTION>`__                    |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.created``           | Date of creation                       | ``"2013-04-04T15:21:35"``                    | `CREATED <https://tika.apache.org/2.9.1/api/org/apache/tika/         |
|                            |                                        |                                              | metadata/TikaCoreProperties.html#CREATED>`__                        |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.print_date``        | When was the doc last printed?         | ``"2013-04-04T15:21:35"``                    | `PRINT_DATE <https://tika.apache.org/2.9.1/api/org/apache/tika/      |
|                            |                                        |                                              | metadata/TikaCoreProperties.html#PRINT_DATE>`__                     |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.metadata_date``     | Last modification of metadata          | ``"2013-04-04T15:21:35"``                    | `METADATA_DATE <https://tika.apache.org/2.9.1/api/org/apache/tika/   |
|                            |                                        |                                              | metadata/TikaCoreProperties.html#METADATA_DATE>`__                  |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.latitude``          | The WGS84 Latitude of the Point        | ``"N 48° 51' 45.81''"``                      | `LATITUDE <https://tika.apache.org/2.9.1/api/org/apache/tika/        |
|                            |                                        |                                              | metadata/TikaCoreProperties.html#LATITUDE>`__                       |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.longitude``         | The WGS84 Longitude of the Point       | ``"E 2° 17'15.331''"``                       | `LONGITUDE <https://tika.apache.org/2.9.1/api/org/apache/tika/       |
|                            |                                        |                                              | metadata/TikaCoreProperties.html#LONGITUDE>`__                      |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.altitude``          | The WGS84 Altitude of the Point        | ``""``                                       | `ALTITUDE <https://tika.apache.org/2.9.1/api/org/apache/tika/        |
|                            |                                        |                                              | metadata/TikaCoreProperties.html#ALTITUDE>`__                       |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.rating``            | A user-assigned rating -1, [0..5]      | ``0``                                        | `RATING <https://tika.apache.org/2.9.1/api/org/apache/tika/          |
|                            |                                        |                                              | metadata/TikaCoreProperties.html#RATING>`__                         |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.comments``          | Comments                               | ``"Comments"``                               | `COMMENTS <https://tika.apache.org/2.9.1/api/org/apache/tika/        |
|                            |                                        |                                              | metadata/TikaCoreProperties.html#COMMENTS>`__                       |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``meta.raw``               | An object with all raw metadata        | ``"meta.raw.channels": "2"``                 |                                                                     |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``file.content_type``      | Content Type                           | ``"application/vnd.oasis.opendocument.text"``|                                                                     |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``file.created``           | Creation date                          | ``"2018-07-30T11:19:23.000+0000"``           |                                                                     |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``file.last_modified``     | Last modification date                 | ``"2018-07-30T11:19:23.000+0000"``           |                                                                     |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``file.last_accessed``     | Last accessed date                     | ``"2018-07-30T11:19:23.000+0000"``           |                                                                     |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``file.indexing_date``     | Indexing date                          | ``"2018-07-30T11:19:30.703+0000"``           |                                                                     |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``file.filesize``          | File size in bytes                     | ``1256362``                                  |                                                                     |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``file.indexed_chars``     | Extracted chars                        | ``100000``                                   |                                                                     |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``file.filename``          | Original file name                     | ``"mydocument.pdf"``                         |                                                                     |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``file.extension``         | Original file name extension           | ``"pdf"``                                    |                                                                     |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``file.url``               | Original file url                      | ``"file://tmp/otherdir/mydocument.pdf"``     |                                                                     |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``file.checksum``          | Checksum                               | ``"c32eafae2587bef4b3b32f73743c3c61"``       |                                                                     |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``path.virtual``           | Relative path from                     | ``"/otherdir/mydocument.pdf"``               |                                                                     |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``path.root``              | MD5 encoded parent path (internal use) | ``"112aed83738239dbfe4485f024cd4ce1"``       |                                                                     |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``path.real``              | Real path name                         | ``"/tmp/otherdir/mydocument.pdf"``           |                                                                     |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``attributes.owner``       | Owner name                             | ``"david"``                                  |                                                                     |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``attributes.group``       | Group name                             | ``"staff"``                                  |                                                                     |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``attributes.permissions`` | Permissions                            | ``764``                                      |                                                                     |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+
| ``external``               | Additional tags                        | ``{ "tenantId": 22, "projectId": 33 }``      |                                                                     |
+----------------------------+----------------------------------------+----------------------------------------------+---------------------------------------------------------------------+

For more information about meta data, please read the `TikaCoreProperties <https://tika.apache.org/2.9.1/api/org/apache/tika/metadata/TikaCoreProperties.html>`__.

Here is a typical JSON document generated by the crawler:

.. code:: json

    {
       "content":"This is a sample text available in page 1\n\nThis second part of the text is in Page 2\n\n",
       "content_semantic":"This is a sample text available in page 1\n\nThis second part of the text is in Page 2\n\n",
       "meta":{
          "author":"David Pilato",
          "title":"Test Tika title",
          "date":"2016-07-07T16:37:00.000+0000",
          "keywords":[
             "keyword1",
             "  keyword2"
          ],
          "language":"en",
          "description":"Comments",
          "created":"2016-07-07T16:37:00.000+0000"
       },
       "file":{
          "extension":"odt",
          "content_type":"application/vnd.oasis.opendocument.text",
          "created":"2018-07-30T11:35:08.000+0000",
          "last_modified":"2018-07-30T11:35:08.000+0000",
          "last_accessed":"2018-07-30T11:35:08.000+0000",
          "indexing_date":"2018-07-30T11:35:19.781+0000",
          "filesize":6236,
          "filename":"test.odt",
          "url":"file:///tmp/test.odt"
       },
       "path":{
          "root":"7537e4fb47e553f110a1ec312c2537c0",
          "virtual":"/test.odt",
          "real":"/tmp/test.odt"
       }
    }

.. _search-examples:

Search examples
^^^^^^^^^^^^^^^

You can use the content field to perform full-text search on

::

   GET docs/_search
   {
     "query" : {
       "match" : {
           "content" : "the quick brown fox"
       }
     }
   }

To perform semantic search, you can use the ``content_semantic`` field:

::

   GET docs/_search
   {
     "query" : {
       "semantic" : {
           "content_semantic" : "a very fast animal"
       }
     }
   }

You can use meta fields to perform search on.

::

   GET docs/_search
   {
     "query" : {
       "term" : {
           "file.filename" : "mydocument.pdf"
       }
     }
   }

Or run some aggregations on top of them, like:

::

   GET docs/_search
   {
     "size": 0,
     "aggs": {
       "by_extension": {
         "terms": {
           "field": "file.extension"
         }
       }
     }
   }

