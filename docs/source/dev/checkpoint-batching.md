(checkpoint-batching)=
# Acknowledged checkpoint batches and directory replay

## Failure being corrected

BUG-03 occurs when traversal progress is persisted before its Elasticsearch writes
are acknowledged. Previously, `FsParser` could mark a directory completed after
queuing its operations, save that state during later work or lifecycle changes,
and only check bulk results after directory traversal. A crash or write failure
could therefore leave a completed directory whose missing writes would not be
replayed when the saved traversal resumed.

BUG-04 makes terminal item failures visible. BUG-03 additionally ensures that the
checkpoint's restart frontier does not omit the work associated with those failures.

## Working progress and acknowledged progress

The parser retains a working traversal and a separate acknowledged checkpoint
snapshot. The crawler thread advances working progress while examining directories.
Pause, close, and error saves copy only acknowledged traversal progress, while
recording the latest lifecycle state and diagnostic message. Those saves cannot
publish speculative completed directories.

At a checkpoint boundary the crawler flushes and verifies outstanding bulk work,
including response callbacks and retries, then builds and writes a stable snapshot.
Only after that write succeeds does the snapshot become the new acknowledged
frontier. The disk write occurs outside the bulk processor queue monitor.

A failed barrier or acknowledgement-checkpoint write escapes ordinary
`continue_on_error` file handling. The crawler reports `ERROR` and preserves the
previous acknowledged frontier in memory. Preserving the previous file through a
failed write or power loss additionally requires atomic file replacement (BUG-07).
Failure details from the bulk client remain in the error.

## Boundaries and bulk settings

These initial limits are implementation constants rather than new YAML options.
A boundary is requested when any limit is reached at a safe traversal point:

| Trigger | Initial policy |
|---|---|
| Fully examined directories since the last commit | 100 |
| Submitted operations | Greater of 1,000 or ten times `elasticsearch.bulk_size` |
| Estimated submitted bytes | Greater of 32 MiB or three times `elasticsearch.byte_size` |
| Elapsed time since the last acknowledged snapshot | 30 seconds |
| End of scan | Commit the final partial batch before successful scan completion |
| Pause | Save the safe frontier immediately; the crawler additionally drains before waiting |
| Close | Save the safe frontier immediately; acknowledged final draining may advance it safely |

Ordinary bulk count, byte, and timer triggers continue to send requests throughout
traversal. Checkpoint limits accumulate across requests and are not queue capacities.
A boundary can force a partial request even when the ordinary bulk trigger has not
been reached. Directory and time limits can still do so with larger bulk settings.

The byte estimate includes UTF-8 content/attachment or raw JSON lengths plus an
allowance for metadata; it is not an exact serialized NDJSON measurement or a hard
memory cap. Indexing, folder writes, and deletion submissions count as operations.
Retry attempts do not count as fresh traversal operations.

The time limit is a request to commit at a safe point, not a guaranteed write every
30 seconds. Reading one large file, listing a provider directory, retries, network
requests, and checkpoint I/O may extend the actual interval. HTTP retry budgets
remain separate from the checkpoint cadence.

## Replay the current directory

A snapshot taken within an unfinished directory puts that directory back into the
pending frontier. It is not marked complete and no numeric file-position cursor
is used to skip its writes on restart. Pending discovered children remain tracked,
and completed directories covered by successful acknowledgement remain completed.

Tentative statistics for the unfinished directory are excluded from the durable
snapshot so replay can count that directory once. These are recovery counters,
not an exactly-once operation journal; deletion accounting and interrupted
in-process traversal counters may differ on replay. A finished directory becomes
committed only after its associated writes succeed. Successful-scan timestamps are
advanced only after the entire scan succeeds.

For a directory with more than 10,000 files, periodic boundaries still confirm work,
but a crash near the end can require examining the whole directory again. For more
than 10,000 child directories, the pending frontier can legitimately be large.
Its size is not limited by these batch thresholds. Elasticsearch reconciliation
query pagination is a separate concern.

Recovery is at least once. Stable IDs usually avoid duplicate indexed documents,
but repeated writes can rerun ingest side effects. This change is not a per-file
durable journal, does not repair previously lost documents automatically, and does
not implement atomic checkpoint-file replacement (BUG-07).

## Regression tests

`FsParserCheckpointAcknowledgementTest` documents seven cases that failed against
the unchanged checkpoint implementation:

- Bulk acknowledgement failure retains previously processed directories for replay.
- A later listing failure cannot commit earlier unacknowledged work.
- A pause save cannot publish speculative completed directories.
- A close save cannot publish speculative completed directories.
- A pause during a blocked acknowledgement persists only the safe frontier.
- Directory completion uses a 100-directory barrier, rather than a flush per directory.
- A boundary inside a 1,001-file directory retains a replayable directory and no file cursor.

`FsCrawlerCheckpointReplayIT` additionally rejects one real document in an ingest
pipeline, corrects the pipeline, and starts a fresh crawler/client. It verifies
that the missing document is recovered and the accepted file is replayed under
the at-least-once recovery policy.

