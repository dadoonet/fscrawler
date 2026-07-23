# Task 1 Report: Settings model `passwords.*`

## Scope

Implemented Task 1 for password-protected documents in the `settings` module only:

- added the `passwords.*` settings model
- wired `passwords` loading into `FsSettingsLoader`
- exposed `FsSettings#getPasswords()` / `setPasswords(...)`
- added a focused unit test and fixture
- updated the default YAML example block

No plugin wiring or Tika integration was added in this task.

## Files changed

- `settings/src/main/java/fr/pilato/elasticsearch/crawler/fs/settings/Passwords.java`
- `settings/src/main/java/fr/pilato/elasticsearch/crawler/fs/settings/PasswordProviders.java`
- `settings/src/main/java/fr/pilato/elasticsearch/crawler/fs/settings/DiskPasswordProviderSettings.java`
- `settings/src/main/java/fr/pilato/elasticsearch/crawler/fs/settings/StaticPasswordProviderSettings.java`
- `settings/src/main/java/fr/pilato/elasticsearch/crawler/fs/settings/ChainedPasswordProviderSettings.java`
- `settings/src/main/java/fr/pilato/elasticsearch/crawler/fs/settings/FsSettings.java`
- `settings/src/main/java/fr/pilato/elasticsearch/crawler/fs/settings/FsSettingsLoader.java`
- `settings/src/main/resources/fr/pilato/elasticsearch/crawler/fs/settings/fscrawler-default.yaml`
- `settings/src/test/java/fr/pilato/elasticsearch/crawler/fs/settings/PasswordsSettingsTest.java`
- `settings/src/test/resources/config/passwords-static/_settings.yaml`

## TDD evidence

### RED

Initial brief command run:

```bash
mvn -pl settings test -Dtests.class=fr.pilato.elasticsearch.crawler.fs.settings.PasswordsSettingsTest
```

Output summary:

```text
BUILD FAILURE
Could not resolve dependencies for project fr.pilato.elasticsearch.crawler:fscrawler-settings:jar:3.0-SNAPSHOT
Could not find artifact fr.pilato.elasticsearch.crawler:fscrawler-framework:jar:3.0-SNAPSHOT
```

Because this workspace needed sibling SNAPSHOT modules built from source, I reran the red phase with `-am` to get a meaningful failure from the new test itself:

```bash
mvn -pl settings -am test -DskipIntegTests -Dtest=PasswordsSettingsTest
```

Output summary:

```text
COMPILATION ERROR
PasswordsSettingsTest.java:[45,39] cannot find symbol
  symbol:   method getPasswords()
  location: variable settings of type FsSettings
PasswordsSettingsTest.java:[46,39] cannot find symbol
  symbol:   method getPasswords()
  location: variable settings of type FsSettings
BUILD FAILURE
```

This confirmed the missing production API before implementation.

### GREEN

Focused green run after implementation:

```bash
mvn -pl settings -am test -DskipIntegTests -Dtest=PasswordsSettingsTest
```

Output summary:

```text
Running fr.pilato.elasticsearch.crawler.fs.settings.PasswordsSettingsTest
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Implementation notes

- `Passwords` has `provider` defaulted to `"noop"` and optional nested `providers`.
- `PasswordProviders` supports `disk`, `static`, and `chained`.
- `StaticPasswordProviderSettings` supports both `values` and `value`, with `resolvedValues()` preferring `values` when present.
- `FsSettings` now carries the `passwords` section in `equals`, `hashCode`, and `toString`.
- Password-bearing settings intentionally avoid exposing configured password values in `toString()` output.

## Verification

Focused test:

```bash
mvn -pl settings -am test -DskipIntegTests -Dtest=PasswordsSettingsTest
```

Result:

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Full module verification:

```bash
mvn -pl settings -am test -DskipIntegTests
```

Result:

```text
FSCrawler Settings ................................. SUCCESS
Tests run: 23, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Formatting:

```bash
mvn spotless:apply -pl settings -am
mvn spotless:check -pl settings -am -DratchetFrom=NONE
```

Result:

```text
Spotless.Java is keeping 24 files clean - 0 needs changes to be clean
BUILD SUCCESS
```

## Self-review

- Loader wiring is minimal and scoped to the `settings` module only.
- The test exercises the exact `passwords.providers.static.values` loading path required by the brief.
- `toString()` output was reviewed to ensure configured password values are not logged.
- No unrelated refactoring was included.

## Concerns

- The brief's exact red command (`-pl settings` without `-am`) does not resolve local sibling SNAPSHOT dependencies in this workspace, so meaningful TDD verification required the standard `-am` dependency build.
