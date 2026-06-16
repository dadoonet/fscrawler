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
package fr.pilato.elasticsearch.crawler.fs.test.integration.elasticsearch;

import fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientException;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Test with multiple crawlers */
class FsCrawlerTestMultipleCrawlersIT extends AbstractFsCrawlerITCase {

    @BeforeEach
    @Override
    protected void cleanExistingIndex() throws ElasticsearchClientException {
        // Also clean the specific indices and templates for this test suite. The base cleanup only
        // covers getCrawlerName(); the per-crawler "_1"/"_2" templates need explicit removal by name.
        client.deleteIndex(getCrawlerName() + "_1" + FsCrawlerUtil.INDEX_SUFFIX_DOCS);
        client.deleteIndex(getCrawlerName() + "_1" + FsCrawlerUtil.INDEX_SUFFIX_FOLDER);
        client.deleteIndex(getCrawlerName() + "_2" + FsCrawlerUtil.INDEX_SUFFIX_DOCS);
        client.deleteIndex(getCrawlerName() + "_2" + FsCrawlerUtil.INDEX_SUFFIX_FOLDER);
        client.removeIndexAndComponentTemplates(cleanupSettings(getCrawlerName() + "_1"));
        client.removeIndexAndComponentTemplates(cleanupSettings(getCrawlerName() + "_2"));
        super.cleanExistingIndex();
    }

    @AfterEach
    @Override
    protected void cleanUp() throws ElasticsearchClientException {
        if (!TEST_KEEP_DATA) {
            // Also clean the specific indices for this test suite
            client.deleteIndex(getCrawlerName() + "_1" + FsCrawlerUtil.INDEX_SUFFIX_DOCS);
            client.deleteIndex(getCrawlerName() + "_1" + FsCrawlerUtil.INDEX_SUFFIX_FOLDER);
            client.deleteIndex(getCrawlerName() + "_2" + FsCrawlerUtil.INDEX_SUFFIX_DOCS);
            client.deleteIndex(getCrawlerName() + "_2" + FsCrawlerUtil.INDEX_SUFFIX_FOLDER);
            client.removeIndexAndComponentTemplates(cleanupSettings(getCrawlerName() + "_1"));
            client.removeIndexAndComponentTemplates(cleanupSettings(getCrawlerName() + "_2"));
        }
        super.cleanUp();
    }

    @Test
    void multiple_crawlers() throws Exception {
        FsSettings fsSettings1 = createTestSettings(getCrawlerName() + "_1");
        fsSettings1.getFs().setUrl(currentTestResourceDir.resolve("crawler1").toString());
        FsSettings fsSettings2 = createTestSettings(getCrawlerName() + "_2");
        fsSettings2.getFs().setUrl(currentTestResourceDir.resolve("crawler2").toString());

        try (FsCrawlerImpl ignored1 = startCrawler(fsSettings1);
                FsCrawlerImpl ignored2 = startCrawler(fsSettings2)) {
            // We should have one doc in index 1...
            ESSearchResponse response1 = countTestHelper(
                    new ESSearchRequest().withIndex(getCrawlerName() + "_1" + FsCrawlerUtil.INDEX_SUFFIX_DOCS),
                    1L,
                    null);
            Assertions.assertThat(response1.getTotalHits()).isEqualTo(1L);
            // We should have one doc in index 2...
            ESSearchResponse response2 = countTestHelper(
                    new ESSearchRequest().withIndex(getCrawlerName() + "_2" + FsCrawlerUtil.INDEX_SUFFIX_DOCS),
                    1L,
                    null);
            Assertions.assertThat(response2.getTotalHits()).isEqualTo(1L);
        }
    }
}
