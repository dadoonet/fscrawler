## Task 9 - password-chained-plugin

### Summary
- Added the new `password-chained-plugin` module with PF4J metadata and a `chained` password provider.
- The provider stores `PasswordProviderLookup` during `start()`, reads `passwords.providers.chained.providers`,
  and opens child sessions lazily in configured order, exhausting each before moving to the next.
- Wired the new plugin into `plugins/pom.xml`, root dependency management, `cli/pom.xml`, and `integration-tests/pom.xml`.

### TDD evidence
1. Red:
   - `mvn test -pl plugins/password-chained-plugin -am -DskipIntegTests -Dtest=PasswordChainedPluginTest`
   - Failed with `ClassNotFoundException` for `PasswordChainedPlugin$Provider` before implementation existed.
2. Green:
   - Same command passed after implementing the provider.

### Tests
- `mvn test -pl plugins/password-chained-plugin -am -DskipIntegTests -Dtest=PasswordChainedPluginTest`
- `mvn spotless:check -pl plugins/password-chained-plugin,cli,integration-tests -am -DskipIntegTests`

### Notes
- The unit test uses stub child providers supplied via a lookup lambda, so it has no compile dependency on the
  disk/static plugin implementations.
