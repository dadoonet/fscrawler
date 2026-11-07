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
previous safe frontier. Failure details from the bulk client remain in the error.

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
not an exactly-once operation journal; deletion accounting may differ on replay. A finished directory becomes
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

Fixture generation, access-time stabilization, validation, and cleanup occur outside
the timed interval. Timing includes crawler construction/startup through one scan
finishing. Each workload has one discarded warm-up and five measured runs. Fixture
access timestamps are fixed to avoid unrelated NTFS deferred-access-time changes
destabilizing the current filesystem sorting comparator.

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
