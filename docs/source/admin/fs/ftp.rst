.. _ftp-settings:

FTP settings
------------

You can index files remotely using FTP.

Here is a list of FTP settings (under ``server.`` prefix):

+-----------------------+-----------------------+-----------------------+
| Name                  | Default value         | Documentation         |
+=======================+=======================+=======================+
| ``server.hostname``   | ``null``              | Hostname              |
+-----------------------+-----------------------+-----------------------+
| ``server.port``       | ``21``                | Port                  |
+-----------------------+-----------------------+-----------------------+
| ``server.username``   | ``anonymous``         | :ref:`ftp_login`      |
+-----------------------+-----------------------+-----------------------+
| ``server.password``   | ``null``              | :ref:`ftp_login`      |
+-----------------------+-----------------------+-----------------------+
| ``server.protocol``   | ``"local"``           | Set it to ``ftp``     |
+-----------------------+-----------------------+-----------------------+

.. _ftp_login:

Username / Password
~~~~~~~~~~~~~~~~~~~

Let’s say you want to index from a remote server using FTP:

-  FS URL: ``/path/to/data/dir/on/server``
-  Server: ``mynode.mydomain.com``
-  Username: ``username`` (default to ``anonymous``)
-  Password: ``password``
-  Protocol: ``ftp`` (default to ``local``)
-  Port: ``21`` (default to ``21``)

.. code:: yaml

   name: "test"
   fs:
     url: "/path/to/data/dir/on/server"
   server:
     hostname: "mynode.mydomain.com"
     port: 21
     username: "username"
     password: "password"
     protocol: "ftp"
