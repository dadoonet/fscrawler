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

import com.carrotsearch.randomizedtesting.annotations.Nightly;
import com.carrotsearch.randomizedtesting.annotations.Timeout;
import com.carrotsearch.randomizedtesting.annotations.TimeoutSuite;
import com.jayway.jsonpath.DocumentContext;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.client.ESSemanticQuery;
import fr.pilato.elasticsearch.crawler.fs.client.ESTermQuery;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil;
import fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.Assumptions;
import org.junit.Test;

/**
 * Test if semantic search is working as we could expect, ie: not activated when it can't or activated when it should.
 */
@Nightly("Loading semantic models could take some minutes")
@TimeoutSuite(millis = 10 * AbstractFSCrawlerTestCase.TIMEOUT_MINUTE_AS_MS)
@Timeout(millis = 10 * AbstractFSCrawlerTestCase.TIMEOUT_MINUTE_AS_MS)
public class FsCrawlerTestSemanticIT extends AbstractFsCrawlerITCase {

    /**
     * Test for #1996: <a
     * href="https://github.com/dadoonet/fscrawler/pull/1996">https://github.com/dadoonet/fscrawler/pull/1996</a>
     */
    @Test
    public void semantic() throws Exception {
        // We will execute this test from version 8.17 with a trial or enterprise license
        Assumptions.assumeThat(client.isSemanticSupported())
                .as("We don't run this test when semantic search is not available")
                .isTrue();

        FsSettings fsSettings = createTestSettings();
        fsSettings.getElasticsearch().setSemanticSearch(true);
        fsSettings.getFs().setUpdateRate(TimeValue.timeValueSeconds(MAX_WAIT_FOR_SEARCH_LONG_TESTS.toSeconds()));
        crawler = startCrawler(fsSettings, MAX_WAIT_FOR_SEARCH_LONG_TESTS);

        // We expect to have 3 files
        countTestHelper(
                new ESSearchRequest().withIndex(getCrawlerName() + FsCrawlerUtil.INDEX_SUFFIX_DOCS),
                3L,
                null,
                MAX_WAIT_FOR_SEARCH_LONG_TESTS);

        // 2 pdf and 1 txt
        countTestHelper(
                new ESSearchRequest()
                        .withIndex(getCrawlerName() + FsCrawlerUtil.INDEX_SUFFIX_DOCS)
                        .withESQuery(new ESTermQuery("file.extension", "pdf")),
                2L,
                null);
        countTestHelper(
                new ESSearchRequest()
                        .withIndex(getCrawlerName() + FsCrawlerUtil.INDEX_SUFFIX_DOCS)
                        .withESQuery(new ESTermQuery("file.extension", "txt")),
                1L,
                null);

        // We should have semantic information
        ESSearchResponse response = countTestHelper(
                new ESSearchRequest()
                        .withIndex(getCrawlerName() + FsCrawlerUtil.INDEX_SUFFIX_DOCS)
                        .withESQuery(
                                new ESSemanticQuery("content_semantic", "Someone understanding loans and finances")),
                3L,
                null);
        DocumentContext document =
                JsonUtil.parseJsonAsDocumentContext(response.getHits().get(0).getSource());
        Assertions.assertThat((String) document.read("$.file.filename")).isEqualTo("3547447.pdf");
    }
}
