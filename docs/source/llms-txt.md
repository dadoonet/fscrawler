---
orphan: true
---

# FSCrawler

> FSCrawler is a Java file system crawler that extracts text from binary documents (PDF, MS Office, images with OCR, and more) with Apache Tika and indexes them into Elasticsearch 7.x, 8.x, or 9.x. It supports local filesystem, SSH/FTP, and a REST upload API.

FSCrawler jobs are configured in YAML files (`~/.fscrawler/{job_name}/_settings.yaml`). Each job has a mandatory `name` field. Documents are indexed in Elasticsearch; search uses a job alias named after the job (for example `fscrawler`).

Links below point to the clean Markdown version of each page (`*.html.md`), as recommended by the [llms.txt](https://llmstxt.org/) v2 proposal. HTML pages also expose `rel="alternate" type="text/markdown"` and `rel="describedby"` pointing here.

For copy-paste prompts to use with ChatGPT, Claude, or other assistants, see the LLM assistant page.

## Getting started

- [Installation](https://fscrawler.readthedocs.io/en/latest/installation.html.md): Docker, ZIP distribution, and Docker Compose.
- [Getting Started](https://fscrawler.readthedocs.io/en/latest/user/getting_started.html.md): First run with `--setup`, default paths, and a basic search example.
- [Upgrading from 2.9](https://fscrawler.readthedocs.io/en/latest/user/upgrade.html.md): 3.0 is a fresh install; there is no in-place upgrade from 2.9.
- [Tutorial](https://fscrawler.readthedocs.io/en/latest/user/tutorial.html.md): End-to-end walkthrough with Elastic start-local, Docker, and Kibana.
- [LLM assistant](https://fscrawler.readthedocs.io/en/latest/user/llm-assistant.html.md): Ready-to-copy prompts for configuring FSCrawler with an AI assistant.

## User guide

- [Crawler options](https://fscrawler.readthedocs.io/en/latest/user/options.html.md): Update rate, `--loop`, and `--restart`.
- [Supported formats](https://fscrawler.readthedocs.io/en/latest/user/formats.html.md): Document types handled by Apache Tika.
- [OCR](https://fscrawler.readthedocs.io/en/latest/user/ocr.html.md): Optical character recognition (Tesseract) and optional VLM OCR.
- [REST API](https://fscrawler.readthedocs.io/en/latest/user/rest.html.md): Upload binary documents over HTTP; fetch from HTTP or S3.
- [Tips and tricks](https://fscrawler.readthedocs.io/en/latest/user/tips.html.md): Docker, multi-machine setups, and common workarounds.

## Configuration reference

- [Local filesystem](https://fscrawler.readthedocs.io/en/latest/admin/fs/local-fs.html.md): Includes, excludes, checksums, and `.fscrawlerignore`.
- [SSH](https://fscrawler.readthedocs.io/en/latest/admin/fs/ssh.html.md): Remote crawling over SSH/SFTP.
- [FTP](https://fscrawler.readthedocs.io/en/latest/admin/fs/ftp.html.md): Remote crawling over FTP.
- [Elasticsearch settings](https://fscrawler.readthedocs.io/en/latest/admin/fs/elasticsearch.html.md): URLs, API key, SSL, indices, bulk settings, semantic search.
- [Kibana settings](https://fscrawler.readthedocs.io/en/latest/admin/fs/kibana.html.md): Default dashboard on job startup (Kibana 9.5+).
- [Password-protected documents](https://fscrawler.readthedocs.io/en/latest/admin/fs/passwords.html.md): Encrypted PDF and Office files.
- [Document IDs](https://fscrawler.readthedocs.io/en/latest/admin/fs/document-ids.html.md): How document identifiers are built.
- [Tags and metadata](https://fscrawler.readthedocs.io/en/latest/admin/fs/tags.html.md): Custom fields on indexed documents.
- [REST server settings](https://fscrawler.readthedocs.io/en/latest/admin/fs/rest.html.md): Embedded REST API configuration.
- [CLI options](https://fscrawler.readthedocs.io/en/latest/admin/cli-options.html.md): Command-line flags and environment variables.

## Common pitfalls

- There is no in-place upgrade from 2.9: install 3.0, recreate jobs with `--setup`, and reindex.
- Use `elasticsearch.urls` (a list), not `nodes`. Unknown keys are silently ignored.
- With Elastic start-local, use `http://` (not `https://`) for Elasticsearch.
- Do not set `elasticsearch.index` to the same value as the job `name`; FSCrawler creates an alias with the job name.
- In Docker, `127.0.0.1` inside the container is not the host; use `host.docker.internal` or the host IP.
- The job `name` field is mandatory in every `_settings.yaml`.

## Optional

- [GitHub repository](https://github.com/dadoonet/fscrawler): Source code, issues, and releases.
- [Release notes (3.1)](https://fscrawler.readthedocs.io/en/latest/release/3.1.html.md): Changes in FSCrawler 3.1.
- [Release notes (3.0)](https://fscrawler.readthedocs.io/en/latest/release/3.0.html.md): Changes in FSCrawler 3.0.
- [Full documentation dump](https://fscrawler.readthedocs.io/en/latest/llms-full.txt): Concatenated Markdown of all pages (large).
