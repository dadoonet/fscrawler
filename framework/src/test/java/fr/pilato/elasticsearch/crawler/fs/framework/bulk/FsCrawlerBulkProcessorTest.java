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
import fr.pilato.elasticsearch.crawler.fs.framework.ByteSizeUnit;
import fr.pilato.elasticsearch.crawler.fs.framework.ByteSizeValue;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil;
import fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import java.io.IOException;
import java.time.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

class FsCrawlerBulkProcessorTest extends AbstractFSCrawlerTestCase {
    private static final Logger logger = LogManager.getLogger();
    private static final TestBean PAYLOAD = new TestBean("bar");
    private static final int PAYLOAD_SIZE =
            JsonUtil.serialize(PAYLOAD).getBytes().length + 12 /* for the JSON payload field overhead */;

    @Test
    void bulkProcessorMaxActions() throws IOException {
        int maxActions = RandomizedTest.randomIntInRange(TEST_RANDOM, 1, 1000);
        TestBulkListener listener = new TestBulkListener();
        FsCrawlerBulkProcessor<TestOperation, TestBulkRequest, TestBulkResponse> bulkProcessor =
                new FsCrawlerBulkProcessor<>(
                        new TestEngine(),
                        listener,
                        maxActions,
                        null,
                        new ByteSizeValue(1, ByteSizeUnit.MB),
                        TestBulkRequest::new);

        generatePayload(bulkProcessor, 1, maxActions - 1);
        Assertions.assertThat(listener.nbSuccessfulExecutions).isZero();
        generatePayload(bulkProcessor, maxActions, 1);
        Assertions.assertThat(listener.nbSuccessfulExecutions).isEqualTo(1);
        generatePayload(bulkProcessor, maxActions + 1, 1);
        bulkProcessor.close();
        Assertions.assertThat(listener.nbSuccessfulExecutions).isEqualTo(2);
    }

    @Test
    void bulkProcessorNullSize() throws IOException {
        int maxActions = RandomizedTest.randomIntInRange(TEST_RANDOM, 1, 1000);
        TestBulkListener listener = new TestBulkListener();
        FsCrawlerBulkProcessor<TestOperation, TestBulkRequest, TestBulkResponse> bulkProcessor =
                new FsCrawlerBulkProcessor<>(new TestEngine(), listener, maxActions, null, null, TestBulkRequest::new);

        generatePayload(bulkProcessor, 1, maxActions - 1);
        Assertions.assertThat(listener.nbSuccessfulExecutions).isZero();
        generatePayload(bulkProcessor, maxActions, 1);
        Assertions.assertThat(listener.nbSuccessfulExecutions).isEqualTo(1);
        generatePayload(bulkProcessor, maxActions + 1, 1);
        bulkProcessor.close();
        Assertions.assertThat(listener.nbSuccessfulExecutions).isEqualTo(2);
    }

    @Test
    void bulkProcessorZeroSize() throws IOException {
        int maxActions = RandomizedTest.randomIntInRange(TEST_RANDOM, 1, 1000);
        TestBulkListener listener = new TestBulkListener();
        FsCrawlerBulkProcessor<TestOperation, TestBulkRequest, TestBulkResponse> bulkProcessor =
                new FsCrawlerBulkProcessor<>(
                        new TestEngine(),
                        listener,
                        maxActions,
                        null,
                        new ByteSizeValue(0, ByteSizeUnit.MB),
                        TestBulkRequest::new);

        generatePayload(bulkProcessor, 1, maxActions - 1);
        Assertions.assertThat(listener.nbSuccessfulExecutions).isZero();
        generatePayload(bulkProcessor, maxActions, 1);
        Assertions.assertThat(listener.nbSuccessfulExecutions).isEqualTo(1);
        generatePayload(bulkProcessor, maxActions + 1, 1);
        bulkProcessor.close();
        Assertions.assertThat(listener.nbSuccessfulExecutions).isEqualTo(2);
    }

    @Test
    void bulkProcessorMaxSize() throws IOException {
        int maxActions = RandomizedTest.randomIntInRange(TEST_RANDOM, 1, 1000);
        TestBulkListener listener = new TestBulkListener();
        FsCrawlerBulkProcessor<TestOperation, TestBulkRequest, TestBulkResponse> bulkProcessor =
                new FsCrawlerBulkProcessor<>(
                        new TestEngine(),
                        listener,
                        0,
                        null,
                        new ByteSizeValue((long) maxActions * PAYLOAD_SIZE, ByteSizeUnit.BYTES),
                        TestBulkRequest::new);

        generatePayload(bulkProcessor, 1, maxActions - 1);
        Assertions.assertThat(listener.nbSuccessfulExecutions).isZero();
        generatePayload(bulkProcessor, maxActions, 1);
        Assertions.assertThat(listener.nbSuccessfulExecutions).isEqualTo(1);
        generatePayload(bulkProcessor, maxActions + 1, maxActions - 1);
        Assertions.assertThat(listener.nbSuccessfulExecutions).isEqualTo(1);
        generatePayload(bulkProcessor, 2 * maxActions, 1);
        Assertions.assertThat(listener.nbSuccessfulExecutions).isEqualTo(2);
        generatePayload(bulkProcessor, 2 * maxActions + 1, 1);
        bulkProcessor.close();
        Assertions.assertThat(listener.nbSuccessfulExecutions).isEqualTo(3);
    }

    @Test
    void bulkProcessorFlushInterval() throws IOException {
        int maxActions = RandomizedTest.randomIntInRange(TEST_RANDOM, 1, 1000);
        TimeValue flushInterval = TimeValue.timeValueMillis(RandomizedTest.randomIntInRange(TEST_RANDOM, 500, 2000));
        TestBulkListener listener = new TestBulkListener();
        FsCrawlerBulkProcessor<TestOperation, TestBulkRequest, TestBulkResponse> bulkProcessor =
                new FsCrawlerBulkProcessor<>(new TestEngine(), listener, 0, flushInterval, null, TestBulkRequest::new);

        // We don't load immediately the bulk processor
        FsCrawlerUtil.waitFor(Duration.ofMillis(100));

        generatePayload(bulkProcessor, 1, maxActions);
        Assertions.assertThat(listener.nbSuccessfulExecutions).isZero();

        // Wait for the flush to happen
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() ->
                        Assertions.assertThat(listener.nbSuccessfulExecutions).isEqualTo(1));
        bulkProcessor.close();
    }

    private void generatePayload(
            FsCrawlerBulkProcessor<TestOperation, TestBulkRequest, TestBulkResponse> bulkProcessor,
            int start,
            int size) {
        for (int i = start; i < start + size; i++) {
            logger.trace("Adding a new operation [{}]", i);
            bulkProcessor.add(new TestOperation(PAYLOAD));
        }
    }
}
