.. _ssh-settings:

SSH settings
------------

You can index files remotely using SSH.

.. contents:: :backlinks: entry

Here is a list of SSH settings (under ``server.`` prefix):

+-----------------------+-----------------------+-----------------------+
| Name                  | Default value         | Documentation         |
+=======================+=======================+=======================+
| ``server.hostname``   | ``null``              | Hostname              |
+-----------------------+-----------------------+-----------------------+
| ``server.port``       | ``22``                | Port                  |
+-----------------------+-----------------------+-----------------------+
| ``server.username``   | ``null``              | :ref:`ssh_login`      |
+-----------------------+-----------------------+-----------------------+
| ``server.password``   | ``null``              | :ref:`ssh_login`      |
+-----------------------+-----------------------+-----------------------+
| ``server.protocol``   | ``"local"``           | Set it to ``ssh``     |
+-----------------------+-----------------------+-----------------------+
| ``server.pem_path``   | ``null``              | :ref:`ssh_pem`        |
+-----------------------+-----------------------+-----------------------+

.. _ssh_login:

Username / Password
~~~~~~~~~~~~~~~~~~~

Let’s say you want to index from a remote server using SSH:

-  FS URL: ``/path/to/data/dir/on/server``
-  Server: ``mynode.mydomain.com``
-  Username: ``username``
-  Password: ``password``
-  Protocol: ``ssh`` (default to ``local``)
-  Port: ``22`` (default to ``22``)

.. code:: yaml

   name: "test"
   fs:
     url: "/path/to/data/dir/on/server"
   server:
     hostname: "mynode.mydomain.com"
     port: 22
     username: "username"
     password: "password"
     protocol: "ssh"

.. _ssh_pem:

Using Username / PEM file
~~~~~~~~~~~~~~~~~~~~~~~~~

Let’s say you want to index from a remote server using SSH:

-  FS URL: ``/path/to/data/dir/on/server``
-  Server: ``mynode.mydomain.com``
-  Username: ``username``
-  PEM File: ``/path/to/private_key.pem``
-  Protocol: ``ssh`` (default to ``local``)
-  Port: ``22`` (default to ``22``)

.. code:: yaml

   name: "test"
   fs:
     url: "/path/to/data/dir/on/server"
   server:
     hostname: "mynode.mydomain.com"
     port: 22
     username: "username"
     password: "password"
     protocol: "ssh"
     pem_path: "/path/to/private_key.pem"

Windows drives
~~~~~~~~~~~~~~

When using Windows, you might want to index documents coming from another drive than ``C:``.
To specify the drive, you need to use the following format:

.. code:: yaml

   name: "test"
   fs:
     url: "/D:/path/to/data/dir/on/server"
   server:
     hostname: "mynode.mydomain.com"
     port: 22
     username: "username"
     password: "password"
     protocol: "ssh"
