# Testing

## Test Types

- **Unit tests**: `*Test.java` — run during `test` phase
- **Integration tests**: `*IT.java` — run during `integration-test` phase

Base classes:
- `AbstractFSCrawlerTestCase` — unit test base
- `AbstractFSCrawlerMetadataTestCase` — integration test base

## Running Tests

```bash
# Single unit test
mvn test -pl <module> -Dtest=ClassName#methodName

# Single integration test — uses TestContainers (auto-starts ES)
mvn verify -pl integration-tests -am \
  -Dtests.class=fr.pilato.elasticsearch.crawler.fs.test.integration.elasticsearch.FsCrawlerTestAddNewFilesIT \
  -Dtests.method="add_new_files_and_force_rescan"

# Single integration test — against a local Elasticsearch cluster
mvn verify -pl integration-tests -am \
  -Dtests.cluster.url=http://localhost:9200 \
  -Dtests.class=fr.pilato.elasticsearch.crawler.fs.test.integration.elasticsearch.FsCrawlerTestAddNewFilesIT \
  -Dtests.method="add_new_files_and_force_rescan"
```

## Useful Test Flags

- `-Dtests.parallelism=1` — avoid resource conflicts
- `-Dtests.leaveTemporary=false` — clean up temp files/containers
- `-Dtests.seed=<SEED>` — reproduce a specific failure
- `-Dtests.output=always` — always show test output
- `-Dtests.cluster.url=http://localhost:9200` — use local ES instead of TestContainers

## Local Elasticsearch for Integration Tests

Use the skills in `.claude/skills/`:
- **`start-elasticsearch`** — starts ES locally via elastic/start-local (port 9200, user `elastic`, password `changeme`)
- **`check-elasticsearch`** — verifies ES is running and inspects indices/mapping

## Test-First Workflow (MANDATORY)

When fixing a bug:
1. **Reproduce first** — write/adjust a test that **fails** with the current behaviour
2. **Confirm red** — run the test, verify it fails
3. **Fix the code** — change production code to make the test pass
4. **Confirm green** — run the test again, verify it passes

**Never fix code first and then write a test that already passes.**
