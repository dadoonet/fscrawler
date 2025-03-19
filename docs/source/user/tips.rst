Tips and tricks
===============

Moving files to a “watched” directory
-------------------------------------

When moving an existing file to the directory FSCrawler is watching, you
need to explicitly ``touch`` all the files as when moved, the files are
keeping their original date intact:

.. code:: sh

   # single file
   touch file_you_moved

   # all files
   find  -type f  -exec touch {} +

   # all .txt files
   find  -type f  -name "*.txt" -exec touch {} +

Or you need to :ref:`restart <cli-options>` from the
beginning with the ``--restart`` option which will reindex everything.

Workaround for huge temporary files
-----------------------------------

fscrawler uses a media library that currently does not clean up their temporary files.
Parsing MP4 files may create very large temporary files in /tmp.
The following commands could be useful e.g. as a cronjob to automatically delete those files once they are old and no longer in use.
Adapt the commands as needed.

.. code:: sh

   # Check all files in /tmp
   find /tmp \( -name 'apache-tika-*.tmp-*' -o -name 'MediaDataBox*' \) -type f -mmin +15 ! -exec fuser -s {} \; -delete

   # When using a systemd service with PrivateTMP enabled
   find $(find /tmp -maxdepth 1 -type d -name 'systemd-private-*-fscrawler.service-*') \( -name 'apache-tika-*.tmp-*' -o -name 'MediaDataBox*' \) -type f -mmin +15 ! -exec fuser -s {} \; -delete


Indexing from HDFS drive
------------------------

There is no specific support for HDFS in FSCrawler. But you can `mount
your HDFS on your
machine <https://wiki.apache.org/hadoop/MountableHDFS>`__ and run FS
crawler on this mount point. You can also read details about `HDFS NFS
Gateway <http://hadoop.apache.org/docs/stable/hadoop-project-dist/hadoop-hdfs/HdfsNfsGateway.html>`__.

Using docker
------------

See :ref:`docker`.
