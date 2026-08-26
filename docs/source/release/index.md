# Release notes

It can happen that you need to upgrade a mapping or reindex an entire
index before starting FSCrawler after a version upgrade. Read carefully
the following update instructions.

For a new install of the same major version, download the new ZIP, unzip it in
another directory, stop the running instances, and start 3.0 as usual. It still
reads settings from the configuration directory.

From **2.9 to 3.0**, there is no in-place upgrade. Install 3.0, recreate jobs with
`--setup`, and reindex. See {ref}`upgrade-from-2.9`.
