# Build Commands

## Maven Commands

```bash
# Fast build, no tests (~1-2 min) — skip Docker
mvn clean package -DskipTests -Ddocker.skip

# Fast build, no tests — with Docker build
mvn clean package -DskipTests

# Unit tests only (~45-60 sec)
mvn clean test -DskipIntegTests

# Full build including integration tests
mvn clean install -Dtests.parallelism=1

# Specific Elasticsearch version
mvn clean install -Pes-8x

# Distribution only
mvn clean package -DskipTests -pl distribution
```

**Note**: `mvn install` with Docker can take 30+ minutes on first run (Tesseract OCR installation).

## Distribution Artifact

`distribution/target/fscrawler-distribution-2.10-SNAPSHOT.zip`

## Running FSCrawler Locally

```bash
# Extract distribution
cd /tmp && unzip distribution/target/fscrawler-distribution-2.10-SNAPSHOT.zip
cd fscrawler-distribution-2.10-SNAPSHOT

# Setup a job
./bin/fscrawler --config_dir /tmp/config --setup my-job
# Edit /tmp/config/my-job/_settings.yaml

# Run (requires Elasticsearch)
./bin/fscrawler --config_dir /tmp/config my-job

# Increase memory
FS_JAVA_OPTS="-Xmx2g" ./bin/fscrawler --config_dir /tmp/config my-job
```

## Running Without a Settings File

All settings can be passed as environment variables or Java system properties, making a `_settings.yaml` optional.

**Via environment variables** (prefix `FSCRAWLER_` + setting path in uppercase, dots replaced by `_`):

```bash
FSCRAWLER_NAME=my-job \
FSCRAWLER_FS_URL=/path/to/docs \
FSCRAWLER_ELASTICSEARCH_API-KEY=<api-key> \
bin/fscrawler my-job
```

**Via Java system properties** (using `FS_JAVA_OPTS`):

```bash
FS_JAVA_OPTS="-Dname=my-job -Dfs.url=/path/to/docs -Delasticsearch.api-key=<api-key>" \
bin/fscrawler my-job
```

Placeholders are also supported inside settings files (resolved from env vars or system properties):

```yaml
fs:
  url: "${HOME}/docs"
elasticsearch:
  nodes:
    - url: "${ES_NODE1:=https://127.0.0.1:9200}"
  api_key: "${ES_API_KEY}"
```

> **Note**: if both a settings file and env vars are defined, the settings file takes precedence.

See also: `docs/source/admin/fs/index.rst`

## CI/CD

- **GitHub Actions**: `.github/workflows/maven.yml`
- **PR Validation**: `.github/workflows/pr.yml`
- Tests run against ES 7.x, 8.x, and 9.x
