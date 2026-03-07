# FSCrawler - Cursor AI Instructions

This document helps AI assistants understand the FSCrawler project architecture and development practices.

## Project Overview

**FSCrawler** is a Java-based file system crawler for [Elasticsearch](https://elastic.co/) that indexes binary documents (PDF, MS Office, Open Office, etc.) into Elasticsearch. It supports local filesystem crawling, remote access via SSH/FTP, and provides a REST API for document uploads.

## Technology Stack

- **Language**: Java 17+
- **Build System**: Maven (multi-module project)
- **Elasticsearch**: Supports versions 7.x, 8.x, 9.x
- **Document Parsing**: Apache Tika
- **REST Framework**: Jersey (JAX-RS)
- **Plugin System**: PF4J (Plugin Framework for Java)
- **Testing**: JUnit 4 with Randomized Testing Framework, TestContainers
- **Logging**: Log4j2

## Module Architecture

```
fscrawler/
├── beans/                    # Data model classes (Doc, File, Folder, Meta, etc.)
├── cli/                      # Command-line interface entry point
├── core/                     # Core crawler implementation
│   ├── FsCrawlerImpl         # Main crawler orchestration
│   ├── FsParser*             # Protocol-specific parsers (Local, SSH, FTP)
│   └── service/              # Document and management services
├── crawler/                  # Crawler implementations
│   ├── crawler-abstract/     # Base abstraction for file operations
│   ├── crawler-fs/           # Local filesystem crawler
│   ├── crawler-ftp/          # FTP protocol support
│   └── crawler-ssh/          # SSH/SFTP protocol support
├── distribution/             # Packaging (ZIP, Docker)
├── elasticsearch-client/     # Elasticsearch client abstraction
├── framework/                # Utilities, bulk processing, JSON handling
├── integration-tests/        # End-to-end tests with TestContainers
├── plugin/                   # Plugin framework interfaces
├── plugins/                  # Built-in plugins
│   ├── fs-local-plugin/      # Local filesystem provider
│   ├── fs-s3-plugin/         # S3/MinIO provider
│   ├── fs-http-plugin/       # HTTP/HTTPS provider
│   └── welcome-plugin/       # Demo plugin
├── rest/                     # REST API server (Jersey)
├── settings/                 # Configuration management (YAML-based)
├── test-documents/           # Sample documents for testing
├── test-framework/           # Test utilities and base classes
└── tika/                     # Apache Tika integration for parsing
```

## Key Classes

### Entry Points
- `FsCrawlerCli` - Main CLI entry point (`cli/`)
- `RestServer` - REST API server (`rest/`)

### Core Components
- `FsCrawlerImpl` - Main crawler orchestration, manages lifecycle
- `FsParser` - Abstract base for file system parsing
- `FsParserLocal`, `FsParserSsh`, `FsParserFTP` - Protocol implementations
- `FsCrawlerBulkProcessor` - Batches documents for Elasticsearch indexing

### Services
- `FsCrawlerDocumentService` - Document indexing operations
- `FsCrawlerManagementService` - Job status management

### Data Model
- `Doc` - Main document representation
- `FsSettings` - Job configuration
- `File`, `Folder`, `Meta`, `Path` - Document metadata

### Configuration
- `FsSettingsLoader` - Loads YAML configuration files
- `FsSettingsParser` - Parses/serializes settings

## Configuration Files

Job configurations are stored in `~/.fscrawler/<job-name>/_settings.yaml`:

```yaml
name: "my-job"
fs:
  url: "/path/to/documents"
  update_rate: "15m"
elasticsearch:
  nodes:
    - url: "https://localhost:9200"
  index: "my-index"
```

## Commit Messages

- **Language**: Write all commit messages in **English**.
- **Emojis**: Use emojis to classify the type of change:
  - **Title**: Prefer a short emoji in the title (e.g. `feat(core): ✨ add X`, `fix(plugin): 🐛 prevent Y`).
  - **Body**: You may add emojis in the body for clarity (e.g. bullet points or section markers).
- **Types**: Common prefixes and emojis:
  - `feat` / ✨ – new feature
  - `fix` / 🐛 – bug fix
  - `docs` / 📝 – documentation
  - `refactor` / ♻️ – refactoring
  - `test` / 🧪 – tests
  - `chore` / 🔧 – maintenance, deps, tooling

Example:

```
fix(core): 🐛 re-check checkpoint nextCheck in between-runs wait

- Allow forced rescan when checkpoint file is updated externally
- Read checkpoint from disk each wait chunk when !userStopped
```

## Build Commands

```bash
# Clean build without tests (fastest)
mvn clean package -DskipTests

# Build with unit tests only
mvn clean install -DskipIntegTests

# Build for specific Elasticsearch version
mvn clean install -Pes-8x -DskipIntegTests

# Run a single test
mvn test -pl integration-tests -Dtest=ClassName#methodName
```

## Testing

### Test Categories
- **Unit Tests**: `*Test.java` files, run during `test` phase
- **Integration Tests**: `*IT.java` files, run during `integration-test` phase

### Test Framework
The project uses the Randomized Testing Framework from Carrotsearch:
- Base class: `AbstractFSCrawlerTestCase`
- Integration test base: `AbstractFSCrawlerMetadataTestCase`
- Uses TestContainers for Elasticsearch instances

### Test Properties
```bash
-Dtests.parallelism=1          # Control parallel test execution
-Dtests.leaveTemporary=false   # Clean up temp files after tests
-Dtests.cluster.url=...        # Use external Elasticsearch cluster
```

### Local Elasticsearch for integration tests

You can run integration tests against a **local** Elasticsearch instead of TestContainers. To start Elasticsearch locally, use the **start-local** method described in the skills under `.agents/skills/`:

- **`.agents/skills/start-elasticsearch/SKILL.md`** – How to start Elasticsearch locally (e.g. `curl -fsSL https://elastic.co/start-local | ES_LOCAL_PASSWORD="changeme" sh -s -- -v 9.3.1`). Run this in an `IGNORE_ME` directory so it is not committed. The cluster will be at http://localhost:9200 with user `elastic` and password `changeme`.
- **`.agents/skills/check-elasticsearch/SKILL.md`** – How to check that Elasticsearch is running and to inspect indices, content, or mapping.

Once Elasticsearch is running locally, run a single integration test with:

```bash
mvn verify -pl integration-tests -am \
  -Dtests.cluster.url=http://localhost:9200 \
  -Dtests.class=fr.pilato.elasticsearch.crawler.fs.test.integration.elasticsearch.FsCrawlerTestAddNewFilesIT \
  -Dtests.method="add_new_files_and_force_rescan"
```

- Replace the class and method with the desired test.
- Same pattern is documented in `docs/source/dev/build.rst` (e.g. `-Dtests.class=... -Dtests.method="METHOD_NAME"`).

### Fixing failing tests

When fixing a bug or addressing a test failure:

1. **Reproduce first**: Add or adjust a unit test and/or integration test that reproduces the error (e.g. an assertion that fails with the current behaviour).
2. **Run the test**: Execute the test and confirm it **fails** (red).
3. **Fix the code**: Change the production (or test) code so that the intended behaviour is correct.
4. **Verify**: Run the test again and confirm it **passes** (green).

Do not fix the code first and then add a test that passes; the test must fail before the fix so that it actually guards against the bug.

## REST API Endpoints

### Server
- `GET /` – Server status (version, job name, Elasticsearch connection)

### Documents (`/_document`)
- `POST /_document` – Upload a document (multipart/form-data or application/json for 3rd-party providers). Optional query/header/form: `id`, `index`, `debug`, `simulate`.
- `PUT /_document/{id}` – Upload a document with a specific ID (multipart/form-data). Optional: `index`, `debug`, `simulate`.
- `DELETE /_document` – Delete a document by filename (header or query `filename`, optional `index`).
- `DELETE /_document/{id}` – Delete a document by ID (optional query `index`).

### Crawler control (`/_crawler`)
- `GET /_crawler/status` – Crawler status (state, checkpoint, scan stats, nextCheck).
- `POST /_crawler/pause` – Pause the crawler and save checkpoint; no auto-run until resume.
- `POST /_crawler/resume` – Resume a paused crawler (or trigger next run between runs).
- `DELETE /_crawler/checkpoint` – Clear the checkpoint file (crawler must be paused or stopped first).

## Plugin Development

Plugins extend `FsCrawlerPlugin` and use PF4J annotations:

```java
public class MyPlugin extends FsCrawlerPlugin {
    @Extension
    public static class MyExtension extends FsCrawlerExtensionFsProviderAbstract {
        // Implementation
    }
}
```

Plugin JARs are placed in the `plugins/` directory of the distribution.

## Common Patterns

### Error Handling
- Use `logger.fatal()` for unrecoverable errors
- Use `FsCrawlerIllegalConfigurationException` for config errors
- Validate settings with `FsCrawlerValidator.validateSettings()`

### Resource Management
- Use try-with-resources for `Closeable` objects
- The `close()` method may throw `InterruptedException` for graceful shutdown

### Bulk Processing
```java
bulkProcessor.add(operation);  // Auto-flushes when limits reached
bulkProcessor.flush();         // Force flush
```

## Debugging

### Log Levels
```bash
# Via environment variable
FS_JAVA_OPTS="-DLOG_LEVEL=debug" bin/fscrawler job-name

# Or in log4j2.xml configuration
```

### Common Issues
1. **Elasticsearch connection**: Check `elasticsearch.nodes` configuration
2. **Document parsing**: Check Tika logs for parsing errors
3. **Memory issues**: Adjust with `FS_JAVA_OPTS="-Xmx2g"`

## CI/CD

- **GitHub Actions**: `.github/workflows/maven.yml`
- **PR Validation**: `.github/workflows/pr.yml`
- Tests run against ES 7.x, 8.x, and 9.x

## Documentation

- User docs: https://fscrawler.readthedocs.io/
- Source: `docs/source/` (reStructuredText)
- Build: Managed by ReadTheDocs

## Code Style

- Apache 2.0 License header required on all source files
- Use Log4j2 for logging (not SLF4J directly)
- Jackson for JSON serialization
- Gestalt for configuration management
