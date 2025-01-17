/*
 * Licensed to David Pilato under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package fr.pilato.elasticsearch.crawler.fs.framework.bulk;

import fr.pilato.elasticsearch.crawler.fs.framework.ByteSizeUnit;
import fr.pilato.elasticsearch.crawler.fs.framework.ByteSizeValue;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import java.io.IOException;

import static com.carrotsearch.randomizedtesting.RandomizedTest.randomIntBetween;
import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.serialize;
import static org.junit.Assert.*;

public class FsCrawlerBulkProcessorTest extends AbstractFSCrawlerTestCase {
    private static final Logger logger = LogManager.getLogger();
    private static final TestBean PAYLOAD = new TestBean("bar");
    private static final int PAYLOAD_SIZE = serialize(PAYLOAD).getBytes().length + 12 /* for the json payload field overhead */;

    @Test
    public void testBulkProcessorMaxActions() throws IOException {
        int maxActions = randomIntBetween(1, 1000);
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
        assertEquals(0, listener.nbSuccessfulExecutions);
        generatePayload(bulkProcessor, maxActions, 1);
        assertEquals(1, listener.nbSuccessfulExecutions);
        generatePayload(bulkProcessor, maxActions + 1, 1);
        bulkProcessor.close();
        assertEquals(2, listener.nbSuccessfulExecutions);
    }

    @Test
    public void testBulkProcessorNullSize() throws IOException {
        int maxActions = randomIntBetween(1, 1000);
        TestBulkListener listener = new TestBulkListener();
        FsCrawlerBulkProcessor<TestOperation, TestBulkRequest, TestBulkResponse> bulkProcessor =
                new FsCrawlerBulkProcessor<>(
                        new TestEngine(),
                        listener,
                        maxActions,
                        null,
                        null,
                        TestBulkRequest::new);

        generatePayload(bulkProcessor, 1, maxActions - 1);
        assertEquals(0, listener.nbSuccessfulExecutions);
        generatePayload(bulkProcessor, maxActions, 1);
        assertEquals(1, listener.nbSuccessfulExecutions);
        generatePayload(bulkProcessor, maxActions + 1, 1);
        bulkProcessor.close();
        assertEquals(2, listener.nbSuccessfulExecutions);
    }

    @Test
    public void testBulkProcessorZeroSize() throws IOException {
        int maxActions = randomIntBetween(1, 1000);
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
        assertEquals(0, listener.nbSuccessfulExecutions);
        generatePayload(bulkProcessor, maxActions, 1);
        assertEquals(1, listener.nbSuccessfulExecutions);
        generatePayload(bulkProcessor, maxActions + 1, 1);
        bulkProcessor.close();
        assertEquals(2, listener.nbSuccessfulExecutions);
    }

    @Test
    public void testBulkProcessorMaxSize() throws IOException {
        int maxActions = randomIntBetween(1, 1000);
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
        assertEquals(0, listener.nbSuccessfulExecutions);
        generatePayload(bulkProcessor, maxActions, 1);
        assertEquals(1, listener.nbSuccessfulExecutions);
        generatePayload(bulkProcessor, maxActions + 1, maxActions - 1);
        assertEquals(1, listener.nbSuccessfulExecutions);
        generatePayload(bulkProcessor, 2 * maxActions, 1);
        assertEquals(2, listener.nbSuccessfulExecutions);
        generatePayload(bulkProcessor, 2 * maxActions + 1, 1);
        bulkProcessor.close();
        assertEquals(3, listener.nbSuccessfulExecutions);
    }

    @Test
    public void testBulkProcessorFlushInterval() throws IOException, InterruptedException {
        int maxActions = randomIntBetween(1, 1000);
        TimeValue flushInterval = TimeValue.timeValueMillis(randomIntBetween(500, 2000));
        TestBulkListener listener = new TestBulkListener();
        FsCrawlerBulkProcessor<TestOperation, TestBulkRequest, TestBulkResponse> bulkProcessor =
                new FsCrawlerBulkProcessor<>(new TestEngine(), listener, 0, flushInterval, null, TestBulkRequest::new);

        // We don't load immediately the bulk processor
        Thread.sleep(100);

        generatePayload(bulkProcessor, 1, maxActions);
        assertEquals(0, listener.nbSuccessfulExecutions);

        Thread.sleep(flushInterval.millis());

        assertEquals(1, listener.nbSuccessfulExecutions);
        bulkProcessor.close();
        assertEquals(1, listener.nbSuccessfulExecutions);
    }

    private void generatePayload(FsCrawlerBulkProcessor<TestOperation, TestBulkRequest, TestBulkResponse> bulkProcessor, int start, int size) {
        for (int i = start; i < start + size; i++) {
            logger.trace("Adding a new operation [{}]", i);
            bulkProcessor.add(new TestOperation(PAYLOAD));
        }
    }

}
