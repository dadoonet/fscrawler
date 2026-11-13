(checkpoint-batching)=
# Acknowledged checkpoint batches and directory replay

## Failure being corrected

The recovery failure occurs when traversal progress is persisted before its
Elasticsearch writes are acknowledged. Previously, `FsParser` could mark a
directory completed after
queuing its operations, save that state during later work or lifecycle changes,
and only check bulk results after directory traversal. A crash or write failure
could therefore leave a completed directory whose missing writes would not be
replayed when the saved traversal resumed.

Bulk-item failure reporting makes terminal item failures visible. Acknowledged
checkpointing additionally ensures that the checkpoint's restart frontier does
not omit the work associated with those failures.

## Working progress and acknowledged progress

The parser retains a working traversal and a separate acknowledged checkpoint
snapshot. The crawler thread advances working progress while examining directories.
Pause, close, and error saves copy only acknowledged traversal progress, while
recording the latest lifecycle state and diagnostic message. Those saves cannot
publish speculative completed directories.

At a checkpoint boundary the crawler flushes and verifies the document client's
bulk work, then the management client's bulk work, including response callbacks
and retries, before building and writing a stable snapshot. Only after that write
succeeds does the snapshot become the new acknowledged frontier. The disk write
occurs outside the bulk processor queue monitor.

The management service uses a separate Elasticsearch client for folder records
and recursive deletion of removed directories and their documents. Its queue and
retained failure state are independent of the document service. A successful
document flush alone cannot acknowledge management operations, even when both
clients connect to the same cluster. Both queues are checked at intermediate
checkpoint boundaries and at the end of the scan before it can become `COMPLETED`.
A management failure preserves the preceding acknowledged frontier and reports
`ERROR`. At the start of the next crawl, retained failures in both clients are
cleared so replay can succeed after the underlying problem is corrected.

The two queues are acknowledged sequentially, not transactionally. An operation
accepted by Elasticsearch is not rolled back when another operation fails;
recovery retains the existing at-least-once replay policy.

A failed barrier or acknowledgement-checkpoint write escapes ordinary
`continue_on_error` file handling. The crawler reports `ERROR` and preserves the
previous acknowledged frontier in memory. Preserving the previous file through a
failed write or power loss additionally requires atomic file replacement.
Failure details from the bulk client remain in the error.

## Boundaries and bulk settings

These limits are configured independently through `fs.checkpoint`; omitted fields
use the defaults below. See {ref}`checkpoint-settings` for loading and validation.
A boundary is requested when any limit is reached at a safe traversal point:

| Trigger | Current policy |
|---|---|
| Fully examined directories since the last commit | 100 (`fs.checkpoint.max_directories`) |
| Submitted operations | 5,000, overridable with `fs.checkpoint.max_operations` |
| Estimated submitted bytes | 32 MiB, overridable with `fs.checkpoint.max_bytes` |
| Elapsed time since the last acknowledged snapshot | 30 seconds (`fs.checkpoint.max_interval`) |
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
not implement atomic checkpoint-file replacement.

## Regression tests

`FsParserCheckpointAcknowledgementTest` documents seven cases that failed against
the unchanged checkpoint implementation:

- Bulk acknowledgement failure retains previously processed directories for replay.
- A later listing failure cannot commit earlier unacknowledged work.
- A pause save cannot publish speculative completed directories.
- A close save cannot publish speculative completed directories.
- A pause during a blocked acknowledgement persists only the safe frontier.
- Directory completion uses a 100-directory barrier, rather than a flush per directory.
- A boundary inside a 5,001-file directory retains a replayable directory and no file cursor; the first operation barrier is at 5,000 submissions.

`FsParserManagementAcknowledgementTest` exercises the real management bulk
processor and failure listener with an in-memory transport. It checks buffered
folder writes, terminal management failures, an intermediate directory boundary,
recovery on a later healthy run, successful and rejected recursive deletions,
and a successful management-item retry. The transport fixture avoids sockets and
does not require a live Elasticsearch cluster.

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

