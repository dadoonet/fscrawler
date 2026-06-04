# Migration JUnit 4 → JUnit Jupiter 6

This document describes the manual migration of FSCrawler's test infrastructure from JUnit 4 +
`randomizedtesting-runner` 2.8.4 to JUnit Jupiter 6.0.3 + `randomizedtesting-jupiter` 0.2.0, done on
branch `519-junit5`.

---

## Summary of changes

### Dependencies (`pom.xml` root)

| Before | After |
|--------|-------|
| `junit:junit:4.13.2` | `org.junit.jupiter:junit-jupiter-api:6.0.3` + engine + platform modules |
| `com.carrotsearch.randomizedtesting:randomizedtesting-runner:2.8.4` | `com.carrotsearch.randomizedtesting:randomizedtesting-jupiter:0.2.0` |
| `com.carrotsearch.randomizedtesting:junit4-maven-plugin:2.8.4` | `org.apache.maven.plugins:maven-surefire-plugin:3.5.5` |

The JUnit BOM (`org.junit:junit-bom:6.0.3`) was also added to `dependencyManagement` to align all
`junit-platform-*` artifact versions.

The `maven-surefire-plugin` was previously disabled (so that `junit4-maven-plugin` could own test
execution). It is now re-enabled as the sole test runner.

### Class-level annotations (test classes)

| Before (JUnit 4) | After (JUnit 5/6) |
|------------------|-------------------|
| `public class Foo extends …` | `class Foo extends …` (no need for `public`) |
| `@Before` | `@BeforeEach` |
| `@After` | `@AfterEach` |
| `@BeforeClass public static void m()` | `@BeforeAll static void m()` |
| `@AfterClass public static void m()` | `@AfterAll static void m()` |
| `public void testMethod()` | `void testMethod()` (no need for `public`) |
| `@Test public void m()` | `@Test void m()` |

### Imports

| Before | After |
|--------|-------|
| `org.junit.Before` / `After` / `BeforeClass` / `AfterClass` / `Test` | `org.junit.jupiter.api.*` equivalents |
| `com.carrotsearch.randomizedtesting.RandomizedTest` | `com.carrotsearch.randomizedtesting.jupiter.RandomizedTest` |
| `com.carrotsearch.randomizedtesting.annotations.Nightly` | `fr.pilato.elasticsearch.crawler.fs.test.framework.Nightly` (custom) |
| `org.junit.Assume.assumeTrue(...)` | `org.assertj.core.api.Assumptions.assumeThat(...)` |
| `org.testcontainers.DockerClientFactory` guard in `@Before` | `@DisabledIfNoDocker` annotation (custom) |
| Inline script in `@DisabledIf(value = "expr...")` | Static method reference: `@DisabledIf(value = "methodName")` — see below |

### `@DisabledIf` — always use static method references

`@DisabledIf` conditions must be **static methods** returning `boolean`, never inline script expressions.
Inline expressions are fragile and may reference instance fields (e.g., `currentTestResourceDir`) that
are not yet initialized when the condition is evaluated — `@DisabledIf` runs before any `@BeforeEach`.

```java
// Wrong — inline expression, currentTestResourceDir is null at evaluation time
@DisabledIf(
    value = "Files.getFileAttributeView(currentTestResourceDir.resolve(\"file.txt\"), AclFileAttributeView.class) == null",
    disabledReason = "...")

// Correct — static method, no dependency on instance state
@DisabledIf(value = "isAclFilesystemNotSupported", disabledReason = "...")

static boolean isAclFilesystemNotSupported() {
    try {
        Path tmp = Files.createTempFile("acl-check", ".tmp");
        try {
            return Files.getFileAttributeView(tmp, AclFileAttributeView.class) == null;
        } finally {
            Files.deleteIfExists(tmp);
        }
    } catch (IOException e) {
        return true;
    }
}
```

See also `TestContainerHelperIT.isExternalClusterSet()` for the reference pattern.

### Class-level activation (`AbstractFSCrawlerTestCase`)

The base test class is now annotated with:

```java
@Randomized
@ExtendWith(FsCrawlerReproduceInfoExtension.class)
@DetectThreadLeaks(scope = DetectThreadLeaks.Scope.SUITE)
@DetectThreadLeaks.LingerTime(millis = 5000)
@DetectThreadLeaks.ExcludeThreads({ … })
@Fast   // ← default timeout: 10 seconds
public abstract class AbstractFSCrawlerTestCase { … }
```

`@Randomized` replaces the old `@RunWith(RandomizedRunner.class)`.

### RandomizedTest API changes

The `randomizedtesting-jupiter` artifact exposes a different API — random-producing methods now
require an explicit `Random` parameter instead of relying on a thread-local context:

