# Building the project

FSCrawler uses Maven. Source code is available on [GitHub](https://github.com/dadoonet/fscrawler/).
The project compiles for Java {{ java_version }}; the inspected pull-request CI
uses Temurin JDK 21. Use JDK 21 when reproducing CI results. Compiling for Java 17
does not mean the tests execute on Java 17, or that every newer JDK is validated.

```{contents}
:backlinks: entry
```

## Prerequisites and platforms

| Requirement | Windows | Linux | macOS |
|-------------|---------|-------|-------|
| Java and Maven | Use JDK 21; check `mvn --version` | Same | Same |
| Python tests | Verify the executable name; installations may expose `python` or `py` rather than `python3` | `python3` must be installed; minimal Java images may omit it | Verify the installed Python command |
| Container-based tests | Docker-compatible service; check the configured endpoint when using Podman | Docker/Testcontainers, or a compatible configured service | Requires a working container service |
| OCR tests | Tesseract and the language data required by the selected tests | Same | Same |

The same Maven phases apply on every platform. Filesystem permissions, symlinks,
trailing-space filenames, executable names and shell quoting can affect coverage.
Some tests deliberately exclude Windows; unavailable OCR or service prerequisites
can cause additional skips. See [platform troubleshooting](build-troubleshooting.md).

## Clone and check your tools

```shell
git clone https://github.com/dadoonet/fscrawler.git
cd fscrawler
mvn --version
```

Run Maven from the repository root containing `pom.xml`. Verify the Java version
shown by Maven, which can differ from another Java installation on your machine.

## Choose the build you need

Each Maven phase includes the earlier phases:

| Phase | Result | Normal test layer |
|-------|--------|-------------------|
| `compile` | Compile production sources | None |
| `test` | Compile and execute unit tests | Surefire: `*Test` |
| `package` | Also create JARs and distribution archives | Unit tests |
| `verify` | Also execute and check integration tests | Unit and Failsafe: `*IT` |
| `install` | Also install artifacts in the local Maven repository | Both; distribution Docker image build is bound here |
| `deploy` | Publish configured artifacts | Both unless skipped; distribution Docker push is bound here |

`clean` removes previous build outputs. Distribution archives are produced under
`distribution/target`.

### Package without executing tests

```shell
mvn clean package "-DskipTests" "-Ddocker.skip"
```

### Run regular unit tests

```shell
mvn test "-Dtest=*Test" "-DskipIntegTests" "-Ddocker.skip"
```

The explicit selector selects the unit-test classes. Check the executed test count
before treating a successful build as validation. Some unit tests start local
HTTP or SSH servers; they are not all independent of sockets and filesystems.

### Run regular integration tests

```shell
mvn verify "-Pdaily,sequential_tests" "-DskipUnitTests" "-Dit.test=*IT" "-Ddocker.skip"
```

For PowerShell, quote the profile argument as `"-Pdaily,sequential_tests"`.
The `daily` profile excludes tests tagged `Nightly`; `sequential_tests` makes
integration execution sequential. This command may need containers and additional
services even though it skips distribution Docker image building.

### Understand the skip flags

| Flag | Effect |
|------|--------|
| `-DskipUnitTests` | Skip Surefire unit-test execution |
| `-DskipIntegTests` | Skip Failsafe integration-test execution |
| `-DskipTests` | By default, skip both layers; test sources can still be compiled |
| `-Ddocker.skip` | Skip distribution Docker build/push activity; does not disable containers requested by tests |

### Run a selected test or module

Use `test` for Surefire and `it.test` for Failsafe. Selecting one integration test
does not itself skip unit tests. `-pl` selects modules; `-am` also builds their
required project modules.

```shell
mvn test -pl tika -am "-Dtest=TikaDocParserTest#extractFromDoc" "-DskipIntegTests" "-Ddocker.skip"
mvn verify -pl integration-tests -am "-Dit.test=FsCrawlerTestAddNewFilesIT#add_new_files_and_force_rescan" "-DskipUnitTests" "-Ddocker.skip"
```

The quoted selectors and system-property arguments also work in PowerShell.
Use single-line commands there, rather than copying POSIX backslash continuations.

## Integration services and profiles

Elasticsearch integration tests normally start Elasticsearch through
[Testcontainers](https://java.testcontainers.org/modules/elasticsearch/).
Alternatively, set `tests.cluster.url` to an existing **disposable test cluster**.
These tests create and remove indices and other test resources; use a dedicated
instance rather than a cluster containing data you want to retain.

```shell
mvn verify "-Pdaily,sequential_tests" "-DskipUnitTests" "-Dit.test=*IT" "-Ddocker.skip" "-Dtests.cluster.url=http://localhost:9200"
```

The full client suite exercises API-key creation, so disabling Elasticsearch
security is not equivalent to providing the required authenticated test service.
The test defaults use `elastic`/`changeme`; `tests.cluster.apiKey` supplies an API
key where appropriate. Use the endpoint's actual HTTP/HTTPS scheme. Trust the
cluster certificate when using HTTPS; `tests.cluster.check_ssl=false` disables
verification for tests against a self-signed test endpoint.

An external Elasticsearch service does not remove other requirements:

* S3 tests start MinIO and require access to the configured container image.
* Some HTTP tests start a local container; others fetch public HTTPS endpoints
  and depend on network access and the test JVM's certificate trust.
* `tests.kibana.url` enables the dashboard integration test; it is skipped when
  no Kibana endpoint is supplied.
* Tesseract and the required language data enable OCR coverage.
* The container-start helper test intentionally skips when an external cluster
  is supplied, so that coverage needs a separate container-start run.

### Elasticsearch versions and execution profiles

The inspected configuration defaults to Elasticsearch 9, with `es-8x` and `es-7x`
profiles selecting their configured versions. The PR matrix explicitly exercises
Elasticsearch 8 and 9; the existence of a profile is not proof of current CI coverage.

Unit tests are configured to execute sequentially; integration tests are configured
for concurrent execution. Use `sequential_tests` to disable integration parallelism.
Specify `daily` explicitly when combining profiles to retain its nightly exclusion:
activating another profile can deactivate Maven's `activeByDefault` profile.

The `nightly` profile selects only `Nightly`-tagged tests, rather than adding them
to the regular tests. Nightly coverage is separate from the regular quick start.

### Useful test settings

| Property | Purpose |
|----------|---------|
| `tests.rest.port` | REST port; `0` selects a random port |
| `tests.leaveTemporary` | Retain temporary files/containers when supported; defaults to `false` |
| `tests.output` | Redirect test output to report files; defaults to `false` |
| `tests.seed` | Reproduce randomized test data |
| `tests.locale`, `tests.timezone` | Reproduce the test locale and timezone |
| `tests.timeoutSuite`, `tests.timeout` | Forwarded timeout settings; class/method annotations also define execution limits |

Use the reproduction command printed for a failure, including its seed, locale
and timezone. Very slow tests can belong to the daily profile; a long runtime
does not by itself mean a nightly test was selected.

## Interpret the results

Read each module's `target/surefire-reports` for unit results and
`target/failsafe-reports` for integration results. Distinguish passes, failures,
errors and skips; a skipped test supplies no validation of its behaviour.

`maven.test.failure.ignore=true` is useful when deliberately collecting failures
across modules, but can produce `BUILD SUCCESS` while tests fail. It should not
be used to make CI green. Also verify that tests actually executed: excluded
tags and discovery settings can leave cases unselected rather than reported as
skipped.

See [platform troubleshooting](build-troubleshooting.md) for executable names,
temporary paths and filesystem restrictions. Check the documentation version and
the checked-out revision before relying on a recently introduced setting.

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

The Docker image build is bound to the Maven `install` phase in the distribution module. If you want to skip the build of the images,
you can manually use the `docker.skip` option:

```shell
mvn install "-Ddocker.skip"
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
