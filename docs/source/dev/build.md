# Building the project

This project is built with [Maven](https://maven.apache.org/). It needs Java >= {{ java_version }}.
Source code is available on [GitHub](https://github.com/dadoonet/fscrawler/).
Thanks to [JetBrains](https://www.jetbrains.com/?from=FSCrawler) for the IntelliJ IDEA License!

[![Jet Brains](../jetbrains.png)](https://www.jetbrains.com/?from=FSCrawler)

```{contents}
:backlinks: entry
```

## Clone the project

Use git to clone the project locally:

```shell
git clone git@github.com:dadoonet/fscrawler.git
cd fscrawler
```

## Build the artifact

To build the project, run:

```shell
mvn clean package
```

The final artifacts are available in `distribution/target`.

````{tip}

 To build it faster (without tests), run:

 ```shell
 mvn clean package -DskipTests
 ```
````

## Integration tests

When running from the command line with `mvn` integration tests are ran against a real
Elasticsearch instance launched using Docker (via [Testcontainers](https://java.testcontainers.org/modules/elasticsearch/)).

### Run tests from your IDE

To run integration tests from your IDE, you need to start tests in `fscrawler-it` module.
But you can specify the Maven profile to use and rebuild the project.

* `es-8x` for Elasticsearch 8.x
* `es-7x` for Elasticsearch 7.x

### Faster integration tests

As we are using [Testcontainers](https://java.testcontainers.org/modules/elasticsearch/),
we can [reuse](https://java.testcontainers.org/features/reuse/) the Elasticsearch container instead of having to restart
one everytime.

```{note} 

 You need to explicitly [enable this feature](https://java.testcontainers.org/features/reuse/).
```

If you run from the IDE, reusing containers is the default behavior. But if you run the CLI, you need
to set `tests.leaveTemporary` to `true`:

```shell
mvn verify -Dtests.leaveTemporary=true
```

### Parallel test execution

By default, parallel test execution is disabled to avoid static field issues (shared ElasticsearchClient for example).
To enable parallel execution of test classes and methods, use the `parallel_tests` Maven profile:

```shell
mvn verify -P parallel_tests
```

This profile enables JUnit 6's parallel execution engine, running both test classes concurrently
and test methods within each class in parallel.

### Run a specific test from your Terminal

To run a specific unit test, just run:

```shell
mvn verify -DskipIntegTests -Dtest=CLASS_NAME#METHOD_NAME
```

To run a specific integration test, just run:

```shell
mvn verify -DskipUnitTests -Dit.test=CLASS_NAME#METHOD_NAME
```

```{note}

 Integration tests (`*IT.java`) are run by `maven-failsafe-plugin` and use `-Dit.test=`.
 Unit tests (`*Test.java`) are run by `maven-surefire-plugin` and use `-Dtest=`.
```

### Run tests with an external cluster

Launching the docker containers might take some time so if to want to run the test suite against an already running
cluster, you need to provide a `tests.cluster.url` value. This will skip launching the docker instances.

To run the test suite against an elasticsearch instance running locally, just run:

```shell
mvn verify -pl fr.pilato.elasticsearch.crawler:fscrawler-it -Dtests.cluster.url=https://localhost:9200
```

````{hint}

 If you are using an external cluster, you must set the `tests.cluster.apiKey` if your cluster does not use
 `elastic` and `changeme` as their credentials, and it's anyway the recommended approach:

 ```shell
 mvn verify -pl fr.pilato.elasticsearch.crawler:fscrawler-it \
     -Dtests.cluster.apiKey=APIKEYHERE \
     -Dtests.cluster.url=https://localhost:9200 \
 ```

 If the cluster is using a self generated SSL certificate, you can bypass checking the certificate by using
 `tests.cluster.check_ssl`:

 ```shell
 mvn verify -pl fr.pilato.elasticsearch.crawler:fscrawler-it \
     -Dtests.cluster.apiKey=APIKEYHERE \
     -Dtests.cluster.url=https://localhost:9200 \
     -Dtests.cluster.check_ssl=false
 ```
````

````{hint}

 To run tests against another instance (ie. running on
 [Elasticsearch service by Elastic](https://www.elastic.co/cloud/elasticsearch-service),
 you can also use `tests.cluster.url` to set where elasticsearch is running::

 ```shell
 mvn verify -pl fr.pilato.elasticsearch.crawler:fscrawler-it \
     -Dtests.cluster.apiKey=APIKEYHERE \
     -Dtests.cluster.url=https://ALIAS.es.REGION.CLOUD_PROVIDER.elastic.cloud
 ```

 You can use both Elasticsearch service and Serverless projects.

 To also run the Kibana dashboard integration test (`FsCrawlerTestKibanaDashboardIT`), set
 `tests.kibana.url`. This IT is skipped unless that property is set (CI enables it only on Cloud and
 Serverless jobs to avoid starting Kibana in the default TestContainers matrix):

 ```shell
 mvn verify -pl fr.pilato.elasticsearch.crawler:fscrawler-it \
     -Dtests.cluster.apiKey=APIKEYHERE \
     -Dtests.cluster.url=https://ALIAS.es.REGION.CLOUD_PROVIDER.elastic.cloud \
     -Dtests.kibana.url=https://ALIAS.kb.REGION.CLOUD_PROVIDER.elastic.cloud
 ```
````

### Tika raw-metadata count expectations and history

The expected raw-metadata counts for the Word, PDF and JPEG fixtures are literal
AssertJ `hasSize` assertions in
`tika/src/test/java/fr/pilato/elasticsearch/crawler/fs/tika/TikaDocParserTest.java`.
They check the number of entries in `doc.getMeta().getRaw()`, not the document's
file size or extracted text length. They are test expectations, not configurable
FSCrawler settings.

At upstream revision `4b9144387ec5177c2498ad49f157eaf9cb14cd7e`, the assertions are:

| Test method | Fixture | Expected entries | Source at that revision |
|-------------|---------|------------------|-------------------------|
| `extractFromDoc` | `test.doc` | 28 | [Line 226](https://github.com/dadoonet/fscrawler/blob/4b9144387ec5177c2498ad49f157eaf9cb14cd7e/tika/src/test/java/fr/pilato/elasticsearch/crawler/fs/tika/TikaDocParserTest.java#L226) |
| `extractFromPdf` | `test.pdf` | 47 | [Line 468](https://github.com/dadoonet/fscrawler/blob/4b9144387ec5177c2498ad49f157eaf9cb14cd7e/tika/src/test/java/fr/pilato/elasticsearch/crawler/fs/tika/TikaDocParserTest.java#L468) |
| `extractFromJpg` | `test.jpg` | 66 | [Line 545](https://github.com/dadoonet/fscrawler/blob/4b9144387ec5177c2498ad49f157eaf9cb14cd7e/tika/src/test/java/fr/pilato/elasticsearch/crawler/fs/tika/TikaDocParserTest.java#L545) |

The following milestones trace the current assertions and their immediate
predecessors. Dates in this table are Git author dates; a change can have a later
committer or merge date.

| Author date | Change | Word | PDF | JPEG |
|-------------|--------|------|-----|------|
| 4 April 2025 | [Switch to AssertJ (`484b2758`)](https://github.com/dadoonet/fscrawler/commit/484b2758bebf762286261da2d79726acd6a9508e): establishes the AssertJ form of the Word/PDF count checks | 25 | 42 | Not introduced by this change |
| 11 November 2025 | [Add Image parsers (`1e1a72b1`)](https://github.com/dadoonet/fscrawler/commit/1e1a72b164fd1f4a04fb635fb6976fe36afb3364): adds the JPEG count assertion | 25 | 42 | 64 |
| 7 July 2026 | [Update to Tika 4 (`897aff27`)](https://github.com/dadoonet/fscrawler/commit/897aff27e41eeb2241537951ba400929b3a46c68): changes all three counts and updates expected metadata entries | 25 → 28 | 42 → 47 | 64 → 66 |
| 24 August 2026 | [Fix some metadata that have been renamed (`22ebb023`)](https://github.com/dadoonet/fscrawler/commit/22ebb0238fd0c0f7103033efb9c743c9f6844da6): updates metadata keys while retaining these counts | 28 | 47 | 66 |
| 29 October 2026 | [Upgrade Tika 4.1.0 to 4.2.0 (`3fd421cc`)](https://github.com/dadoonet/fscrawler/commit/3fd421cc26718c9508c6b632e18fc4bdbd41070c), merged in [PR #2601](https://github.com/dadoonet/fscrawler/pull/2601): changes the dependency and documented version, not these tests | 28 | 47 | 66 |

The Tika 4 update was authored on 7 July 2026 but has a committer date of
24 August 2026. Git blame attributes all three current `hasSize` lines to that
commit. This distinction explains why an author-date history and a chronological
integration history can display different dates for the same change.

#### Observed mismatch on Windows and Linux

On 31 October 2026, local validation of unchanged upstream revision `4b914438`
reported the same three count failures on Windows (Microsoft JDK 25.0.2) and
Linux through Podman (Temurin JDK 21.0.9), using Maven 3.9.11:

| Fixture | Expected | Windows actual | Linux actual |
|---------|----------|----------------|--------------|
| Word | 28 | 30 | 30 |
| PDF | 47 | 49 | 49 |
| JPEG | 66 | 56 | 56 |

The Linux run selected only these three methods, with no failures ignored. The
Word and PDF tests passed their preceding text, content-type and basic metadata
checks before failing on the raw-map size. The JPEG test disables OCR and also
expects specific fields that are absent from the observed map, so changing only
the expected count would not resolve all of its assertions.

These observations establish that the count mismatches are not Windows-specific.
They do not establish which dependency change first caused them, whether the
observed metadata is correct, or whether macOS behaves the same way. In particular,
the Tika 4.2.0 upgrade is historical context, not a proven cause. This documentation
does not change the assertions or production parsing behaviour.

To reproduce the focused checks from the repository root:

```shell
mvn test -pl tika -am "-Dtest=TikaDocParserTest#extractFromDoc+extractFromPdf+extractFromJpg" "-DskipIntegTests" "-Ddocker.skip"
```

The Linux validation used the `maven:3.9.11-eclipse-temurin-21` image, source copied
into the container's native filesystem, and `-Dspotless.skip` because the source
archive did not include Git metadata. Results are written to
`tika/target/surefire-reports`. This focused command does not run the full Tika or
repository test suite.

### Changing the REST port

By default, FS crawler will run the integration tests using a randomly chosen port for the REST service.
You can change this by using `tests.rest.port` option:

```shell
mvn verify -Dtests.rest.port=8280
```

When set to `0` (default value), the port is assigned randomly.

### Randomized testing

FS Crawler uses the [randomized testing framework](https://github.com/randomizedtesting/randomizedtesting-jupiter).
In case of failure, it will print a line like:

```none
REPRODUCE WITH:
mvn verify -pl integration-tests -am -Dit.test=FsCrawlerTestOcrIT#ocr_disabled -Dtests.seed=AC6992149EB4B547 -Dtests.locale=tr-TR
```

You can just run the test again using the same seed to make sure you always run the test in the same context as before.

### Tests options

Some options are available from the command line when running the tests:

* `tests.leaveTemporary` leaves temporary files after tests (and also the TestContainers instance). `false` by default.
* `tests.parallelism` set to `true` if you want to allow multiple suites to be ran in parallel. Default to `false`.
* `tests.output` set to `true` if you want to redirect the tests output redirected to a file.
Defaults to `false`.
* `tests.seed` if you need to reproduce a specific failure using the exact same random seed.
* `tests.timeoutSuite` how long a full suite of tests can run. It's set by default to `120` which means 2 minutes.
* `tests.timeout` how long a single test can run. It's set by default to `60` which means 1 minute.
* `tests.locale` by default it's set to `random` but you can force the locale to use.
* `tests.timezone` by default it's set to `random` but you can force the timezone to use, like `CEST` or `-0200`.

For example:

```shell
mvn install -rf :fscrawler-it \
    -Dtests.output=always \
    -Dtests.locale=fr-FR \
    -Dtests.timezone=CEST \
    -Dtests.verbose \
    -Dtests.leaveTemporary \
    -Dtests.seed=E776CE45185A6E7A
```

### Nightly tests

To run the tests in a more exhaustive way, you can use the `nightly` profile which will run only the longest tests:

```shell
mvn verify -Pnightly
```

## Code formatting (Spotless)

The project uses [Spotless](https://github.com/diffplug/spotless) to enforce consistent code formatting
(Java via Palantir Java Format, POM sorting, JSON and YAML formatting).

To check that the code you changed since `origin/main` is correctly formatted, run:

```shell
mvn spotless:check
```

To check the **entire** codebase (not just files changed since `origin/main`):

```shell
mvn spotless:check -DratchetFrom=NONE
```

To **automatically fix** formatting issues:

```shell
mvn spotless:apply
```

To install a **git pre-push hook** that runs the formatting check automatically before each push:

```shell
mvn spotless:install-git-pre-push-hook
```

```{tip}

 Run `mvn spotless:apply` before committing to avoid CI failures due to formatting.
```

## Check for vulnerabilities (CVE)

The project is using [OSS Sonatype service](https://ossindex.sonatype.org/) to check for known
vulnerabilities. This is run during the `verify` phase.

Sonatype provides this service but with a anonymous account, you might be limited
by the number of tests you can run during a given period.

You can bypass this limit by creating a [Personal Access Token (PAT)](https://guide.sonatype.com/settings/tokens)
and then set the `sonatype.username` and `sonatype.password` properties:

```shell
mvn verify -DskipTests \
    -Dsonatype.username=youremail@domain.com \
    -Dsonatype.password=YOUR_PAT
```

If you want to just warn but not fail, you can run with `-Dossindex.fail=false`:

```shell
mvn verify -Dossindex.fail=false
```

If you want to skip the check, you can run with `-Dossindex.skip`:

```shell
mvn verify -Dossindex.skip
```

If a CVE needs a temporary exclusion, you can add it to the `excludeVulnerabilityIds` list
of the `ossindex` maven plugin in the `pom.xml` file:

```xml
<configuration>
    <excludeVulnerabilityIds>
        <!-- LINK TO CVE and COMMENT -->
        <excludeVulnerabilityId>CVE-2022-1471</excludeVulnerabilityId>
    </excludeVulnerabilityIds>
</configuration>
```

## Docker build

The docker images build is run when calling the maven `package` phase. If you want to skip the build of the images,
you can manually use the `docker.skip` option:

```shell
mvn package -Ddocker.skip
```

## Docker Hub tags

Each image is published under a version tag ({{ release_image_tags }}) plus a **floating** alias:

| Build | OCR alias | No-OCR alias |
|---|---|---|
| SNAPSHOT (`main`, no extra profile) | `snapshot` | `snapshot-noocr` |
| Release (`-Prelease`) | `latest` | `noocr` |

`docker pull dadoonet/fscrawler` therefore always means the last **stable** release.
Do not retag `latest` from a SNAPSHOT push.

To point `latest` at an already published release without rebuilding (multi-arch
manifest, amd64 + arm64), copy the existing tag:

```shell
docker buildx imagetools create --tag dadoonet/fscrawler:latest dadoonet/fscrawler:3.0
docker buildx imagetools create --tag dadoonet/fscrawler:noocr dadoonet/fscrawler:3.0-noocr
```

`docker tag` + `docker push` on a single machine would drop the other architecture.
Docker Hub's UI is not a reliable way to copy a multi-platform manifest.

## DockerHub publication

To publish the latest build to [DockerHub](https://hub.docker.com/r/dadoonet/fscrawler/) you can manually
call `docker:push` maven task and provide credentials `docker.push.username` and `docker.push.password`:

```shell
mvn -f distribution/pom.xml docker:push \
    -Ddocker.push.username=yourdockerhubaccount \
    -Ddocker.push.password=yourverysecuredpassword
```

Otherwise, if you call the maven `deploy` phase, it will be done automatically.
Note that it will still require that you provide the credentials `docker.push.username` and `docker.push.password`:

```shell
mvn deploy \
    -Ddocker.push.username=yourdockerhubaccount \
    -Ddocker.push.password=yourverysecuredpassword
```

You can also provide the settings as environment variables:

*  `env.DOCKER_USERNAME` or `DOCKER_USERNAME`
*  `env.DOCKER_PASSWORD` or `DOCKER_PASSWORD`
