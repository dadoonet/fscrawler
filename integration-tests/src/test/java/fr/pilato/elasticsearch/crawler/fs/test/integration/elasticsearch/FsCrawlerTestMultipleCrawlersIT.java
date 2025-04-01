/*
 * Licensed to David Pilato (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
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
 */

package fr.pilato.elasticsearch.crawler.fs.test.integration.elasticsearch;

import fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientException;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

/**
 * Test with multiple crawlers
 */
public class FsCrawlerTestMultipleCrawlersIT extends AbstractFsCrawlerITCase {

    @Before
    public void cleanExistingIndex() throws IOException, ElasticsearchClientException {
        super.cleanExistingIndex();
        // Also clean the specific indices for this test
        managementService.getClient().deleteIndex(getCrawlerName() + "_1");
        managementService.getClient().deleteIndex(getCrawlerName() + "_2");
    }

    @Test
    public void test_multiple_crawlers() throws Exception {
        FsSettings fsSettings1 = createTestSettings(getCrawlerName() + "_1");
        fsSettings1.getFs().setUrl(currentTestResourceDir.resolve("crawler1").toString());
        FsSettings fsSettings2 = createTestSettings(getCrawlerName() + "_2");
        fsSettings2.getFs().setUrl(currentTestResourceDir.resolve("crawler2").toString());

        try (FsCrawlerImpl ignored1 = startCrawler(fsSettings1); FsCrawlerImpl ignored2 = startCrawler(fsSettings2)) {
            // We should have one doc in index 1...
            countTestHelper(new ESSearchRequest().withIndex(getCrawlerName() + "_1"), 1L, null);
            // We should have one doc in index 2...
            countTestHelper(new ESSearchRequest().withIndex(getCrawlerName() + "_2"), 1L, null);
        }
    }
}
