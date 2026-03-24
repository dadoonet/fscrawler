Review code changes in FSCrawler against project conventions.

## Review Checklist

### License
- [ ] Apache 2.0 license header present on all new/modified source files

### Code Style
- [ ] Log4j2 used for logging (no direct SLF4J)
- [ ] `logger.fatal()` used only for unrecoverable errors
- [ ] Jackson used for JSON serialization
- [ ] 4-space indentation, consistent with surrounding code
- [ ] try-with-resources used for `Closeable` objects

### Error Handling
- [ ] `FsCrawlerIllegalConfigurationException` used for config errors
- [ ] No swallowed exceptions (at minimum, log them)

### Tests
- [ ] New behaviour is covered by a test
- [ ] If this is a bug fix, there is a test that would have caught the original bug
- [ ] Test names are descriptive

### Formatting
- [ ] `mvn spotless:check` passes (no formatting errors)

### Documentation
- [ ] If the change has user-visible impact (new option, new endpoint, changed behaviour): `docs/source/` updated

### Commit / PR
- [ ] Commit message follows `type(scope): emoji description` format
- [ ] PR targets `master` branch
- [ ] PR description explains *why*, not just *what*

Report each item as ✅ pass or ❌ fail with a brief explanation. Suggest fixes for any failures.
