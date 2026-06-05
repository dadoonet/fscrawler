# Conditional Parallel Test Execution in JUnit 6 — Implementation Plan

> **Pour exécuter ce plan:** Utilisez superpowers:subagent-driven-development (recommandé) pour dispatcher une sous-tâche par task avec review intermédiaire, ou superpowers:executing-plans pour exécution inline avec checkpoints.

**Objectif:** Activer la parallélisation optionnelle des tests JUnit 6 à deux niveaux (classes + méthodes) via un profil Maven non-activé par défaut, pour éviter les problèmes de shared state avec TestContainers.

**Architecture:** Un profil Maven opt-in (`parallel_tests`) qui s'active uniquement via `-P parallel_tests`. Le profil override la propriété `junit.jupiter.execution.parallel.mode.default` de `same_thread` à `concurrent`, ainsi que `junit.jupiter.execution.parallel.mode.classes.default` à `concurrent`. Aucun autre changement requis.

**Tech Stack:** Maven profiles, JUnit 6 parallel execution configuration

---

## File Structure

**Modification:**
- `pom.xml` — Ajouter le profil `parallel_tests` dans la section `<profiles>`

---

## Task 1: Add `parallel_tests` Profile to pom.xml

**Files:**
- Modify: `pom.xml` (section `<profiles>`)

- [ ] **Step 1: Locate the `<profiles>` section in pom.xml**

```bash
grep -n "<profiles>" pom.xml
```

Expected: Line number where `<profiles>` starts (e.g., `1234:<profiles>`)

- [ ] **Step 2: View the existing profiles section**

```bash
sed -n '1234,1300p' pom.xml
```

(Replace `1234` with the line from Step 1, and adjust the range to see all profiles)

Expected: Output showing existing profiles like `daily`, `nightly`, etc.

- [ ] **Step 3: Add the new `parallel_tests` profile before the closing `</profiles>` tag**

Insert this profile (adjust indentation to match existing profiles):

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

Location: Add before the closing `</profiles>` tag, after all other profiles.

- [ ] **Step 4: Verify the pom.xml is valid**

```bash
mvn help:validate
```

Expected: No errors. Output: `BUILD SUCCESS`

- [ ] **Step 5: Commit the change**

```bash
git add pom.xml
git commit -m "feat(test): add parallel_tests profile for opt-in parallel test execution

- Enable two-level parallelization (test classes and methods)
- Activation: only via -P parallel_tests (not automatic)
- Default: sequential execution to avoid TestContainers shared state issues
- CI workflows can opt-in with -P parallel_tests when using external clusters"
```

---

## Task 2: Verify Profile Activation with Opt-In Flag

**Files:**
- Test: Run integration tests with `-P parallel_tests`

- [ ] **Step 1: Start a local Elasticsearch instance (if needed)**

Activate the `start-elasticsearch` skill:
```bash
/start-elasticsearch
```

Or manually start ES on `http://localhost:9200` with user `elastic` and password `changeme`.

Wait for ES to be ready.

- [ ] **Step 2: Run a single integration test with `-P parallel_tests` to verify parallelization activates**

```bash
mvn verify -pl integration-tests -am -DskipUnitTests \
  -Dtests.cluster.url=http://localhost:9200 \
  -Dit.test=FsCrawlerTestAddNewFilesIT#add_new_files_and_force_rescan \
  -P parallel_tests \
  -Dtests.output=true
```

Expected: Test passes. Check the console output for parallel execution indicators (multiple tests running concurrently).

- [ ] **Step 3: Verify the profile is active (optional — check Maven output)**

Look for a line like:
```
[INFO] --- maven-help-plugin:3.x.x:active-profiles (default-cli) @ <module> ---
[INFO] Active profiles: es-8x, parallel_tests
```

If you see `parallel_tests` in the active profiles list, the activation worked.

- [ ] **Step 4: Run a broader set of integration tests**

```bash
mvn verify -pl integration-tests -am -DskipUnitTests \
  -Dtests.cluster.url=http://localhost:9200 \
  -Dtests.parallelism=1 \
  -Dtests.output=false
```

Expected: All tests pass with parallel execution enabled.

---

## Task 3: Verify Sequential Execution (Default Behavior)

**Files:**
- Test: Run tests without `-P parallel_tests` (default sequential execution)

- [ ] **Step 1: Run unit tests (no integration tests, without `-P parallel_tests`)**

```bash
mvn clean test -DskipIntegTests
```

Expected: All unit tests pass. The `parallel_tests` profile should NOT be active.

Check output: Should NOT show `parallel_tests` in active profiles.

- [ ] **Step 2: Verify tests remain sequential**

The default config (without the profile) should be:
- Test classes: `concurrent`
- Test methods: `same_thread` (sequential)

No additional verification needed — if tests pass with the default config, it's working.

- [ ] **Step 3: Optionally run integration tests with TestContainers (slow)**

```bash
mvn verify -pl integration-tests -am -DskipUnitTests
```

Expected: All tests pass sequentially (TestContainers scenario). May take several minutes.

---

## Task 4: Optional — Enable Parallelization in CI Workflows

**Files:**
- Optional modify: `.github/workflows/pr.yml`

- [ ] **Step 1: Review the PR workflow to identify jobs that could benefit from parallelization**

```bash
grep -n "mvn verify" .github/workflows/pr.yml
```

Expected: Multiple matches showing existing `mvn verify` jobs in CI.

- [ ] **Step 2: Decision: Keep default sequential or opt-in to parallelization**

Option A (Recommended — sequential by default):
- No workflow changes needed. CI runs with default sequential execution to be conservative.
- Performance: slower but safest.

Option B (Advanced — opt-in parallelization):
- Add `-P parallel_tests` to jobs that run against external Elasticsearch clusters.
- Example:
```
mvn verify -P parallel_tests -Dtests.cluster.url=${{ secrets.ELASTIC_SERVERLESS_URL }} ...
```
- Performance: faster for external cluster jobs, but adds profile dependency.

- [ ] **Step 3: Decision made — no further changes needed**

CI workflows work correctly with the new `parallel_tests` profile either way.
