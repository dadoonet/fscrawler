(bulk-item-failures)=
# Bulk item failures and crawl completion

A successful Elasticsearch HTTP request does not necessarily mean every document
in that request was indexed. This page explains how terminal bulk-item failures
reach FSCrawler's completion checks, the BUG-04 reproduction, and the tests added
for the correction.

This change builds on the queue ownership and flush barriers described in
[Bulk processing, concurrency, and shutdown](bulk-processing.md). It applies to
ordinary filesystem crawling; the REST gateway does not need to be enabled.

## The issue

Elasticsearch can return an HTTP-successful bulk response containing both
successful and failed items. For example, one document may be accepted while
another is rejected by an ingest pipeline or an index mapping.

Previously, `ElasticsearchBulkResponse` recognized failed items, and the simple
bulk listener logged them. However, `ElasticsearchClient.FailOnHttpBulkErrorListener`
only recorded a fatal response failure when `response.getException()` was non-null.
That exception represents a whole-request transport failure; it is null for a
successfully transported response containing failed items.

Consequently, `flushAndEnsureBulkSucceeded()` could return normally even when a
document was rejected. `FsParser` then followed its successful completion path and
persisted a `COMPLETED` checkpoint. Files that remained unchanged could be omitted
from a later incremental scan.

The real-Elasticsearch regression reproduced this sequence before the fix:

1. Create two files in a temporary local directory.
2. Configure an ingest pipeline to reject one named file and accept the other.
3. Run one filesystem crawl with REST explicitly disabled.
4. Verify that only the accepted document exists in Elasticsearch.
5. Read the checkpoint from disk.

The assertion expected `ERROR`, but the original code persisted `COMPLETED`.
This was a false-success reporting defect, not just a missing log message.

## Failure outcomes

The listener distinguishes the following outcomes:

| Outcome | Handling |
|---|---|
| Item succeeded | Do not record a failure. |
| Item failed and is not eligible for the existing retry policy | Record a terminal failure. |
| Retry accepted by the bulk processor | Defer the final outcome to the retry's completion. |
| Failed item cannot be matched to an original operation | Record a terminal failure with the retry failure cause. |
| Processor rejects the retry, for example during close | Record a terminal failure with the rejection cause. |
| Conflict accepted by the existing conflict policy | Preserve that behavior; do not newly classify it as terminal. |
| Whole-request failure | Preserve existing fatal transport-failure handling. |

Merely replacing the old condition with `response.hasFailures()` would mark a
transient failure as permanent even if its retry subsequently succeeds. The
terminal-outcome callback avoids that mistake.

## Implementation changes

### Structured response details

`fr.pilato.elasticsearch.crawler.fs.framework.bulk.FsCrawlerBulkResponse.BulkItemResponse`
now retains optional `status` and `failureType` fields alongside the failure reason
and operation identity. These fields are optional for other framework consumers.

`fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchBulkResponse` populates
those fields from the item response and preserves the `index`, `create`, or
`delete` action when constructing its operation identity. Diagnostics can identify
the actual action, index, document ID, status, error type, and reason.

An absent reason does not prevent a failure from being reported. The existing
`version_conflict_engine_exception` exception policy is unchanged by this fix.

### Terminal-outcome notification

`fr.pilato.elasticsearch.crawler.fs.framework.bulk.FsCrawlerRetryBulkProcessorListener`
adds the protected `onTerminalItemFailure()` callback. It is invoked for failed
items that were not requeued. Existing framework subclasses retain their logging
behavior unless they override the callback.

The private retry helper now reports whether it found and requeued an operation.
A missing match is no longer only a warning for the Elasticsearch client. Exceptions
while adding a retry are passed to the terminal callback rather than escaping the
response callback without recording a fatal outcome.

Message-based retry matching is now null-safe. This change deliberately preserves
the existing matching policy and does not introduce new retry eligibility rules.

### Client failure recording

