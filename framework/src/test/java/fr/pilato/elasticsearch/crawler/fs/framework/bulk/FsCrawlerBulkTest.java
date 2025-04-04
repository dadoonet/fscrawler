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
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import static com.carrotsearch.randomizedtesting.RandomizedTest.randomIntBetween;
import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.serialize;
import static org.assertj.core.api.Assertions.assertThat;

public class FsCrawlerBulkTest extends AbstractFSCrawlerTestCase {
    private static final Logger logger = LogManager.getLogger();
    private static final TestBean PAYLOAD = new TestBean("bar");
    private static final int PAYLOAD_SIZE = serialize(PAYLOAD).getBytes().length + 12 /* for the json payload field overhead */;

    @Test
    public void bulkRequestLimitsMaxActions() {
        int maxActions = randomIntBetween(1, 1000);
        FsCrawlerBulkRequest<TestOperation> bulk = generateBulk(maxActions, new ByteSizeValue(1, ByteSizeUnit.MB));
        generateAndTest(bulk, 1, maxActions - 1, false);
        generateAndTest(bulk, maxActions, 1, true);
    }

    @Test
    public void bulkRequestLimitsMaxSize() {
        int firstSize = randomIntBetween(1, 1000);
        int secondSize = 1;
        FsCrawlerBulkRequest<TestOperation> bulk = generateBulk(0, new ByteSizeValue((long) firstSize * PAYLOAD_SIZE, ByteSizeUnit.BYTES));
        generateAndTest(bulk, 1, firstSize - 1, false);
        generateAndTest(bulk, firstSize, secondSize, true);
    }

    @Test
    public void bulkRequestLimitsNullSize() {
        int maxActions = randomIntBetween(1, 1000);
        FsCrawlerBulkRequest<TestOperation> bulk = generateBulk(maxActions, null);
        generateAndTest(bulk, 1, maxActions - 1, false);
        generateAndTest(bulk, maxActions, 1, true);
    }

    @Test
    public void bulkRequestLimitsZeroSize() {
        int maxActions = randomIntBetween(1, 1000);
        FsCrawlerBulkRequest<TestOperation> bulk = generateBulk(maxActions, new ByteSizeValue(0, ByteSizeUnit.KB));
        generateAndTest(bulk, 1, maxActions - 1, false);
        generateAndTest(bulk, maxActions, 1, true);
    }

    @Test
    public void bulkRequestNoLimits() {
        int nbActions = randomIntBetween(1, 1000);
        FsCrawlerBulkRequest<TestOperation> bulk = generateBulk(0, null);
        generateAndTest(bulk, 1, nbActions, false);
    }

    private void generateAndTest(FsCrawlerBulkRequest<TestOperation> bulk, int start, int size, boolean overTheLimit) {
        logger.debug("adding {} actions to the bulk which contains {} actions", size, bulk.numberOfActions());
        generatePayload(bulk, start, size);
        logger.debug("is over the limit after {} actions and {} bytes? {}", start + size - 1, bulk.totalByteSize(), bulk.isOverTheLimit());
        assertThat(bulk.isOverTheLimit()).isEqualTo(overTheLimit);
        assertThat(bulk.numberOfActions()).isEqualTo(start + size - 1);
    }

    private FsCrawlerBulkRequest<TestOperation> generateBulk(Integer maxActions, ByteSizeValue maxBulkSize) {
        logger.debug("Creating a new bulk request with maxActions [{}] and maxBulkSize [{}]", maxActions, maxBulkSize);
        FsCrawlerBulkRequest<TestOperation> bulk = new TestBulkRequest();
        bulk.maxNumberOfActions(maxActions);
        bulk.maxBulkSize(maxBulkSize);
        return bulk;
    }

    private void generatePayload(FsCrawlerBulkRequest<TestOperation> bulk, int start, int size) {
        for (int i = start; i < start + size; i++) {
            logger.trace("Adding a new operation [{}]", i);
            bulk.add(new TestOperation(PAYLOAD));
        }
    }
}