Restart cases use a fresh parser and document service so the old in-memory queue
cannot make the test pass accidentally. They verify that previously queued work is
submitted again and that a subsequent healthy run reaches `COMPLETED`.

Example 1: run the checkpoint regressions and adjacent crawler tests.

```powershell
mvn -B -ntp test -pl core -am '-Dtest=FsParser*Test,FsCrawlerImplTest' '-DskipIntegTests'
```

## Healthy-workload benchmark

`FsCrawlerCheckpointBenchmarkIT` is opt-in and uses real Elasticsearch with REST
disabled. Every run must finish `COMPLETED` and validate its expected document
count (an empty workload may legitimately create no document index).

| Workload | Fixture | Purpose |
|---|---|---|
| Small directories | 100 directories, ten JSON files each | Batching across directory boundaries |
| Large directory | 10,050 JSON files in one directory | Boundaries within an unfinished directory |
| Wide directory | 10,050 empty child directories | Large pending frontier and directory checkpoint cost |

The benchmark uses JSON passthrough, semantic search disabled, bulk count 100, and
flush interval five seconds. Folder indexing and deletion reconciliation are
disabled to isolate indexing/traversal/checkpoint behavior. These are not PDF
extraction or deletion-reconciliation benchmarks.

Fixture generation, validation, and cleanup occur outside the timed interval. Timing includes crawler construction/startup through one scan
finishing. Each workload has one discarded warm-up and five measured runs. Both phases use the same test-only `CheckpointBenchmarkFsProvider`. It reads real
files and metadata and inherits local stream handling, but sorts immutable path
names instead of repeatedly querying mutable access times during comparisons.
This isolates checkpoint cost from the stock local provider's separate sorting
failure observed in exploratory runs. It is not a benchmark of stock-provider
sorting performance, and no production sorting change is included.

Example 2: collect samples on the baseline revision, then repeat on the fixed revision
using the same cluster configuration and benchmark script.

```powershell
./scripts/benchmark-checkpoint.ps1 -Label baseline -ClusterUrl http://127.0.0.1:19203
./scripts/benchmark-checkpoint.ps1 -Label fixed -ClusterUrl http://127.0.0.1:19203
```

The commands run the currently checked-out code; change revisions between commands.
The runner records revision, source SHA-256, raw samples and timestamps. It rejects
failed or incomplete runs. Standard integration runs skip this expensive benchmark
unless `tests.checkpoint.benchmark=true` is explicitly set.

Example 3: compare measured samples, excluding warm-up runs.

```powershell
python scripts/compare-checkpoint-benchmarks.py target/checkpoint-benchmark/baseline.json target/checkpoint-benchmark/fixed.json
```

The comparison reports medians and an indicative bootstrap interval for percentage
change. A 10% slowdown is the initial practical threshold for this experiment, not
a product performance guarantee. Five runs per workload cannot establish behavior
for every filesystem, document type, cluster load, or deployment. A borderline
result calls for more repetitions rather than an all-clear based on one timing.

## Recorded validation: 7 November 2026

The local implementation branch is `codex/fix-checkpoint-ack-batching`. It is stacked
on the bulk-queue, bulk-item-failure, and bounded-retry fixes, merged with main
`9ab67967`. The baseline retains those prerequisite fixes and the same benchmark
harness, but uses the old checkpoint implementation. This comparison isolates
BUG-03; it is not a comparison against an unmodified upstream release.

The production change is confined to `FsParser`. The benchmark-only filesystem
adapter and its service registration are integration-test sources. No production
filesystem sorting change is included.

### Before and after correctness checks

| Check | Before BUG-03 fix | After BUG-03 fix |
|---|---|---|
| New checkpoint unit regressions | Seven failures | Seven passed |
| Real Elasticsearch rejection/restart/replay integration test | Failed: missing document was not replayed | Passed: both documents recovered; accepted file replayed |
| Focused checkpoint, parser, crawler and bulk/retry validation | Not used as a substitute for the red tests | 55 unit cases and two integration cases passed |

The focused integration cases are `FsCrawlerCheckpointReplayIT` and
`FsCrawlerBulkItemFailureIT`. They use real Elasticsearch with the REST gateway
disabled. The red phase ran the new tests against old production code before the
fix. Their failing state and the passing fixed state were recorded separately.

### Healthy processing times

Environment: Windows, Java 25.0.2, Maven 3.9.11, Elasticsearch 9.5.5 in a disposable
Podman container. All accepted timing runs finished successfully and validated
document counts. No failures were injected. The runner also rejected processing
errors and retry diagnostics. No other Maven suite ran concurrently with timing.

