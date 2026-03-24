Run a specific test in FSCrawler.

Ask the user:
1. **Test type**: unit test (`*Test.java`) or integration test (`*IT.java`)?
2. **Module**: which Maven module (e.g. `integration-tests`, `settings`, `tika`)?
3. **Class name**: the test class (e.g. `FsCrawlerTestAddNewFilesIT`)
4. **Method name** (optional): specific test method

For **unit tests**, run:
```
mvn test -pl <module> -Dtest=<ClassName>#<methodName>
```

For **integration tests**, ask whether to use:
- **TestContainers** (auto-starts Elasticsearch): no extra flags
- **Local Elasticsearch** (already running at localhost:9200): add `-Dtests.cluster.url=http://localhost:9200`

Integration test command:
```
mvn verify -pl integration-tests -am \
  [-Dtests.cluster.url=http://localhost:9200] \
  -Dtests.class=<fully.qualified.ClassName> \
  -Dtests.method="<methodName>"
```

If using local Elasticsearch, use the `start-elasticsearch` skill first if it's not already running.

Always use `-Dtests.parallelism=1` for integration tests to avoid resource conflicts.