| Before | After |
|--------|-------|
| `RandomizedTest.randomLongBetween(5, 20)` | `RandomizedTest.randomLongInRange(rnd, 5, 20)` |
| `RandomizedTest.randomIntBetween(1, 100)` | `RandomizedTest.randomIntInRange(rnd, 1, 100)` |
| `RandomizedTest.randomAsciiAlphanumOfLength(10)` | `RandomizedTest.randomAsciiAlphanumOfLength(rnd, 10)` |

The `Random` instance is either injected directly into `@BeforeAll`/`@BeforeEach`/`@Test` method
parameters (supported by `@Randomized`), or stored as `TEST_RANDOM` in the base class via:

```java
@BeforeEach
void storeRandom(Random rnd) {
    TEST_RANDOM = rnd;
}
```

`getCurrentTestName()` (old randomizedtesting helper) was replaced by `jobName`, a field computed
in `@BeforeEach` from `TestInfo.getTestMethod().getName()`.

### New infrastructure classes in `test-framework`

| Class | Purpose |
|-------|---------|
| `Fast` | Composed annotation: `@Timeout(10, SECONDS)`. Applied at class level on `AbstractFSCrawlerTestCase`. |
| `Slow` | Composed annotation: `@Timeout(1, MINUTES)`. Can be applied at class or method level. |
| `VerySlow` | Composed annotation: `@Timeout(10, MINUTES)`. Can be applied at class or method level. |
| `Nightly` | Simple tag annotation: `@Tag("Nightly")`. Marks tests only run in nightly CI. **No implicit timeout** — always combine with `@Slow`, `@VerySlow`, or `@Timeout` explicitly. |
| `DisabledIfNoDocker` | JUnit 5 `ExecutionCondition` extension: disables test if Docker is not available. Replaces `Assume.assumeTrue(DockerClientFactory…)`. |
| `FsCrawlerReproduceInfoExtension` | `AfterTestExecutionCallback`: prints reproduction command line on failure (replaces `FSCrawlerReproduceInfoPrinter`). Detects `*IT` vs `*Test` class names to produce the correct `-Dit.test=` or `-Dtest=` flag. Extensible via the inner `@Properties` annotation: add it to a test class to inject additional system properties into the reproduction command. |
| `IntelliJThreadsFilter` | `Predicate<Thread>`: ignores JMX/RMI threads created by IntelliJ when running under `@DetectThreadLeaks`. |
| `JUnitThreadsFilter` | `Predicate<Thread>`: ignores the `junit-jupiter-timeout-watcher` thread created by `@Timeout`. **Required** — without it, `@Timeout` triggers a thread-leak false positive. |
| `KeepAliveTimerThreadFilter` | `Predicate<Thread>`: ignores `Keep-Alive-Timer` threads from `sun.net.www.http.KeepAliveCache` (JDK HTTP keep-alive pool, created by Jersey's default `HttpURLConnection` connector or Testcontainers health checks). Always `TERMINATED` when detected — pure false positive. |
| `Java2DThreadFilter` | `Predicate<Thread>`: ignores `Java2D Disposer`, `AppKit Thread` (macOS only), and `AWT-*` threads. Created by the JDK's AWT/Java2D subsystem when PDFBox (via Apache Tika) initialises graphics resources to parse PDFs. JVM-lifetime system threads — false positives. |
| `JNACleanerThreadFilter` | `Predicate<Thread>`: ignores the `JNA Cleaner` thread started by the JNA native library binding. |
| `TestContainerThreadFilter` | `Predicate<Thread>`: ignores `process reaper`, `ducttape-*`, and all threads in the `testcontainers` thread group created by the Testcontainers infrastructure. |
| `WindowsSpecificThreadFilter` | `Predicate<Thread>`: ignores threads in thread groups whose name starts with `TGRP-`, which appear on Windows only. |
| `MinioThreadFilter` | `Predicate<Thread>`: ignores `Okio Watchdog`, `OkHttp TaskRunner`, and `ForkJoinPool.commonPool-worker-1` threads left by the MinIO Java client. Temporary workaround pending [minio/minio-java#1584](https://github.com/minio/minio-java/issues/1584). Not in the base class exclusion list — apply on S3 test subclasses only. |
| `WireMockThreadFilter` | `Predicate<Thread>`: ignores `qtp*`, `WireMock`, and `Jetty` threads left by WireMock's embedded Jetty server. Not in the base class exclusion list — apply on HTTP-mock test subclasses only. |

### Deleted class

`FSCrawlerReproduceInfoPrinter` was removed. Its functionality is now in `FsCrawlerReproduceInfoExtension`.

### Maven surefire / failsafe split

Unit tests (`*Test.java`) and integration tests (`*IT.java`) now run under separate Maven plugins:

| Plugin | Handles | Skip flag |
|--------|---------|-----------|
| `maven-surefire-plugin` | `*Test.java` | `-DskipUnitTests` |
| `maven-failsafe-plugin` | `*IT.java` | `-DskipIntegTests` |

Test selection flags differ accordingly:

```bash
# Run a single unit test
mvn test -pl <module> -am -DskipIntegTests -Dtest=ClassName#methodName

# Run a single integration test
mvn verify -pl integration-tests -am -DskipUnitTests \
  -Dit.test=FsCrawlerTestAddNewFilesIT#add_new_files_and_force_rescan
```

`-DskipTests` still skips both, as before.

**Important:** always use `mvn verify` (not `mvn integration-test`) to run integration tests.
`failsafe:integration-test` runs the tests but does **not** fail the build on failure — that is the
responsibility of `failsafe:verify`, which is bound to the `verify` phase. Running only
`mvn integration-test` will execute the tests but silently swallow any failures.

### Maven profiles

Two new Maven profiles were added for test filtering via Surefire tags:

| Profile | Active by default | Effect |
|---------|------------------|--------|
| `daily` | ✅ yes | Excludes tests tagged `Nightly` |
| `nightly` | ❌ no | Includes only tests tagged `Nightly` |

Usage:
```bash
mvn verify -P nightly   # run nightly tests
mvn verify              # run daily tests (default, no @Nightly)
```

### Parallel execution disabled (static `@TempDir` aliasing)

Parallel class execution (`tests.parallelism`) is set to `false` because `rootTmpDir` is declared
as a **static** `@TempDir` field on `AbstractFSCrawlerTestCase` and inherited by all ~70 test
classes. When two classes run concurrently, JUnit's `@TempDir` injection overwrites this single
shared field, causing one class to use (and later lose) the temp directory of another — resulting
in flaky failures (e.g. `FsCrawlerUtilTest`).

The Surefire parallel configuration in the root `pom.xml` (lines ~756-759) is kept in place but
is inert while `tests.parallelism=false`:

```xml
<junit.jupiter.execution.parallel.enabled>${tests.parallelism}</junit.jupiter.execution.parallel.enabled>
<junit.jupiter.execution.parallel.mode.default>same_thread</junit.jupiter.execution.parallel.mode.default>
<junit.jupiter.execution.parallel.mode.classes.default>concurrent</junit.jupiter.execution.parallel.mode.classes.default>
<junit.jupiter.execution.parallel.config.strategy>dynamic</junit.jupiter.execution.parallel.config.strategy>
```

**Deferred fix:** To re-enable parallelism cleanly, replace the static field with a JUnit extension
that stores each class's temp directory in `ExtensionContext.Store` (keyed by class) and exposes it
via a static `rootTmpDir()` accessor (safe because `mode.default=same_thread` guarantees the full
class lifecycle runs on one thread). This requires renaming ~65 field accesses to `rootTmpDir()`.

### Resolved: old `junit4-maven-plugin` config items

Several `junit4-maven-plugin` features had no direct Surefire equivalent. The `<!-- TODO REPLACE ? -->`
comment blocks have been removed from the root `pom.xml`; here is how each item was resolved:

- `heartbeat` — no equivalent; removed
- `jvmOutputAction` — no equivalent; removed
- `leaveTemporary` — passed as system property `tests.leaveTemporary` via `systemPropertyVariables`
- `listeners` / `showOutput="onError"` — replaced by
  `<redirectTestOutputToFile>${tests.output}</redirectTestOutputToFile>`: stdout/stderr goes to
  `target/surefire-reports/TEST-*.txt` for passing tests and is included in the console failure
  report for failing tests
- `seed` — passed as system property `tests.seed` via `systemPropertyVariables`; picked up by
  `randomizedtesting-jupiter`
- `tests.timeoutSuite` / `tests.timeout` — values are now in **seconds** (not milliseconds),
  mapped to `<parallelTestsTimeoutInSeconds>` in Failsafe and also forwarded as system properties

---

## Reproduction commands

After a test failure, `FsCrawlerReproduceInfoExtension` prints a command like:

For a **unit test** (`*Test`):
```
🐛 REPRODUCE WITH:
mvn verify -DskipIntegTests -Dtest=…ClassName#methodName -Dtests.locale=… -Dtests.timezone=…
```

For an **integration test** (`*IT`):
```
🐛 REPRODUCE WITH:
mvn verify -DskipUnitTests -Dit.test=…ClassName#methodName -Dtests.locale=… -Dtests.timezone=…
```

The extension detects the test type automatically from the class name suffix. To inject additional
system properties (e.g. `tests.cluster.url`) into the reproduction command, annotate the test class
with the inner `@Properties` annotation:

```java
@FsCrawlerReproduceInfoExtension.Properties({"tests.cluster.url"})
class MyIT extends AbstractITCase { … }
```

This replaces the old `FSCrawlerReproduceInfoPrinter` that was a JUnit 4 `RunListener`.
