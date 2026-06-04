# Conditional Parallel Test Execution in JUnit 6

**Date:** 2026-06-04  
**Status:** Design  
**Target Branch:** `519-junit5` (or feature branch)

## Overview

Enable parallel test execution at two levels (test classes and test methods) when running against a live Elasticsearch cluster, while keeping tests sequential with TestContainers to avoid instability.

Parallel execution should be **automatic**: activated only when `-Dtests.cluster.url` is provided, with no additional flags or configuration required.

## Problem Statement

Currently, all FSCrawler tests run sequentially (`-Dtests.parallelism=1`), even when connected to a live Elasticsearch cluster where parallel execution is safe. This results in longer CI pipeline times and slower local feedback cycles.

With TestContainers-based tests, parallel execution has caused instability in the past, so sequential execution must remain the default for those scenarios.

## Design Goals

1. **Automatic activation:** Parallelize automatically when `-Dtests.cluster.url` is provided; no new flags needed
2. **Two-level parallelization:** Execute test classes concurrently AND test methods within each class concurrently
3. **Safe with TestContainers:** Remain sequential (safe default) when using TestContainers
4. **Zero CI changes:** Existing CI workflows gain performance without modification
5. **Dynamic thread allocation:** Use JUnit 6's dynamic strategy to adapt thread count to available CPU

## Solution Architecture

### Maven Profile: `cluster-parallel`

A conditional Maven profile that activates when `-Dtests.cluster.url` is present:

```xml
<profile>
    <id>cluster-parallel</id>
    <activation>
        <property>
            <name>tests.cluster.url</name>
        </property>
    </activation>
    <properties>
        <!-- Override: test methods also execute in parallel (not just test classes) -->
        <junit.jupiter.execution.parallel.mode.default>concurrent</junit.jupiter.execution.parallel.mode.default>
    </properties>
</profile>
```

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
| Local with live ES | `mvn verify -Dtests.cluster.url=http://localhost:9200` | ✅ Yes | Classes + Methods (dynamic) |
| CI with ES Cloud | `-Dtests.cluster.url=${{ secrets.ES_CLOUD_URL }}` | ✅ Yes | Classes + Methods (dynamic) |
| Local with TestContainers | `mvn verify` | ❌ No | Classes only (methods sequential) |
| CI unit tests only | `mvn test -DskipIntegTests` | ❌ No | Classes only (methods sequential) |

### Why This Works

1. **Each test creates unique indices:** FSCrawler tests generate unique Elasticsearch indices per test, eliminating collision risk during parallel execution
2. **Dynamic thread allocation:** JUnit 6's `dynamic` strategy automatically scales thread count based on CPU cores, adapting to different CI/local environments
3. **No TestContainers risk:** TestContainers scenarios (unit tests, local test runs without `-Dtests.cluster.url`) remain sequential, avoiding past instability
4. **Automatic activation:** No new flags or workflow changes; existing `-Dtests.cluster.url` usage triggers parallelization

## Implementation Changes

### File: `pom.xml`

**Location:** In the `<profiles>` section (or create it if absent)

**Addition:**
```xml
<profile>
    <id>cluster-parallel</id>
    <activation>
        <property>
            <name>tests.cluster.url</name>
        </property>
    </activation>
    <properties>
        <junit.jupiter.execution.parallel.mode.default>concurrent</junit.jupiter.execution.parallel.mode.default>
    </properties>
</profile>
```

**No other files require changes.** CI workflows continue to work unchanged.

## Testing & Validation

### Unit Tests
- Existing unit tests (`*Test.java`) with TestContainers remain sequential and should pass without change
- Run locally: `mvn clean test -DskipIntegTests`

### Integration Tests with Live ES
- Run with local Elasticsearch: `mvn verify -Dtests.cluster.url=http://localhost:9200`
- Expected: All integration tests pass, execution significantly faster than before
- Validate no race conditions or index collisions

### CI Validation
- Elastic Cloud job: No changes needed; `-Dtests.cluster.url` triggers parallelization
- Elastic Serverless job: No changes needed; `-Dtests.cluster.url` triggers parallelization
- Expected: CI pipeline time reduced proportional to available parallelism

### Performance Expectations
- With 2-4 CPU cores and dynamic threading: ~30-50% reduction in test execution time
- Gains vary based on CI runner CPU and test distribution

## Edge Cases & Considerations

### Timeout Inheritance
Tests marked with `@Slow` or `@VerySlow` continue to respect their timeouts; parallel execution does not change timeout semantics. Verify that no test hits timeout due to resource contention during parallel runs.

### Log Output
With parallel execution, test output may interleave. Ensure CI logging captures per-test output correctly (Surefire/Failsafe handle this).

### Local ES Instance
Users must ensure their local Elasticsearch instance (started via `start-elasticsearch` skill) has sufficient heap and disk space for multiple concurrent tests. Default settings should be adequate.

### TestContainers Stability
TestContainers scenarios (unit tests, `mvn test`, no `-Dtests.cluster.url`) remain sequential by design. Do not attempt to parallelize these without further investigation into #399 (WatchService improvements) or other stability measures.

## Rollback Plan

If parallel execution causes instability:

1. Remove the `cluster-parallel` profile from `pom.xml`
2. Revert to sequential execution with `mvn verify -Dtests.cluster.url=http://localhost:9200 -Dtests.parallelism=1`

Profile-based activation makes rollback trivial.

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
