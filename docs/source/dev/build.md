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

## Verifying Tika metadata expectations

The Word, PDF and JPEG checks in `TikaDocParserTest` assert both the exact number
of raw metadata entries and their expected keys/values. Update these expectations
only after checking the fixture and parser behaviour; changing a count alone can
hide a missing field.

### Word and PDF fixture evidence

For `test.doc`, an independent OLE property reader finds stored line and paragraph
counts of 3 and 2. Tika 4.2.0 deliberately exposes these properties as
`meta:line-count` and `meta:paragraph-count` ([TIKA-4694 in the release notes](https://github.com/apache/tika/blob/4.2.0/CHANGES.txt)).
Its [SummaryExtractor](https://github.com/apache/tika/blob/4.2.0/tika-parsers/tika-parsers-standard/tika-parsers-standard-modules/tika-parser-microsoft-module/src/main/java/org/apache/tika/parser/microsoft/SummaryExtractor.java)
reads the corresponding document-summary properties. With the same fixture,
source, JDK, seed, locale and timezone, Tika 4.1.0 returns 28 entries and 4.2.0
returns 30; these are the only two added fields, with no existing value changes.
The test expects 30 and explicitly checks both new values.

For `test.pdf`, independent inspection finds two pages, a structure tree, the
document's marked-content flag, and marked-content sequences on each page.
Tika's [PDFMarkedContent2XHTML](https://github.com/apache/tika/blob/4.2.0/tika-parsers/tika-parsers-standard/tika-parsers-standard-modules/tika-parser-pdf-module/src/main/java/org/apache/tika/parser/pdf/PDFMarkedContent2XHTML.java)
records tagged and fallback page counts. Both Tika 4.1.0 and 4.2.0 return
`pdf:marked-content-pages-tagged=2` and `pdf:marked-content-pages-fallback=0`.
These are the only two fields beyond the former 47-entry expectation; the other
asserted values match. The test expects 49 and checks both counters explicitly.

### Why JPEG metadata depends on the input and configuration

The `test.jpg` fixture is 41,426 bytes and contains a 3,888-byte ICC profile with
17 tag entries. Reading the ICC tag table directly confirms three `curv` tags
(`rTRC`, `gTRC`, `bTRC`) and three `para` tags (`aarg`, `aagg`, `aabg`).
Their presence in the file does not mean Tika exposes them by default.

Tika 4.1.0 intentionally stopped emitting ICC tone curves and lookup tables by
default ([TIKA-4902 in the release notes](https://github.com/apache/tika/blob/4.1.0/CHANGES.txt)).
The [ImageMetadataConfig default](https://github.com/apache/tika/blob/4.2.0/tika-parsers/tika-parsers-standard/tika-parsers-standard-modules/tika-parser-image-module/src/main/java/org/apache/tika/parser/image/ImageMetadataConfig.java)
is `includeIccCurvesAndLuts=false`; enabling it restores these six fields:

* `icc:Red TRC`, `icc:Green TRC`, `icc:Blue TRC`;
* `icc:Red Parametric TRC`, `icc:Green Parametric TRC`, `icc:Blue Parametric TRC`.

The [JPEG metadata extractor](https://github.com/apache/tika/blob/4.2.0/tika-parsers/tika-parsers-standard/tika-parsers-standard-modules/tika-parser-image-module/src/main/java/org/apache/tika/parser/image/ImageMetadataExtractor.java)
reads a file when its `TikaInputStream` already has one, otherwise it reads the
stream directly. `img:File Name`, `img:File Size` and `img:File Modified Date`
describe that backing file, not embedded image metadata. A file-backed
`TikaInputStream` also supplies `Content-Length`.

A four-way Tika 4.2.0 experiment with the same JPEG confirmed:

| Input | ICC curves enabled | Added fields compared with the default stream |
|-------|--------------------|-----------------------------------------------|
| Stream | No | None |
| Stream | Yes | The six ICC curve fields |
| File | No | `Content-Length` and the three `img:File` fields |
| File | Yes | All ten fields above |

No other metadata values changed across those comparisons. The direct-parser
experiment has fewer parser bookkeeping fields than the full FSCrawler pipeline;
its absolute counts should not be substituted into the FSCrawler test.

The existing FSCrawler JPEG test parses a resource stream with OCR disabled and
the default image configuration. Both Tika 4.1.0 and 4.2.0 return 56 raw entries.
The corrected test preserves all 56 remaining field checks and explicitly asserts
that the ten inapplicable fields are absent. It does not disable colour-profile
extraction, enable optional curve dumps, or change production parsing behaviour.

### Reproducing the focused checks

Historical validation with the same fixture hashes returned:

| Tika version | Word entries | PDF entries | JPEG entries |
|--------------|--------------|-------------|--------------|
| 4.0.0 | 28 | 47 | 66 |
| 4.1.0 | 28 | 49 | 56 |
| 4.2.0 | 30 | 49 | 56 |

The 4.1.0/4.2.0 comparison used the same FSCrawler source revision (`4b914438`).
Current FSCrawler uses page-parser APIs absent from Tika 4.0.0, so the 4.0.0
historical run used its compatible pre-upgrade revision (`0c244410`). All three
original assertions passed there. This older-source comparison is historical
corroboration; the parser sources and documented defaults explain the changes.

Run from the repository root:

```shell
mvn test -pl tika -am "-Dtest=TikaDocParserTest#extractFromDoc+extractFromPdf+extractFromJpg" "-DskipIntegTests" "-Ddocker.skip"
```

Use JDK 21 on both Windows and Linux when comparing platform behaviour. The
metadata investigation fixed the seed (`4C95134AF6B8B696`), locale (`en-US`) and
timezone (`UTC`), and checked fixture SHA-256 hashes between version runs.
Results are in `tika/target/surefire-reports`. Run the remaining Tika unit tests
after changing these expectations; unavailable OCR tools can produce intentional
skips, which should be reported separately from passes.

Validation on 31 October 2026 ran all five Tika unit-test classes (58 reported
tests). Windows/Temurin 21.0.13 and Linux/Temurin 21.0.9 through Podman each
finished with 47 passed, 11 OCR-dependent skips and no failures or errors. The
three metadata checks passed on both platforms. The full repository and its
integration suite were not run for this test-only correction.

On Windows, the first full run encountered the separate WireMock loopback startup
failure. Setting `java.io.tmpdir` and `jdk.net.unixdomain.tmpdir` to a short local
temporary directory for the test process allowed that test to run; the successful
Windows full-suite result includes this environment workaround. The metadata
correction does not resolve the socket-startup issue itself. The Linux result
used a clean build of the final source without the diagnostic capture code.

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