This earlier validation used the initial 1,000-operation minimum. The later
5,000-operation experiment and its validation are recorded separately below.

The local implementation branch is `codex/fix-checkpoint-ack-batching`. It is stacked
on the bulk-queue, bulk-item-failure, and bounded-retry fixes, merged with main
`9ab67967`. The baseline retains those prerequisite fixes and the same benchmark
harness, but uses the old checkpoint implementation. This comparison isolates
the checkpoint acknowledgement change; it is not a comparison against an
unmodified upstream release.

The production change is confined to `FsParser`. The benchmark-only filesystem
adapter and its service registration are integration-test sources. No production
filesystem sorting change is included.

### Before and after correctness checks

| Check | Before checkpoint fix | After checkpoint fix |
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
| `FsCrawlerTestRemoveDeletedIT.remove_deleted_continues_after_directory_query_limit` | Timeout while repeatedly resuming the directory removed by the test; provider listing fails | Same test and repeated missing-directory error recorded in the earlier bounded-retry validation run; not rerun on today's baseline |

These results are **not an all-green full suite**. The historical comparison is
evidence of an already observed deletion-test problem, not proof that every
baseline/environment combination behaves identically. The three smaller failing
cases were explicitly rerun against baseline revision `868e4c9a`; all three failed
again. No new failing integration identity appeared relative to the saved
bounded-retry validation run.

Repository-wide Spotless and the strict Sphinx HTML build passed. Raw local Maven
logs, XML reports, red/green results, and benchmark runner reports were retained
for review; benchmark samples and revision/source hashes are included in the
download above. No unrelated test failures were suppressed in the reported counts.

## Larger alternating experiment with a 5,000-operation minimum

This historical experiment predates explicit `fs.checkpoint` settings. Its bulk
scaling formula describes those measured revisions, not the current loader. With
the benchmark bulk defaults, the four effective limits match today's defaults.

The candidate branch `codex/checkpoint-5000-benchmark` preserves the earlier
1,000-operation branch and changes only the operation minimum to 5,000. The
100-directory, estimated-byte, 30-second and lifecycle boundaries remain intact.
The operation limit is therefore `max(5000, 10 * elasticsearch.bulk_size)`.

### Experimental design and controls

Two datasets were tested: 50,000 JSON files in one directory, and 500 child
directories containing 100 JSON files each. Each dataset/version received two
discarded warm-ups and ten measured crawls: 48 validated crawls in total, of which
40 were measured. The test asserts `COMPLETED` and exactly 50,000 Elasticsearch
documents on every crawl. Retry and processing-failure diagnostics invalidate the
experiment. Fresh destination indices and checkpoints are used for each crawl.

Both versions remain in separate persistent JVMs. The coordinator permits one
crawler at a time and alternates pair order AB, BA, AB, BA, including warm-ups.
Both fixtures must be ready before timing starts. Phase gates prevent subsequent
fixture preparation and final teardown from overlapping another timed crawl.
Validation, index/checkpoint cleanup and crawler close finish before the next turn.
The timed interval remains crawler construction/start through scan completion.
This measures warm filesystem conditions; it does not claim cold-cache behavior.

The same stable-order test provider and harness run on both versions, with source
hash equality checked before starting. The provider reads real local files and
metadata but avoids the separate stock mutable-access-time sorting problem.
Settings remain bulk count 100, flush interval five seconds, JSON passthrough,
semantic search disabled, REST gateway disabled, no folder indexing and no deletion
reconciliation. These are synthetic checkpoint/indexing tests, not representative
PDF extraction or production-provider sorting tests.

