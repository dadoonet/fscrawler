(llm-assistant)=
# Using an LLM to configure FSCrawler

You can use ChatGPT, Claude, Gemini, or any other AI assistant to help you write or troubleshoot FSCrawler job settings.
Copy one of the prompts below into your assistant, then describe your use case.

```{tip}
FSCrawler follows the [llms.txt](https://llmstxt.org/) v2 proposal:

* Canonical index on the docs site: [llms.txt](https://fscrawler.readthedocs.io/en/latest/llms.txt)
  (also [mirrored at the repository root](https://github.com/dadoonet/fscrawler/blob/main/llms.txt)).
* Every HTML page has a clean Markdown twin at the same URL with `.md` appended
  (for example [tutorial.html.md](https://fscrawler.readthedocs.io/en/latest/user/tutorial.html.md)).
* HTML pages advertise that twin with `rel="alternate" type="text/markdown"` and point to
  `llms.txt` with `rel="describedby"`.

Prefer the `.html.md` links (or the alternate link) when giving a page to an LLM — they avoid
navigation chrome and are much cheaper in tokens.
```

## Before you ask

Include as much context as you can in your follow-up message:

- How you run FSCrawler (Docker, ZIP, Docker Compose)
- How Elasticsearch is deployed (Elastic Cloud, start-local, self-managed cluster) and its version
- Where your files live (local path, SSH, FTP)
- Whether you need OCR, password-protected documents, or the REST upload API

## Starter prompt

Copy everything inside the block below and paste it into your LLM. Then add your question at the end.

````
You are helping me configure FSCrawler, a Java file system crawler that extracts text from binary documents (PDF, MS Office, images, etc.) with Apache Tika and indexes them into Elasticsearch 7.x, 8.x, or 9.x.

Official documentation: https://fscrawler.readthedocs.io/en/latest/
LLM-friendly index (llms.txt): https://fscrawler.readthedocs.io/en/latest/llms.txt
Prefer documentation URLs ending in `.html.md` (clean Markdown) over `.html` when fetching pages.

Key concepts:
- Each FSCrawler "job" has a YAML settings file at ~/.fscrawler/{job_name}/_settings.yaml (or /root/.fscrawler in Docker).
- The `name` field is mandatory and defines the job name and the Elasticsearch alias used for search.
- By default, FSCrawler watches /tmp/es every 15 minutes and indexes documents into {name}_docs and folders into {name}_folder.
- Run once and exit with: bin/fscrawler {job_name} --loop 1
- First-time setup: bin/fscrawler --setup

Minimal settings example:
```yaml
name: "myjob"
fs:
  url: "/path/to/documents"
  update_rate: "15m"
elasticsearch:
  urls:
    - "http://localhost:9200"
  api_key: "your-api-key"
```

Docker quick start (mount config and documents):
```sh
docker run -it --rm \
  --add-host=host.docker.internal:host-gateway \
  -v ~/.fscrawler:/root/.fscrawler \
  -v /path/to/docs:/tmp/es:ro \
  -e FSCRAWLER_ELASTICSEARCH_URLS=http://host.docker.internal:9200 \
  -e FSCRAWLER_ELASTICSEARCH_API_KEY="your-api-key" \
  dadoonet/fscrawler{{ docker_image_tag }}
```

Important rules — do not get these wrong:
1. Use `elasticsearch.urls` (a YAML list). The old `nodes` key is not supported; unknown keys are silently ignored.
2. With Elastic start-local, use http:// (not https://) — start-local exposes HTTP on localhost only.
3. Do not set `elasticsearch.index` to the same value as `name`. FSCrawler creates a search alias named after the job; a conflicting index name causes "alias name self-conflicts with index name".
4. There is no in-place upgrade from FSCrawler 2.9. Recreate jobs with `--setup` and reindex.
5. From 3.0 to 3.1, existing jobs keep working. Rewrite SSH/FTP connection settings from the top-level `server.*` block to `fs.provider` plus `fs.providers.ssh` / `fs.providers.ftp`. Keep `fs.url` as the crawl root. Do not keep `server.*` in the rewritten file.
6. In Docker, 127.0.0.1 is the container itself. Use host.docker.internal (Linux: add --add-host=host.docker.internal:host-gateway) or the host machine IP to reach Elasticsearch on the host.
7. Environment variables follow the pattern FSCRAWLER_* (see CLI options in the docs). SSH/FTP keys are FSCRAWLER_FS_PROVIDERS_SSH_* / FSCRAWLER_FS_PROVIDERS_FTP_*.

