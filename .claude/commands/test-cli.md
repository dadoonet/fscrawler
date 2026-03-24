Test the final FSCrawler distribution binary (`bin/fscrawler`) end-to-end.

This is different from Maven unit/integration tests — it exercises the actual built artifact against a real Elasticsearch cluster.

## Prerequisites

**Elasticsearch is required** — TestContainers is not used here. Choose one:
- **Local instance**: use the `start-elasticsearch` skill if not already running (http://localhost:9200, user `elastic`, password `changeme`)
- **Cloud instance**: have the cluster URL and API key ready

Build the distribution first if not already done:
```bash
mvn clean package -DskipTests -Ddocker.skip -pl distribution
cd /tmp && unzip -o <project-root>/distribution/target/fscrawler-distribution-*.zip
cd fscrawler-distribution-*
```

## Running Without a Settings File

All settings can be passed as environment variables — no `_settings.yaml` needed:

```bash
# With a local Elasticsearch (no security)
FSCRAWLER_NAME=test-job \
FSCRAWLER_FS_URL=/path/to/docs \
bin/fscrawler test-job

# With a local Elasticsearch (username/password)
FSCRAWLER_NAME=test-job \
FSCRAWLER_FS_URL=/path/to/docs \
FSCRAWLER_ELASTICSEARCH_USERNAME=elastic \
FSCRAWLER_ELASTICSEARCH_PASSWORD=changeme \
bin/fscrawler test-job

# With a cloud instance (API key)
FSCRAWLER_NAME=test-job \
FSCRAWLER_FS_URL=/path/to/docs \
FSCRAWLER_ELASTICSEARCH_NODES_0_URL=https://my-cluster.es.io:443 \
FSCRAWLER_ELASTICSEARCH_API-KEY=<api-key> \
bin/fscrawler test-job
```

Or via `FS_JAVA_OPTS`:
```bash
FS_JAVA_OPTS="-Dname=test-job -Dfs.url=/path/to/docs -Delasticsearch.username=elastic -Delasticsearch.password=changeme" \
bin/fscrawler test-job
```

## What to Check After Running

Use the `check-elasticsearch` skill to verify:
- The index was created and contains documents
- Mappings look correct
- Document content was extracted properly

```bash
curl -s http://localhost:9200/<index-name>/_search?pretty
```

## Ask the User

1. Is Elasticsearch already running locally, or do they need to start it / use a cloud instance?
2. Which directory to crawl (`FSCRAWLER_FS_URL`)?
3. Any specific settings to pass (index name, update rate, etc.)?
