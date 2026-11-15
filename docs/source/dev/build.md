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

## Build and test prerequisites

Maven runs the build and Java tests in the environment where you launch it.
Testcontainers is a Java library that starts supporting services through a
Docker-compatible runtime; it does not run the Maven build itself in a container.

| Task | Requirements |
|------|--------------|
| Compile and package without tests | JDK and Maven; no Elasticsearch or container runtime required |
| Unit tests (`*Test.java`) | JDK and Maven; local temporary files and embedded Java servers for some tests; no Docker required |
| Distribution unit tests | Python, in addition to the Java test requirements; see [Python tests](#python-tests-in-the-distribution-module) |
| Integration tests (`*IT.java`) | A running Docker-compatible runtime for Elasticsearch, MinIO and Nginx test services; see [Integration tests](#integration-tests) |
| OCR test coverage | Tesseract and the language data needed by the tests in the environment running Maven; some OCR tests skip when Tesseract is unavailable |
| Build FSCrawler Docker images | A running Docker-compatible runtime; image construction is bound to Maven's `install` phase |

The tests use JUnit Jupiter with RandomizedTesting. Unit tests run through Maven
Surefire during `test`; integration tests run through Maven Failsafe during
`integration-test` and are checked during `verify`.

### Windows and macOS setup

[Docker Desktop](https://docs.docker.com/desktop/) supplies a Linux container
runtime on both platforms. Start it before running container-based tests or
building images. Testcontainers normally detects Docker Desktop automatically;
alternative runtimes such as Podman and Colima can need additional socket and
network settings. See the [Testcontainers runtime requirements](https://java.testcontainers.org/supported_docker_environment/).

| Aspect | Windows | macOS |
|--------|---------|-------|
| Container runtime | Docker Desktop in Linux-container mode, commonly using its WSL 2 backend | Docker Desktop running a Linux virtual machine |
| Java/Maven environment | Native Windows from PowerShell, or Linux inside WSL | Native macOS from a terminal |
| Filesystem used by Java tests | Windows rules for native Maven; Linux rules inside WSL, subject to the filesystem holding the checkout | macOS filesystem rules |
| Python executable | May need `"-Dtests.python=python"` or a full interpreter path | Uses `python3` by default; install it if missing |

Running test services in Linux containers does not change the filesystem rules
for Java tests running on the host. Native Windows runs skip selected tests that
require Unix permissions, symlinks or trailing-space filenames. See
[SSH tests on Windows and Unix filesystems](#ssh-tests-on-windows-and-unix-filesystems).
The Windows-specific exclusions do not apply on macOS, but that does not establish
full macOS test coverage.

If you run Maven inside WSL, install the JDK, Maven, Python and any OCR tools in
that Linux environment and enable Docker Desktop integration for the distribution.
Keep the checkout in WSL's Linux filesystem when testing Unix filesystem behaviour;
a checkout under `/mnt/c` retains Windows-backed filesystem constraints. See
[Docker's WSL guidance](https://docs.docker.com/desktop/features/wsl/).
Installing Tesseract in the FSCrawler Docker image does not provide it to tests
running in the host JVM.

## Build the artifact

To build the project, run:

```shell
mvn clean package
```

The final artifacts are available in `distribution/target`.

### Common build and test commands

Run these commands from the repository root. The quoted Maven properties work in
PowerShell and macOS shells. Adjust `tests.python` as described above if necessary.

| Purpose | Command | Container runtime needed? |
|---------|---------|---------------------------|
| Package without running tests | `mvn clean package "-DskipTests" "-Ddocker.skip"` | No |
| Run unit tests | `mvn clean test "-DskipIntegTests" "-Ddocker.skip"` | No |
| Run unit and integration tests | `mvn clean verify "-Ddocker.skip"` | Yes, for container-based tests |
| Install artifacts without building Docker images, with tests | `mvn clean install "-Ddocker.skip"` | Yes, for container-based tests |
| Install artifacts and build Docker images without running tests | `mvn clean install "-DskipTests"` | Yes, for image construction |

`package` runs unit tests but does not reach the integration-test phases.
`verify` runs both test suites, and `install` also builds the distribution's Docker
images unless `docker.skip` is set. See [Docker build](#docker-build).

`-Ddocker.skip` controls the Docker image build, not Testcontainers. Use
`-DskipIntegTests` to skip integration tests, `-DskipUnitTests` to skip unit tests,
or `-DskipTests` to skip both suites.

````{tip}

 To build it faster (without tests), run:

 ```shell
 mvn clean package -DskipTests
 ```
````

## SSH tests on Windows and Unix filesystems

The SSH plugin separates portable configuration checks from its local SSH server
and filesystem fixture:

* `FsSshPluginTest` contains six checks for the plugin type, default SFTP URL and
  port, required hostname and username, blank username rejection, and a missing
  overlay path. These checks do not start an SSH server or create the SSH filesystem
  fixture, and run on Windows as well as Linux.
* `FsSshPluginFileSystemTest` contains the existing `sshPlugin` test, its
  `@BeforeEach` filesystem/server setup, `@AfterEach` cleanup, and fixture helpers.
  It checks file listings, Unix permissions and a directory whose name ends in a
  space. A class-level `@DisabledOnOs(OS.WINDOWS)` prevents both the test and its
  per-test setup from running on Windows. It remains part of the ordinary Maven
  unit-test suite on other operating systems.

Previously, all seven checks shared the filesystem setup. On Windows,
`Path.resolve` rejected the fixture name `"subdir_with_space "` with
`InvalidPathException` before any test assertions ran. Moving the setup out of the
configuration test class allows those six checks to execute, rather than skipping
them. Only the filesystem-dependent check is skipped on Windows, consistent with
the existing trailing-space SSH integration-test restriction. Production SSH code
and configuration behaviour are unchanged.

### Run both SSH test classes

Run this command from the repository root. Quoting the test selector also makes it
suitable for PowerShell:

```shell
mvn test -pl plugins/fs-ssh-plugin -am "-Dtest=FsSshPlugin*Test" "-DskipIntegTests" "-Ddocker.skip"
```

This compiles the required modules and selects both SSH test classes. It does not
run the complete repository test suite. Inspect the SSH module's
`target/surefire-reports` directory for the individual results.

### Windows and Linux validation

The test split was validated locally on 31 October 2026 with Maven 3.9.11:

| Environment | Passed | Skipped | Failures/errors |
|-------------|--------|---------|-----------------|
| Windows, Microsoft JDK 25.0.2 | 6 | 1 (filesystem test) | 0 |
| Linux through Podman, Temurin JDK 21 | 7 | 0 | 0 |

The unchanged Windows test class first reproduced all seven setup errors. After
the split, the six portable checks passed on Windows, while Linux exercised all
seven checks including the real local SSH server and filesystem assertions.
The Linux run used the `maven:3.9.11-eclipse-temurin-21` image and source copied into
the container's native filesystem, rather than a Windows-backed source mount.
This preserves Unix permissions and trailing-space filename behaviour during the
test. The disposable test container was removed after collecting the reports.

Spotless and Git whitespace checks also passed. These results cover the focused
SSH tests, not the full unit or integration suite, and do not establish macOS
coverage or a broader supported-JDK matrix.

## Integration tests

Integration tests use real services, including Linux containers managed by
[Testcontainers](https://java.testcontainers.org/). Start the container runtime
before running the full integration suite. The first run downloads the required
images; subsequent runs can use the local image cache.

| Service | How tests provide it |
|---------|----------------------|
| Elasticsearch | The main crawler integration suite tries its configured endpoint, defaulting to `https://127.0.0.1:9200`. If the default endpoint is unavailable, it starts an Elasticsearch container using the configured integration-test version, mapped port and container certificate when present. |
| MinIO | Containers started by S3 plugin and selected REST tests provide an S3-compatible service. |
| Nginx | Containers started by HTTP plugin, selected REST and Elasticsearch client tests serve HTTP content. |
| SSH/SFTP and FTP | Tests start embedded Java SSH and fake FTP servers; no separate SSH or FTP container is required. |
| Kibana | Optional external service for the dashboard test, enabled with `tests.kibana.url`; it is not started by the default container setup. |

The crawler and its REST server run in the test JVM. You do not need to build the
FSCrawler Docker images to run these tests. The main crawler suite shares its
Elasticsearch client and container within the test JVM; other test modules can
start their own containers.

Some container-based tests use `@DisabledIfNoDocker` and skip when no runtime is
available. Check the Failsafe reports in each module's `target/failsafe-reports`
directory for skipped tests before treating a successful run as full coverage.

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

To use an already running Elasticsearch cluster, provide `tests.cluster.url`.
For the main crawler suite, a non-default endpoint that cannot be reached causes
an error rather than falling back to a container. Use the protocol exposed by your
cluster (`http://` or `https://`).

This replaces the Elasticsearch container for tests that support an external
cluster. It does not replace the MinIO or Nginx containers started by other tests,
so the full integration suite still needs a container runtime.

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

## Python tests in the distribution module

`UpdateReadmeVersionsTest` checks the README version-table markers and launches
Python's `unittest` discovery over `scripts/tests`. That Python suite also covers
documentation markup and release-automation helpers. Python must be installed to
run it; an unavailable interpreter is an error, not a skipped test.

The interpreter defaults to `python3`. If your installation uses another name,
set the Maven system property `tests.python` to the executable name or full path.
For example, a Windows installation exposing `python.exe` can use:

```powershell
mvn test -pl distribution -am "-Dtest=UpdateReadmeVersionsTest" "-Dtests.python=python" "-DskipIntegTests" "-Ddocker.skip"
```

An absolute path avoids relying on `PATH`. Quote the entire Maven argument when
the path contains spaces:

```powershell
mvn test -pl distribution -am "-Dtest=UpdateReadmeVersionsTest" "-Dtests.python=C:/Program Files/Python315/python.exe" "-DskipIntegTests" "-Ddocker.skip"
```

The property is one executable, not a shell command: do not supply `py -3.15` or
append Python arguments. The test adds `-m unittest discover` and its discovery
arguments itself, and passes the executable as a single `ProcessBuilder` argument.
Choose the full interpreter path when you need a particular installed version.

On Linux, no override is needed when `python3` is on `PATH`. Minimal Java/Maven
container images may not include Python; install it before running these tests.
The setting affects this Java test wrapper only. It does not change your system
Python installation, release scripts, or production FSCrawler behaviour.

## Docker build

The distribution module uses the Fabric8 Docker Maven plugin to build OCR and
no-OCR FSCrawler images during the Maven `install` phase. `package` produces the
distribution archive and `verify` runs integration tests, without constructing
these images.

To install artifacts without building images, use `docker.skip`:

```shell
mvn clean install "-Ddocker.skip"
```

Tests still run with this command, including tests that use Testcontainers. To
build the images while skipping both test suites, use:

```shell
mvn clean install "-DskipTests"
```

The container runtime must be running for image construction. The first OCR image
build can take substantially longer because it installs Tesseract and language
packages. Image publication is a separate step bound to `deploy`; see
[DockerHub publication](#dockerhub-publication).

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
