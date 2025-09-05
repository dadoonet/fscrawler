# FSCrawler Development Guide

Always reference these instructions first and fallback to search or bash commands only when you encounter unexpected information that does not match the info here.

## Project Overview

FSCrawler is a Java-based file system crawler for Elasticsearch that helps index binary documents (PDF, Office docs, etc.). It's a multi-module Maven project built with Java ≥11, using TestContainers for integration tests and Docker for distribution.

## Working Effectively

### Bootstrap, Build, and Test the Repository

**CRITICAL TIMING**: Build and test operations take significant time. NEVER CANCEL long-running operations.

- **Basic build (NEVER CANCEL - takes ~1-2 minutes)**:
  ```bash
  mvn clean package -DskipTests
  # Distribution artifact: distribution/target/fscrawler-distribution-2.10-SNAPSHOT.zip
  ```

- **Unit tests (NEVER CANCEL - takes ~45-60 seconds)**:
  ```bash
  mvn clean install -DskipIntegTests
  ```



### Run FSCrawler

**ALWAYS build first** before running FSCrawler:

- **Extract and test distribution**:
  ```bash
  # After building
  cd /tmp && unzip distribution/target/fscrawler-distribution-2.10-SNAPSHOT.zip
  cd fscrawler-distribution-2.10-SNAPSHOT
  ./bin/fscrawler --help
  ```

- **Setup a new crawling job**:
  ```bash
  mkdir -p /tmp/config
  ./bin/fscrawler --config_dir /tmp/config --setup my-job
  # Edit /tmp/config/my-job/_settings.yaml to configure
  ```

- **Run crawler** (requires Elasticsearch running):
  ```bash
  ./bin/fscrawler --config_dir /tmp/config my-job
  ```

- **Run REST server** (requires Elasticsearch running):
  ```bash
  ./bin/fscrawler --config_dir /tmp/config --rest my-job
  ```

## Validation

### Build Time Expectations

Set appropriate timeouts for ALL build commands:
- Basic build: 2+ minutes timeout
- Unit tests: 2+ minutes timeout 
- Install with Docker: 30+ minutes timeout (due to tesseract OCR installation)

### Manual Testing Scenarios

Always test functionality after code changes:

1. **Build validation**:
   - Build completes without errors
   - Distribution ZIP is created in `distribution/target/`
   - CLI help shows correctly: `./bin/fscrawler --help`

2. **Configuration test**:
   - Setup works: `./bin/fscrawler --setup test-job`
   - Config file is created and readable
   - Can modify config (e.g., set `fs.url` path)

Always run validation commands with long timeouts and wait for completion.

## Common Issues and Workarounds

- **Docker build timeouts**: Normal during first build due to tesseract installation - wait for completion
- **Memory settings**: Use `FS_JAVA_OPTS` to configure JVM: `FS_JAVA_OPTS="-Xmx2g" bin/fscrawler`
- **Elasticsearch connection**: FSCrawler requires Elasticsearch running to function (defaults to https://127.0.0.1:9200)

## Repository Structure

Key directories and their purposes:

```
├── cli/                    # Command-line interface
├── core/                   # Core FSCrawler functionality  
├── distribution/           # Final packaging and Docker builds
├── integration-tests/      # Integration test suite
├── test-framework/         # Testing utilities
├── crawler/                # Crawling implementations (FS, FTP, SSH)
├── elasticsearch-client/   # Elasticsearch client library
├── tika/                   # Apache Tika integration for document parsing
├── docs/                   # Documentation (ReadTheDocs)
├── contrib/                # Docker Compose examples
└── plugins/                # Plugin system modules
```

## CI/GitHub Actions

The project uses several workflows:
- `.github/workflows/pr.yml`: Pull request validation
- `.github/workflows/maven.yml`: Master branch build and deploy
- Build runs unit tests, integration tests for ES 6.x/7.x/8.x/9.x, Docker builds

## Development Tips

- **Always build before testing**: Run `mvn clean install -DskipTests` first
- **Parallel testing**: Use `-Dtests.parallelism=1` to avoid resource conflicts
- **Test isolation**: Use `-Dtests.leaveTemporary=false` to clean up containers
- **Maven profiles**: Use `-Pes-8x`, `-Pes-7x`, etc. for specific Elasticsearch versions
- **Check build logs**: Long operations show progress - Docker builds are especially verbose

## Documentation

- Main docs: https://fscrawler.readthedocs.io/
- Build docs: `docs/source/dev/build.rst`
- Contributing: `CONTRIBUTING.md`
- Local docs build: Check `docs/pom.xml`

## Quick Reference Commands

```bash
# Clean build without tests
mvn clean package -DskipTests

# Build with unit tests
mvn clean test -DskipIntegTests -Dtests.parallelism=1 -Dtests.leaveTemporary=false

# Build distribution only
mvn clean package -DskipTests -pl distribution
```

Remember: This project requires patience for builds due to extensive OCR and Docker setup. Always wait for completion rather than canceling operations.