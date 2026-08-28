# File System Crawler for Elasticsearch

Welcome to FSCrawler for [Elasticsearch](https://elastic.co/)

This crawler helps to index binary documents such as PDF, Open Office, MS Office.

![FSCrawler Explained - Generated with Gemini](fscrawler-explained.png)

**Main features**:

* Local file system (or a mounted drive) crawling and index new files, update existing ones and removes old ones.
* Remote file system over SSH/FTP crawling.
* REST interface to let you "upload" your binary documents to elasticsearch.

## Latest versions

Current versions are:

<!-- release-versions:start -->
| Elasticsearch | FSCrawler    | Released   | Docs                                                            |
|---------------|--------------|------------|-----------------------------------------------------------------|
| 6.x, 7.x      | 2.9          | 2022-03-08 | [2.9](https://fscrawler.readthedocs.io/en/fscrawler-2.9/)       |
| 7.x, 8.x, 9.x | 3.0          | 2026-08-26 | [3.0](https://fscrawler.readthedocs.io/en/fscrawler-3.0/)       |
| 8.x, 9.x      | 3.1-SNAPSHOT |            | [3.1-SNAPSHOT](https://fscrawler.readthedocs.io/en/latest/)     |
<!-- release-versions:end -->

## Quick start

Run Elasticsearch with `start-local`:

```sh
# Start Elasticsearch and Kibana
curl -fsSL https://elastic.co/start-local | sh
# Get the generated API key (you will need it for FSCrawler)
source elastic-start-local/.env
```

Run FSCrawler with Docker:

```sh
docker pull dadoonet/fscrawler
docker run -it --rm \
  --add-host=host.docker.internal:host-gateway \
  -v ~/.fscrawler:/root/.fscrawler \
  -v $(pwd)/resumes:/tmp/es:ro \
  -e FSCRAWLER_ELASTICSEARCH_URLS=http://host.docker.internal:9200 \
  -e FSCRAWLER_ELASTICSEARCH_API_KEY="${ES_LOCAL_API_KEY}" \
  -e FS_JAVA_OPTS="-DLOG_LEVEL=debug" \
  dadoonet/fscrawler
```

Then [open Kibana](http://localhost:5601) and watch for your documents coming to the `fscrawler` alias:

```sql
FROM fscrawler 
| STATS numDocs = COUNT(*)
```

Or search for some text:

```sql
FROM fscrawler
| WHERE content : "David"
```

Or count by `file.content_type`:

```sql
FROM fscrawler 
| STATS numDocs = COUNT(*) BY file.content_type
```

Note:

* `~/resumes` contains the documents you want to index
* Job settings will be stored in `~/.fscrawler/fscrawler/_settings.yaml`

Read the [documentation](https://fscrawler.readthedocs.io/) for more details and specifically the 
[tutorial](https://fscrawler.readthedocs.io/en/latest/user/tutorial.html) page.

Need help writing your job settings? Copy a ready-made prompt from the
[LLM assistant guide](https://fscrawler.readthedocs.io/en/latest/user/llm-assistant.html)
into ChatGPT, Claude, or your favorite AI assistant.

FSCrawler also publishes an [`llms.txt`](https://fscrawler.readthedocs.io/en/latest/llms.txt)
index ([mirrored in the repo](llms.txt)) and clean Markdown versions of every docs page
(`*.html.md`) so agents can skip HTML chrome — see the [llms.txt](https://llmstxt.org/) v2 proposal.

## Project information

### Stats

![GitHub Repo stars](https://img.shields.io/github/stars/dadoonet/fscrawler)
![GitHub forks](https://img.shields.io/github/forks/dadoonet/fscrawler)
![GitHub contributors](https://img.shields.io/github/contributors/dadoonet/fscrawler)
![Docker Pulls](https://img.shields.io/docker/pulls/dadoonet/fscrawler)
![GitHub License](https://img.shields.io/github/license/dadoonet/fscrawler)

### Version in preparation

[![Latest SNAPSHOT](https://img.shields.io/github/v/release/dadoonet/fscrawler?include_prereleases&label=Latest%20SNAPSHOT)](https://github.com/dadoonet/fscrawler/releases)
![Docker Image Version (snapshot)](https://img.shields.io/docker/v/dadoonet/fscrawler/snapshot?label=snapshot)
![Docker Image Size (snapshot)](https://img.shields.io/docker/image-size/dadoonet/fscrawler/snapshot?label=snapshot%20size)
![GitHub commits since latest release](https://img.shields.io/github/commits-since/dadoonet/fscrawler/latest/main)
![GitHub last commit](https://img.shields.io/github/last-commit/dadoonet/fscrawler)
[![Build](https://github.com/dadoonet/fscrawler/actions/workflows/maven.yml/badge.svg)](https://github.com/dadoonet/fscrawler/actions/workflows/maven.yml)
[![Documentation Status](https://readthedocs.org/projects/fscrawler/badge/?version=latest)](https://fscrawler.readthedocs.io/en/latest/?badge=latest)

To test this SNAPSHOT with Docker, use the `snapshot` tag. Untagged `dadoonet/fscrawler`
(or `:latest`) is the last **stable** release:

```sh
docker pull dadoonet/fscrawler:snapshot
```

For Docker Compose, set `FSCRAWLER_VERSION=snapshot` in `.env`. Use `snapshot-noocr` if you
do not need OCR. The ZIP is published as a GitHub pre-release on every push to `main`.

### Latest release

[![GitHub Release](https://img.shields.io/github/v/release/dadoonet/fscrawler?label=Latest%20release)](https://github.com/dadoonet/fscrawler/releases/latest)
![GitHub Release Date](https://img.shields.io/github/release-date/dadoonet/fscrawler)
![Docker Image Version](https://img.shields.io/docker/v/dadoonet/fscrawler/latest?label=docker%20latest)
![Docker Image Size](https://img.shields.io/docker/image-size/dadoonet/fscrawler/latest?label=docker%20latest%20size)

### Build & quality

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=dadoonet_fscrawler&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=dadoonet_fscrawler)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=dadoonet_fscrawler&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=dadoonet_fscrawler)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=dadoonet_fscrawler&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=dadoonet_fscrawler)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=dadoonet_fscrawler&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=dadoonet_fscrawler)

[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=dadoonet_fscrawler&metric=bugs)](https://sonarcloud.io/summary/new_code?id=dadoonet_fscrawler)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=dadoonet_fscrawler&metric=vulnerabilities)](https://sonarcloud.io/summary/new_code?id=dadoonet_fscrawler)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=dadoonet_fscrawler&metric=code_smells)](https://sonarcloud.io/summary/new_code?id=dadoonet_fscrawler)
[![Lines of Code](https://sonarcloud.io/api/project_badges/measure?project=dadoonet_fscrawler&metric=ncloc)](https://sonarcloud.io/summary/new_code?id=dadoonet_fscrawler)
[![Duplicated Lines (%)](https://sonarcloud.io/api/project_badges/measure?project=dadoonet_fscrawler&metric=duplicated_lines_density)](https://sonarcloud.io/summary/new_code?id=dadoonet_fscrawler)
[![Technical Debt](https://sonarcloud.io/api/project_badges/measure?project=dadoonet_fscrawler&metric=sqale_index)](https://sonarcloud.io/summary/new_code?id=dadoonet_fscrawler)

# License

Read more about the [Apache2 License](https://fscrawler.readthedocs.io/en/latest/index.html#license).

# Thanks

Thanks to [JetBrains](https://www.jetbrains.com/?from=FSCrawler) for the IntelliJ IDEA License! The best IDE out there!

Thanks to SonarCloud for the free analysis! You guys rock!

[![SonarCloud](https://sonarcloud.io/images/project_badges/sonarcloud-white.svg)](https://sonarcloud.io/summary/new_code?id=dadoonet_fscrawler)
