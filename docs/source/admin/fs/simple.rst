.. _simple_crawler:

The most simple crawler
-----------------------

You can define the most simple crawler job by writing a
``~/.fscrawler/test/_settings.yaml`` file as follow:

.. code:: yaml

   name: "test"

This will scan every 15 minutes all documents available in ``/tmp/es``
dir and will index them into ``test_doc`` index. It will connect to an
elasticsearch cluster running on ``127.0.0.1``, port ``9200``.

**Note**: ``name`` is a mandatory field.