The nested client listener is renamed to `FailOnBulkErrorListener`. Its terminal
callback constructs an `ElasticsearchClientException` with item details and calls
the existing `recordFatalBulkFailure()` method.

That method uses the existing `AtomicReference` to retain the first recorded
failure and increments the `AtomicLong` failure generation. Subsequent successful
batches do not erase the failure. Completion checks do not clear it either.

`clearFatalBulkFailure()` still explicitly clears the retained failure at the
start of a new crawl. It does not reset the generation counter, so a caller that
captured an earlier generation can still detect an intervening failure.

Failure-check exception messages now include the recorded detail rather than
always saying that HTTP retries were exhausted. This gives the persisted crawl
error a useful document identity and rejection reason.

### Crawler completion

No new filesystem-crawler control path is required. `FsParser` already calls
`documentService.flushAndEnsureBulkSucceeded()` before recording successful
completion. The failure now reaches that existing check, which throws and causes
the existing error handler to persist an `ERROR` checkpoint.

The filesystem integration test also verifies that the successful-scan watermark
is not advanced and the scan end time is not set as though the crawl succeeded.
The bulk flush barrier waits for listener completion before evaluating failure
state, as established by the parent queue-concurrency fix.

## Scope and remaining work

This change makes completion reporting truthful. It is not a durable replay
journal, and it does not correct directories being marked complete before all
associated writes are acknowledged. That separate checkpoint/replay issue is
BUG-03. Correcting a mapping or pipeline does not by itself guarantee that every
previously rejected file will be replayed automatically.

The existing retry listener still matches configured text in the failure reason.
A rejection whose reason does not match is now reported as terminal instead of
being silently treated as success. Structured error classification, retry limits,
backoff, deadlines, and removal of recursive retry execution remain BUG-05 work.

The existing conflict acceptance policy is preserved. No batch-size, timer,
transport-retry, or other configuration defaults change. Failed documents are not
rolled back together with successful items in the same bulk request.

## Regression tests and before/after evidence

### Client unit tests with WireMock

`elasticsearch-client/src/test/java/fr/pilato/elasticsearch/crawler/fs/client/ElasticsearchBulkItemFailureTest.java`
uses real client and bulk-processing code with controlled HTTP responses.
Incidental index names, IDs, and rejection text are randomized. The timer is
explicitly disabled in these tests so each requested flush has a controlled body.

| Test | Before the fix | After the fix | What it proves |
|---|---|---|---|
| `mixedHttpSuccessWithTerminalItemFailureFailsFlush` | Failed | Passed | A mixed HTTP-successful response fails completion and preserves item diagnostics. |
| `laterSuccessfulBatchCannotHideTerminalFailureOrItsGeneration` | Failed | Passed | A later success and repeated checks cannot hide failure; generation survives explicit clearing. |
| `successfullyRetriedItemDoesNotBecomeTerminalFailure` | Passed | Passed | A rejection followed by successful retry does not create a terminal failure. |
| `retryWithoutMatchingRequestOperationIsTerminal` | Failed | Passed | A failed item that cannot be requeued is reported to the caller. |
| `itemFailureWithoutReasonStillFailsFlush` | Failed | Passed | Missing error reason cannot turn an item failure into successful completion. |
| `acceptedCreateConflictDoesNotFailFlush` | Passed | Passed | The existing accepted-conflict policy is preserved. |
| `retryRejectedDuringCloseIsRecordedAsTerminalFailure` | Failed | Passed | A retry rejected after shutdown starts remains visible to failure checks. |

The parent implementation ran all seven tests: five failures and two passing
controls. After the correction, all seven passed. The focused run also included
the existing response parser, HTTP retry, and bulk queue/shutdown tests: 46 tests
passed in total.

### Filesystem integration test

`integration-tests/src/test/java/fr/pilato/elasticsearch/crawler/fs/test/integration/elasticsearch/FsCrawlerBulkItemFailureIT.java`
contains `rejectedDocumentMakesFilesystemCrawlFailWithoutRest`.

