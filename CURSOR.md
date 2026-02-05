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

### Running Tests

**IMPORTANT for AI assistants**: Always use `-Dtests.parallelism=1` when running integration tests to avoid race conditions and flaky tests.

```bash
# Run unit tests only (fast)
mvn test -pl <module>

# Run integration tests (ALWAYS use parallelism=1)
mvn verify -pl integration-tests -Dtests.parallelism=1

# Run a specific integration test
mvn verify -pl integration-tests -Dtests.parallelism=1 -Dtest=FsCrawlerImplAllDocumentsIT

# Run integration tests with a specific Elasticsearch version
mvn verify -pl integration-tests -Pes-8x -Dtests.parallelism=1
```

### Manual End-to-End Testing (Full Build Verification)

As a final verification step, build the full distribution and test it manually:

```bash
# 1. Build the distribution (skip tests for speed)
mvn install -DskipTests -Ddocker.skip

# 2. Unzip the distribution
cd distribution/target
unzip fscrawler-distribution-2.10-SNAPSHOT.zip
cd fscrawler-distribution-2.10-SNAPSHOT

# 3. Run FSCrawler with debug logging
FS_JAVA_OPTS="-DLOG_LEVEL=debug" bin/fscrawler --config_dir config

# Or with a specific job name
FS_JAVA_OPTS="-DLOG_LEVEL=debug" bin/fscrawler my_job --config_dir config
```

This is the ultimate test to verify the build works correctly as it would for an end user.

### Test Properties
```bash
-Dtests.parallelism=1          # REQUIRED for integration tests - run tests sequentially
-Dtests.leaveTemporary=false   # Clean up temp files after tests
-Dtests.cluster.url=...        # Use external Elasticsearch cluster
```

## REST API Endpoints

- `GET /` - Server status
- `POST /_document` - Upload a document (multipart/form-data)
- `PUT /_document/{id}` - Upload with specific ID
- `DELETE /_document/{id}` - Delete a document

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

## AI Assistant Instructions

This section contains important reminders for AI assistants working on this project.

### Running Tests
- **ALWAYS** use `-Dtests.parallelism=1` when running integration tests
- Integration tests use TestContainers and shared Elasticsearch instances - parallel execution causes race conditions
- Example: `mvn verify -pl integration-tests -Dtests.parallelism=1`

### Adding New Pipeline Plugins
When creating a new pipeline plugin (input, filter, or output):
1. Create the plugin class extending `AbstractInputPlugin`, `AbstractFilterPlugin`, or `AbstractOutputPlugin`
2. Annotate with `@Extension` (PF4J annotation)
3. Create `META-INF/services/fr.pilato.elasticsearch.crawler.plugins.pipeline.[input|filter|output].[Input|Filter|Output]Plugin` file
4. Add the fully qualified class name to the service file
5. The plugin will be auto-discovered via ServiceLoader

### Module Dependencies
- `plugin/` - Contains plugin interfaces and `PipelinePluginsManager`
- `plugins/` - Contains plugin implementations (filter plugins, output plugins, etc.)
- `core/` - Main crawler logic, depends on plugins for the pipeline

### Configuration Migration (v1 to v2)
- v1 configurations are automatically converted to v2 format in memory by `FsSettingsLoader`
- Use `--migrate` CLI option to convert configuration files permanently
- v2 uses pipeline architecture: inputs → filters → outputs
