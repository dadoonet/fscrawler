(installation)=
# Installation

Choose one of:

* {ref}`docker` — pull the image from Docker Hub
* {ref}`docker-compose` — Elasticsearch, Kibana, and FSCrawler together
* {ref}`local-installation` — ZIP distribution on your machine

Coming from 2.9? There is no in-place upgrade. See {ref}`upgrade-from-2.9`.

(docker)=
## Using Docker

Pull the Docker image from [Docker Hub](https://hub.docker.com/r/dadoonet/fscrawler).

`latest` (the default when you omit the tag) is the **last stable release**, with OCR.
SNAPSHOT builds from `main` use the `snapshot` tag instead, matching GitHub pre-releases
which are never GitHub's "Latest" release.

| Tag | Image |
|---|---|
| `latest` (or untagged) | Last **stable** release, with OCR |
| `noocr` | Last **stable** release, without OCR |
| `snapshot` | Current SNAPSHOT (`main`), with OCR |
| `snapshot-noocr` | Current SNAPSHOT, without OCR |
| {{ release_docker_tags }} | This documentation version |
| `3.0`, `3.0-noocr`, `3.1-SNAPSHOT`, … | A specific version |

```{code-block} sh
:substitutions:

docker pull |docker_image|
```

::::{ifconfig} release.endswith('-SNAPSHOT')
:::{warning}
These docs describe a **SNAPSHOT** build. `docker pull dadoonet/fscrawler` (no tag) still
pulls the last **stable** release. To run this SNAPSHOT:

```{code-block} sh

docker pull dadoonet/fscrawler:snapshot
# or: docker pull dadoonet/fscrawler:snapshot-noocr
```
:::
::::

::::{note}
This image is very big (500+mb) as it contains [Tesseract](https://tesseract-ocr.github.io/tessdoc/) and
all the [trained language data](https://tesseract-ocr.github.io/tessdoc/Data-Files.html).
If you don't want to use OCR at all, you can use a smaller image (around 230mb) by pulling
{{ docker_image_noocr_code }} instead:

```{code-block} sh
:substitutions:

docker pull |docker_image_noocr|
```
::::

Let say your documents are located in `~/tmp` dir, and you want to store your FSCrawler jobs in `~/.fscrawler`.
On first run, create the job settings:

```{code-block} sh
:substitutions:

docker run -it --rm \
     -v ~/.fscrawler:/root/.fscrawler \
     |docker_image| --setup
```

Then run FSCrawler with:

```{code-block} sh
:substitutions:

docker run -it --rm \
     -v ~/.fscrawler:/root/.fscrawler \
     -v ~/tmp:/tmp/es:ro \
     |docker_image|
```

```{note}

 The configuration file is expected to be stored on your machine in `~/.fscrawler/fscrawler/_settings.yaml`.
 Remember to change the URL of your elasticsearch instance as the container won't be able to see it
 running under the default `127.0.0.1`. You will need to use the actual IP address of the host,
 or `host.docker.internal` on Docker Desktop.

 Or use the `FSCRAWLER_ELASTICSEARCH_URLS` environment variable to set the elasticsearch URL.
 See {ref}`cli-options` for more information.
```

If you need to add a 3rd party library (jar) or your Tika custom jar, you can put it in a `external` directory and
mount it as well:

```{code-block} sh
:substitutions:

docker run -it --rm \
     -v ~/.fscrawler:/root/.fscrawler \
     -v ~/tmp:/tmp/es:ro \
     -v "$PWD/external:/usr/share/fscrawler/external" \
     |docker_image|
```

If you want to use the {ref}`rest-service`, don't forget to also expose the port:

```{code-block} sh
:substitutions:

docker run -it --rm \
     -v ~/.fscrawler:/root/.fscrawler \
     -v ~/tmp:/tmp/es:ro \
     -p 8080:8080 \
     |docker_image|
```

If you want to change the log level for FSCrawler, you can run:

```{code-block} sh
:substitutions:

docker run -it --rm \
     -v ~/.fscrawler:/root/.fscrawler \
     -v ~/tmp:/tmp/es:ro \
     -v ~/logs:/root/logs \
     -e FS_JAVA_OPTS="-DLOG_LEVEL=debug -DDOC_LEVEL=debug" \
     |docker_image|
```

And you can read the logs from the `~/logs` directory:

```sh
tail -f ~/logs/documents.log
```

You can pass all the CLI options to the docker container as well:

```{code-block} sh
:substitutions:

docker run -it --rm \
     -v ~/.fscrawler:/root/.fscrawler \
     -v ~/tmp:/tmp/es:ro \
     |docker_image| job_name --restart --loop 1
```

See {ref}`cli-options` for more information.

(docker-compose)=
## Using Docker Compose

In this section, the following directory layout is assumed:

```none
.
├── .env
├── docs
│   └── <your PDF, DOC, ... files>
└── docker-compose.yml
```

The `.env` file looks like this:

```{code-block} sh
:substitutions:

# Password for the 'elastic' user (at least 6 characters)
ES_LOCAL_PASSWORD=changeme

# Version of Elastic products
ES_LOCAL_VERSION=|ES_stack_version|

# Set the ES container name
ES_LOCAL_CONTAINER_NAME=es-fscrawler

# Set to 'basic' or 'trial' to automatically start the 30-day trial
ES_LOCAL_LICENSE=basic
#ES_LOCAL_LICENSE=trial

# Port to expose Elasticsearch HTTP API to the host
ES_LOCAL_PORT=9200
ES_LOCAL_DISK_SPACE_REQUIRED=1gb
ES_LOCAL_JAVA_OPTS="-XX:UseSVE=0 -Xms128m -Xmx2g"

# Kibana
KIBANA_LOCAL_CONTAINER_NAME=kibana-fscrawler
KIBANA_LOCAL_PORT=5601

# Project namespace (defaults to the current folder name if not set)
COMPOSE_PROJECT_NAME=fscrawler

# FSCrawler Settings
FSCRAWLER_VERSION=|docker_hub_tag|
FSCRAWLER_PORT=8080

# Optionally, you can change the log level settings
FS_JAVA_OPTS="-DLOG_LEVEL=debug -DDOC_LEVEL=debug"
```

::::{ifconfig} release.endswith('-SNAPSHOT')
:::{warning}
These docs describe a **SNAPSHOT** build. `FSCRAWLER_VERSION=latest` still pulls the last
**stable** release. To test this SNAPSHOT with Compose, set:

```{code-block} sh

FSCRAWLER_VERSION=snapshot
# or: FSCRAWLER_VERSION=snapshot-noocr
```

`snapshot` is overwritten on every push to `main`, so run `docker compose pull` to pick up
a new SNAPSHOT. Use `snapshot-noocr` if you do not need OCR.
:::
::::

And, the `docker-compose.yml` file looks like this:

```yaml
---
services:
  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:${ES_LOCAL_VERSION}
    container_name: ${ES_LOCAL_CONTAINER_NAME}
    volumes:
      - dev-elasticsearch:/usr/share/elasticsearch/data
    ports:
      - 127.0.0.1:${ES_LOCAL_PORT}:9200
    environment:
      - discovery.type=single-node
      - ELASTIC_PASSWORD=${ES_LOCAL_PASSWORD}
      - xpack.security.enabled=true
      - xpack.security.http.ssl.enabled=false
      - xpack.license.self_generated.type=${ES_LOCAL_LICENSE}
      - xpack.ml.use_auto_machine_memory_percent=true
      - ES_JAVA_OPTS=${ES_LOCAL_JAVA_OPTS}
      - cluster.routing.allocation.disk.watermark.low=${ES_LOCAL_DISK_SPACE_REQUIRED}
      - cluster.routing.allocation.disk.watermark.high=${ES_LOCAL_DISK_SPACE_REQUIRED}
      - cluster.routing.allocation.disk.watermark.flood_stage=${ES_LOCAL_DISK_SPACE_REQUIRED}
    ulimits:
      memlock:
        soft: -1
        hard: -1
    healthcheck:
      test:
        [
          "CMD-SHELL",
          "curl --output /dev/null --silent --head --fail -u elastic:${ES_LOCAL_PASSWORD} http://elasticsearch:9200",
        ]
      interval: 10s
      timeout: 10s
      retries: 30

  kibana:
    image: docker.elastic.co/kibana/kibana:${ES_LOCAL_VERSION}
    container_name: ${KIBANA_LOCAL_CONTAINER_NAME}
    volumes:
      - dev-kibana:/usr/share/kibana/data
    ports:
      - 127.0.0.1:${KIBANA_LOCAL_PORT}:5601
    environment:
      - ELASTICSEARCH_HOSTS=http://${ES_LOCAL_CONTAINER_NAME}:9200
      - ELASTICSEARCH_USERNAME=elastic
      - ELASTICSEARCH_PASSWORD=${ES_LOCAL_PASSWORD}
    depends_on:
      elasticsearch:
        condition: service_healthy
    healthcheck:
      test:
        [
          "CMD-SHELL",
          "curl -s -I http://localhost:5601 | grep -q 'HTTP/1.1 302 Found'",
        ]
      interval: 10s
      timeout: 10s
      retries: 30

  # FSCrawler
  fscrawler:
    image: dadoonet/fscrawler:${FSCRAWLER_VERSION}
    container_name: fscrawler
    restart: always
    environment:
      - FS_JAVA_OPTS=${FS_JAVA_OPTS}
      - FSCRAWLER_ELASTICSEARCH_URLS=http://${ES_LOCAL_CONTAINER_NAME}:9200
      - FSCRAWLER_ELASTICSEARCH_USERNAME=elastic
      - FSCRAWLER_ELASTICSEARCH_PASSWORD=${ES_LOCAL_PASSWORD}
      - FSCRAWLER_KIBANA_URL=http://${KIBANA_LOCAL_CONTAINER_NAME}:5601
      - FSCRAWLER_REST_URL=http://fscrawler:${FSCRAWLER_PORT}
    volumes:
      - ${PWD}/docs:/tmp/es:ro
    depends_on:
      elasticsearch:
        condition: service_healthy
      kibana:
        condition: service_healthy
    ports:
      - ${FSCRAWLER_PORT}:8080
    command: --rest

volumes:
  dev-elasticsearch:
  dev-kibana:
```

Copy your pdf/doc files into the `docs` directory and run the full stack, including FSCrawler with:

```sh
docker-compose up
```

This example does not mount `~/.fscrawler`. Job settings come from `FSCRAWLER_*` environment
variables. The default job name is `fscrawler`, so documents are searchable on the `fscrawler`
alias. Username and password match the Elastic user created by the stack; API keys are preferred
in production. See {ref}`credentials`.

When the job has finished indexing, you can check your documents in Elasticsearch with:

```sh
curl -u elastic:changeme "http://localhost:9200/fscrawler/_search"
```

```{note}
You will find this example in the `contrib/docker-compose-example-elasticsearch` project directory.
```

(local-installation)=
## Local installation (ZIP)

If you prefer to run FSCrawler from a ZIP distribution on your machine instead of Docker,
download {{ download_link }}
from {{ GitHub }}:

```{code-block} sh
:substitutions:

wget |downloadUrl|
unzip fscrawler-|release|.zip
cd fscrawler-distribution-|release|
```

::::{ifconfig} release.endswith('-SNAPSHOT')
:::{warning}
This is a **SNAPSHOT** build. The ZIP is overwritten on every push to `main`.
SNAPSHOT pre-releases are **not** GPG-signed. Stable versions (with `.asc` and SHA-256)
are listed on the same {{ GitHub }} page.
:::
::::

::::{ifconfig} release == version
:::{tip}
This is a **stable** version. Development SNAPSHOT builds are published as GitHub pre-releases on the same
{{ GitHub }} page.
:::

(verify-zip)=
```{rubric} Verify the ZIP
```

Stable releases attach a GPG signature and a SHA-256 checksum next to the ZIP.

```{code-block} sh
:substitutions:

wget |downloadUrl|
wget |downloadUrl|.asc
wget |downloadUrl|.sha256
sha256sum -c fscrawler-|release|.zip.sha256
# macOS: shasum -a 256 -c fscrawler-|release|.zip.sha256
gpg --import KEYS
# or: gpg --keyserver hkps://keys.openpgp.org --recv-keys EDEC15CE428D7527CF87E998C7E192835B0ABB2E
gpg --verify fscrawler-|release|.zip.asc fscrawler-|release|.zip
```

The signing key is **David Pilato** `<david@pilato.fr>`, fingerprint
`EDEC 15CE 428D 7527 CF87 E998 C7E1 9283 5B0A BB2E`.
`KEYS` lives at the root of the [git repository](https://github.com/dadoonet/fscrawler/blob/main/KEYS).
::::

After extracting the ZIP, you get a directory with `bin/` (run scripts), `config/` (logging), `lib/` (core and
dependencies), `external/` (optional JARs), and `logs/`. See {ref}`layout` for the full directory layout.

Then continue with {ref}`getting-started`.

### Optional libraries (external)

You may need to add JARs to the `external` directory for some formats. For example, to support JPEG2000 (JPX/JP2)
images in PDFs, add the `jai-imageio-jpeg2000` library: download it from
[Maven Central](https://central.sonatype.com/search?q=g:com.github.jai-imageio) and put
`jai-imageio-jpeg2000-1.4.0.jar` in the `external` directory.

## Running as a Service on Windows

Create a `fscrawlerRunner.bat` as:

```sh
set JAVA_HOME=c:\Program Files\Java\jdk17.0.18
set FS_JAVA_OPTS=-Xmx2g -Xms2g
/Elastic/fscrawler/bin/fscrawler.bat --config_dir /Elastic/fscrawler data >> /Elastic/logs/fscrawler.log 2>&1
```

Then use `fscrawlerRunner.bat` to create your Windows service.
