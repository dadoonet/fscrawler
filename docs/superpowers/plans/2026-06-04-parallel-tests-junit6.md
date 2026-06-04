# Conditional Parallel Test Execution in JUnit 6 — Implementation Plan

> **Pour exécuter ce plan:** Utilisez superpowers:subagent-driven-development (recommandé) pour dispatcher une sous-tâche par task avec review intermédiaire, ou superpowers:executing-plans pour exécution inline avec checkpoints.

**Objectif:** Activer la parallélisation des tests JUnit 6 à deux niveaux (classes + méthodes) automatiquement quand `-Dtests.cluster.url` est fourni, tout en gardant les tests séquentiels avec TestContainers.

**Architecture:** Un seul profil Maven conditionnel (`cluster-parallel`) qui s'active si `-Dtests.cluster.url` est présent. Le profil override la propriété `junit.jupiter.execution.parallel.mode.default` de `same_thread` à `concurrent`. Aucun autre changement requis.

**Tech Stack:** Maven profiles, JUnit 6 parallel execution configuration

---

## File Structure

**Modification:**
- `pom.xml` — Ajouter le profil `cluster-parallel` dans la section `<profiles>`

---

## Task 1: Add `cluster-parallel` Profile to pom.xml

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

- [ ] **Step 3: Add the new `cluster-parallel` profile before the closing `</profiles>` tag**

Insert this profile (adjust indentation to match existing profiles):

```xml
        <profile>
            <id>cluster-parallel</id>
            <activation>
                <property>
                    <name>tests.cluster.url</name>
                </property>
            </activation>
            <properties>
                <!-- Enable parallel execution of test methods when running against a live Elasticsearch cluster -->
                <junit.jupiter.execution.parallel.mode.default>concurrent</junit.jupiter.execution.parallel.mode.default>
            </properties>
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
git commit -m "feat(test): add cluster-parallel profile for conditional parallel test execution

- Automatically enable two-level parallelization (test classes and methods)
- Activation: when -Dtests.cluster.url is provided
- Falls back to sequential execution for TestContainers scenarios
- No CI workflow changes required"
```

---

## Task 2: Verify Profile Activation with Live Elasticsearch

**Files:**
- Test: Run integration tests with `-Dtests.cluster.url`

- [ ] **Step 1: Start a local Elasticsearch instance (if needed)**

Activate the `start-elasticsearch` skill:
```bash
/start-elasticsearch
```

Or manually start ES on `http://localhost:9200` with user `elastic` and password `changeme`.

Wait for ES to be ready.

- [ ] **Step 2: Run a single integration test with `-Dtests.cluster.url` to verify parallelization activates**

```bash
mvn verify -pl integration-tests -am -DskipUnitTests \
  -Dtests.cluster.url=http://localhost:9200 \
  -Dit.test=FsCrawlerTestAddNewFilesIT#add_new_files_and_force_rescan \
  -Dtests.output=true
```

Expected: Test passes. Check the console output for parallel execution indicators (multiple tests running concurrently).

- [ ] **Step 3: Verify the profile is active (optional — check Maven output)**

Look for a line like:
```
[INFO] --- maven-help-plugin:3.x.x:active-profiles (default-cli) @ <module> ---
[INFO] Active profiles: es-8x, cluster-parallel
```

If you see `cluster-parallel` in the active profiles list, the activation condition worked.

- [ ] **Step 4: Run a broader set of integration tests**

```bash
mvn verify -pl integration-tests -am -DskipUnitTests \
  -Dtests.cluster.url=http://localhost:9200 \
  -Dtests.parallelism=1 \
  -Dtests.output=false
```

Expected: All tests pass with parallel execution enabled.

---

## Task 3: Verify Sequential Execution Without `-Dtests.cluster.url`

**Files:**
- Test: Run unit tests without the cluster URL (TestContainers scenario)

- [ ] **Step 1: Run unit tests (no integration tests, no `-Dtests.cluster.url`)**

```bash
mvn clean test -DskipIntegTests
```

Expected: All unit tests pass. The `cluster-parallel` profile should NOT be active (no `-Dtests.cluster.url`).

Check output: Should NOT show `cluster-parallel` in active profiles.

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

## Task 4: Verify CI Workflows Are Unaffected

**Files:**
- Review (no changes): `.github/workflows/pr.yml`

- [ ] **Step 1: Review the PR workflow to confirm it already uses `-Dtests.cluster.url`**

```bash
grep -n "Dtests.cluster.url" .github/workflows/pr.yml
```

Expected: Multiple matches showing existing `-Dtests.cluster.url` usage in CI jobs.

- [ ] **Step 2: Confirm no workflow changes are needed**

Existing CI commands like:
```
mvn verify -Dtests.cluster.url=${{ secrets.ELASTIC_SERVERLESS_URL }} ...
```

will automatically activate the `cluster-parallel` profile now, resulting in faster test execution with zero workflow changes.

- [ ] **Step 3: Note: No commit needed**

CI workflows run unchanged and benefit from parallelization automatically.
