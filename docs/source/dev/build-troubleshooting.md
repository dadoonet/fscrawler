# Build and test troubleshooting by platform

Return to the [build quick start](build.md) for Maven phases and test commands.
Use these notes to check platform-specific prerequisites and diagnose failures.

## Windows Python executable names

`UpdateReadmeVersionsTest` checks the README version-table markers and launches
Python's `unittest` discovery over `scripts/tests`. The Python suite also covers
documentation markup and release-automation helpers. An unavailable interpreter
is an error, not a skipped test.

The interpreter defaults to `python3`. If your installation exposes `python`
instead, set `tests.python` to the executable name or full path:

```powershell
mvn test -pl distribution -am "-Dtest=UpdateReadmeVersionsTest" "-Dtests.python=python" "-DskipIntegTests" "-Ddocker.skip"
```

An absolute path also works, including spaces, when the entire argument is quoted:

```powershell
mvn test -pl distribution -am "-Dtest=UpdateReadmeVersionsTest" "-Dtests.python=C:/Program Files/Python315/python.exe" "-DskipIntegTests" "-Ddocker.skip"
```

Supply one executable, not a command such as `py -3.15`. The wrapper supplies
Python's arguments. Open a new terminal after an installer changes `PATH`, or use
the explicit interpreter path. No override can install a missing interpreter.
The setting affects this Java test wrapper only; it does not change the system
Python installation or production FSCrawler behaviour. On Linux, no override is
needed when `python3` is on `PATH`.

## Windows local-socket startup

If WireMock or Grizzly reports `Unable to establish loopback connection`, inspect
the nested exception. When it identifies a Windows Unix-domain socket connection
failure, try a short writable temporary directory. Changing the JDK alone may
not resolve a temporary-path problem.

For a PowerShell test session, choose a short writable directory, for example:

```powershell
$testTemp = Join-Path $env:TEMP 'fs-tests'
New-Item -ItemType Directory -Force -Path $testTemp | Out-Null
$env:JAVA_TOOL_OPTIONS = "-Djava.io.tmpdir=$testTemp -Djdk.net.unixdomain.tmpdir=$testTemp"
```

If `TEMP` is itself long, choose a shorter directory. Paths containing spaces
require appropriately quoted JVM option values. This is an environment workaround,
not a demonstrated fix for every cause of socket-startup failures. The setting
applies to subsequent Java processes in that shell session.

## Containers on Windows and Linux

Testcontainers needs a working Docker-compatible API. A Windows Podman machine
can expose a named pipe, for example:

```powershell
$env:DOCKER_HOST = 'npipe:////./pipe/podman-machine-default'
```

Inspect your actual Podman machine connection rather than assuming that pipe name
exists on every installation. Docker installations can expose a different endpoint.
Linux environments need their own reachable container service. A Java/Maven image
does not necessarily include Python or Tesseract.

For Unix-permission and trailing-space-name tests, use the container's native
filesystem. A Windows-backed source mount can change the filesystem behaviour
being tested.

## Filesystem restrictions and optional coverage

Some Windows exclusions are deliberate: Unix owner/group/permission checks,
POSIX unreadable-file checks, symlink scenarios, and trailing-space directory names.
Do not label every skip a missing dependency, or disable a whole portable test class
to hide one incompatible fixture.

### SSH tests on Windows and Unix filesystems

The SSH plugin separates portable configuration tests from its SSH server and
filesystem fixture. `FsSshPluginTest` runs six configuration checks on all
platforms. `FsSshPluginFileSystemTest` checks file listings, Unix permissions and
trailing-space directory names, and is disabled on Windows with
`@DisabledOnOs(OS.WINDOWS)`.

The Windows restriction applies to the filesystem test class, including its
setup. Keeping that setup separate allows the configuration checks to run without
starting an SSH server or creating platform-dependent filenames.

Run both SSH test classes from the repository root:

```shell
mvn test -pl plugins/fs-ssh-plugin -am "-Dtest=FsSshPlugin*Test" "-DskipIntegTests" "-Ddocker.skip"
```

This builds the required modules and selects both SSH classes. Inspect the SSH
module's `target/surefire-reports` directory for their results.

### OCR and service-dependent tests

OCR tests need Tesseract and the language data used by the selected cases. Missing
OCR support accounts for intentional skips, but an `ocr_disabled` test can still
fail an unrelated metadata assertion. Kibana tests and container-start tests also
have service-dependent conditions; inspect the report's actual skip reason.

## Metadata expectations

Tika upgrades can change the metadata returned for existing fixtures. When an
exact metadata assertion fails, compare the added, removed and changed fields
before updating the expected count. Differences can depend on parser configuration
and whether input is supplied as a stream or a file.

See the [field comparison in PR #2608](https://github.com/dadoonet/fscrawler/pull/2608)
for investigation details and [PR #2616](https://github.com/dadoonet/fscrawler/pull/2616)
for the merged Tika 4.2 assertion updates.

## macOS prerequisites

On macOS, verify the Java and Python executables and container service before
running the commands in the build guide. Filesystem-dependent tests also rely on
the permissions and filename behaviour of the filesystem used for test fixtures.
