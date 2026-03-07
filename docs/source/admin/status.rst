.. _status-files:

Checkpoint file
===============

Once the crawler is running, it will write checkpoint information and
statistics in:

-  ``~/.fscrawler/{job_name}/_checkpoint.json``

The checkpoint file serves multiple purposes:

1. **Progress tracking**: It tracks the current state of the scan, including which directories have been processed and which are pending.
2. **Resume capability**: If FSCrawler is stopped or crashes during a scan, it will automatically resume from where it left off when restarted.
3. **Scan history**: After a successful scan, the checkpoint stores when the last scan completed (``scan_end_time``) and when the next scan should run (``next_check``).

The information in this file includes:

- ``scan_id``: unique identifier for the current scan session
- ``state``: current state (``RUNNING``, ``PAUSED``, ``STOPPED``, ``COMPLETED``, ``ERROR``)
- ``scan_start_time``: when the current/last scan started
- ``scan_end_time``: when the last scan completed (only for ``COMPLETED`` state)
- ``next_check``: next time the job will be checked for new files
- ``current_path``: the directory currently being processed
- ``pending_paths``: directories waiting to be processed
- ``completed_paths``: directories that have been fully processed
- ``files_processed``: total number of files indexed during the scan
- ``files_deleted``: total number of files removed during the scan
- ``retry_count``: number of retry attempts after network errors
- ``last_error``: last error message encountered (if any)

For example, a checkpoint for a completed scan:

.. code-block:: json

   {
     "state": "COMPLETED",
     "scan_end_time": "2025-07-01T12:00:00",
     "next_check": "2025-07-01T12:15:00",
     "files_processed": 100,
     "files_deleted": 0
   }

A checkpoint for a running scan:

.. code-block:: json

   {
     "scan_id": "abc123-def456",
     "state": "RUNNING",
     "scan_start_time": "2025-07-01T12:00:00",
     "current_path": "/data/documents/subfolder",
     "pending_paths": ["/data/documents/other"],
     "completed_paths": ["/data/documents", "/data/documents/processed"],
     "files_processed": 50,
     "files_deleted": 0,
     "retry_count": 0
   }

Forcing a new scan
------------------

If you don't want to wait for the next scheduled scan, you can manually edit the 
``~/.fscrawler/{job_name}/_checkpoint.json`` file and set ``next_check`` to the 
current time or to ``null``. FSCrawler will then start a new scan at most after 5 seconds.

You can also use the REST API to check the status and force a scan by clearing the checkpoint.
See :ref:`rest-service` for more details.

Migration from previous versions
--------------------------------

.. note::

   In versions prior to 2.10, FSCrawler used a ``_status.json`` file to store scan information.
   When upgrading to 2.10 or later, FSCrawler will automatically migrate the data from 
   ``_status.json`` to the new ``_checkpoint.json`` format. The old ``_status.json`` file 
   will be removed after successful migration.