3.0 → 3.1 SSH/FTP rewrite (same field names: hostname, port, username, password, pem_path):
- `server.protocol: ssh` (or ftp) → `fs.provider: ssh` (or ftp). Prefer `fs.provider` if both exist.
- `server.hostname` / `port` / `username` / `password` / `pem_path` → `fs.providers.<ssh|ftp>.*`
- Remove the top-level `server:` block after the rewrite.
- REST `_document` payloads stay `{ "type": "ssh", "ssh": { ... } }` (not `fs.providers`).

Useful documentation pages (Markdown versions for LLMs):
- Tutorial (start-local + Docker + Kibana): https://fscrawler.readthedocs.io/en/latest/user/tutorial.html.md
- Elasticsearch settings: https://fscrawler.readthedocs.io/en/latest/admin/fs/elasticsearch.html.md
- Local filesystem settings: https://fscrawler.readthedocs.io/en/latest/admin/fs/local-fs.html.md
- SSH remote crawling: https://fscrawler.readthedocs.io/en/latest/admin/fs/ssh.html.md
- FTP remote crawling: https://fscrawler.readthedocs.io/en/latest/admin/fs/ftp.html.md
- Release notes 3.1: https://fscrawler.readthedocs.io/en/latest/release/3.1.html.md
- Docker installation: https://fscrawler.readthedocs.io/en/latest/installation.html.md

When answering:
- Propose a complete _settings.yaml tailored to my situation.
- If I paste an existing job file, return a complete rewritten `_settings.yaml` for 3.1 and a short list of what changed.
- Explain each non-obvious setting you add.
- Warn me about the pitfalls listed above when relevant.
- If I use Docker, include the docker run command with the right volume mounts and environment variables.
- If something is unclear, ask one focused question before guessing.

My situation:
[PASTE YOUR CONTEXT HERE — deployment method, ES version/URL, document source, constraints]
````

## Scenario prompts

Use these shorter prompts when you already know what you want to set up.

### Index local files with Elastic start-local

````
Help me configure FSCrawler to index PDF and Word files from ~/Documents/resumes into Elasticsearch started with Elastic's start-local script (http://localhost:9200, API key in elastic-start-local/.env). I want to run FSCrawler with Docker. Give me the docker run command and the _settings.yaml content. Remember: use elasticsearch.urls with http://, not nodes, and do not set elasticsearch.index to the job name.
````

### Crawl a remote server over SSH

````
Help me configure FSCrawler to crawl documents on a remote Linux server over SSH/SFTP and index them into Elasticsearch 8.x. My Elasticsearch URL is https://my-es.example.com:9200 and I authenticate with an API key. Give me the complete _settings.yaml with fs.provider: ssh, fs.url, fs.providers.ssh (hostname, port, username, password or pem_path), and elasticsearch settings. Use elasticsearch.urls (list), not nodes. Do not use the deprecated top-level server block.
````

### Migrate job settings from 3.0 to 3.1

Paste the starter prompt first, then this question with your current `_settings.yaml`:

````
I am using FSCrawler 3.0 with the following `_settings.yaml`:

[PASTE YOUR CURRENT _settings.yaml HERE]

Can you migrate these settings to FSCrawler 3.1? Move any top-level `server.*` SSH/FTP settings to `fs.provider` and `fs.providers.ssh` or `fs.providers.ftp`, keep `fs.url` as the crawl root, keep `elasticsearch.urls`, and do not set `elasticsearch.index` to the job name. Give me the complete rewritten `_settings.yaml` and a short list of what changed.
````

### Troubleshoot connection errors

````
I'm getting connection errors when FSCrawler tries to reach Elasticsearch. Here is my _settings.yaml and the error message:

[PASTE YOUR _settings.yaml AND ERROR LOG HERE]

Help me diagnose the issue. Common causes I know about: wrong protocol (http vs https), using 127.0.0.1 inside Docker, invalid elasticsearch.urls key, or elasticsearch.index conflicting with the job name.
````

### Search indexed documents in Kibana

````
FSCrawler has indexed my documents. The job name is "resumes" so the search alias is "resumes". Show me ES|QL queries in Kibana to count documents, search for text in the content field, and group by file.content_type. Documentation: https://fscrawler.readthedocs.io/en/latest/user/tutorial.html
````

## What not to expect

These prompts help with **configuration and operations**. They do not enable built-in LLM features inside FSCrawler itself (such as automatic summarisation or tagging of documents). That is tracked separately as a feature request on GitHub.
