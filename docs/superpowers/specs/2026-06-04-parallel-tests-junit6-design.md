# Conditional Parallel Test Execution in JUnit 6

**Date:** 2026-06-04  
**Status:** Design  
**Target Branch:** `519-junit5` (or feature branch)

## Overview

Enable parallel test execution at two levels (test classes and test methods) via an opt-in Maven profile, while keeping tests sequential by default with TestContainers to avoid instability.

Parallel execution is **opt-in**: activated only when the `-P parallel_tests` flag is provided explicitly. Default behavior remains sequential to prioritize stability.

## Problem Statement

Currently, all FSCrawler tests run sequentially (`-Dtests.parallelism=1`), even when connected to a live Elasticsearch cluster where parallel execution is safe. This results in longer CI pipeline times and slower local feedback cycles.

With TestContainers-based tests, parallel execution has caused instability in the past. To balance performance gains with stability concerns, we need an **opt-in** parallel profile that users can explicitly enable when they choose to run against a live cluster.

## Design Goals

1. **Opt-in activation:** Parallelize only when `-P parallel_tests` is explicitly provided; sequential remains default
2. **Two-level parallelization:** Execute test classes concurrently AND test methods within each class concurrently
3. **Safe with TestContainers:** Remain sequential (safe default) when using TestContainers—no profile = no parallelization
4. **Flexible CI:** CI workflows can opt-in with `-P parallel_tests` when performance is needed
5. **Dynamic thread allocation:** Use JUnit 6's dynamic strategy to adapt thread count to available CPU

## Solution Architecture

### Maven Profile: `parallel_tests`

An opt-in Maven profile that activates only when explicitly provided via `-P parallel_tests`:

```xml
<!-- This profile configures JUnit 6 to parallelize test methods, not just test classes -->
<profile>
    <id>parallel_tests</id>
    <build>
        <plugins>
            <!-- Override Failsafe plugin configuration to enable parallel test method execution -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-failsafe-plugin</artifactId>
                <configuration>
                    <systemPropertyVariables combine.children="override">
                        <!-- Enable parallel execution -->
                        <junit.jupiter.execution.parallel.enabled>true</junit.jupiter.execution.parallel.enabled>
                        <!-- Enable parallel execution of classes -->
                        <junit.jupiter.execution.parallel.mode.classes.default>concurrent</junit.jupiter.execution.parallel.mode.classes.default>
                        <!-- Enable parallel execution of test methods -->
                        <junit.jupiter.execution.parallel.mode.default>concurrent</junit.jupiter.execution.parallel.mode.default>
                    </systemPropertyVariables>
                </configuration>
            </plugin>
        </plugins>
    </build>
</profile>
```

**Activation:** Only via explicit `-P parallel_tests` flag. No automatic activation.

### Existing JUnit 6 Configuration (Unchanged)

The pom.xml already contains parallel execution setup (added during JUnit 4→6 migration):

```xml
<junit.jupiter.execution.parallel.enabled>${tests.parallelism}</junit.jupiter.execution.parallel.enabled>
<junit.jupiter.execution.parallel.mode.default>same_thread</junit.jupiter.execution.parallel.mode.default>
<junit.jupiter.execution.parallel.mode.classes.default>concurrent</junit.jupiter.execution.parallel.mode.classes.default>
<junit.jupiter.execution.parallel.config.strategy>dynamic</junit.jupiter.execution.parallel.config.strategy>
```

**Current state:** Test classes run concurrently, but methods within each class run sequentially (safe default).  
**After profile activation:** Both classes AND methods run concurrently.

### Behavior Matrix

| Scenario | Command | Profile Active? | Parallelization |
|----------|---------|-----------------|-----------------|
| Local with live ES (parallel) | `mvn verify -Dtests.cluster.url=http://localhost:9200 -P parallel_tests` | ✅ Yes | Classes + Methods (dynamic) |
| CI with ES Cloud (parallel) | `mvn verify -Dtests.cluster.url=${{ secrets.ES_CLOUD_URL }} -P parallel_tests` | ✅ Yes | Classes + Methods (dynamic) |
| Local with TestContainers | `mvn verify` | ❌ No | Classes only (methods sequential) |
| CI unit tests only | `mvn test -DskipIntegTests` | ❌ No | Classes only (methods sequential) |
| Local with live ES (sequential) | `mvn verify -Dtests.cluster.url=http://localhost:9200` | ❌ No | Classes only (methods sequential) |

### Why This Works

1. **Each test creates unique indices:** FSCrawler tests generate unique Elasticsearch indices per test, eliminating collision risk during parallel execution
2. **Dynamic thread allocation:** JUnit 6's `dynamic` strategy automatically scales thread count based on CPU cores, adapting to different CI/local environments
3. **Safe defaults:** Without `-P parallel_tests`, tests run sequentially, avoiding TestContainers instability by default
4. **Explicit opt-in:** Users and CI workflows control when parallelization happens; not forced on anyone
5. **Low friction:** One flag to enable; easy to mix and match parallelism as needed per job

