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
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientException;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.INDEX_SUFFIX_DOCS;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.INDEX_SUFFIX_FOLDER;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test with multiple crawlers
 */
public class FsCrawlerTestMultipleCrawlersIT extends AbstractFsCrawlerITCase {

    @Before
    @Override
    public void cleanExistingIndex() throws ElasticsearchClientException {
        // Also clean the specific indices for this test suite
        client.deleteIndex(getCrawlerName() + "_1" + INDEX_SUFFIX_DOCS);
        client.deleteIndex(getCrawlerName() + "_1" + INDEX_SUFFIX_FOLDER);
        client.deleteIndex(getCrawlerName() + "_2" + INDEX_SUFFIX_DOCS);
        client.deleteIndex(getCrawlerName() + "_2" + INDEX_SUFFIX_FOLDER);
        super.cleanExistingIndex();
    }

    @After
    @Override
    public void cleanUp() throws ElasticsearchClientException {
        if (!TEST_KEEP_DATA) {
            // Also clean the specific indices for this test suite
            client.deleteIndex(getCrawlerName() + "_1" + INDEX_SUFFIX_DOCS);
            client.deleteIndex(getCrawlerName() + "_1" + INDEX_SUFFIX_FOLDER);
            client.deleteIndex(getCrawlerName() + "_2" + INDEX_SUFFIX_DOCS);
            client.deleteIndex(getCrawlerName() + "_2" + INDEX_SUFFIX_FOLDER);
        }
        super.cleanUp();
    }

    @Test
    public void multiple_crawlers() throws Exception {
        FsSettings fsSettings1 = createTestSettings(getCrawlerName() + "_1");
        fsSettings1.getFs().setUrl(currentTestResourceDir.resolve("crawler1").toString());
        FsSettings fsSettings2 = createTestSettings(getCrawlerName() + "_2");
        fsSettings2.getFs().setUrl(currentTestResourceDir.resolve("crawler2").toString());

        try (FsCrawlerImpl ignored1 = startCrawler(fsSettings1); FsCrawlerImpl ignored2 = startCrawler(fsSettings2)) {
            // We should have one doc in index 1...
            ESSearchResponse response1 = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName() + "_1" + INDEX_SUFFIX_DOCS), 1L, null);
            assertThat(response1.getTotalHits()).isEqualTo(1L);
            // We should have one doc in index 2...
            ESSearchResponse response2 = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName() + "_2" + INDEX_SUFFIX_DOCS), 1L, null);
            assertThat(response2.getTotalHits()).isEqualTo(1L);
        }
    }
}