Hardware was an Intel Core Ultra 5 135U (12 cores, 14 logical processors), Windows
11 Enterprise with approximately 15.5 GiB visible RAM. Podman had seven virtual
CPUs and 2 GiB RAM; Elasticsearch 9.5.5 had a 1 GiB heap. Test JVMs had a 1 GiB
maximum heap. Java was 25.0.2 and Maven 3.9.11. No other build or test suite ran
during measured crawls. The idle partner JVM remains resident, which is part of
this controlled comparison rather than a single-process production deployment.

The identical randomized seed otherwise generates identical API-key names, and
the shared test helper invalidates an existing key by name. A small smoke test
exposed that authentication collision. Both workers therefore receive one
dedicated test API key through `CHECKPOINT_BENCHMARK_API_KEY`. It is not recorded
in result metadata. The corrected smoke test passed all twelve reduced-size
crawls. The failed smoke run is excluded from the larger experiment.

### Measured results

| Dataset | Before median | Fixed 5K median | Change | Indicative paired-bootstrap 95% interval |
|---|---:|---:|---:|---:|
| One directory / 50,000 files | 83.026 s | 85.822 s | +3.37% | -31.56% to +38.03% |
| 500 directories / 100 files each | 89.421 s | 91.117 s | +1.90% | -23.16% to +27.96% |

For `single-50000`, the comparison reports **insufficient precision to rule out a material slowdown** against the preselected 10% practical slowdown threshold.

For `many-50000`, the comparison reports **insufficient precision to rule out a material slowdown** against the preselected 10% practical slowdown threshold.


The table compares the two versions' separate medians. The median of the
within-pair percentage changes was -0.63% for the single directory and -5.21% for
many directories. These are different statistics: the median of ratios need not
equal the ratio of medians. Both are included in the raw comparison. The interval
resamples matched pairs and estimates change in the ratio of medians shown in
the table. Neither dataset has enough precision to rule out a 10% slowdown.

These results compare the old checkpoint implementation with the fixed 5,000-operation
policy. They do not directly compare 1,000 against 5,000 on the same larger datasets.
The earlier smaller, sequential experiment cannot establish that difference.
Paired bootstrap intervals retain baseline/fixed pairing but remain indicative:
samples within persistent JVMs may be correlated, and alternating order does not
remove every filesystem-cache, thermal, load or cluster effect. No runs were
discarded based on whether their timing favored the change.

Size-triggered bulk requests execute synchronously on the calling crawler thread
in `FsCrawlerBulkProcessor.execute`. Timer-triggered requests can overlap traversal.
Consequently, reduced asynchronous overlap alone is not an established explanation
for the earlier timing difference. This experiment measures complete healthy scans;
it does not separately attribute time to acknowledgement waiting, checkpoint I/O,
filesystem operations or garbage collection.

Download the {download}`larger experiment samples, source revisions, hardware and paired comparisons <checkpoint-benchmark-5000-results.json>`.

### Candidate validation and reproduction

The revised 5,001-file regression was first run against the 1,000-operation code.
It failed because actual barriers were `[1000, 2000, 3000, 4000, 5000, 5001]`,
where the candidate requires `[5000, 5001]`. After changing the minimum, 27
parser/crawler unit cases and two real Elasticsearch integration cases passed,
including rejected-document recovery with a fresh crawler/client. The earlier
full regular-suite totals above belong to the earlier revision; they are not
presented as a new full-suite run on this candidate.

The benchmark runner requires clean committed sources in both working trees and
an identical harness/provider. It preserves old Failsafe summaries separately so
modules without selected tests cannot verify a stale failure from another run.
It rejects incomplete samples, failed Maven verification and processing errors.

Example 5: run the complete alternating experiment from the fixed checkout.
Supply a dedicated API key for the disposable test cluster and identical JVM
options for both workers through the parent environment.

```powershell
$env:CHECKPOINT_BENCHMARK_API_KEY = '<dedicated test API key>'
python scripts/benchmark-checkpoint-paired.py --baseline C:\work\baseline --fixed C:\work\fixed --output C:\bench\run01
```

