# Build and test troubleshooting by platform

Return to the [build quick start](build.md) for Maven phases and test commands.
These notes distinguish verified local observations from pending changes.

## Windows Python executable names

The upstream revision audited on 31 October 2026 (`4b914438`) launches `python3`
from `UpdateReadmeVersionsTest`. A Python installation exposing only `python` or
`py` can pass the Python tests directly while that Java wrapper fails before
starting them. Python 3.15.0 passed all 25 discovered script tests on the audited
Windows host; Python 3.12.3 passed them in Linux.

[PR #2609](https://github.com/dadoonet/fscrawler/pull/2609) introduces
`tests.python`, retaining `python3` as its default. **The following override requires
that change to be present in your checkout.** An open PR is not a released feature.

```powershell
mvn test -pl distribution -am "-Dtest=UpdateReadmeVersionsTest" "-Dtests.python=python" "-Ddocker.skip"
```

An absolute path also works, including spaces, when the entire argument is quoted:

```powershell
mvn test -pl distribution -am "-Dtest=UpdateReadmeVersionsTest" "-Dtests.python=C:/Program Files/Python315/python.exe" "-Ddocker.skip"
```

Supply one executable, not a command such as `py -3.15`. The wrapper supplies
Python's arguments. Open a new terminal after an installer changes `PATH`, or use
the explicit interpreter path. No override can install a missing interpreter.

## Windows local-socket startup

The audited Windows host encountered `Unable to establish loopback connection`
while starting WireMock/Grizzly. The nested exception involved a Windows
Unix-domain socket connection. Short temporary paths allowed the affected tests
to execute, including on JDK 21; changing the JDK alone was insufficient there.

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

Testcontainers needs a working Docker-compatible API. For the audited Windows
Podman machine, the connection used was:

```powershell
$env:DOCKER_HOST = 'npipe:////./pipe/podman-machine-default'
```

Inspect your actual Podman machine connection rather than assuming that pipe name
exists on every installation. Docker installations can expose a different endpoint.
Linux environments need their own reachable container service. A Java/Maven image
does not necessarily include Python or Tesseract.

Linux validation used sources copied into a container's native filesystem for
Unix-permission and trailing-space-name cases. A Windows-backed source mount can
change the filesystem behaviour being tested. These Linux results do not establish
that the same setup has been verified on macOS.

## Filesystem restrictions and optional coverage

Some Windows exclusions are deliberate: Unix owner/group/permission checks,
POSIX unreadable-file checks, symlink scenarios, and trailing-space directory names.
Do not label every skip a missing dependency, or disable a whole portable test class
to hide one incompatible fixture.

[PR #2606](https://github.com/dadoonet/fscrawler/pull/2606) separates six portable
SSH configuration checks from the server/filesystem fixture, restricting only the
latter on Windows. Without that fix, the trailing-space setup can fail all seven
checks before their assertions execute.

OCR tests need Tesseract and the language data used by the selected cases. Missing
OCR support accounts for intentional skips, but an `ocr_disabled` test can still
fail an unrelated metadata assertion. Kibana tests and container-start tests also
have service-dependent conditions; inspect the report's actual skip reason.

## Metadata expectations and known integration problems

The fixture investigation found these raw-metadata counts:

| Tika | Word | PDF | JPEG |
|------|------|-----|------|
| 4.0.0 | 28 | 47 | 66 |
| 4.1.0 | 28 | 49 | 56 |
| 4.2.0 | 30 | 49 | 56 |

The 4.1/4.2 comparison kept FSCrawler source and fixture hashes identical. The
4.0 comparison required an older compatible FSCrawler revision. Word gains stored
line/paragraph counts in 4.2; PDF gains tagged-page counters; JPEG stream parsing
does not supply backing-file fields and defaults to omitting ICC curve dumps.
See the [history documentation in PR #2607](https://github.com/dadoonet/fscrawler/pull/2607)
and [verified unit-test correction in PR #2608](https://github.com/dadoonet/fscrawler/pull/2608).
Changing an exact count without checking the field differences can hide regressions.

The 31 October 2026 Windows daily integration audit of the combined portability
branch reported 159 passed, 13 skipped, 2 failures and 5 errors. It excluded nightly
tests and used a disposable authenticated Elasticsearch 9.5.5 instance. Remaining
unsuccessful cases involved MinIO image access, external HTTPS fetching, REST
deletion HTTP 400, an older JPEG expectation in an integration test, and a crawler
pause-acknowledgement timeout in the deleted-directory query-limit regression.
These observations are not a promise that the same failures occur on every host.

The combined Windows unit run reported 376 passed and 15 skipped, with no failures
or errors, after applying the pending SSH, Tika and Python fixes. Do not assume
that unchanged upstream code has that result. [PR #2610](https://github.com/dadoonet/fscrawler/pull/2610)
adds separate Windows/JDK 21 unit CI and depends on those fixes; the inspected
existing Windows integration job is separate and uses Elastic Cloud configuration.

## macOS validation status

Use the POSIX-shell command examples as a starting point and verify the selected
Java, Python, container service and filesystem behaviour locally. No macOS run
was performed in this investigation, and the inspected PR workflow had no macOS
job. Do not describe Windows/Linux evidence as a three-platform validation result.
