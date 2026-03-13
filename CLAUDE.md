# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**FSCrawler** is a Java-based file system crawler for Elasticsearch that indexes binary documents (PDF, MS Office, etc.). It supports local filesystem, SSH/FTP, S3, and HTTP sources, and provides a REST API for document uploads. Supports Elasticsearch 7.x, 8.x, and 9.x.

- **Language**: Java 17+
- **Build**: Maven multi-module (16 modules)
- **Document parsing**: Apache Tika
- **REST framework**: Jersey (JAX-RS)
- **Plugin system**: PF4J
- **Testing**: JUnit 4 + Randomized Testing Framework + TestContainers

## Build Commands

```bash
# Fast build, no tests (~1-2 min — NEVER CANCEL but also skip Docker build)
mvn clean package -DskipTests -Ddocker.skip

# Fast build, no tests (~1-2 min — NEVER CANCEL but with Docker build which takes a bit more time)
mvn clean package -DskipTests

# Build with unit tests only (~45-60 sec — NEVER CANCEL)
mvn clean test -DskipIntegTests

# Full build including integration tests
mvn clean install -Dtests.parallelism=1

# Specific Elasticsearch version
mvn clean install -Pes-8x

# Distribution only
mvn clean package -DskipTests -pl distribution
```

Distribution artifact: `distribution/target/fscrawler-distribution-2.10-SNAPSHOT.zip`

**Note**: `mvn install` with Docker can take 30+ minutes on first run (Tesseract OCR installation). Skip Docker with `-Ddocker.skip`.

## Running Tests

Unit tests are `*Test.java`; integration tests are `*IT.java`.

```bash
# Single unit test
mvn test -pl <module> -Dtest=ClassName#methodName

# Single integration test against TestContainers (auto-starts ES)
mvn verify -pl integration-tests -am \
  -Dtests.class=fr.pilato.elasticsearch.crawler.fs.test.integration.elasticsearch.FsCrawlerTestAddNewFilesIT \
  -Dtests.method="add_new_files_and_force_rescan"

# Single integration test against a local Elasticsearch cluster (for example started with start-local)
mvn verify -pl integration-tests -am \
  -Dtests.cluster.url=http://localhost:9200 \
  -Dtests.class=fr.pilato.elasticsearch.crawler.fs.test.integration.elasticsearch.FsCrawlerTestAddNewFilesIT \
  -Dtests.method="add_new_files_and_force_rescan"
```

Useful test flags:
- `-Dtests.parallelism=1` — avoid resource conflicts
- `-Dtests.leaveTemporary=false` — clean up temp files/containers
- `-Dtests.seed=<SEED>` — reproduce a specific failure
- `-Dtests.output=always` — always show test output

### Fixing Failing Tests

1. Add/adjust a test that **reproduces** the error (confirm it fails first).
2. Fix the production code.
3. Confirm the test now passes.

Never fix code first and then write a test that already passes.

## Module Architecture

```
fscrawler/
├── beans/                # Data model: Doc, File, Folder, Meta, Path
├── cli/                  # Entry point: FsCrawlerCli
├── core/                 # FsCrawlerImpl, FsParser*, bulk processing, services
├── crawler/
│   ├── crawler-abstract/ # Base file operations
│   ├── crawler-fs/       # Local filesystem
│   ├── crawler-ftp/      # FTP
│   └── crawler-ssh/      # SSH/SFTP
├── distribution/         # ZIP + Docker packaging
├── elasticsearch-client/ # ES client abstraction
├── framework/            # Utilities, JSON, bulk processor
├── integration-tests/    # End-to-end tests
├── plugin/               # Plugin interfaces
├── plugins/              # fs-local, fs-s3, fs-http, fs-ftp, fs-ssh, welcome
├── rest/                 # REST API server
├── settings/             # YAML config management (Gestalt)
├── test-documents/       # Sample files for testing
├── test-framework/       # Base test classes
└── tika/                 # Apache Tika integration
```

## Data Flow

1. `FsSettingsLoader` loads `~/.fscrawler/<job-name>/_settings.yaml`
2. `FsCrawlerCli` (CLI) or `RestServer` (REST) starts the crawler
3. `FsCrawlerImpl` orchestrates the crawl lifecycle
4. `FsParser` implementations (Local/SSH/FTP) walk the file system
5. `TikaDocParser` extracts text and metadata from documents
6. `FsCrawlerBulkProcessor` batches documents for Elasticsearch indexing
7. `FsCrawlerDocumentService` / `FsCrawlerManagementService` handle indexing and job state

## Key Classes

| Class                    | Module    | Role                       |
|--------------------------|-----------|----------------------------|
| `FsCrawlerCli`           | cli       | CLI entry point            |
| `RestServer`             | rest      | REST API entry point       |
| `FsCrawlerImpl`          | core      | Main crawler orchestration |
| `FsParser`               | core      | Abstract FS traversal      |
| `TikaDocParser`          | tika      | Document text extraction   |
| `FsCrawlerBulkProcessor` | framework | ES bulk indexing           |
| `FsSettings`             | settings  | Job configuration          |
| `Doc`                    | beans     | Document representation    |

## Code Style

- Apache 2.0 license header required on all source files.
- Use Log4j2 for logging (not SLF4J directly).
- Use Jackson for JSON serialization.
- Use `logger.fatal()` for unrecoverable errors; `FsCrawlerIllegalConfigurationException` for config errors.
- No automatic formatter; match surrounding code style (default: 4-space indent).

## Commit Messages

Format: `type(scope): emoji description`

```
fix(core): 🐛 re-check checkpoint nextCheck in between-runs wait

- Allow forced rescan when checkpoint file is updated externally
- Read checkpoint from disk each wait chunk when !userStopped
```

Types: `feat` ✨, `fix` 🐛, `docs` 📝, `refactor` ♻️, `test` 🧪, `chore` 🔧

## Git Workflow

- **Main branch**: `master` (target for all PRs)
- **Feature branches**: `feature/<description>` or `<username>/<description>`
- **Bug fix branches**: `fix/<description>` or `fix/<issue-number>-<description>`

## Running FSCrawler Locally

```bash
# After building, extract distribution
cd /tmp && unzip ~/IdeaProjects/fscrawler/distribution/target/fscrawler-distribution-2.10-SNAPSHOT.zip
cd fscrawler-distribution-2.10-SNAPSHOT

# Setup a job
./bin/fscrawler --config_dir /tmp/config --setup my-job
# Edit /tmp/config/my-job/_settings.yaml

# Run (requires Elasticsearch)
./bin/fscrawler --config_dir /tmp/config my-job

# Increase memory
FS_JAVA_OPTS="-Xmx2g" ./bin/fscrawler --config_dir /tmp/config my-job
```

Start a local Elasticsearch for manual testing:
```bash
docker run -d --name elasticsearch -p 9200:9200 \
  -e "discovery.type=single-node" -e "xpack.security.enabled=false" \
  docker.elastic.co/elasticsearch/elasticsearch:9.3.1
```

## Documentation

- User docs: https://fscrawler.readthedocs.io/
- Source: `docs/source/` (reStructuredText, built by ReadTheDocs)
- Build guide: `docs/source/dev/build.rst`
- Agent-specific architecture notes: `.agents/CURSOR.md`