Example 6: calculate the paired comparison after successful completion.

```powershell
python scripts/compare-checkpoint-paired.py C:\bench\run01\paired-results.json
```

## Explicit checkpoint configuration validation

The later `codex/configurable-checkpoint-limits` branch replaces implicit bulk
scaling with four independently configured `fs.checkpoint` fields. Existing jobs
use 100 directories, 5,000 operations, 32 MiB and 30 seconds; overrides can change
any subset. Invalid values and unknown YAML/JSON section keys fail loading.
Valid limits below bulk thresholds warn without being rewritten.

`CheckpointSettingsTest` covers real YAML/JSON loading, partial/default sections,
round trips, split files, property precedence, invalid values and warning output.
`FsParserCheckpointAcknowledgementTest` checks configured directory, operation,
byte and elapsed-time boundaries, including independence from large bulk settings.
Its elapsed-time case advances the internal monotonic timestamp instead of sleeping.
`FsCrawlerCheckpointSettingsIT` writes and reloads a real job `_settings.yaml`,
then checks a completed Elasticsearch crawl with explicit limits. Existing replay
and bulk-item-failure integration cases remain part of the focused validation.

Validation on 7 November 2026: the initial tests produced 27 settings failures and
one ignored-directory-limit failure against the constant-based implementation.
Further red cases exposed saturated non-finite/oversized byte sizes and semantic
duration equality on a settings round trip; both were corrected in the new code.
The final integration-test reactor run reported 402 unit cases (387 passed, 15
skipped) and three selected integration cases (all passed), without failure-ignore
flags. All 33 `CheckpointSettingsTest` cases and all eleven checkpoint
acknowledgement/boundary cases passed. The distribution-module suite was outside
this reactor selection. Strict Sphinx documentation validation also passed.

Example 7: reproduce the settings/parser unit and selected integration verification
against a disposable Elasticsearch instance.

```powershell
mvn -B -ntp verify -pl integration-tests -am '-Pdaily,sequential_tests' '-Dtest=*Test' '-Dit.test=FsCrawlerCheckpointSettingsIT,FsCrawlerCheckpointReplayIT,FsCrawlerBulkItemFailureIT' '-Dtests.cluster.url=http://127.0.0.1:19203' '-Dtests.leaveTemporary=false' '-Ddocker.skip=true'
```

## Nested text-tree recovery regression

This test checks how FSCrawler uses `_checkpoint.json` to restart an
interrupted scan. During a scan, the checkpoint records completed folders,
folders still waiting to be scanned, and the current unfinished folder. Progress
is saved in batches, after the associated Elasticsearch bulk operations have
succeeded, so the file can lag behind the work currently happening in memory.

When scanning restarts, FSCrawler skips folders recorded as completed in the
saved checkpoint and continues with the pending folders. It replays an unfinished
folder from its beginning, so some files in that folder may be indexed again.
Work completed after the last saved checkpoint may also be repeated. This is
intentional: recovery repeats work where necessary rather than treating
unconfirmed progress as safely completed. A checkpoint marked `COMPLETED`
instead supplies the time reference for a new scan.

`FsCrawlerCheckpointNestedReplayIT` creates 1,000 real UTF-8 `.txt` files in
40 directories, plus the source root. Eight independent branches each extend
five directory levels below the root; every non-root directory contains 25 files.
Names include a randomized, reproducible token. Content includes a non-ASCII
character to check UTF-8 extraction. All paths use Java `Path.resolve`, without
drive letters, shell commands or platform-specific separators.

The test uses real Tika extraction and a real Elasticsearch instance. Its
test-only `CheckpointRecoveryFsProvider` extends the existing stable-order local
filesystem adapter. Instance-local `ConcurrentHashMap` counters record directory
listings and file opens. Hooks execute on the crawler thread, so interruption is
triggered by a particular file rather than an elapsed-time guess. This adapter
is registered only in the integration-test service-loader resource.

