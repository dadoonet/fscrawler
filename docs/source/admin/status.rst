.. _status-files:

Status files
============

Once the crawler is running, it will write status information and
statistics in:

-  ``~/.fscrawler/{job_name}/_status.json``

It means that if you stop the job at some point (after a full run), FSCrawler will restart
it from where it stops.

The information in this file is:

- ``name``: the name of the job
- ``lastrun``: start time of the job
- ``next_check``: next time the job will be checked for new files
- ``indexed``: total number of files indexed on the last run
- ``deleted``: total number of files removed on the last run

For example:

.. code-block:: json

   {
     "name": "fscrawler",
     "lastrun": "2025-07-01T12:00:00Z",
     "next_check": "2025-07-01T12:15:00Z",
     "indexed": 100,
     "deleted": 0
   }

If you don't want to wait for the next scan, you can manually edit the ``~/.fscrawler/test/_status.json`` file and
set ``next_check`` to the current time or to ``null``. FSCrawler will then start a new scan at most after 5 seconds.
