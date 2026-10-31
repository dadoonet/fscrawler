/*
 * Licensed to David Pilato (the "Author") under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 * Made from 🇫🇷🇪🇺 with ❤️ - 2011-2026
 */
package fr.pilato.elasticsearch.crawler.fs.framework.bulk;

import com.carrotsearch.randomizedtesting.jupiter.RandomizedTest;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import fr.pilato.elasticsearch.crawler.fs.test.framework.Slow;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@Slow
class FsCrawlerBulkProcessorRaceTest extends AbstractFSCrawlerTestCase {
    @Test
    void timerAndSizeFlushMustDeliverEveryAcceptedOperationExactlyOnce() throws Exception {
        var timerCapturedBatch = new CountDownLatch(1);
        var releaseTimer = new CountDownLatch(1);
        var producerFinished = new CountDownLatch(1);
        var allocations = new AtomicInteger();
        var delivered = new ConcurrentLinkedQueue<TestOperation>();
        var failures = new ConcurrentLinkedQueue<Throwable>();
        var operations = List.of(operation(), operation(), operation());
        Engine<TestOperation, TestBulkRequest, TestBulkResponse> engine = request -> {
            delivered.addAll(List.copyOf(request.getOperations()));
            return new TestBulkResponse();
        };
        var listener = new FsCrawlerSimpleBulkProcessorListener<TestOperation, TestBulkRequest, TestBulkResponse>() {
            @Override
            public void afterBulk(long id, TestBulkRequest request, Throwable failure) {
                failures.add(failure);
            }
        };
        var processor = new FsCrawlerBulkProcessor.Builder<>(engine, listener, () -> {
                    // Allocation 1 creates the initial queue. Allocation 2 is the timer's
                    // replacement queue, after execute() has already captured the old queue.
                    if (allocations.incrementAndGet() == 2) {
                        timerCapturedBatch.countDown();
                        await(releaseTimer);
                    }
                    return new TestBulkRequest();
                })
                .setBulkActions(2)
                .setFlushInterval(TimeValue.timeValueMillis(10))
                .build();
        var producer = Executors.newSingleThreadExecutor();
        try {
            processor.add(operations.get(0));
            await(timerCapturedBatch);
            var produced = producer.submit(() -> {
                try {
                    // Fills A and triggers a competing rotation to B, then populates B.
                    processor.add(operations.get(1));
                    processor.add(operations.get(2));
                } finally {
                    producerFinished.countDown();
                }
            });
            // Buggy code completes the producer while the timer is paused. Correct
            // locking may block it, so release the timer after a bounded grace period.
            // No assertion depends on whether the producer is allowed to run early.
            producerFinished.await(1, TimeUnit.SECONDS);
            releaseTimer.countDown();
            produced.get(5, TimeUnit.SECONDS);
            processor.flushWhileQuiesced(() -> {});
            Assertions.assertThat(failures).isEmpty();
            Assertions.assertThat(delivered)
                    .as("Each accepted operation must reach the engine exactly once, without losses or duplicates")
                    .containsExactlyInAnyOrderElementsOf(operations);
        } finally {
            releaseTimer.countDown();
            producer.shutdownNow();
            Assertions.assertThat(producer.awaitTermination(5, TimeUnit.SECONDS))
                    .isTrue();
            processor.close();
        }
    }

