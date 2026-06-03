# JUnit 4 → JUnit Jupiter 6 — Migration Summary

Quick-reference of every change made on branch `519-junit5`, grouped by theme.
Full narrative: `docs/MIGRATE_JUNIT6.md`. Blog post: `docs/MIGRATE_JUNIT6_BLOG.md`.

---

## 1. Dependencies

- `junit:junit:4.13.2` → `org.junit.jupiter:junit-jupiter-api/engine:6.0.3`
- `org.junit:junit-bom:6.0.3` added to `dependencyManagement` (aligns all `junit-platform-*` versions)
- `com.carrotsearch.randomizedtesting:randomizedtesting-runner:2.8.4` → `randomizedtesting-jupiter:0.2.0`

## 2. Build System

- `junit4-maven-plugin` removed; `maven-surefire-plugin:3.5.5` is now the sole test runner for unit tests
- `maven-failsafe-plugin` handles `*IT.java` integration tests; `maven-surefire-plugin` handles `*Test.java` unit tests
- `-DskipUnitTests` / `-DskipIntegTests` properties added for selective execution
- Always use `mvn verify` (not `mvn integration-test`) to run IT tests: `failsafe:verify` (bound to the `verify` phase) is what actually fails the build on test failures
- Two Maven profiles: `daily` (default, excludes `@Nightly`) and `nightly` (includes only `@Nightly`)
- Parallel execution disabled (`tests.parallelism=false`) to avoid static `@TempDir` aliasing
- `tests.timeoutSuite` and `tests.timeout` are in **seconds** (mapped to `parallelTestsTimeoutInSeconds` in Failsafe)

## 3. JUnit API Changes (annotations & imports)

| Before (JUnit 4)                          | After (JUnit 6)                              |
|-------------------------------------------|----------------------------------------------|
| `@RunWith(RandomizedRunner.class)`        | `@Randomized`                                |
| `@Before` / `@After`                      | `@BeforeEach` / `@AfterEach`                 |
| `@BeforeClass` / `@AfterClass`            | `@BeforeAll` / `@AfterAll`                   |
| `public class Foo` / `public void test()` | package-private (`class Foo`, `void test()`) |
| `org.junit.Assume.assumeTrue(...)`        | `@Disabled` or `Assumptions.assumeThat(...)` |
| Inline `@DisabledIf` expressions          | Static method references only                |
| `org.testcontainers` guard in `@Before`   | `@DisabledIfNoDocker` custom annotation      |

## 4. RandomizedTest API

- Random methods now require an explicit `Random` parameter (injected by `@Randomized`):
  `randomLongBetween(5, 20)` → `randomLongInRange(rnd, 5, 20)`
- `getCurrentTestName()` removed; replaced by `jobName` field derived from `TestInfo` in `@BeforeEach`
- `TEST_RANDOM` field stored in `@BeforeEach void storeRandom(Random rnd)` for use throughout the test

## 5. Thread Leak Detection

Seven thread filters registered on `AbstractFSCrawlerTestCase` via `@DetectThreadLeaks.ExcludeThreads`:

| Filter                        | Source                                                                 |
|-------------------------------|------------------------------------------------------------------------|
| `JUnitThreadsFilter`          | `junit-jupiter-timeout-watcher` — created by `@Timeout`                |
| `IntelliJThreadsFilter`       | JMX/RMI threads created by IntelliJ                                    |
| `KeepAliveTimerThreadFilter`  | `Keep-Alive-Timer` from JDK HTTP keep-alive pool                       |
| `Java2DThreadFilter`          | `Java2D Disposer`, `AppKit Thread`, `AWT-*` — triggered by PDFBox/Tika |
| `JNACleanerThreadFilter`      | `JNA Cleaner` thread from native library binding                       |
| `TestContainerThreadFilter`   | `process reaper`, `ducttape-*`, `testcontainers` group threads         |
| `WindowsSpecificThreadFilter` | Windows `TGRP-*` thread group threads                                  |

Additional filters available for specific test classes (not in the base exclusion list):

| Filter                 | Source                                                          |
|------------------------|-----------------------------------------------------------------|
| `MinioThreadFilter`    | `Okio Watchdog`, `OkHttp TaskRunner` from the MinIO Java client |
| `WireMockThreadFilter` | `qtp*`, `WireMock`, `Jetty` threads left by WireMock            |

## 6. New Test Infrastructure Classes

| Class                             | Role                                                                                                                                       |
|-----------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------|
| `Fast`                            | `@Timeout(10, SECONDS)` — default, inherited from `AbstractFSCrawlerTestCase`                                                              |
| `Slow`                            | `@Timeout(1, MINUTES)` — class or method level                                                                                             |
| `VerySlow`                        | `@Timeout(10, MINUTES)` — class or method level                                                                                            |
| `Nightly`                         | `@Tag("Nightly")` — excluded from daily CI; always combine with `@Slow`/`@VerySlow`                                                        |
| `DisabledIfNoDocker`              | Disables test if Docker is unavailable (replaces `Assume.assumeTrue(DockerClientFactory…)`)                                                |
| `FsCrawlerReproduceInfoExtension` | `AfterTestExecutionCallback` — prints reproduction command on failure; detects IT vs unit test automatically; extensible via `@Properties` |

## 7. Test Quality

- `Assume.assumeTrue(...)` replaced by `@Disabled` where condition is static, or `Assumptions.assumeThat(...)` where dynamic
- `satisfies(...)` assertion chains replaced with explicit named assertions for clearer failure messages
- `@Nightly` tests now also annotated `@VerySlow` to enforce a 10-minute timeout
- `@DisabledIf` conditions refactored to static methods (inline expressions can reference uninitialized instance state)
- `@Nightly` tag casing fixed: `"nightly"` → `"Nightly"` to match Surefire's case-sensitive profile filter

## 8. Code Quality (non-test)

- Test class and method visibility reduced from `public` to package-private across all ~70 test classes
- `FsCrawlerBulkProcessor` refactored to Builder pattern
- `CrawlerStatusResponse` made `final`

## 9. Documentation

- `docs/MIGRATE_JUNIT6.md` — full migration reference guide
- `CLAUDE.md` and `.claude/rules/testing.md` updated with new test commands and conventions
- `docs/source/dev/build.rst` updated with Sonatype PAT instructions and surefire/failsafe commands
