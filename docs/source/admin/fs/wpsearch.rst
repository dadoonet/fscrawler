.. _wpsearch-settings:

Workplace Search settings
-------------------------

.. versionadded:: 2.7

FSCrawler can now send documents to `Workplace Search <https://www.elastic.co/workplace-search>`_.

.. note::

    Although this won't be needed in the future, it is still mandatory to have access to the elasticsearch
    instance running behind Workplace Search. In this section of the documentation, we will only cover the
    specifics for workplace search. Please refer to :ref:`elasticsearch-settings` chapter.

.. hint::

    To easily start locally with Workplace Search, follow the steps:

    * Check-out the source code on `GitHub <https://github.com/dadoonet/fscrawler/>`_::

        git clone git@github.com:dadoonet/fscrawler.git
        cd fscrawler
        cd contrib/docker-compose-workplacesearch
        docker-compose up

    This will start Elasticsearch, Kibana (not used) and Workplace Search.

    * Wait for it to start and open http://127.0.0.1:3002/ws.
    * Enter ``enterprise_search`` as the login and ``changeme`` as the password.
    * Click on "Add sources" button and choose `Custom API <http://127.0.0.1:3002/ws/org/sources#/add/custom>`_.
    * Name it ``fscrawler`` and click on "Create Custom API Source" button.
    * Copy the "Access Token" value. We will mention it as ``ACCESS_TOKEN`` for the rest of this documentation.
    * Copy the "Key" value. We will mention it as ``KEY`` for the rest of this documentation.

    .. image:: /_static/wpsearch/fscrawler-custom-source.png

Here is a list of Workplace Search settings (under ``workplace_search.`` prefix)`:

+----------------------------------+---------------------------+---------------------------------+
| Name                             | Default value             | Documentation                   |
+==================================+===========================+=================================+
| ``workplace_search.access_token``| None (Must be set)        | `Keys`_                         |
+----------------------------------+---------------------------+---------------------------------+
| ``workplace_search.key``         | None (Must be set)        | `Keys`_                         |
+----------------------------------+---------------------------+---------------------------------+
| ``workplace_search.server``      | ``http://127.0.0.1:3002`` | `Server`_                       |
+----------------------------------+---------------------------+---------------------------------+


Keys
^^^^

Once you have created your Custom API and have the ``ACCESS_TOKEN`` and ``KEY``, you can add to your existing
FSCrawler configuration file:

.. code:: yaml

   name: "test"
   workplace_search:
     access_token: "ACCESS_TOKEN"
     key: "KEY"

Server
^^^^^^

When using Workplace Search, FSCrawler will by default connect to ``http://127.0.0.1:3002``
which is the default when running a local node on your machine.

Of course, in production, you would probably change this and connect to
a production cluster:

.. code:: yaml

   name: "test"
   workplace_search:
     access_token: "ACCESS_TOKEN"
     key: "KEY"
     server: "http://wpsearch.mycompany.com:3002"

Running on Cloud
^^^^^^^^^^^^^^^^

The easiest way to get started is to deploy Enterprise Search on
`Elastic Cloud Service <https://www.elastic.co/workplace-search>`_.

Then you can define the following:

.. code:: yaml

   name: "test"
   elasticsearch:
     username: "elastic"
     password: "PASSWORD"
     nodes:
     - cloud_id: "CLOUD_ID"
   workplace_search:
     access_token: "ACCESS_TOKEN"
     key: "KEY"
     server: "https://XYZ.ent-search.ZONE.CLOUD_PROVIDER.elastic-cloud.com"

.. note::

    Change the ``PASSWORD``, ``CLOUD_ID`` by values coming from the `Elastic Console <https://cloud.elastic.co/deployments/>`_.
    And get the ``ACCESS_TOKEN`` and ``KEY`` from your Enterprise Search deployment once you have created the
    Custom API source as seen previously.

