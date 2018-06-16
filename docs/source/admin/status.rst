Status files
------------

Once the crawler is running, it will write status information and
statistics in:

-  ``~/.fscrawler/{job_name}/_settings.json``
-  ``~/.fscrawler/{job_name}/_status.json``

It means that if you stop the job at some point, FSCrawler will restart
it from where it stops.

FSCrawler will also store default mappings and index settings for
elasticsearch in ``~/.fscrawler/_default/_mappings``:

-  ``1/_settings.json``: for elasticsearch 1.x series document index
   settings
-  ``1/_settings_folder.json``: for elasticsearch 1.x series folder
   index settings
-  ``2/_settings.json``: for elasticsearch 2.x series document index
   settings
-  ``2/_settings_folder.json``: for elasticsearch 2.x series folder
   index settings
-  ``5/_settings.json``: for elasticsearch 5.x series document index
   settings
-  ``5/_settings_folder.json``: for elasticsearch 5.x series folder
   index settings
-  ``6/_settings.json``: for elasticsearch 6.x series document index
   settings
-  ``6/_settings_folder.json``: for elasticsearch 6.x series folder
   index settings

Read :ref:`mappings` section for more information.

