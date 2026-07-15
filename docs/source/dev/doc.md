# Writing documentation

This project uses [ReadTheDocs](https://readthedocs.org/) to build and serve the documentation.

If you want to run the generation of documentation (recommended!), you need
to have Python3 installed.

Assuming you have [Python3](https://www.python.org/) already, install [Sphinx](http://www.sphinx-doc.org/):

```
$ pip install -r docs/requirements.txt
```

Go to the `docs` directory and build the html documentation:

```
$ cd docs
$ make html
```

Just open then `target/html/index.html` page in your browser.

```{hint}
You can hot reload your changes by using `sphinx-autobuild`:

```
$ sphinx-autobuild source target/html
```

Then just edit the documentation and look for your changes at http://127.0.0.1:8000
```

Documentation sources are written in [MyST Markdown](https://myst-parser.readthedocs.io/).
Sphinx admonitions (note, warning, deprecated, etc.) use MyST directives such as:

````
```{note}
Your note content here.
```
````

To update the requirements file if you changed the `requirements.in` file, run:

```
$ cd docs
$ pip-compile requirements.in
```
