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

import fr.pilato.elasticsearch.crawler.fs.client.ESMatchQuery;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientException;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import static fr.pilato.elasticsearch.crawler.fs.framework.Await.awaitBusy;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.INDEX_SUFFIX_DOCS;
import static fr.pilato.elasticsearch.crawler.fs.framework.TimeValue.MAX_WAIT_FOR_SEARCH;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test json support crawler setting
 */
public class FsCrawlerTestJsonSupportIT extends AbstractFsCrawlerITCase {
    private static final Logger logger = LogManager.getLogger();

    /**
     * Test case for issue #5: <a href="https://github.com/dadoonet/fscrawler/issues/5">https://github.com/dadoonet/fscrawler/issues/5</a> : Support JSon documents
     */
    @Test
    public void json_support() throws Exception {
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setJsonSupport(true);
        crawler = startCrawler(fsSettings);

        assertThat(awaitBusy(() -> {
            try {
                ESSearchResponse response = client.search(new ESSearchRequest()
                        .withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS)
                        .withESQuery(new ESMatchQuery("text", "tweet")));
                return response.getTotalHits() == 2;
            } catch (ElasticsearchClientException e) {
                logger.warn("Caught exception while running the test", e);
                return false;
            }
        }, MAX_WAIT_FOR_SEARCH))
                .as("We should have 2 doc for tweet in text field...")
                .isTrue();
    }

    /**
     * Test case for issue #5: <a href="https://github.com/dadoonet/fscrawler/issues/5">https://github.com/dadoonet/fscrawler/issues/5</a> : Support JSon documents
     */
    @Test
    public void json_disabled() throws Exception {
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setJsonSupport(false);
        crawler = startCrawler(fsSettings);

        assertThat(awaitBusy(() -> {
            try {
                ESSearchResponse response = client.search(new ESSearchRequest()
                        .withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS)
                        .withESQuery(new ESMatchQuery("text", "tweet")));
                return response.getTotalHits() == 0;
            } catch (ElasticsearchClientException e) {
                logger.warn("Caught exception while running the test", e);
                return false;
            }
        }, MAX_WAIT_FOR_SEARCH))
                .as("We should have 0 doc for tweet in text field...")
                .isTrue();

        assertThat(awaitBusy(() -> {
            try {
                ESSearchResponse response = client.search(new ESSearchRequest()
                        .withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS)
                        .withESQuery(new ESMatchQuery("content", "tweet")));
                return response.getTotalHits() == 2;
            } catch (ElasticsearchClientException e) {
                logger.warn("Caught exception while running the test", e);
                return false;
            }
        }, MAX_WAIT_FOR_SEARCH))
                .as("We should have 2 docs for tweet in content field...")
                .isTrue();
    }

    /**
     * Test case for issue #237:  <a href="https://github.com/dadoonet/fscrawler/issues/237">https://github.com/dadoonet/fscrawler/issues/237</a> Delete json documents
     */
    @Test
    public void add_as_inner_object() throws Exception {
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setJsonSupport(true);
        fsSettings.getFs().setAddAsInnerObject(true);
        crawler = startCrawler(fsSettings);

        assertThat(awaitBusy(() -> {
            try {
                ESSearchResponse response = client.search(new ESSearchRequest()
                        .withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS)
                        .withESQuery(new ESMatchQuery("object.text", "tweet")));
                return response.getTotalHits() == 2;
            } catch (ElasticsearchClientException e) {
                logger.warn("Caught exception while running the test", e);
                return false;
            }
        }, MAX_WAIT_FOR_SEARCH))
                .as("We should have 2 doc for tweet in object.text field...")
                .isTrue();
    }

    /**
     * Test case for issue #204: <a href="https://github.com/dadoonet/fscrawler/issues/204">https://github.com/dadoonet/fscrawler/issues/204</a> : JSON files are indexed twice
     */
    @Test
    public void json_support_and_other_files() throws Exception {
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setJsonSupport(true);
        crawler = startCrawler(fsSettings, TimeValue.timeValueSeconds(5));

        assertThat(awaitBusy(() -> {
            try {
                ESSearchResponse response = client.search(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS));
                return response.getTotalHits() == 2;
            } catch (ElasticsearchClientException e) {
                logger.warn("Caught exception while running the test", e);
                return false;
            }
        }, MAX_WAIT_FOR_SEARCH))
                .as("We should have 2 docs only...")
                .isTrue();
    }
}
