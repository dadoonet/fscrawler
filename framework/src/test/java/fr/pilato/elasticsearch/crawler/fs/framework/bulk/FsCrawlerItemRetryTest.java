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
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class FsCrawlerItemRetryTest extends AbstractFSCrawlerTestCase {
    @Test
    void retriesDoNotNestBulkExecution() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger depth = new AtomicInteger();
        var operation = new TestOperation(
                new TestBean(RandomizedTest.randomAsciiLettersOfLength(randomizedRandomForTests, 12)));
        var listener =
                new FsCrawlerRetryBulkProcessorListener<TestOperation, TestBulkRequest, TestBulkResponse>("rejected");
        Engine<TestOperation, TestBulkRequest, TestBulkResponse> engine = request -> {
            depth.accumulateAndGet(
                    (int) Arrays.stream(Thread.currentThread().getStackTrace())
                            .filter(frame -> frame.getClassName().equals(FsCrawlerBulkProcessor.class.getName())
                                    && frame.getMethodName().equals("execute"))
                            .count(),
                    Math::max);
            return response(operation, calls.incrementAndGet() == 1);
        };
        try (var processor = new FsCrawlerBulkProcessor.Builder<>(engine, listener, TestBulkRequest::new)
                .setBulkActions(1)
                .build()) {
            processor.add(operation);
            processor.flush();
            Assertions.assertThat(calls.get()).isEqualTo(2);
            Assertions.assertThat(depth.get()).isEqualTo(1);
        }
    }

    @Test
    void interruptedBackoffRecordsTerminalFailureAndPreservesInterrupt() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger terminal = new AtomicInteger();
        var operation = new TestOperation(
                new TestBean(RandomizedTest.randomAsciiLettersOfLength(randomizedRandomForTests, 12)));
        var listener =
                new FsCrawlerRetryBulkProcessorListener<TestOperation, TestBulkRequest, TestBulkResponse>("rejected") {
                    @Override
                    protected void onTerminalItemFailure(
                            long id,
                            TestBulkRequest request,
                            FsCrawlerBulkResponse.BulkItemResponse<TestOperation> item,
                            Exception cause) {
                        terminal.incrementAndGet();
                        Assertions.assertThat(cause).hasMessageContaining("interrupted");
                    }
                };
        Engine<TestOperation, TestBulkRequest, TestBulkResponse> engine =
                request -> response(operation, calls.incrementAndGet() == 1);
        var processor = new FsCrawlerBulkProcessor.Builder<>(engine, listener, TestBulkRequest::new)
                .setBulkActions(100)
                .build();
        try {
            processor.add(operation);
            Thread.currentThread().interrupt();
            processor.flush();
            Assertions.assertThat(Thread.currentThread().isInterrupted()).isTrue();
            Assertions.assertThat(calls.get()).isEqualTo(1);
            Assertions.assertThat(terminal.get()).isEqualTo(1);
        } finally {
            Thread.interrupted();
            processor.close();
        }
    }

    private TestBulkResponse response(TestOperation operation, boolean failed) {
        var response = new TestBulkResponse();
        var item = new FsCrawlerBulkResponse.BulkItemResponse<TestOperation>();
        item.setOperation(operation);
        item.setFailed(failed);
        item.setFailureMessage(failed ? "rejected" : null);
        response.items.add(item);
        response.errors = failed;
        return response;
    }
}
