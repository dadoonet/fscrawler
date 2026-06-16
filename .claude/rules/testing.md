# Testing

## Framework

**JUnit Jupiter 6** (`org.junit.jupiter:junit-jupiter-api:6.0.3`) + **randomizedtesting-jupiter 0.2.0**.
Tests are run by `maven-surefire-plugin 3.5.5` (the old `junit4-maven-plugin` has been removed).
See `docs/MIGRATE_JUNIT6.md` for the full migration reference.

## Test Types

- **Unit tests**: `*Test.java` — run during `test` phase
- **Integration tests**: `*IT.java` — run during `integration-test` phase

Base classes:
- `AbstractFSCrawlerTestCase` — base for all tests (unit and IT); carries `@Fast` (10 s default timeout), `@Randomized`, `@DetectThreadLeaks`
- `AbstractFSCrawlerMetadataTestCase` — adds a `.fscrawler` temp directory
- `AbstractITCase` — IT base; connects to ES via TestContainers or a local cluster
- `AbstractFsCrawlerITCase` — IT base with full crawler lifecycle helpers

## Running Tests

```bash
# Single unit test (Surefire)
mvn test -pl <module> -am -DskipIntegTests -Dtest=ClassName#methodName

# Single integration test (Failsafe) — uses TestContainers (auto-starts ES)
mvn verify -pl integration-tests -am -DskipUnitTests \
  -Dit.test=FsCrawlerTestAddNewFilesIT#add_new_files_and_force_rescan

# Single integration test (Failsafe) — against a local Elasticsearch cluster
mvn verify -pl integration-tests -am -DskipUnitTests \
  -Dtests.cluster.url=http://localhost:9200 \
  -Dit.test=FsCrawlerTestAddNewFilesIT#add_new_files_and_force_rescan
```

> **Note**: les tests d'intégration (`*IT.java`) sont gérés par `maven-failsafe-plugin` et utilisent
> `-Dit.test=` ; les tests unitaires (`*Test.java`) sont gérés par `maven-surefire-plugin` et
> utilisent `-Dtest=`. Les deux propriétés sont indépendantes — pas de double exécution possible.

## Useful Test Flags

- `-Dtests.leaveTemporary=false` — clean up temp files/containers
- `-Dtests.seed=<SEED>` — reproduce a specific failure (passed as system property)
- `-Dtests.output=false|true` — show test output (default: false)
- `-Dtests.cluster.url=http://localhost:9200` — use local ES instead of TestContainers

## Speed tier annotations

Every test class **must** declare its tier (used by `@Timeout` and for CI filtering):

| Annotation | Timeout | Notes |
|-----------|---------|-------|
| `@Fast` (default) | 10 s | Already on `AbstractFSCrawlerTestCase` — all subclasses inherit it |
| `@Slow` | 1 min | Apply at class **or** method level to override `@Fast` |
| `@VerySlow` | 10 min | Apply at class **or** method level |
| `@Nightly` | tag `nightly` only — **no implicit timeout** | Excluded from default daily CI; run with `-P nightly`; combine with `@Slow`/`@VerySlow` explicitly |

**Important:** IT classes that use Awaitility waits > 10 s **must** override the timeout with
`@Slow` or `@VerySlow` at the class level, otherwise they may be silently limited to 10 s.
See `docs/MIGRATE_JUNIT6.md` §1 for the open question on timeout inheritance.

## Maven profiles

- `daily` (active by default) — excludes `@Nightly` tests
- `nightly` — includes only `@Nightly` tests (`mvn verify -P nightly`)
- `parallel_tests` (opt-in) — enables parallel class and method execution (`mvn verify -P parallel_tests`); use with live ES clusters, avoid with TestContainers

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