It starts a real filesystem crawler with `rest=false`, reads the checkpoint from
disk, and checks Elasticsearch rather than mocking the document service. The
accepted file must be present at version 1, only one document may exist, the
checkpoint must be `ERROR`, the error must identify the rejected file and reason,
and the successful-scan watermark must remain unadvanced.

Before the production change, the test failed because the checkpoint was
`COMPLETED` despite the missing rejected document. After the correction, the test passed: `ERROR` and the rejection details were persisted.

## Reproducing the focused checks

Example 1: run the new client regressions together with adjacent existing tests.

```sh
mvn -B -ntp test -pl elasticsearch-client -am "-Dtest=ElasticsearchBulkItemFailureTest,ElasticsearchBulkResponseTest,ElasticsearchClientRetryTest,FsCrawlerBulkProcessor*Test" "-DskipIntegTests"
```

Example 2: run the real filesystem regression. Use a working Testcontainers runtime
or configure an external Elasticsearch cluster as described in [Building FSCrawler](build.md).

```sh
mvn -B -ntp verify -pl integration-tests -am "-Pdaily,sequential_tests" "-DskipUnitTests" "-Dit.test=FsCrawlerBulkItemFailureIT" "-Ddocker.skip=true"
```

Example 3: explicitly select the regular unit suite.

```sh
mvn -B -ntp test "-Dtest=*Test" "-DskipIntegTests"
```

Example 4: explicitly select the regular integration suite.

```sh
mvn -B -ntp verify "-Pdaily,sequential_tests" "-DskipUnitTests" "-Dit.test=*IT" "-Ddocker.skip=true"
```

Record the selected profiles, environment, and skipped tests when comparing runs.
If `maven.test.failure.ignore=true` is used to collect results across modules,
Maven's `BUILD SUCCESS` does not mean every test passed. Full-suite results should
be reported separately from the focused regression results.

## Full-suite validation record: 31 October 2026

The validation environment was Windows 11, Maven 3.9.11, test JVM 25.0.2, and
Elasticsearch 9.5.5 in a disposable Podman container. The integration run selected
`daily,sequential_tests`; the nightly profile was not run. The broad runs used
`maven.test.failure.ignore=true` to collect all modules' results and were not
all-green runs.

| Run | Reported cases | Passed | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| Full unit suite with this change | 405 | 380 | 3 | 8 | 14 |
| Saved unchanged-version unit baseline | 391 | 366 | 3 | 8 | 14 |
| Full integration suite with this change | 185 | 160 | 6 | 6 | 13 |
| Previous BUG-01 full integration run | 184 | 160 | 6 | 5 | 13 |

All unsuccessful and skipped unit-test identities match the saved unchanged
baseline at `4b9144387ec5177c2498ad49f157eaf9cb14cd7e`. Its existing failures
concern Tika metadata expectations, Windows SSH fixture paths, and a missing
`python3` executable. The 14 additional passing cases consist of seven parent
BUG-01 regressions and seven BUG-04 regressions.

The new filesystem integration test passed both independently and within the full
integration suite. All 11 unsuccessful test identities from the previous BUG-01
integration run recur. Those concern Windows Testcontainers IOCP thread checks,
MinIO image pull denial, external HTTPS trust, REST deletion returning HTTP 400,
OCR metadata expectations, and a deleted-directory checkpoint timeout. This is
a comparison with the saved BUG-01 run, not a new full integration run on
unchanged upstream code.

One additional full-run error occurred in `FsCrawlerTestSshIT.ssh_with_key`:
Apache SSHD reported `IllegalStateException: Executor has been shut down` through
a Windows asynchronous socket completion. The SSH class passed a focused rerun
with the recorded seed `C1129EE8898B3FE4`: two passed, one skipped.
The same class and seed also passed on the saved unchanged source: two passed,
one skipped. The intermittent cause remains unresolved. These focused passes do
not erase the full-run error or prove that timing-sensitive interactions cannot
recur.
