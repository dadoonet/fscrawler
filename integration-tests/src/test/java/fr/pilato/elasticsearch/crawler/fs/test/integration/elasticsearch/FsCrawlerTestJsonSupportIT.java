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

import java.io.IOException;

import static fr.pilato.elasticsearch.crawler.fs.framework.Await.awaitBusy;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Test json support crawler setting
 */
public class FsCrawlerTestJsonSupportIT extends AbstractFsCrawlerITCase {
    private static final Logger logger = LogManager.getLogger();

    /**
     * Test case for issue #5: <a href="https://github.com/dadoonet/fscrawler/issues/5">https://github.com/dadoonet/fscrawler/issues/5</a> : Support JSon documents
     */
    @Test
    public void test_json_support() throws Exception {
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setJsonSupport(true);
        crawler = startCrawler(fsSettings);

        assertThat("We should have 2 doc for tweet in text field...", awaitBusy(() -> {
            try {
                ESSearchResponse response = documentService.search(new ESSearchRequest()
                        .withIndex(getCrawlerName())
                        .withESQuery(new ESMatchQuery("text", "tweet")));
                return response.getTotalHits() == 2;
            } catch (IOException | ElasticsearchClientException e) {
                logger.warn("Caught exception while running the test", e);
                return false;
            }
        }), equalTo(true));
    }

    /**
     * Test case for issue #5: <a href="https://github.com/dadoonet/fscrawler/issues/5">https://github.com/dadoonet/fscrawler/issues/5</a> : Support JSon documents
     */
    @Test
    public void test_json_disabled() throws Exception {
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setJsonSupport(false);
        crawler = startCrawler(fsSettings);

        assertThat("We should have 0 doc for tweet in text field...", awaitBusy(() -> {
            try {
                ESSearchResponse response = documentService.search(new ESSearchRequest()
                        .withIndex(getCrawlerName())
                        .withESQuery(new ESMatchQuery("text", "tweet")));
                return response.getTotalHits() == 0;
            } catch (IOException | ElasticsearchClientException e) {
                logger.warn("Caught exception while running the test", e);
                return false;
            }
        }), equalTo(true));

        assertThat("We should have 2 docs for tweet in content field...", awaitBusy(() -> {
            try {
                ESSearchResponse response = documentService.search(new ESSearchRequest()
                        .withIndex(getCrawlerName())
                        .withESQuery(new ESMatchQuery("content", "tweet")));
                return response.getTotalHits() == 2;
            } catch (IOException | ElasticsearchClientException e) {
                logger.warn("Caught exception while running the test", e);
                return false;
            }
        }), equalTo(true));
    }

    /**
     * Test case for issue #237:  <a href="https://github.com/dadoonet/fscrawler/issues/237">https://github.com/dadoonet/fscrawler/issues/237</a> Delete json documents
     */
    @Test
    public void test_add_as_inner_object() throws Exception {
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setJsonSupport(true);
        fsSettings.getFs().setAddAsInnerObject(true);
        crawler = startCrawler(fsSettings);

        assertThat("We should have 2 doc for tweet in object.text field...", awaitBusy(() -> {
            try {
                ESSearchResponse response = documentService.search(new ESSearchRequest()
                        .withIndex(getCrawlerName())
                        .withESQuery(new ESMatchQuery("object.text", "tweet")));
                return response.getTotalHits() == 2;
            } catch (IOException | ElasticsearchClientException e) {
                logger.warn("Caught exception while running the test", e);
                return false;
            }
        }), equalTo(true));
    }

    /**
     * Test case for issue #204: <a href="https://github.com/dadoonet/fscrawler/issues/204">https://github.com/dadoonet/fscrawler/issues/204</a> : JSON files are indexed twice
     */
    @Test
    public void test_json_support_and_other_files() throws Exception {
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setJsonSupport(true);
        // TODO : This is a workaround for the test to pass. We should fix the code instead.
        // The problem here is that the bulk request is sent with an error in it. If you send 1 doc per bulk request,
        // you will just catch the error and then run another bulk request. But if you send 2 docs in the same bulk request,
        // the whole request will fail, meaning that no document will be indexed.
        fsSettings.getElasticsearch().setBulkSize(1);
        crawler = startCrawler(fsSettings, TimeValue.timeValueSeconds(5));

        assertThat("We should have 2 docs only...", awaitBusy(() -> {
            try {
                ESSearchResponse response = documentService.search(new ESSearchRequest().withIndex(getCrawlerName()));
                return response.getTotalHits() == 2;
            } catch (IOException | ElasticsearchClientException e) {
                logger.warn("Caught exception while running the test", e);
                return false;
            }
        }), equalTo(true));
    }
}
