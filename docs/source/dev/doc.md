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

## LLM-friendly documentation ([llms.txt](https://llmstxt.org/) v2)

The HTML build also produces:

* `llms.txt` — curated index (source: `docs/source/llms-txt.md`) with links to Markdown pages
* `*.html.md` — clean Markdown twin of each HTML page (via [sphinx-llm](https://pypi.org/project/sphinx-llm/))
* `llms-full.txt` — concatenation of those Markdown pages

Each HTML page includes `<link rel="alternate" type="text/markdown">` and
`<link rel="describedby">` pointing at `llms.txt` (see `docs/source/_templates/layout.html`).

When you add an important user-facing page, consider linking it from `docs/source/llms-txt.md`
(and keep the repository-root `llms.txt` in sync with absolute Read the Docs URLs).

Documentation sources are written in [MyST Markdown](https://myst-parser.readthedocs.io/).
Sphinx admonitions (note, warning, deprecated, etc.) use MyST directives such as:

````
```{note}
Your note content here.
```
````

Conditional blocks (`{ifconfig}`) and admonitions that wrap a code sample must use
[colon fences](https://myst-parser.readthedocs.io/en/latest/syntax/optional.html#code-fences)
(`::::{ifconfig}` / `:::{warning}`). A backtick-fenced `{ifconfig}` is closed by the first
nested ` ``` ` of the same length, so the rest of the block leaks into the page.
Do not put Markdown headings inside `{ifconfig}` (they close the directive); use a
`{rubric}` if you need a title in the conditional block.

MyST substitutions such as {{ release }} work in prose only. They are **not** expanded inside
fenced or inline code. For versioned commands, use a Sphinx `{code-block}` with
`:substitutions:` and RST `|name|` placeholders (see `sphinx-substitution-extensions`):

````
```{code-block} sh
:substitutions:

docker pull |docker_image|
wget |downloadUrl|
unzip fscrawler-|release|.zip
```
````

`|release|` is Sphinx's built-in. `|docker_image|`, `|docker_image_noocr|`, `|docker_hub_tag|`,
and `|downloadUrl|` are defined in `docs/source/conf.py`.

To update the requirements file if you changed the `requirements.in` file, run:

```
$ cd docs
$ pip-compile requirements.in
```
