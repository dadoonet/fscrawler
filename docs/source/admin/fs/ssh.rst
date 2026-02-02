.. _ssh-settings:

SSH settings
------------

You can index files remotely using SSH/SFTP.

.. contents:: :backlinks: entry

Here is a list of SSH settings:

+-----------------------+-----------------------+-----------------------------------------+
| Name                  | Default value         | Documentation                           |
+=======================+=======================+=========================================+
| ``fs.provider``       | ``"local"``           | Set it to ``"ssh"`` for SSH crawling    |
+-----------------------+-----------------------+-----------------------------------------+
| ``server.hostname``   | ``null``              | Hostname                                |
+-----------------------+-----------------------+-----------------------------------------+
| ``server.port``       | ``22``                | Port                                    |
+-----------------------+-----------------------+-----------------------------------------+
| ``server.username``   | ``null``              | :ref:`ssh_login`                        |
+-----------------------+-----------------------+-----------------------------------------+
| ``server.password``   | ``null``              | :ref:`ssh_login`                        |
+-----------------------+-----------------------+-----------------------------------------+
| ``server.pem_path``   | ``null``              | :ref:`ssh_pem`                          |
+-----------------------+-----------------------+-----------------------------------------+

.. deprecated:: 2.10

   The ``server.protocol`` setting is deprecated. Use ``fs.provider`` instead.

.. _ssh_login:

Username / Password
~~~~~~~~~~~~~~~~~~~

Let's say you want to index from a remote server using SSH:

-  FS URL: ``/path/to/data/dir/on/server``
-  Server: ``mynode.mydomain.com``
-  Username: ``username``
-  Password: ``password``
-  Provider: ``ssh``
-  Port: ``22`` (default to ``22``)

.. code:: yaml

   name: "test"
   fs:
     provider: "ssh"
     url: "/path/to/data/dir/on/server"
   server:
     hostname: "mynode.mydomain.com"
     port: 22
     username: "username"
     password: "password"

.. _ssh_pem:

Using Username / PEM file
~~~~~~~~~~~~~~~~~~~~~~~~~

Let's say you want to index from a remote server using SSH with a PEM key file:

-  FS URL: ``/path/to/data/dir/on/server``
-  Server: ``mynode.mydomain.com``
-  Username: ``username``
-  PEM File: ``/path/to/private_key.pem``
-  Provider: ``ssh``
-  Port: ``22`` (default to ``22``)

.. code:: yaml

   name: "test"
   fs:
     provider: "ssh"
     url: "/path/to/data/dir/on/server"
   server:
     hostname: "mynode.mydomain.com"
     port: 22
     username: "username"
     pem_path: "/path/to/private_key.pem"

Windows drives
~~~~~~~~~~~~~~

When using Windows, you might want to index documents coming from another drive than ``C:``.
To specify the drive, you need to use the following format:

.. code:: yaml

   name: "test"
   fs:
     provider: "ssh"
     url: "/D:/path/to/data/dir/on/server"
   server:
     hostname: "mynode.mydomain.com"
     port: 22
     username: "username"
     password: "password"