| Workload | Measured runs per phase | Before median | After median | Change | Indicative 95% interval for change |
|---|---:|---:|---:|---:|---:|
| 100 directories / 1,000 JSON files | 5 | 2.153 s | 1.927 s | -10.5% | -27.8% to -7.9% |
| One directory / 10,050 JSON files, initial experiment | 5 | 13.259 s | 16.064 s | +21.2% | -13.2% to +23.5% |
| 10,050 empty child directories | 5 | 11.693 s | 12.001 s | +2.6% | -3.9% to +3.4% |
| One directory / 10,050 JSON files, separate follow-up | 10 | 12.671 s | 13.775 s | +8.7% | +0.08% to +16.38% |

**A material large-directory slowdown cannot be ruled out.** The follow-up's
observed 8.7% median increase is below the chosen 10% practical threshold, but its
interval extends above that threshold. The initial unfavorable result remains
reported rather than being replaced or selectively pooled with the follow-up.
The small-directory and wide-directory samples did not show a material slowdown.
These results do not establish equivalent performance in every deployment.

The experiment ran baseline then fixed, rather than interleaving randomized A/B
trials. The bootstrap intervals describe sample variability; they do not remove
machine-load, filesystem-cache, JVM, or ordering effects. Directory replay is a
correctness tradeoff and checkpoint acknowledgement barriers can add waiting.
The measurements do not isolate which component accounts for the observed cost.

| Experiment | Baseline revision | Fixed revision |
|---|---|---|
| Initial, five samples | `8d5b408de99c2c1e6c350d43e86e057836d688ea` | `a4f2cd67` |
| Follow-up, ten samples | `868e4c9ac07c8b54300f23be649b22b2825f63fb` | `c8af774c03c0dd05dd478ece917ef930edd7f065` |

The production parser SHA-256 is identical between the two baseline experiments,
and likewise identical between the two fixed experiments. Only the workload
selection in the test harness changed for the follow-up.
Download the {download}`raw samples, revisions, source hashes, timestamps and comparisons <checkpoint-benchmark-results.json>`.

Example 4: repeat the large-directory experiment with ten measured runs per phase.
Run once on each corresponding revision, using distinct labels.

```powershell
./scripts/benchmark-checkpoint.ps1 -Label baseline-large10 -Workload large-directory -Repeats 10 -ClusterUrl http://127.0.0.1:19203
./scripts/benchmark-checkpoint.ps1 -Label fixed-large10 -Workload large-directory -Repeats 10 -ClusterUrl http://127.0.0.1:19203
```

### Regular Maven suite

The full unit run reported 418 cases: 402 passed, 15 skipped, and one error.
`UpdateReadmeVersionsTest.updateReadmeVersionsPythonTestsPass` could not start
`python3` on Windows (`CreateProcess error=2`). Running the underlying Python
suite directly with the available interpreter passed all 25 tests. This direct
check does not change the Maven wrapper's reported error. The wrapper also
failed with the same missing-`python3` error when explicitly rerun on the
unchanged-checkpoint baseline.

The broad suite uses `maven.test.failure.ignore=true` to collect failures across
modules. A successful Maven process exit under that option does **not** mean that
all tests passed.

The completed daily/sequential integration run reported 183 cases:
160 passed, 19 skipped, one assertion failure, and three errors.
The opt-in timing benchmark accounts for one intentional skip in this ordinary run.
The new replay test and existing bulk-item-failure test passed in this broad run too.

Six cases were skipped because Testcontainers could not discover a Docker
environment through the Windows container API. The Podman CLI and the explicitly
started Elasticsearch container worked, so the Elasticsearch-backed tests ran,
but container-dependent S3/MinIO and Nginx coverage was not exercised. Other skips
include Windows filesystem exclusions, unavailable Tesseract/Kibana, and already
disabled cases. A smaller failure count than an earlier run must not be read as
proof that these skipped scenarios have been fixed.

| Unsuccessful integration case | Observed cause | Baseline evidence |
|---|---|---|
| `FsHttpPluginIT.readTxtFileFromWebsite` | Could not open the external HTTPS robots.txt stream | Reproduced against unchanged checkpoint code in the current environment |
| `FsCrawlerRestIT.deleteDocumentApi` | HTTP 400 Bad Request | Reproduced against unchanged checkpoint code in the current environment |
| `FsCrawlerTestOcrIT.ocr_disabled` | Raw metadata assertion expected 66 fields, received 56 | Reproduced against unchanged checkpoint code in the current environment |
| `FsCrawlerTestRemoveDeletedIT.remove_deleted_continues_after_directory_query_limit` | Timeout while repeatedly resuming the directory removed by the test; provider listing fails | Same test and repeated missing-directory error recorded in the earlier BUG-05 run; not rerun on today's baseline |

These results are **not an all-green full suite**. The historical comparison is
evidence of an already observed deletion-test problem, not proof that every
baseline/environment combination behaves identically. The three smaller failing
cases were explicitly rerun against baseline revision `868e4c9a`; all three failed
again. No new failing integration identity appeared relative to the saved BUG-05 run.

Repository-wide Spotless and the strict Sphinx HTML build passed. Raw local Maven
logs, XML reports, red/green results, and benchmark runner reports were retained
for review; benchmark samples and revision/source hashes are included in the
download above. No unrelated test failures were suppressed in the reported counts.