### Scenario A: restart an interrupted nested crawl

Checkpoint boundaries are deliberately small: one completed directory or ten
operations; Elasticsearch bulk size is ten. When traversal opens the thirteenth
file in the fourth branch's fifth-level directory, the hook calls
`FsParser.close()`. Earlier whole five-level branches already have acknowledged
checkpoints, which the test explicitly asserts. The current directory is
incomplete, and multiple directories remain pending.

The test closes and joins the original crawler, checks the on-disk checkpoint,
then creates a fresh `FsCrawlerImpl`, provider and Elasticsearch clients with
the same metadata directory. Recovery assertions verify:

- The current directory is pending and is not durably marked completed.
- The saved file cursor is zero; its processed counter excludes the unfinished
  directory, even when some of its documents have reached Elasticsearch.
- Completed directories are neither listed nor opened again.
- Every remaining directory is listed exactly once.
- Every file in the interrupted directory is reopened, including its first file.
- The first replayed document's Elasticsearch version increases by exactly one;
  a document in a completed directory retains version one.
- All 1,000 expected filenames and extracted contents match, rather than merely
  checking a total that could hide a missing or duplicated document.
- The folder index contains 41 records, and final checkpoint counters report
  1,000 files, zero deletions, COMPLETED, and empty traversal lists.

### Scenario B: parent replay and defensive completed-entry handling

The second parameterized case stops in the fourth branch at depth one, so
the root has both completed and pending immediate children. It then deliberately
adjusts that genuine saved frontier: the root is made current and
unfinished, and an already completed directory is placed in the pending queue.
This is a synthetic recovery fixture, not a claim that a healthy queue normally
contains that contradictory entry.

On restart, the completed queue entry must be skipped. Re-listing the root must
neither enqueue its completed children nor duplicate children already pending.
Queue snapshots are captured by the provider and asserted on the test thread.
The same final document, version, directory-listing and counter checks apply.

Each fixture uses its own job and index names. The two cases also execute on the
same JUnit thread. No production code or checkpoint format changes are needed.

### Running on Windows or Linux

Example 8: run the nested recovery cases against a dedicated Elasticsearch test
cluster. This single-line Maven command works in PowerShell and Linux shells.
The existing harness uses the supplied API key, or generates one with its default
test credentials (`elastic` / `changeme`); API-key support must be enabled.

```text
mvn -B -ntp verify -pl integration-tests -am -DskipUnitTests -Dit.test=FsCrawlerCheckpointNestedReplayIT -Dtests.cluster.url=http://127.0.0.1:19204 -Dtests.leaveTemporary=false -Ddocker.skip=true -Dossindex.skip=true
```

The test is not opt-in: selecting normal integration tests includes it. Its
`@VerySlow` annotation permits up to ten minutes. Each Awaitility wait is bounded
at two minutes. Dataset generation and instrumentation are test code only.

This is a fresh-instance recovery test after a controlled stop. It does not kill
the JVM, simulate power loss, verify atomic checkpoint replacement, or
exercise files being renamed/deleted while the crawler is stopped. Those are
separate failure scenarios. Linux and Windows runtime results are recorded with
the validation report for this change.

Validation on 9 November 2026: the integration-test reactor on Windows (Java 25,
compiling for Java 17) reported 405 unit cases: 390 passed, 15 skipped, with no
failures or errors. All five selected integration cases passed (the two nested
recovery cases plus the existing replay, checkpoint-settings and bulk-item-failure
cases). After strengthening the natural stop point to depth five, both nested
cases passed again on Windows and on Linux (Java 21 in the Maven 3.9.11 container),
without failures, errors or skips. Elasticsearch 9.5.6 ran in a dedicated test
container. The integration-test module's full Spotless check also passed.
These runs did not select every integration test or the distribution-module suite.
