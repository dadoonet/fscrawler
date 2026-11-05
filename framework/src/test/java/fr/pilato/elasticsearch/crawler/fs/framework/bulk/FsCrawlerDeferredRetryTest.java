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
import fr.pilato.elasticsearch.crawler.fs.framework.ByteSizeValue;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class FsCrawlerDeferredRetryTest extends AbstractFSCrawlerTestCase {
    @Test
    void responseCallbackDoesNotWaitForRetryDelay() throws Exception {
        var calls = new AtomicInteger();
        int count = RandomizedTest.randomIntInRange(randomizedRandomForTests, 8, 16);
        var operations = new ArrayList<TestOperation>();
        for (int i = 0; i < count; i++) operations.add(operation());
        var clock = new AtomicLong();
        var listener = new FsCrawlerRetryBulkProcessorListener<TestOperation, TestBulkRequest, TestBulkResponse>(
                Duration.ofMinutes(1), Duration.ofSeconds(5), Duration.ofSeconds(5), "rejected");
        Engine<TestOperation, TestBulkRequest, TestBulkResponse> engine =
                request -> response(request, calls.incrementAndGet() == 1);
        var processor = new FsCrawlerBulkProcessor.Builder<>(engine, listener, TestBulkRequest::new)
                .setBulkActions(count)
                .setNanoTimeSupplier(clock::get)
                .build();
        var worker = Executors.newSingleThreadExecutor();
        try {
            var added = worker.submit(() -> operations.forEach(processor::add));
            Assertions.assertThatCode(() -> added.get(500, TimeUnit.MILLISECONDS))
                    .as("the response callback must queue retries without sleeping")
                    .doesNotThrowAnyException();
            Assertions.assertThat(calls.get()).isEqualTo(1);
        } finally {
            clock.set(TimeUnit.MINUTES.toNanos(2));
            worker.shutdownNow();
            Assertions.assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            processor.close();
        }
    }

    @Test
    void expiredQueuedRetryIsReportedWithoutAnotherRequest() throws Exception {
        var calls = new AtomicInteger();
        var terminal = new AtomicInteger();
        var clock = new AtomicLong();
        var listener =
                new FsCrawlerRetryBulkProcessorListener<TestOperation, TestBulkRequest, TestBulkResponse>(
                        Duration.ofMillis(100), Duration.ZERO, Duration.ZERO, "rejected") {
                    @Override
                    protected void onTerminalItemFailure(
                            long id,
                            TestBulkRequest request,
                            FsCrawlerBulkResponse.BulkItemResponse<TestOperation> item,
                            Exception cause) {
                        terminal.incrementAndGet();
                        Assertions.assertThat(cause).hasMessageContaining("deadline");
                    }
                };
        Engine<TestOperation, TestBulkRequest, TestBulkResponse> engine =
                request -> response(request, calls.incrementAndGet() == 1);
        try (var processor = new FsCrawlerBulkProcessor.Builder<>(engine, listener, TestBulkRequest::new)
                .setBulkActions(1)
                .setNanoTimeSupplier(clock::get)
                .build()) {
            processor.add(operation());
            clock.set(TimeUnit.MILLISECONDS.toNanos(150));
            processor.flush();
            Assertions.assertThat(calls.get()).isEqualTo(1);
            Assertions.assertThat(terminal.get()).isEqualTo(1);
        }
    }

    @Test
    void transientRejectedBatchRecoversWithoutPrematureTerminalFailures() throws Exception {
        var clock = new AtomicLong();
        var calls = new AtomicInteger();
        var terminal = new AtomicInteger();
        int count = RandomizedTest.randomIntInRange(randomizedRandomForTests, 8, 16);
        var operations = new ArrayList<TestOperation>();
        for (int i = 0; i < count; i++) operations.add(operation());
        var listener =
                new FsCrawlerRetryBulkProcessorListener<TestOperation, TestBulkRequest, TestBulkResponse>(
                        Duration.ofSeconds(1), Duration.ofMillis(100), Duration.ofMillis(100), "rejected") {
                    @Override
                    protected long retryDelayNanos(long cap) {
                        return 0;
                    }

                    @Override
                    protected void onTerminalItemFailure(
                            long id,
                            TestBulkRequest request,
                            FsCrawlerBulkResponse.BulkItemResponse<TestOperation> item,
                            Exception cause) {
                        terminal.incrementAndGet();
                    }
                };
        Engine<TestOperation, TestBulkRequest, TestBulkResponse> engine = request -> {
            Assertions.assertThat(request.getOperations()).containsExactlyElementsOf(operations);
            return response(request, calls.incrementAndGet() < 3);
        };
        try (var processor = new FsCrawlerBulkProcessor.Builder<>(engine, listener, TestBulkRequest::new)
                .setBulkActions(count)
                .setNanoTimeSupplier(clock::get)
                .build()) {
            operations.forEach(processor::add);
            processor.flushWhileQuiesced(() -> {
                Assertions.assertThat(calls.get()).isEqualTo(3);
                Assertions.assertThat(terminal.get()).isZero();
            });
        }
    }

    @Test
    void retryExpiresAfterBeforeBulkWithoutSendingIt() throws Exception {
        var clock = new AtomicLong();
        var calls = new AtomicInteger();
        var terminal = new AtomicInteger();
        var listener =
                new FsCrawlerRetryBulkProcessorListener<TestOperation, TestBulkRequest, TestBulkResponse>(
                        Duration.ofSeconds(1), Duration.ZERO, Duration.ZERO, "rejected") {
                    /** Advance the clock while the pre-dispatch callback is running. */
                    @Override
                    public void beforeBulk(long id, TestBulkRequest request) {
                        if (id > 1) clock.set(TimeUnit.SECONDS.toNanos(2));
                    }

                    @Override
                    protected void onTerminalItemFailure(
                            long id,
                            TestBulkRequest request,
                            FsCrawlerBulkResponse.BulkItemResponse<TestOperation> item,
                            Exception cause) {
                        terminal.incrementAndGet();
                        Assertions.assertThat(cause).hasMessageContaining("deadline");
                    }
                };
        Engine<TestOperation, TestBulkRequest, TestBulkResponse> engine =
                request -> response(request, calls.incrementAndGet() == 1);
        try (var processor = new FsCrawlerBulkProcessor.Builder<>(engine, listener, TestBulkRequest::new)
                .setBulkActions(1)
                .setNanoTimeSupplier(clock::get)
                .build()) {
            processor.add(operation());
            processor.flushWhileQuiesced(
                    () -> Assertions.assertThat(terminal.get()).isEqualTo(1));
            processor.flush();
            Assertions.assertThat(calls.get()).isEqualTo(1);
            Assertions.assertThat(terminal.get()).isEqualTo(1);
        }
    }

    @Test
    void retryGroupsRespectActionAndByteLimits() throws Exception {
        for (boolean byteLimited : new boolean[] {false, true}) {
            var sizes = new ArrayList<Integer>();
            Engine<TestOperation, TestBulkRequest, TestBulkResponse> engine = request -> {
                sizes.add(request.numberOfActions());
                return response(request, false);
            };
            var builder = new FsCrawlerBulkProcessor.Builder<>(engine, noRetryListener(), TestBulkRequest::new);
            int limit = byteLimited ? 1 : RandomizedTest.randomIntInRange(randomizedRandomForTests, 2, 4);
            if (byteLimited) builder.setByteSize(new ByteSizeValue(1));
            else builder.setBulkActions(limit);
            try (var processor = builder.build()) {
                int count = limit * 3 + 1;
                for (int i = 0; i < count; i++) {
                    processor.addRetry(operation(), 0, Long.MAX_VALUE, failure -> Assertions.fail("unexpected expiry"));
                }
                Assertions.assertThat(sizes).isEmpty();
                processor.flush();
                Assertions.assertThat(sizes)
                        .allSatisfy(size -> Assertions.assertThat(size).isBetween(1, limit));
                Assertions.assertThat(sizes.stream().mapToInt(Integer::intValue).sum())
                        .isEqualTo(count);
            }
        }
    }

    @Test
    void earlyRetryWakeupDoesNotWaitForPeriodicFlush() throws Exception {
        var completed = new CountDownLatch(1);
        var clock = new AtomicLong(System.nanoTime());
        Engine<TestOperation, TestBulkRequest, TestBulkResponse> engine = request -> {
            completed.countDown();
            return response(request, false);
        };
        try (var processor = new FsCrawlerBulkProcessor.Builder<>(engine, noRetryListener(), TestBulkRequest::new)
                .setFlushInterval(TimeValue.timeValueSeconds(60))
                .setNanoTimeSupplier(clock::get)
                .build()) {
            processor.addRetry(
                    operation(),
                    clock.get() + TimeUnit.MINUTES.toNanos(1),
                    clock.get() + TimeUnit.MINUTES.toNanos(2),
                    failure -> Assertions.fail("unexpected expiry"));
            processor.addRetry(
                    operation(),
                    clock.get(),
                    clock.get() + TimeUnit.MINUTES.toNanos(2),
                    failure -> Assertions.fail("unexpected expiry"));
            Assertions.assertThat(completed.await(2, TimeUnit.SECONDS)).isTrue();
            clock.addAndGet(TimeUnit.MINUTES.toNanos(1));
        }
    }

    @Test
    void expiryCallbacksCannotMakeAnotherExpiredRetryReachTheEngine() throws Exception {
        var clock = new AtomicLong(6);
        var calls = new AtomicInteger();
        var terminal = new AtomicInteger();
        Engine<TestOperation, TestBulkRequest, TestBulkResponse> engine = request -> {
            calls.incrementAndGet();
            return response(request, false);
        };
        try (var processor = new FsCrawlerBulkProcessor.Builder<>(engine, noRetryListener(), TestBulkRequest::new)
                .setNanoTimeSupplier(clock::get)
                .build()) {
            processor.addRetry(operation(), 0, 10, failure -> terminal.incrementAndGet());
            processor.addRetry(operation(), 0, 5, failure -> {
                terminal.incrementAndGet();
                clock.set(11);
                throw new IllegalStateException("terminal observer failed");
            });
            processor.flushWhileQuiesced(
                    () -> Assertions.assertThat(terminal.get()).isEqualTo(2));
            Assertions.assertThat(calls.get()).isZero();
            processor.flush();
            Assertions.assertThat(terminal.get()).isEqualTo(2);
        }
    }

    @Test
    void interruptedDelayedDrainRetainsRetryAndInterruptFlag() throws Exception {
        var clock = new AtomicLong();
        var calls = new AtomicInteger();
        Engine<TestOperation, TestBulkRequest, TestBulkResponse> engine = request -> {
            calls.incrementAndGet();
            return response(request, false);
        };
        try (var processor = new FsCrawlerBulkProcessor.Builder<>(engine, noRetryListener(), TestBulkRequest::new)
                .setNanoTimeSupplier(clock::get)
                .build()) {
            processor.addRetry(
                    operation(),
                    TimeUnit.SECONDS.toNanos(1),
                    TimeUnit.SECONDS.toNanos(2),
                    failure -> Assertions.fail("unexpected expiry"));
            try {
                Thread.currentThread().interrupt();
                Assertions.assertThatThrownBy(processor::flush).isInstanceOf(IllegalStateException.class);
                Assertions.assertThat(Thread.currentThread().isInterrupted()).isTrue();
                Assertions.assertThat(calls.get()).isZero();
            } finally {
                Thread.interrupted();
            }
            clock.set(TimeUnit.SECONDS.toNanos(1));
            processor.flush();
            Assertions.assertThat(calls.get()).isEqualTo(1);
        }
    }

    @Test
    void closeDrainsBoundedRetriesAndRejectsNewAdditions() throws Exception {
        var calls = new AtomicInteger();
        var terminal = new AtomicInteger();
        var listener =
                new FsCrawlerRetryBulkProcessorListener<TestOperation, TestBulkRequest, TestBulkResponse>(
                        Duration.ofSeconds(1), Duration.ZERO, Duration.ZERO, "rejected") {
                    @Override
                    protected void onTerminalItemFailure(
                            long id,
                            TestBulkRequest request,
                            FsCrawlerBulkResponse.BulkItemResponse<TestOperation> item,
                            Exception cause) {
                        terminal.incrementAndGet();
                    }
                };
        Engine<TestOperation, TestBulkRequest, TestBulkResponse> engine = request -> {
            calls.incrementAndGet();
            return response(request, true);
        };
        var processor = new FsCrawlerBulkProcessor.Builder<>(engine, listener, TestBulkRequest::new).build();
        processor.add(operation());
        processor.close();
        Assertions.assertThat(calls.get()).isEqualTo(4);
        Assertions.assertThat(terminal.get()).isEqualTo(1);
        Assertions.assertThatThrownBy(() -> processor.add(operation())).isInstanceOf(IllegalStateException.class);
        Assertions.assertThatThrownBy(() -> processor.addRetry(operation(), 0, 1, failure -> {}))
                .isInstanceOf(IllegalStateException.class);
    }

    private FsCrawlerRetryBulkProcessorListener<TestOperation, TestBulkRequest, TestBulkResponse> noRetryListener() {
        return new FsCrawlerRetryBulkProcessorListener<>("rejected");
    }

    private TestOperation operation() {
        return new TestOperation(
                new TestBean(RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 8, 16)));
    }

    private TestBulkResponse response(TestBulkRequest request, boolean failed) {
        var response = new TestBulkResponse();
        for (var operation : request.getOperations()) {
            var item = new FsCrawlerBulkResponse.BulkItemResponse<TestOperation>();
            item.setOperation(operation);
            item.setFailed(failed);
            item.setFailureType(failed ? "rejected" : null);
            item.setFailureMessage(failed ? "queue full" : null);
            response.items.add(item);
        }
        response.errors = failed;
        return response;
    }
}
