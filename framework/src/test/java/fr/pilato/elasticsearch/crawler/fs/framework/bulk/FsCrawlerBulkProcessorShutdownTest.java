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
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import fr.pilato.elasticsearch.crawler.fs.test.framework.Slow;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@Slow
class FsCrawlerBulkProcessorShutdownTest extends AbstractFSCrawlerTestCase {
    @Test
    void interruptedCloseWhileBulkIsInFlightReportsIOExceptionAndPreservesInterrupt() throws Exception {
        try (var fixture = new BlockingBulk()) {
            var producing = fixture.startBulk();
            var closing = startClose(fixture.workers, fixture.processor);
            closing.interruptWaiting();
            var outcome = closing.result().get(5, TimeUnit.SECONDS);
            Assertions.assertThat(outcome.failure())
                    .isInstanceOf(IOException.class)
                    .hasCauseInstanceOf(InterruptedException.class);
            Assertions.assertThat(outcome.interrupted()).isTrue();
            Assertions.assertThatThrownBy(() -> fixture.processor.add(operation()))
                    .isInstanceOf(IllegalStateException.class);
            fixture.releaseEngine.countDown();
            producing.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void closeCanBeRetriedAfterInterruptionAndDrainsPreviouslyAcceptedOperations() throws Exception {
        try (var fixture = new BlockingBulk()) {
            var producing = fixture.startBulk();
            fixture.processor.add(fixture.operations.get(2));
            var closing = startClose(fixture.workers, fixture.processor);
            closing.interruptWaiting();
            Assertions.assertThat(closing.result().get(5, TimeUnit.SECONDS).failure())
                    .isNotNull();
            fixture.releaseEngine.countDown();
            producing.get(5, TimeUnit.SECONDS);
            fixture.processor.close();
            Assertions.assertThat(fixture.delivered).containsExactlyInAnyOrderElementsOf(fixture.operations);
            fixture.processor.close();
            Assertions.assertThat(fixture.delivered).hasSize(fixture.operations.size());
        }
    }

    @Test
    void interruptedCloseWaitingForQuiescedFlushLeavesProcessorOpen() throws Exception {
        var actionEntered = new CountDownLatch(1);
        var releaseAction = new CountDownLatch(1);
        var delivered = new ConcurrentLinkedQueue<TestOperation>();
        Engine<TestOperation, TestBulkRequest, TestBulkResponse> engine = request -> {
            delivered.addAll(List.copyOf(request.getOperations()));
            return new TestBulkResponse();
        };
        var processor = processor(engine);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var flushing = workers.submit(() -> processor.flushWhileQuiesced(() -> {
                actionEntered.countDown();
                await(releaseAction);
            }));
            await(actionEntered);
            var closing = startClose(workers, processor);
            closing.interruptWaiting();
            var outcome = closing.result().get(5, TimeUnit.SECONDS);
            Assertions.assertThat(outcome.failure())
                    .isInstanceOf(IOException.class)
                    .hasCauseInstanceOf(InterruptedException.class);
            Assertions.assertThat(outcome.interrupted()).isTrue();
            releaseAction.countDown();
            flushing.get(5, TimeUnit.SECONDS);
            var accepted = operation();
            processor.add(accepted);
            processor.close();
            Assertions.assertThat(delivered).containsExactly(accepted);
        } finally {
            releaseAction.countDown();
            workers.shutdownNow();
            Assertions.assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            processor.close();
        }
    }

    @Test
    void interruptedFlushPreservesUncheckedContractAndDoesNotRunCompletionAction() throws Exception {
        try (var fixture = new BlockingBulk()) {
            var producing = fixture.startBulk();
            var actionRan = new AtomicBoolean();
            var thread = new AtomicReference<Thread>();
            var started = new CountDownLatch(1);
            var result = fixture.workers.submit(() -> {
                thread.set(Thread.currentThread());
                started.countDown();
                Exception failure = null;
                try {
                    fixture.processor.flushWhileQuiesced(() -> actionRan.set(true));
                } catch (Exception e) {
                    failure = e;
                }
                return new CloseOutcome(failure, Thread.currentThread().isInterrupted());
            });
            await(started);
            new ClosingAttempt(thread, result).interruptWaiting();
            var outcome = result.get(5, TimeUnit.SECONDS);
            Assertions.assertThat(outcome.failure())
                    .isInstanceOf(IllegalStateException.class)
                    .hasCauseInstanceOf(InterruptedException.class);
            Assertions.assertThat(outcome.interrupted()).isTrue();
            Assertions.assertThat(actionRan).isFalse();
            fixture.processor.add(fixture.operations.get(2));
            fixture.releaseEngine.countDown();
            producing.get(5, TimeUnit.SECONDS);
            fixture.processor.flushWhileQuiesced(() -> actionRan.set(true));
            Assertions.assertThat(actionRan).isTrue();
            Assertions.assertThat(fixture.delivered).containsExactlyInAnyOrderElementsOf(fixture.operations);
        }
    }

    private ClosingAttempt startClose(
            ExecutorService workers,
            FsCrawlerBulkProcessor<TestOperation, TestBulkRequest, TestBulkResponse> processor) {
        var thread = new AtomicReference<Thread>();
        var started = new CountDownLatch(1);
        var result = workers.submit(() -> {
            thread.set(Thread.currentThread());
            started.countDown();
            Exception failure = null;
            try {
                processor.close();
            } catch (Exception e) {
                failure = e;
            }
            return new CloseOutcome(failure, Thread.currentThread().isInterrupted());
        });
        await(started);
        return new ClosingAttempt(thread, result);
    }

    private record CloseOutcome(Exception failure, boolean interrupted) {}

    private record ClosingAttempt(AtomicReference<Thread> thread, Future<CloseOutcome> result) {
        void interruptWaiting() {
            // The engine or flush action stays blocked until after this assertion.
            Assertions.assertThatThrownBy(() -> result.get(200, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            thread.get().interrupt();
        }
    }

    private class BlockingBulk implements AutoCloseable {
        final CountDownLatch engineEntered = new CountDownLatch(1);
        final CountDownLatch releaseEngine = new CountDownLatch(1);
        final ConcurrentLinkedQueue<TestOperation> delivered = new ConcurrentLinkedQueue<>();
        final List<TestOperation> operations = List.of(operation(), operation(), operation());
        final ExecutorService workers = Executors.newFixedThreadPool(2);
        final FsCrawlerBulkProcessor<TestOperation, TestBulkRequest, TestBulkResponse> processor =
                processor(request -> {
                    engineEntered.countDown();
                    await(releaseEngine);
                    delivered.addAll(List.copyOf(request.getOperations()));
                    return new TestBulkResponse();
                });

        Future<?> startBulk() {
            var producing = workers.submit(() -> {
                processor.add(operations.get(0));
                processor.add(operations.get(1));
            });
            await(engineEntered);
            return producing;
        }

        @Override
        public void close() throws Exception {
            releaseEngine.countDown();
            workers.shutdownNow();
            Assertions.assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            processor.close();
        }
    }

    private FsCrawlerBulkProcessor<TestOperation, TestBulkRequest, TestBulkResponse> processor(
            Engine<TestOperation, TestBulkRequest, TestBulkResponse> engine) {
        return new FsCrawlerBulkProcessor.Builder<>(
                        engine,
                        new FsCrawlerSimpleBulkProcessorListener<TestOperation, TestBulkRequest, TestBulkResponse>(),
                        TestBulkRequest::new)
                .setBulkActions(2)
                .build();
    }

    private TestOperation operation() {
        return new TestOperation(
                new TestBean(RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 8, 16)));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out coordinating interrupted shutdown");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted coordinating shutdown test", e);
        }
    }
}
