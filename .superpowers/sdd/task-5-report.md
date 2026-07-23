# Task 5 report: Tika password-aware extract + retry loop

## Summary

Implemented password-aware Tika extraction in `fscrawler-tika` with a retry loop for encrypted documents.

## What changed

- Added `fscrawler-plugin` as a dependency of `tika`.
- Added `TikaDocParser.generate(InputStreamSupplier, Doc, long, String, FsCrawlerExtensionPasswordProvider)`.
- Kept the legacy `generate(InputStream, Doc, long)` behavior as a single-pass/no-provider path.
- Taught `TikaInstance` to build a per-call `ParseContext` and attach a Tika `PasswordProvider` only for that call.
- Distinguished encrypted extraction attempts from generic parser failures with a typed result.
- Implemented retry behavior:
  - explicit password: one attempt only, no provider fallback
  - no explicit password: try without password, then iterate provider `PasswordSession` candidates
  - never log secret values
- Ported `test-protected.pdf` into `test-documents`.
- Expanded `TikaDocParserTest` coverage for:
  - protected docx/pdf empty content without password
  - protected docx/pdf extraction with explicit password
  - wrong explicit password yielding empty content without provider fallback
  - provider retry succeeding after a wrong candidate

## Verification

### Red

Ran:

```bash
mvn test -pl tika -am -DskipIntegTests -Dtest=TikaDocParserTest#protectedDocument+protectedDocumentExplicitPasswordDoesNotUseProviderFallback+protectedDocumentRetriesPasswordProviderCandidatesUntilOneWorks
```

Observed expected failure before implementation:

- test compile failed because `fscrawler-plugin` was not yet on the `tika` classpath and the new password-aware API did not exist.

### Green

Ran:

```bash
mvn test -pl tika -am -DskipIntegTests -Dtest=TikaDocParserTest
```

Result:

- `Tests run: 43, Failures: 0, Errors: 0, Skipped: 11`

Ran:

```bash
mvn spotless:check -pl tika -am
```

Result:

- build success, no formatting violations

## Notes / concerns

- The new overload is now available for future REST/core wiring, but this task intentionally focused on `TikaDocParser`/`TikaInstance` plus tests/fixture/dependency changes.
