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

import fr.pilato.elasticsearch.crawler.fs.client.BulkProcessor;
import fr.pilato.elasticsearch.crawler.fs.client.IndexRequest;
import fr.pilato.elasticsearch.crawler.fs.client.SearchResponse;
import fr.pilato.elasticsearch.crawler.fs.client.VersionComparator;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.TimeValue;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;

/**
 * Test elasticsearch HTTP client
 */
public class ElasticsearchClientIT extends AbstractITCase {

    @Before
    public void cleanExistingIndex() throws IOException {
        logger.info(" -> Removing existing index [{}*]", getCrawlerName());
        elasticsearchClient.deleteIndex(getCrawlerName() + "*");
    }

    @Test
    public void testCreateIndex() throws IOException {
        elasticsearchClient.createIndex(getCrawlerName());
        boolean exists = elasticsearchClient.isExistingIndex(getCrawlerName());
        assertThat(exists, is(true));
    }

    @Test
    public void testRefresh() throws IOException {
        elasticsearchClient.createIndex(getCrawlerName());
        refresh();
    }

    @Test
    public void testCreateIndexAlreadyExists() throws IOException {
        elasticsearchClient.createIndex(getCrawlerName());
        elasticsearchClient.waitForHealthyIndex(getCrawlerName());
        try {
            elasticsearchClient.createIndex(getCrawlerName());
            fail("we should reject creation of an already existing index");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), is("index already exists"));
        }
    }

    @Test
    public void testIsExistingTypeWithNoIndex() throws IOException {
        boolean existingType = elasticsearchClient.isExistingType(getCrawlerName(), "doc");
        assertThat(existingType, is(false));
    }

    @Test
    public void testIsExistingTypeWithIndexNoType() throws IOException {
        elasticsearchClient.createIndex(getCrawlerName());
        elasticsearchClient.waitForHealthyIndex(getCrawlerName());
        boolean existingType = elasticsearchClient.isExistingType(getCrawlerName(), "doc");
        assertThat(existingType, is(false));
    }

    @Test
    public void testIsExistingTypeWithIndexAndType() throws IOException {
        elasticsearchClient.createIndex(getCrawlerName());
        elasticsearchClient.waitForHealthyIndex(getCrawlerName());
        elasticsearchClient.putMapping(getCrawlerName(), "doc", "{\"doc\":{}}");
        boolean existingType = elasticsearchClient.isExistingType(getCrawlerName(), "doc");
        assertThat(existingType, is(true));
    }

    @Test
    public void testSearch() throws IOException {
        elasticsearchClient.createIndex(getCrawlerName());
        elasticsearchClient.waitForHealthyIndex(getCrawlerName());

        // Depending on the version we are using, we need to adapt the test mapping
        String version = elasticsearchClient.findVersion();

        String mapping;
        // With elasticsearch 5.0.0, we need to use `type: text` instead of `type: string`
        if (new VersionComparator().compare(version, "5") >= 0) {
            mapping = "{\n" +
                    "      \"doc\" : {\n" +
                    "        \"properties\" : {\n" +
                    "          \"foo\" : {\n" +
                    "            \"type\" : \"text\",\n" +
                    "            \"store\" : true\n" +
                    "            }\n" +
                    "          }\n" +
                    "        }\n" +
                    "      }\n" +
                    "    }";
        } else {
            mapping = "{\n" +
                    "      \"doc\" : {\n" +
                    "        \"properties\" : {\n" +
                    "          \"foo\" : {\n" +
                    "            \"type\" : \"string\",\n" +
                    "            \"store\" : true\n" +
                    "            }\n" +
                    "          }\n" +
                    "        }\n" +
                    "      }\n" +
                    "    }";
        }


        elasticsearchClient.putMapping(getCrawlerName(), "doc", mapping);

        elasticsearchClient.index(getCrawlerName(), "doc", "1", "{ \"foo\" : \"bar\" }");
        elasticsearchClient.index(getCrawlerName(), "doc", "2", "{ \"foo\" : \"baz\" }");

        elasticsearchClient.refresh(getCrawlerName());

        // match_all
        SearchResponse response = elasticsearchClient.search(getCrawlerName(), "doc", (String) null);
        assertThat(response.getHits().getTotal(), is(2L));

        // term
        response = elasticsearchClient.search(getCrawlerName(), "doc", "foo:bar");
        assertThat(response.getHits().getTotal(), is(1L));

        // using fields
        response = elasticsearchClient.search(getCrawlerName(), "doc", "foo:bar", 10, "_source");
        assertThat(response.getHits().getTotal(), is(1L));
        response = elasticsearchClient.search(getCrawlerName(), "doc", "foo:bar", 10, "foo");
        assertThat(response.getHits().getTotal(), is(1L));
        assertThat(response.getHits().getHits().get(0).getFields(), hasEntry("foo", Collections.singletonList("bar")));
    }

    @Test
    public void testBulkWithTime() throws IOException, InterruptedException {
        // Create the index first
        elasticsearchClient.createIndex(getCrawlerName());
        elasticsearchClient.waitForHealthyIndex(getCrawlerName());

        BulkProcessor bulkProcessor = BulkProcessor.simpleBulkProcessor(elasticsearchClient, 100, TimeValue.timeValueSeconds(2));
        for (int i = 0; i < 10; i++) {
            bulkProcessor.add(new IndexRequest(getCrawlerName(), "doc", "id" + i).source("{\"foo\":\"bar\"}"));
        }

        elasticsearchClient.refresh(getCrawlerName());

        waitForAllShardsAssigned();

        // We wait for 3 seconds (2 should be enough)
        Thread.sleep(3000L);

        elasticsearchClient.refresh(getCrawlerName());

        // We should have now our docs
        SearchResponse response = elasticsearchClient.search(getCrawlerName(), "doc", (String) null);
        assertThat(response.getHits().getTotal(), is(10L));

        bulkProcessor.close();
    }

    @Test
    public void testBulkWithoutTime() throws IOException, InterruptedException {
        // Create the index first
        elasticsearchClient.createIndex(getCrawlerName());
        elasticsearchClient.waitForHealthyIndex(getCrawlerName());

        BulkProcessor bulkProcessor = BulkProcessor.simpleBulkProcessor(elasticsearchClient, 10, null);
        for (int i = 0; i < 9; i++) {
            bulkProcessor.add(new IndexRequest(getCrawlerName(), "doc", "id" + i).source("{\"foo\":\"bar\"}"));
        }

        elasticsearchClient.refresh(getCrawlerName());

        waitForAllShardsAssigned();

        bulkProcessor.add(new IndexRequest(getCrawlerName(), "doc", "id" + 9).source("{\"foo\":\"bar\"}"));

        elasticsearchClient.refresh(getCrawlerName());

        // We should have now our docs
        SearchResponse response = elasticsearchClient.search(getCrawlerName(), "doc", (String) null);
        assertThat(response.getHits().getTotal(), is(10L));

        bulkProcessor.close();
    }

    /**
     * If we search just a few ms after sending the requests, we won't have all data.
     * But in elasticsearch 1.x series that might fail with:
     *    [2016-07-06 19:35:58,613][DEBUG][action.search.type       ] [Mentus] All shards failed for phase: [query]
     *    org.elasticsearch.index.IndexShardMissingException: [fscrawler_test_bulk_without_time][4] missing
     *    at org.elasticsearch.index.IndexService.shardSafe(IndexService.java:210)
     *    at org.elasticsearch.search.SearchService.createContext(SearchService.java:560)
     *    at org.elasticsearch.search.SearchService.createAndPutContext(SearchService.java:544)
     *    at org.elasticsearch.search.SearchService.executeQueryPhase(SearchService.java:306)
     *    at org.elasticsearch.search.action.SearchServiceTransportAction$5.call(SearchServiceTransportAction.java:231)
     *    at org.elasticsearch.search.action.SearchServiceTransportAction$5.call(SearchServiceTransportAction.java:228)
     *    at org.elasticsearch.search.action.SearchServiceTransportAction$23.run(SearchServiceTransportAction.java:559)
     *    at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1142)
     *    at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:617)
     *    at java.lang.Thread.run(Thread.java:745)
     *
     * @throws InterruptedException
     */
    private void waitForAllShardsAssigned() throws InterruptedException {
        awaitBusy(() -> {
            try {
                elasticsearchClient.search(getCrawlerName(), "doc", (String) null);
            } catch (IOException e) {
                // For elasticsearch 1.x series
                if (e.getMessage().contains("SearchPhaseExecutionException")) {
                    logger.warn("Error while running against 1.x cluster. Trying again...");
                    return false;
                }
                fail("We got an unexpected exception: " + e.getMessage());
            }
            return true;
        });
    }

    @Test
    public void testFindVersion() throws IOException {
        String version = elasticsearchClient.findVersion();
        logger.info("Current elasticsearch version: [{}]", version);

        // TODO if we store in a property file the elasticsearch version we are running tests against we can add some assertions
    }
}
