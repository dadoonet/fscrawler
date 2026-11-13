(bulk-item-retries)=
# Bounded bulk item retries

An Elasticsearch bulk response can succeed at HTTP level while rejecting individual
operations with `es_rejected_execution_exception`. Filesystem crawls and REST uploads
both use the client retry listener.

## BUG-05 and corrected behavior

Previously, retry eligibility searched the human-readable reason for an error name.
A structured rejection type with ordinary reason text was missed. Conversely, a
permanent mapping error mentioning that name could be retried. Rejections matching
the reason text were resubmitted without a retry limit or backoff. Size-triggered
resubmission could nest `execute()` calls inside response listeners, growing the
Java stack. A final flush could also continue indefinitely under persistent rejection.

The listener now checks structured error type when available. Reason matching is
retained only for framework backends that supply no error type. Elasticsearch's
accepted-conflict policy remains unchanged.

Each operation receives at most **three additional attempts**, for four total item
attempts. Its elapsed retry budget begins when the first retryable item rejection
is processed. Elasticsearch item retries use the existing `retry_max_duration`,
`retry_initial_delay`, and `retry_max_delay` settings. HTTP-level retry handling
continues to have its own budget; these budgets do not constitute a strict overall
crawl deadline or interrupt an in-flight network request.

Backoff doubles on successive retries, capped by `retry_max_delay`, with jitter
between half and all of the capped delay. Waiting is interruptible and takes place
outside the processor queue monitor. Waiting is per rejected operation, so
large rejected batches can accumulate delays. An exhausted budget, exhausted attempt count,
interruption, missing original operation, or closed processor produces a terminal
failure through the BUG-04 completion reporting mechanism.

`FsCrawlerBulkProcessor.addRetry()` queues work without triggering nested network
execution. The regular flush timer or explicit flush drains it. Flush barriers keep
in-flight response callbacks and queued retries visible before reporting completion.
With the timer disabled, callers must explicitly flush to complete deferred retries.

Bulk response positions identify original operations, so repeated document IDs in
one batch retry the rejected payload rather than the first payload with that ID.

Retry accounting uses a synchronized identity map keyed by the original operation
object. Successful and terminal outcomes and whole-request failures remove its
entries. The state is in memory and is not a durable replay journal.

## Regression evidence

Five new tests reproduced BUG-05 against the BUG-04 parent:

| Test | Parent outcome | Corrected expectation |
|---|---|---|
| `structuredRejectionRetriesWithoutTypeInReason` | Error | Rejection retries and succeeds. |
| `rejectionRetriesAreBounded` | Failure | Four requests maximum and a reported terminal failure. |
| `permanentErrorMentioningRejectionIsNotRetried` | Failure | One request and a reported permanent failure. |
| `retriesDoNotNestBulkExecution` | Failure | Two requests, maximum `execute()` stack depth one. |
| `interruptedBackoffRecordsTerminalFailureAndPreservesInterrupt` | Failure | No retry, terminal failure, interrupt preserved. |

An additional `repeatedIdRetriesTheRejectedPayloadOnly` regression failed against
the ID-only lookup and passes with response-position matching. It checks the
actual retry HTTP body, rather than just the number of requests.

The persistent-rejection fixture returns success on its sixth response so the old
implementation can terminate safely. The corrected implementation must stop before
that success response. Adjacent BUG-04, queue ownership, interrupted shutdown,
response-parser, and whole-request HTTP retry tests are also run.

Example 1: focused validation.

```sh
mvn -B -ntp test -pl elasticsearch-client -am "-Dtest=ElasticsearchBulkItemFailureTest,ElasticsearchClientRetryTest,ElasticsearchBulkResponseTest,FsCrawlerBulkProcessor*Test,FsCrawlerItemRetryTest" "-DskipIntegTests"
```

Example 2: regular unit suite.

```sh
mvn -B -ntp test "-Dtest=*Test" "-DskipIntegTests"
```

Example 3: regular integration suite with the configured external test cluster.

```sh
mvn -B -ntp verify "-Pdaily,sequential_tests" "-DskipUnitTests" "-Dit.test=*IT" "-Ddocker.skip=true"
```

When collecting results with `maven.test.failure.ignore=true`, report actual failure
and skip counts; Maven's reactor success is not an all-green test result.

## Validation record: 31 October 2026

Validation used Windows 11, Maven 3.9.11, test JVM 25.0.2, and a fresh disposable
Elasticsearch 9.5.5 instance. This branch is stacked on BUG-04 commit
`b623085b2928b279139020ebe007c539697034d2`.

All six new regressions passed, together with adjacent coverage: **52 focused
tests passed**. The real filesystem rejection integration test also passed in
the full suite, preserving BUG-04 failure reporting with REST disabled.

| Final run | Reported | Passed | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| Regular unit suite | 411 | 386 | 3 | 8 | 14 |
| Regular integration suite | 185 | 161 | 6 | 5 | 13 |

All unsuccessful and skipped unit identities match the saved BUG-04 results.
All unsuccessful integration identities also appeared in the preceding runs;
there are no new unsuccessful identities. The previous intermittent SSH shutdown
error did not recur; this does not establish that it has been fixed.

Existing failures concern Tika/OCR metadata expectations, Windows SSH fixture
paths, missing `python3`, Windows Testcontainers thread checks, MinIO download
access, external HTTPS trust, REST deletion HTTP 400, and the deleted-directory
checkpoint timeout. These broad suites are not all green. Collection used
`maven.test.failure.ignore=true` so all modules could run. The integration profiles
were `daily,sequential_tests`; nightly tests were not run.

The initial partial integration run was stopped when the repeated-ID regression
was identified. The final unit and integration runs above both cover the final
implementation; the integration run used a newly recreated test cluster.