    @Test
    void slowBulkAllowsEnqueueAndCloseWaitsForAllAcceptedOperations() throws Exception {
        var engineEntered = new CountDownLatch(1);
        var releaseEngine = new CountDownLatch(1);
        var closeStarted = new CountDownLatch(1);
        var executions = new AtomicInteger();
        var delivered = new ConcurrentLinkedQueue<TestOperation>();
        var operations = List.of(operation(), operation(), operation());
        Engine<TestOperation, TestBulkRequest, TestBulkResponse> engine = request -> {
            if (executions.incrementAndGet() == 1) {
                engineEntered.countDown();
                await(releaseEngine);
            }
            delivered.addAll(List.copyOf(request.getOperations()));
            return new TestBulkResponse();
        };
        var processor = new FsCrawlerBulkProcessor.Builder<>(
                        engine,
                        new FsCrawlerSimpleBulkProcessorListener<TestOperation, TestBulkRequest, TestBulkResponse>(),
                        TestBulkRequest::new)
                .setBulkActions(2)
                .build();
        var workers = Executors.newFixedThreadPool(3);
        try {
            var first = workers.submit(() -> {
                processor.add(operations.get(0));
                processor.add(operations.get(1));
            });
            await(engineEntered);
            // Pending work must be enqueueable while another detached batch is in HTTP I/O.
            workers.submit(() -> processor.add(operations.get(2))).get(2, TimeUnit.SECONDS);
            var closing = workers.submit(() -> {
                closeStarted.countDown();
                processor.close();
                return null;
            });
            await(closeStarted);
            Assertions.assertThatThrownBy(() -> closing.get(200, TimeUnit.MILLISECONDS))
                    .isInstanceOf(java.util.concurrent.TimeoutException.class);
            releaseEngine.countDown();
            first.get(5, TimeUnit.SECONDS);
            closing.get(5, TimeUnit.SECONDS);
            Assertions.assertThat(delivered).containsExactlyInAnyOrderElementsOf(operations);
            Assertions.assertThatThrownBy(() -> processor.add(operation())).isInstanceOf(IllegalStateException.class);
            processor.close(); // Idempotent: no duplicate delivery on a second close.
            Assertions.assertThat(delivered).hasSize(operations.size());
        } finally {
            releaseEngine.countDown();
            workers.shutdownNow();
            Assertions.assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            processor.close();
        }
    }

    @Test
    void quiescedFlushWaitsForInFlightListenerAndDrainsItsRetry() throws Exception {
        var engineEntered = new CountDownLatch(1);
        var releaseEngine = new CountDownLatch(1);
        var flushStarted = new CountDownLatch(1);
        var executions = new AtomicInteger();
        var callbackExecutions = new AtomicInteger();
        var operation = operation();
        Engine<TestOperation, TestBulkRequest, TestBulkResponse> engine = request -> {
            if (executions.incrementAndGet() == 1) {
                engineEntered.countDown();
                await(releaseEngine);
            }
            return new TestBulkResponse();
        };
        var listener = new FsCrawlerSimpleBulkProcessorListener<TestOperation, TestBulkRequest, TestBulkResponse>() {
            private boolean retried;

            @Override
            public void afterBulk(long id, TestBulkRequest request, TestBulkResponse response) {
                if (!retried) {
                    retried = true;
                    bulkProcessor.add(operation);
                }
            }
        };
        var processor = new FsCrawlerBulkProcessor.Builder<>(engine, listener, TestBulkRequest::new)
                .setBulkActions(2)
                .setFlushInterval(TimeValue.timeValueMillis(10))
                .build();
        var worker = Executors.newSingleThreadExecutor();
        try {
            processor.add(operation);
            await(engineEntered);
            var flushing = worker.submit(() -> {
                flushStarted.countDown();
                processor.flushWhileQuiesced(() -> callbackExecutions.set(executions.get()));
            });
            await(flushStarted);
            Assertions.assertThatThrownBy(() -> flushing.get(200, TimeUnit.MILLISECONDS))
                    .isInstanceOf(java.util.concurrent.TimeoutException.class);
            releaseEngine.countDown();
            flushing.get(5, TimeUnit.SECONDS);
            Assertions.assertThat(callbackExecutions.get()).isEqualTo(2);
        } finally {
            releaseEngine.countDown();
            worker.shutdownNow();
            Assertions.assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            processor.close();
        }
    }

    private TestOperation operation() {
        return new TestOperation(
                new TestBean(RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 8, 16)));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out coordinating the bulk rotation race");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted coordinating the bulk rotation race", e);
        }
    }
}