## Implementation Changes

### File: `pom.xml`

**Location:** In the `<profiles>` section (or create it if absent)

**Addition:**
```xml
<!-- This profile configures JUnit 6 to parallelize test methods, not just test classes -->
<profile>
    <id>parallel_tests</id>
    <build>
        <plugins>
            <!-- Override Failsafe plugin configuration to enable parallel test method execution -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-failsafe-plugin</artifactId>
                <configuration>
                    <systemPropertyVariables combine.children="override">
                        <!-- Enable parallel execution -->
                        <junit.jupiter.execution.parallel.enabled>true</junit.jupiter.execution.parallel.enabled>
                        <!-- Enable parallel execution of classes -->
                        <junit.jupiter.execution.parallel.mode.classes.default>concurrent</junit.jupiter.execution.parallel.mode.classes.default>
                        <!-- Enable parallel execution of test methods -->
                        <junit.jupiter.execution.parallel.mode.default>concurrent</junit.jupiter.execution.parallel.mode.default>
                    </systemPropertyVariables>
                </configuration>
            </plugin>
        </plugins>
    </build>
</profile>
```

**No other files require changes.** Users opt-in with `-P parallel_tests` as needed.

## Testing & Validation

### Unit Tests
- Existing unit tests (`*Test.java`) with TestContainers remain sequential and should pass without change
- Run locally: `mvn clean test -DskipIntegTests`

### Integration Tests with Live ES (Sequential, Default)
- Run with local Elasticsearch: `mvn verify -Dtests.cluster.url=http://localhost:9200`
- Expected: All integration tests pass, execution sequential (safe default)
- Validates no regressions in sequential mode

### Integration Tests with Live ES (Parallel, Opt-in)
- Run with local Elasticsearch and parallelization: `mvn verify -Dtests.cluster.url=http://localhost:9200 -P parallel_tests`
- Expected: All integration tests pass, execution significantly faster than sequential
- Validate no race conditions or index collisions with parallel execution

### CI Validation
- Elastic Cloud job (sequential): `mvn verify -Dtests.cluster.url=${{ secrets.ES_CLOUD_URL }}`
  - Expected: Baseline performance (sequential)
- Elastic Cloud job (parallel, optional): `mvn verify -Dtests.cluster.url=${{ secrets.ES_CLOUD_URL }} -P parallel_tests`
  - Expected: CI pipeline time reduced proportional to available parallelism
- Elastic Serverless job: Same as Elastic Cloud

### Performance Expectations
- With 2-4 CPU cores and dynamic threading (when `-P parallel_tests` used): ~30-50% reduction in test execution time
- Gains vary based on CI runner CPU and test distribution
- Default sequential mode: baseline performance (no change from today)

## Edge Cases & Considerations

### Timeout Inheritance
Tests marked with `@Slow` or `@VerySlow` continue to respect their timeouts; parallel execution does not change timeout semantics. Verify that no test hits timeout due to resource contention during parallel runs.

### Log Output
With parallel execution, test output may interleave. Ensure CI logging captures per-test output correctly (Surefire/Failsafe handle this).

### Local ES Instance
Users must ensure their local Elasticsearch instance (started via `start-elasticsearch` skill) has sufficient heap and disk space for multiple concurrent tests. Default settings should be adequate.

### TestContainers Stability
TestContainers scenarios (unit tests, `mvn test`, no `-Dtests.cluster.url`) remain sequential by design. Do not use `-P parallel_tests` with TestContainers without further investigation into #399 (WatchService improvements) or other stability measures. Parallel + TestContainers = undefined behavior and likely test failures.

## Rollback Plan

If parallel execution causes instability, the opt-in design makes rollback trivial:

1. **Stop using `-P parallel_tests`** — go back to sequential execution by default
2. CI workflows that opted in can remove `-P parallel_tests` from their commands
3. If needed, remove the `parallel_tests` profile from `pom.xml` entirely (no active adoption = no loss)

No users are forced into parallelization; sequential mode remains the default safety net.

## Success Criteria

- ✅ Tests pass sequentially and in parallel
- ✅ No race conditions or index collisions with parallel execution
- ✅ CI pipeline time reduced without changing workflows
- ✅ TestContainers tests remain stable (sequential)
- ✅ Profile activates only when `-Dtests.cluster.url` is provided

## Related Issues

- Migration to JUnit 6: Branch `519-junit5`, PR #???
- Potential timeout inheritance issue: `docs/MIGRATE_JUNIT6.md` §1
- WatchService for event-driven crawling: Issue #399

## References

- [JUnit 6 Parallel Execution](https://junit.org/junit5/docs/current/user-guide/#writing-tests-parallel-execution)
- FSCrawler test framework: `test-framework/` module, `AbstractFSCrawlerTestCase`
- Current pom.xml parallel config: `<junit.jupiter.execution.parallel.*>` properties
