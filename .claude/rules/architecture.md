# Architecture

## Module Structure

```
fscrawler/
├── beans/                    # Data model: Doc, File, Folder, Meta, Path
├── cli/                      # Entry point: FsCrawlerCli
├── core/                     # FsCrawlerImpl, FsParser*, bulk processing, services
├── crawler/
│   ├── crawler-abstract/     # Base file operations
│   ├── crawler-fs/           # Local filesystem
│   ├── crawler-ftp/          # FTP
│   └── crawler-ssh/          # SSH/SFTP
├── distribution/             # ZIP + Docker packaging
├── elasticsearch-client/     # ES client abstraction
├── framework/                # Utilities, JSON, bulk processor
├── integration-tests/        # End-to-end tests
├── plugin/                   # Plugin interfaces
├── plugins/                  # fs-local, fs-s3, fs-http, fs-ftp, fs-ssh, welcome
├── rest/                     # REST API server (Jersey)
├── settings/                 # YAML config management (Gestalt)
├── test-documents/           # Sample files for testing
├── test-framework/           # Base test classes
└── tika/                     # Apache Tika integration
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
| `FsParserLocal`, `FsParserSsh`, `FsParserFTP` | core | Protocol implementations |
| `TikaDocParser`          | tika      | Document text extraction   |
| `FsCrawlerBulkProcessor` | framework | ES bulk indexing           |
| `FsSettings`             | settings  | Job configuration          |
| `FsSettingsLoader`       | settings  | Loads YAML config          |
| `Doc`                    | beans     | Document representation    |
| `FsCrawlerDocumentService` | core   | Document indexing ops      |
| `FsCrawlerManagementService` | core | Job status management     |

## Configuration Files

Job configurations: `~/.fscrawler/<job-name>/_settings.yaml`

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

## REST API Endpoints

### Server
- `GET /` — Server status (version, job name, ES connection)

### Documents (`/_document`)
- `POST /_document` — Upload document (multipart/form-data or application/json)
- `PUT /_document/{id}` — Upload with specific ID
- `DELETE /_document` — Delete by filename (header or query `filename`)
- `DELETE /_document/{id}` — Delete by ID

### Crawler control (`/_crawler`)
- `GET /_crawler/status` — Crawler state, checkpoint, scan stats, nextCheck
- `POST /_crawler/pause` — Pause and save checkpoint
- `POST /_crawler/resume` — Resume or trigger next run
- `DELETE /_crawler/checkpoint` — Clear checkpoint (crawler must be paused/stopped)

## Plugin Development

Plugins extend `FsCrawlerPlugin` with PF4J annotations:

```java
public class MyPlugin extends FsCrawlerPlugin {
    @Extension
    public static class MyExtension extends FsCrawlerExtensionFsProviderAbstract {
        // Implementation
    }
}
```

Plugin JARs go in the `plugins/` directory of the distribution.

## Common Patterns

### Error Handling
- `logger.fatal()` for unrecoverable errors
- `FsCrawlerIllegalConfigurationException` for config errors
- `FsCrawlerValidator.validateSettings()` to validate settings

### Resource Management
- Use try-with-resources for `Closeable` objects
- `close()` may throw `InterruptedException` for graceful shutdown

### Bulk Processing
```java
bulkProcessor.add(operation);  // Auto-flushes when limits reached
bulkProcessor.flush();         // Force flush
```

## Debugging

```bash
# Increase log level
FS_JAVA_OPTS="-DLOG_LEVEL=debug" bin/fscrawler job-name
```

Common issues:
1. **ES connection**: Check `elasticsearch.nodes` configuration
2. **Document parsing**: Check Tika logs for parsing errors
3. **Memory issues**: `FS_JAVA_OPTS="-Xmx2g"` to increase heap
