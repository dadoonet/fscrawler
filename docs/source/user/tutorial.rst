Tutorial
--------

This tutorial use case is:

Search for the resumes (PDF or Word file which resides in One drive or local) and search for anything in the content
using Kibana. For example location worked or the previous company, etc.

Prerequisites
^^^^^^^^^^^^^

* Java 11+ must be installed
* ``JAVA_HOME`` must be defined

Install Elastic stack
^^^^^^^^^^^^^^^^^^^^^

* Download `Elasticsearch <https://www.elastic.co/downloads/elasticsearch>`_
* Download `Kibana <https://www.elastic.co/downloads/kibana>`_
* Start Elasticsearch server
* Start Kibana server
* Check that Kibana is running by opening http://localhost:5601

Start FSCrawler
^^^^^^^^^^^^^^^

* Download FSCrawler. See :ref:`installation`.
* Open a terminal and navigate to the ``fscrawler`` folder.
* Type:

.. code:: sh

   # On Linux/Mac
   bin/fscrawler resumes
   # On Windows
   .\bin\fscrawler resumes

* It will ask "Do you want to create it (Y/N)?". Answer ``Y``.
* Go to the FSCrawler configuration folder to edit the job configuration. The FSCrawler configuration folder named
  ``.fscrawler`` is by default in the user home directory, like ``C:\Users\myuser`` on Windows platform or
  ``~`` on Linux/MacOS. In this folder, you will find another folder named ``resumes``. Enter this folder:

.. code:: sh

   # On Linux/Mac
   cd ~/.fscrawler/resumes
   # On Windows
   cd C:\Users\myuser\.fscrawler\resumes

* Edit the ``_settings.yaml`` file which is in this folder and change the ``url`` value to your folder
  which contains the resumes you would like to index:

.. code:: yaml

   ---
   name: "resumes"
   fs:
     # On Linux
     url: "/path/to/resumes"
     # On Windows
     url: "c:\\path\\to\\resumes"

* Start again FSCrawler:

.. code:: sh

   # On Linux/Mac
   bin/fscrawler resumes
   # On Windows
   .\bin\fscrawler resumes

FSCrawler should index all the documents inside your directory.

.. note::

    If you want to start again reindexing from scratch instead of monitoring the changes, stop FSCrawler, restart it
    with the ``--restart`` option:

    .. code:: sh

       # On Linux/Mac
       bin/fscrawler resumes --restart
       # On Windows
       .\bin\fscrawler resumes --restart

Create Index pattern
^^^^^^^^^^^^^^^^^^^^

* Open `Kibana <http://localhost:5601>`_
* Go to the `Management <http://0.0.0.0:5601/app/kibana#/management/>`_ page
* Open the `Index Patterns <http://0.0.0.0:5601/app/kibana#/management/kibana/index_patterns?_g=()>`_ page
  under Kibana settings.
* Click on ``Create index pattern``
* Type ``resumes`` in the input box. Don't forget to remove the star ``*`` that is automatically added by default
  by Kibana.

.. image:: /_static/tutorial/kibana-step1.jpg

* Choose the date field you'd like to use if you want to be able to filter documents by date. Use
  ``file.created`` field if you want to filter by file creation date, ``file.last_modified`` to filter
  by last modification date or ``file.indexing_date`` if you want to filter by the date when the document
  has been indexed into elasticsearch. You can also choose not to use the time filter (the last option).

.. image:: /_static/tutorial/kibana-step2.jpg

* Click on "Create index pattern". You should see something like:

.. image:: /_static/tutorial/kibana-step3.jpg


Search for the CVs
^^^^^^^^^^^^^^^^^^

* Open `Kibana <http://localhost:5601>`_
* Go to the `Discover <http://0.0.0.0:5601/app/kibana#/discover/>`_ page
* Depending on the date you selected in the `Create Index pattern`_ step, you should see something similar to the
  following image. If you don't see it, you probably have to adjust the time picker to make sure you are looking
  at the right period of time.

.. image:: /_static/tutorial/kibana-step4.jpg

* You can select the fields you'd like to display in the result page, such as ``content``,
  ``file.filename``, ``file.extension``, ``file.url``, ``file.filesize``, etc.

.. image:: /_static/tutorial/kibana-step5.jpg

* Of course, you can search for content, like ``collaborateurs`` here and see the highlighted content.

.. image:: /_static/tutorial/kibana-step6.jpg

Adding new files
^^^^^^^^^^^^^^^^

Just copy new files in the ``resumes`` folder. It could take up to 15 minutes for FSCrawler to
detect the change. This is the default value for ``update_rate`` option. You can also change this
value. See :ref:`local-fs-update_rate`.

.. note::

    On some OS, moving files won't touch the modified date and the "new" files won't be detected.
    It's then better probably to copy the files instead.

    You might have to "touch" the files like:

    .. code:: sh

        touch /path/to/resumes/CV2.pdf

Just hit the Kibana refresh button and see the changes.

.. image:: /_static/tutorial/kibana-step7.jpg


