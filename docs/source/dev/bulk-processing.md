(bulk-processing)=
# Bulk processing, concurrency, and shutdown

FSCrawler collects Elasticsearch operations in a pending bulk request. The queue
contains operations ready for indexing or deletion, rather than files waiting
for document parsing. Crawler work and REST uploads can reach the same processor.

This page explains batch ownership, the timer/producer race that motivated the
synchronization, and the regression tests that protect it. For batch and timer
configuration, see [Elasticsearch settings](../admin/fs/elasticsearch.md).

## Components and execution triggers

The implementation is
`fr.pilato.elasticsearch.crawler.fs.framework.bulk.FsCrawlerBulkProcessor`.
Its pending `bulkRequest` accumulates operations until one of these triggers runs:

- A producer calls `add()` and reaches the configured action-count or byte-size limit.
- The `ScheduledExecutorService` runs the periodic flush task.
- A caller explicitly flushes or closes the processor.

The timer is a background Java thread used to send small batches that have not
reached either size threshold. Closing the bulk processor stops this timer; it
does not shut down the operating system or the Elasticsearch server.

## The original queue-rotation race

Previously, `add()` was synchronized on the processor instance, but the timer did
not acquire that monitor. Producer threads were serialized with one another,
while a producer and the timer could both capture and replace `bulkRequest`.

The following sequence illustrates the regression reproduced by the unit test.
A, B, and C are operations; Q1, Q2, and Q3 are separate request objects.

| Step | Timer thread | Producer thread | Result |
|---|---|---|---|
| 1 | Captures Q1 containing A, then pauses while allocating a replacement. | | The timer retains Q1. |
| 2 | | Adds B to Q1, reaching the two-operation threshold. | Q1 contains A and B. |
| 3 | | Replaces Q1 with Q2 and sends Q1. | A and B are submitted. |
| 4 | | Adds C to Q2. | C is pending. |
| 5 | Installs its own replacement, Q3. | | Q2 is displaced, losing C. |
| 6 | Sends its captured Q1. | | A and B are submitted again. |

The original implementation delivered A, B, A, B and omitted C in the regression
run. Making individual flags volatile or atomic cannot protect this compound
capture-and-replace operation.

There was also a gap between detaching a request and registering it as in flight.
A flush could observe an empty queue and no registered executions during that gap.

## Atomic batch ownership

The private `queueLock` monitor protects state that must change together:

| State | Responsibility |
|---|---|
| `bulkRequest` | Adding operations and replacing the pending request. |
| `inFlightExecutions` | Registering detached requests and recording completion. |
| `closed` | Rejecting new work after shutdown begins. |
| `closeCompleted` | Distinguishing a completed close from an interrupted attempt. |
| `quiesced` | Suppressing ordinary timer/size execution during a flush barrier. |

`detachBatch()` runs with `queueLock` held. It returns nothing for an empty queue;
otherwise it captures the current request, installs an empty replacement with the
same limits, and increments `inFlightExecutions` before releasing the lock.

No other queue operation can observe a detached but unregistered batch. A detached
request belongs to its execution; producers append to the new pending request.
The lifecycle flags use ordinary booleans because their accesses share the monitor.
The `AtomicLong` execution-ID generator remains independent.

## Execution and listener completion

The processor releases `queueLock` before invoking the bulk engine or listeners.
A slow Elasticsearch request therefore does not hold the queue monitor throughout
network I/O, and a listener can enqueue a retry.

Execution remains synchronous for the triggering thread. There is no additional
bulk-request worker executor, and this mechanism does not establish a global
completion order between concurrent batches.

After the engine and listener finish, execution decrements `inFlightExecutions`
and calls `queueLock.notifyAll()`. In-flight work includes listener processing:
listeners can record failures or enqueue more operations after HTTP completes.

## Flush barriers

The existing `quiesceLock`, a `ReentrantLock`, serializes explicit flush barriers
and shutdown. It has a different responsibility from the short queue-state
critical sections protected by `queueLock`.

`flush()` delegates to `flushWhileQuiesced()` with an empty completion action.
The shared `drainAndRun()` helper repeatedly:

1. Waits for registered in-flight executions to complete.
2. Detaches and executes pending operations.
3. Rechecks the queue and in-flight count, including listener retry requeues.
4. Runs the completion action when both are empty.

`queueLock.wait()` releases the monitor while waiting, allowing a completing
listener to enqueue retries and signal progress. Other producers can enqueue
while the processor drains, but ordinary execution by other threads is suppressed.

The final idle check and completion action run while holding `queueLock`. This
prevents a producer from adding work between the check and action. The Elasticsearch
client uses the action to check recorded bulk failures through
`flushAndEnsureBulkSucceededSince()`. That check must wait for listeners as well
as network requests. Completion actions should be short and must not depend on
another thread acquiring the queue lock.

An interrupted flush preserves the thread's interrupt flag and throws
`IllegalStateException`. It does not run the completion action as though draining
had succeeded.

## Shutdown and interruption

Here, shutdown means calling `close()` on the bulk processor. It:

1. Acquires the lifecycle lock.
2. Marks the processor closed to new operations under `queueLock`.
3. Stops scheduling timer work.
4. Waits for accepted in-flight work and drains pending operations.
5. Waits for timer-executor termination and records completed shutdown.

A timer invocation that encounters a closed processor returns. A completed close
is idempotent: calling it again does not resubmit operations.

If a thread is interrupted while waiting for an in-flight execution, `close()`
restores its interrupt flag and reports an `IOException` caused by the
`InterruptedException`. The shared drain helper propagates interruption so each
public method can preserve its own exception contract.

`closed` and `closeCompleted` are distinct. An interrupted close after shutdown
begins still rejects new operations, but a later `close()` can finish draining
previously accepted work. Retry from a thread that is no longer interrupted.
Interruption while acquiring the lifecycle lock, before shutdown begins, leaves
the processor open. An interruption does not necessarily cancel a request already
executing in another thread.

The ten-second executor-termination wait is not an overall shutdown deadline:
draining occurs first and can wait longer for requests or listeners. Shutdown
also does not promise to accept new listener requeues after the processor has
closed to new operations.

## Regression tests

### Queue ownership and draining

`framework/src/test/java/fr/pilato/elasticsearch/crawler/fs/framework/bulk/FsCrawlerBulkProcessorRaceTest.java`
contains three tests:

| Test | Behavior verified |
|---|---|
| `timerAndSizeFlushMustDeliverEveryAcceptedOperationExactlyOnce` | Pauses timer replacement allocation, overlaps producer work, and requires every accepted operation once with no missing or extra delivery. |
| `slowBulkAllowsEnqueueAndCloseWaitsForAllAcceptedOperations` | Enqueueing remains possible during blocked bulk I/O; close waits, drains accepted work, rejects later adds, and is idempotent. |
| `quiescedFlushWaitsForInFlightListenerAndDrainsItsRetry` | Flush waits for listener completion and drains a requeued operation below the batch threshold before running its action. |

The tests coordinate execution with `CountDownLatch`, collect observations in
`ConcurrentLinkedQueue`, and use bounded waits. The timer test allows the corrected
producer to block on the queue lock; its correctness assertion does not require
unsafe early producer progress. Incidental unit-test payloads are randomized.

### Interrupted shutdown and flush

`framework/src/test/java/fr/pilato/elasticsearch/crawler/fs/framework/bulk/FsCrawlerBulkProcessorShutdownTest.java`
adds four cases:

| Test | Behavior verified |
|---|---|
| `interruptedCloseWhileBulkIsInFlightReportsIOExceptionAndPreservesInterrupt` | Close reports `IOException` with an interruption cause, preserves the interrupt flag, and rejects new work once shutdown starts. |
| `closeCanBeRetriedAfterInterruptionAndDrainsPreviouslyAcceptedOperations` | A second close drains pending work left by an interrupted attempt without duplicate delivery. |
| `interruptedCloseWaitingForQuiescedFlushLeavesProcessorOpen` | Interruption before acquiring the lifecycle lock does not prematurely close the processor. |
| `interruptedFlushPreservesUncheckedContractAndDoesNotRunCompletionAction` | Flush retains its unchecked interruption contract, skips the premature action, and can subsequently drain successfully. |

The first two cases failed before the shutdown correction: the drain helper
surfaced an unchecked exception, and the closed flag caused a later close to
return without draining pending work.

### Real Elasticsearch requests

`ElasticsearchClientIT.concurrent_timer_and_size_flush_deliver_each_document_once`
in the `elasticsearch-client` module repeats the queue-rotation choreography
through `ElasticsearchEngine` and real bulk HTTP requests.

It indexes three documents with distinct IDs into a fresh index, collects bulk
failures, flushes, and performs real-time GETs. Each document must exist with version
1. Checking only the count would miss duplicate indexing of an existing ID;
checking versions detects it in this controlled scenario. Real-time GET avoids
depending on search refresh timing.

The exactly-once assertions describe these controlled queue-ownership scenarios.
They are not an end-to-end exactly-once guarantee across network failures, retries,
or process crashes. The listener test simulates a requeue rather than exercising
the complete HTTP retry policy.

## Running the checks

Example 1: run the existing bulk tests together with the race and shutdown tests
(15 tests at the time these regressions were added):

```sh
mvn -B -ntp test -pl framework -am "-Dtest=FsCrawlerBulkProcessor*Test" "-DskipIntegTests"
```

Example 2: run the Elasticsearch regression. This requires a working container
runtime for Testcontainers, or an external cluster configured as described in
[Building FSCrawler](build.md).

```sh
mvn -B -ntp verify -pl elasticsearch-client -am "-DskipUnitTests" "-Dit.test=ElasticsearchClientIT#concurrent_timer_and_size_flush_deliver_each_document_once"
```

Example 3: select the full unit suite explicitly when comparing modified and
unchanged source trees:

```sh
mvn -B -ntp test "-Dtest=*Test" "-DskipIntegTests"
```

Compare test class, method, and result, not only aggregate counts. Record skips
separately from failures and errors, and use the same environment and test
selection for both runs. If `maven.test.failure.ignore=true` is used to collect
results across all modules, Maven's `BUILD SUCCESS` does not mean all tests passed.
