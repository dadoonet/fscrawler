# FSCrawler Development Guide

Always reference these instructions first and fallback to search or bash commands only when you encounter unexpected information that does not match the info here.

## Code Style

- Follow existing code conventions in the codebase
- Apache 2.0 License header required on all source files
- Use Log4j2 for logging
- Jackson for JSON serialization
- No automatic formatter is enforced; match the style of surrounding code, normally 4 spaces is the default.

## Project Overview

FSCrawler is a Java-based file system crawler for Elasticsearch that helps index binary documents (PDF, Office docs, etc.). It's a multi-module Maven project built with Java ≥17, using TestContainers for integration tests and Docker for distribution.

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
  mvn clean test -DskipIntegTests
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

### Elasticsearch Setup

FSCrawler requires a running Elasticsearch instance. The integration tests ar starting
Elasticsearch with TestContainers. But if you need to test the binary of fscrawler 
or the Docker image genrated by the build, then start one Elasticsearch instance with Docker:

```bash
# Elasticsearch 9.x: you cna check the expected version in the main `pom.xml`:
# <elasticsearch.version>${elasticsearch9.version}</elasticsearch.version>
docker run -d --name elasticsearch -p 9200:9200 -e "discovery.type=single-node" -e "xpack.security.enabled=false" docker.elastic.co/elasticsearch/elasticsearch:9.3.0

# Verify it's running
curl http://localhost:9200
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
├── framework/              # Utilities, bulk processing, JSON handling
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
- Build runs unit tests, integration tests for ES 7.x/8.x/9.x, Docker builds

## Git Workflow

### Branches
- `master`: Main development branch, target for all PRs
- Feature branches: `feature/<description>` or `<username>/<description>`
- Bug fix branches: `fix/<description>` or `fix/xxxx-<description>` where `xxxx` is the issue number.

### Commits
Use clear, descriptive commit messages, using emojis. Example format:

```
⚙️ Add support for S3 bucket crawling

- Implement S3FsProvider extension
- Add configuration options for bucket/region

Closes #xxxx.
```

### Pull Requests
- Target `master` branch
- Ensure all CI checks pass (unit tests, integration tests)
- PRs are merged via merge commit or squash (maintainer discretion). Very often using mergify.

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

## Security

- **Secrets**: Never commit credentials, API keys, or passwords. Use environment variables (e.g., `SONATYPE_USER`, `SONATYPE_PASS`)
- **Dependency scanning**: The project uses Sonatype OSS Index for CVE checks. Run `mvn ossindex:audit` to scan for vulnerabilities
