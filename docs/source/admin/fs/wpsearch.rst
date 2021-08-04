.. _wpsearch-settings:

Workplace Search settings
-------------------------

.. versionadded:: 2.7

FSCrawler can now send documents to `Workplace Search <https://www.elastic.co/workplace-search>`_.

.. contents:: :backlinks: entry

.. note::

    Although this won't be needed in the future, it is still mandatory to have access to the elasticsearch
    instance running behind Workplace Search. In this section of the documentation, we will only cover the
    specifics for workplace search. Please refer to :ref:`elasticsearch-settings` chapter.

.. hint::

    To easily start locally with Workplace Search, follow the steps::

        git clone git@github.com:dadoonet/fscrawler.git
        cd fscrawler
        cd contrib/docker-compose-workplacesearch
        docker-compose up

    This will start Elasticsearch, Kibana and Workplace Search. Wait for it to start.
    http://0.0.0.0:5601/app/enterprise_search/workplace_search must be available before continuing.

Here is a list of Workplace Search settings (under ``workplace_search.`` prefix):

+-------------------------------------+--------------------------------+---------------------------------+
| Name                                | Default value                  | Documentation                   |
+=====================================+================================+=================================+
| ``workplace_search.id``             | None                           | `Custom Source ID`_             |
+-------------------------------------+--------------------------------+---------------------------------+
| ``workplace_search.name``           | Local files for job + Job Name | `Custom Source Name`_           |
+-------------------------------------+--------------------------------+---------------------------------+
| ``workplace_search.username``       | same as for elasticsearch      | `Secrets`_                      |
+-------------------------------------+--------------------------------+---------------------------------+
| ``workplace_search.password``       | same as for elasticsearch      | `Secrets`_                      |
+-------------------------------------+--------------------------------+---------------------------------+
| ``workplace_search.server``         | ``http://127.0.0.1:3002``      | `Server`_                       |
+-------------------------------------+--------------------------------+---------------------------------+
| ``workplace_search.bulk_size``      | ``100``                        | `Bulk settings`_                |
+-------------------------------------+--------------------------------+---------------------------------+
| ``workplace_search.flush_interval`` | ``"5s"``                       | `Bulk settings`_                |
+-------------------------------------+--------------------------------+---------------------------------+
| ``workplace_search.url_prefix``     | ``http://127.0.0.1``           | `Documents Repository URL`_     |
+-------------------------------------+--------------------------------+---------------------------------+

.. note::

    At least, one of the settings under ``workplace_search.`` prefix must be set if you want to activate
    the Workplace Search output. Otherwise, it will use Elasticsearch as the output.

Secrets
^^^^^^^

FSCrawler is using the username/password capabilities of the Workplace Search API.
The default values are the ones you defined in Elasticsearch configuration (see :ref:`elasticsearch-settings`).
So the following settings will just work:

.. code:: yaml

   name: "test"
   elasticsearch:
     username: "elastic"
     password: "PASSWORD"
   workplace_search:
     name: "My fancy custom source name"

But if you want to create another user (recommended) for FSCrawler like ``fscrawler``, you can define it as follows:

.. code:: yaml

   name: "test"
   elasticsearch:
     username: "elastic"
     password: "PASSWORD"
   workplace_search:
     username: "fscrawler"
     password: "FSCRAWLER_PASSWORD"

Custom Source Management
^^^^^^^^^^^^^^^^^^^^^^^^

When starting, FSCrawler will check if a Custom Source already exists with the name that you used for the job.

Custom Source ID
~~~~~~~~~~~~~~~~

When a Custom Source is found with the same name, the ``KEY`` of the Custom Source is automatically fetched and applied
to the workplace search job settings.

If you already have defined a Custom API in `Workplace Search Admin UI <http://0.0.0.0:5601/app/enterprise_search/workplace_search>`
and have the ``KEY``, you can add it to your existing FSCrawler configuration file:

.. code:: yaml

   name: "test"
   elasticsearch:
     username: "elastic"
     password: "PASSWORD"
   workplace_search:
     id: "KEY"

.. tip::
    If you let FSCrawler creates the Custom Source for you, it is recommended to manually edit the job settings
    and provide the ``workplace_search.id``. So if you rename the Custom Source, FSCrawler won't try to create it again.

