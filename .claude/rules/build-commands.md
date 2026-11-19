# Build Commands

## Maven Commands

```bash
# Package without tests — no container runtime required
mvn clean package -DskipTests -Ddocker.skip

# Install artifacts and build Docker images without tests — requires Docker
mvn clean install -DskipTests

# Unit tests only (~45-60 sec)
mvn clean test -DskipIntegTests

# Unit and integration tests, without building Docker images — requires Docker
mvn clean verify -Ddocker.skip

# Full build including integration tests and Docker images — requires Docker
mvn clean install

# Specific Elasticsearch version
mvn clean install -Pes-8x

# Distribution only
mvn clean package -DskipTests -pl distribution
```

**Note**: `mvn install` with Docker can take 30+ minutes on first run (Tesseract OCR installation).

Docker image construction is bound to `install` in `distribution/pom.xml`, not
`package`. `-Ddocker.skip` skips that image build; it does not disable
Testcontainers. Use `-DskipIntegTests` to skip integration tests or `-DskipTests`
to skip both test suites.

Unit tests need no Docker runtime, but the distribution tests require Python
(`python3` by default; override with `"-Dtests.python=python"` or an executable
path). Integration tests use Elasticsearch, MinIO and Nginx containers. An external
Elasticsearch cluster does not replace the MinIO/Nginx containers. SSH/FTP test
servers run inside the JVM. OCR coverage needs Tesseract in the Maven environment.

On Windows, use Linux containers (typically Docker Desktop with WSL 2); on macOS,
Docker Desktop runs a Linux VM. Native Java tests retain host filesystem rules.
See `docs/source/dev/build.md` for prerequisites, platform setup and commands.

## Distribution Artifact

`distribution/target/fscrawler-distribution-3.0-SNAPSHOT.zip`

## Running FSCrawler Locally

```bash
# Extract distribution
cd /tmp && unzip distribution/target/fscrawler-distribution-3.0-SNAPSHOT.zip
cd fscrawler-distribution-3.0-SNAPSHOT

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
  urls:
    - "${ES_NODE1:=https://127.0.0.1:9200}"
  api_key: "${ES_API_KEY}"
```

> **Note**: if both a settings file and env vars are defined, the settings file takes precedence.

See also: `docs/source/admin/fs/index.md`

## CI/CD

- **GitHub Actions**: `.github/workflows/maven.yml`
- **PR Validation**: `.github/workflows/pr.yml`
- Tests run against 8.x, and 9.x
