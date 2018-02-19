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

package fr.pilato.elasticsearch.crawler.fs.test.integration;

import fr.pilato.elasticsearch.crawler.fs.settings.Fs;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Test;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Test json support crawler setting
 */
public class FsCrawlerTestJsonSupportIT extends AbstractFsCrawlerITCase {

    /**
     * Test case for issue #5: https://github.com/dadoonet/fscrawler/issues/5 : Support JSon documents
     */
    @Test
    public void test_json_support() throws Exception {
        assumeVersion6AtLeast();
        Fs fs = startCrawlerDefinition()
                .setJsonSupport(true)
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        assertThat("We should have 2 doc for tweet in text field...", awaitBusy(() -> {
            try {
                SearchResponse response = elasticsearchClient.search(new SearchRequest(getCrawlerName()).source(
                        new SearchSourceBuilder().query(QueryBuilders.matchQuery("text", "tweet"))));
                return response.getHits().getTotalHits() == 2;
            } catch (IOException e) {
                logger.warn("Caught exception while running the test", e);
                return false;
            }
        }), equalTo(true));
    }

    /**
     * Test case for issue #5: https://github.com/dadoonet/fscrawler/issues/5 : Support JSon documents
     */
    @Test
    public void test_json_disabled() throws Exception {
        assumeVersion6AtLeast();
        Fs fs = startCrawlerDefinition()
                .setJsonSupport(false)
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        assertThat("We should have 0 doc for tweet in text field...", awaitBusy(() -> {
            try {
                SearchResponse response = elasticsearchClient.search(new SearchRequest(getCrawlerName()).source(
                        new SearchSourceBuilder().query(QueryBuilders.matchQuery("text", "tweet"))));
                return response.getHits().getTotalHits() == 0;
            } catch (IOException e) {
                logger.warn("Caught exception while running the test", e);
                return false;
            }
        }), equalTo(true));

        assertThat("We should have 2 docs for tweet in content field...", awaitBusy(() -> {
            try {
                SearchResponse response = elasticsearchClient.search(new SearchRequest(getCrawlerName()).source(
                        new SearchSourceBuilder().query(QueryBuilders.matchQuery("content", "tweet"))));
                return response.getHits().getTotalHits() == 2;
            } catch (IOException e) {
                logger.warn("Caught exception while running the test", e);
                return false;
            }
        }), equalTo(true));
    }

    /**
     * Test case for issue #237:  https://github.com/dadoonet/fscrawler/issues/237 Delete json documents
     */
    @Test
    public void test_add_as_inner_object() throws Exception {
        assumeVersion6AtLeast();
        Fs fs = startCrawlerDefinition()
                .setJsonSupport(true)
                .setAddAsInnerObject(true)
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        assertThat("We should have 2 doc for tweet in object.text field...", awaitBusy(() -> {
            try {
                fr.pilato.elasticsearch.crawler.fs.client.SearchResponse response = elasticsearchClient.searchJson(getCrawlerName(),
                        "{\"query\":{\"match\":{\"object.text\":\"tweet\"}}}");
                return response.getHits().getTotal() == 2;
            } catch (IOException e) {
                logger.warn("Caught exception while running the test", e);
                return false;
            }
        }), equalTo(true));
    }

    /**
     * Test case for issue #204: https://github.com/dadoonet/fscrawler/issues/204 : JSON files are indexed twice
     */
    @Test
    public void test_json_support_and_other_files() throws Exception {
        Fs fs = startCrawlerDefinition()
                .setJsonSupport(true)
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        assertThat("We should have 2 docs only...", awaitBusy(() -> {
            try {
                SearchResponse response = elasticsearchClient.search(new SearchRequest(getCrawlerName()));
                return response.getHits().getTotalHits() == 2;
            } catch (IOException e) {
                logger.warn("Caught exception while running the test", e);
                return false;
            }
        }), equalTo(true));
    }
}
