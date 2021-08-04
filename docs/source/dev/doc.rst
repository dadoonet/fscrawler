Writing documentation
---------------------

This project uses `ReadTheDocs <https://readthedocs.org/>`_ to build and serve the documentation.

If you want to run the generation of documentation (recommended!), you need
to have Python3 installed.

Assuming you have `Python3 <https://www.python.org/>`_ already, install `Sphinx <http://www.sphinx-doc.org/>`_::

    $ pip install sphinx sphinx-autobuild sphinx_rtd_theme recommonmark

Go to the ``docs`` directory and build the html documentation::

    $ cd docs
    $ make html

Just open then ``target/html/index.html`` page in your browser.

.. hint:: You can hot reload your changes by using ``sphinx-autobuild``::

    $ sphinx-autobuild source target/html

Then just edit the documentation and look for your changes at http://127.0.0.1:8000

To learn more about the reStructuredText format, please look at the
`basic guide <http://www.sphinx-doc.org/en/master/usage/restructuredtext/basics.html>`_.