Custom Source Name
~~~~~~~~~~~~~~~~~~

You can specify the custom source name you want to use when FSCrawler creates it automatically:

.. code:: yaml

   name: "test"
   elasticsearch:
     username: "elastic"
     password: "PASSWORD"
   workplace_search:
     name: "My fancy custom source name"

.. tip::

    By default, FSCrawler will use as the name ``Local files for JOB_NAME`` where ``JOB_NAME`` is
    the FSCrawler ``name`` setting value. So the following job settings:

    .. code:: yaml

       name: "test"
       elasticsearch:
         username: "elastic"
         password: "PASSWORD"
       workplace_search:
         username: "fscrawler"
         password: "FSCRAWLER_PASSWORD"

    will use ``Local files for test`` as the Custom Source name in Workplace Search.

Automatic Custom Source Creation
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

If the Custom Source id is not provided and no Custom Source exists with the same name, it will create automatically
the Custom Source for you with all the default settings, which are read from
``~/.fscrawler/_default/7/_wpsearch_settings.json``. You can read its content from
`the source <https://github.com/dadoonet/fscrawler/blob/master/settings/src/main/resources/fr/pilato/elasticsearch/crawler/fs/_default/7/_wpsearch_settings.json>`__.

If you want to define your own settings, you can either define your own Custom Source using the Workplace Search
Administration UI or define a ``~/.fscrawler/_default/7/_wpsearch_settings.json`` document
which contains the settings you wish **before starting FSCrawler**.
See `Workplace Search documentation <https://www.elastic.co/guide/en/workplace-search/current/workplace-search-content-sources-api.html#create-content-source-api>`__
for more details.

Define explicit settings per job
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Let’s say you created a job named ``job_name`` and you are sending
documents against a workplace search instance running version ``7.x``.

If you create the following file, it will be picked up at job start
time instead of the default ones:

-  ``~/.fscrawler/{job_name}/_mappings/7/_wpsearch_settings.json``

Server
^^^^^^

When using Workplace Search, FSCrawler will by default connect to ``http://127.0.0.1:3002``
which is the default when running a local node on your machine.

Of course, in production, you would probably change this and connect to
a production cluster:

.. code:: yaml

   name: "test"
   elasticsearch:
     username: "elastic"
     password: "PASSWORD"
   workplace_search:
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
     server: "URL"

.. note::

    Change the ``PASSWORD``, ``CLOUD_ID`` and ``URL`` by values coming from the `Elastic Console <https://cloud.elastic.co/deployments/>`_.
    ``URL`` is something like ``https://XYZ.ent-search.ZONE.CLOUD_PROVIDER.elastic-cloud.com``.

Bulk settings
^^^^^^^^^^^^^

FSCrawler is using bulks to send data to Workplace Search. By default the
bulk is executed every 100 operations or every 5 seconds. You can change
default settings using ``workplace_search.bulk_size`` and ``workplace_search.flush_interval``:

.. code:: yaml

  name: "test"
   elasticsearch:
     username: "elastic"
     password: "PASSWORD"
  workplace_search:
    bulk_size: 1000
    flush_interval: "2s"


Documents Repository URL
^^^^^^^^^^^^^^^^^^^^^^^^

The URL that will be used to give access to your users to the source document is
prefixed by default with ``http://127.0.0.1``. That means that if you are able to run
a Web Server locally which can serve the directory you defined in ``fs.url`` setting
(see :ref:`root-directory`), your users will be able to click in the Workplace Search interface
to have access to the documents.

Of course, in production, you would probably change this and connect to
another url. This can be done by changing the ``workplace_search.url_prefix`` setting:

.. code:: yaml

   name: "test"
   elasticsearch:
     username: "elastic"
     password: "PASSWORD"
   workplace_search:
     url_prefix: "https://repository.mycompany.com/docs"

.. note::

    If ``fs.url`` is set to ``/tmp/es`` and you have indexed a document named
    ``/tmp/es/path/to/foobar.txt``, the default url will be ``http://127.0.0.1/path/to/foobar.txt``.

    If you change ``workplace_search.url_prefix`` to ``https://repository.mycompany.com/docs``, the
    same document will be served as ``https://repository.mycompany.com/docs/path/to/foobar.txt``.
