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
import fr.pilato.elasticsearch.crawler.fs.client.BulkRequest;
import fr.pilato.elasticsearch.crawler.fs.client.BulkResponse;
import fr.pilato.elasticsearch.crawler.fs.client.IndexRequest;
import fr.pilato.elasticsearch.crawler.fs.client.SearchResponse;
import fr.pilato.elasticsearch.crawler.fs.client.VersionComparator;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.TimeValue;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.logging.log4j.LogManager;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeThat;

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
    public void testCreateIndexWithSettings() throws IOException {
        elasticsearchClient.createIndex(getCrawlerName(), false, "{\n" +
                "  \"settings\": {\n" +
                "    \"number_of_shards\": 1,\n" +
                "    \"number_of_replicas\": 1\n" +
                "  }\n" +
                "}");
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
    public void testSearch() throws IOException {
        // Depending on the version we are using, we need to adapt the test settings (mapping)
        String version = elasticsearchClient.findVersion();

        String settings;
        // With elasticsearch 5.0.0, we need to use `type: text` instead of `type: string`
        if (new VersionComparator().compare(version, "5") >= 0) {
            settings = "{\n" +
                    "  \"mappings\": {\n" +
                    "    \"doc\": {\n" +
                    "      \"properties\": {\n" +
                    "        \"foo\": {\n" +
                    "          \"type\": \"text\",\n" +
                    "          \"store\": true\n" +
                    "        }\n" +
                    "      }\n" +
                    "    }\n" +
                    "  }\n" +
                    "}\n";
        } else {
            settings = "{\n" +
                    "  \"mappings\": {\n" +
                    "    \"doc\": {\n" +
                    "      \"properties\": {\n" +
                    "        \"foo\": {\n" +
                    "          \"type\": \"string\",\n" +
                    "          \"store\": true\n" +
                    "        }\n" +
                    "      }\n" +
                    "    }\n" +
                    "  }\n" +
                    "}\n";
        }

        elasticsearchClient.createIndex(getCrawlerName(), false, settings);
        elasticsearchClient.waitForHealthyIndex(getCrawlerName());

        elasticsearchClient.index(getCrawlerName(), "1", "{ \"foo\" : \"bar\" }");
        elasticsearchClient.index(getCrawlerName(), "2", "{ \"foo\" : \"baz\" }");

        elasticsearchClient.refresh(getCrawlerName());

        // match_all
        SearchResponse response = elasticsearchClient.search(getCrawlerName(), (String) null);
        assertThat(response.getHits().getTotal(), is(2L));

        // term
        response = elasticsearchClient.search(getCrawlerName(), "foo:bar");
        assertThat(response.getHits().getTotal(), is(1L));

        // using fields
        response = elasticsearchClient.search(getCrawlerName(), "foo:bar", 10, "_source");
        assertThat(response.getHits().getTotal(), is(1L));
        response = elasticsearchClient.search(getCrawlerName(), "foo:bar", 10, "foo");
        assertThat(response.getHits().getTotal(), is(1L));
        assertThat(response.getHits().getHits().get(0).getFields(), hasEntry("foo", Collections.singletonList("bar")));

        // match_all
        response = elasticsearchClient.searchJson(getCrawlerName(), "{}");
        assertThat(response.getHits().getTotal(), is(2L));

        // match
        response = elasticsearchClient.searchJson(getCrawlerName(), "{ \"query\" : { \"match\": { \"foo\" : \"bar\" } } }");
        assertThat(response.getHits().getTotal(), is(1L));
    }

    @Test
    public void testBulkWithTime() throws IOException, InterruptedException {
        // Create the index first
        elasticsearchClient.createIndex(getCrawlerName());
        elasticsearchClient.waitForHealthyIndex(getCrawlerName());

        BulkProcessor bulkProcessor = BulkProcessor.simpleBulkProcessor(elasticsearchClient, 100, TimeValue.timeValueSeconds(2), null);
        for (int i = 0; i < 10; i++) {
            bulkProcessor.add(new IndexRequest(getCrawlerName(), "doc", "id" + i).source("{\"foo\":\"bar\"}"));
        }

        elasticsearchClient.refresh(getCrawlerName());

        waitForAllShardsAssigned();

        // We wait for 3 seconds (2 should be enough)
        Thread.sleep(3000L);

        elasticsearchClient.refresh(getCrawlerName());

        // We should have now our docs
        SearchResponse response = elasticsearchClient.search(getCrawlerName(), (String) null);
        assertThat(response.getHits().getTotal(), is(10L));

        bulkProcessor.close();
    }

    @Test
    public void testBulkWithoutTime() throws IOException, InterruptedException {
        // Create the index first
        elasticsearchClient.createIndex(getCrawlerName());
        elasticsearchClient.waitForHealthyIndex(getCrawlerName());

        BulkProcessor bulkProcessor = BulkProcessor.simpleBulkProcessor(elasticsearchClient, 10, null, null);
        for (int i = 0; i < 9; i++) {
            bulkProcessor.add(new IndexRequest(getCrawlerName(), "doc", "id" + i).source("{\"foo\":\"bar\"}"));
        }

        elasticsearchClient.refresh(getCrawlerName());

        waitForAllShardsAssigned();

        bulkProcessor.add(new IndexRequest(getCrawlerName(), "doc", "id" + 9).source("{\"foo\":\"bar\"}"));

        elasticsearchClient.refresh(getCrawlerName());

        // We should have now our docs
        SearchResponse response = elasticsearchClient.search(getCrawlerName(), (String) null);
        assertThat(response.getHits().getTotal(), is(10L));

        bulkProcessor.close();
    }

    @Test
    public void testBulkWithPipeline() throws IOException, InterruptedException {
        // We can only run this test against a 5.0 cluster or >
        assumeThat("We skip the test as we are not running it with a 5.0 cluster or >",
                elasticsearchClient.isIngestSupported(), is(true));

        // Create the index first
        elasticsearchClient.createIndex(getCrawlerName());
        elasticsearchClient.waitForHealthyIndex(getCrawlerName());

        // Create an empty ingest pipeline
        String pipeline = "{\n" +
                "  \"description\" : \"describe pipeline\",\n" +
                "  \"processors\" : [\n" +
                "    {\n" +
                "      \"set\" : {\n" +
                "        \"field\": \"foo\",\n" +
                "        \"value\": \"bar\"\n" +
                "      }\n" +
                "    }\n" +
                "  ]\n" +
                "}";
        StringEntity entity = new StringEntity(pipeline, ContentType.APPLICATION_JSON);

        elasticsearchClient.getClient().performRequest("PUT", "_ingest/pipeline/" + getCrawlerName(),
                Collections.emptyMap(), entity);

        BulkProcessor bulkProcessor = BulkProcessor.simpleBulkProcessor(elasticsearchClient, 100, TimeValue.timeValueSeconds(2), getCrawlerName());
        for (int i = 0; i < 10; i++) {
            bulkProcessor.add(new IndexRequest(getCrawlerName(), "doc", "id" + i).source("{\"field\": \"baz\"}"));
        }

        elasticsearchClient.refresh(getCrawlerName());

        waitForAllShardsAssigned();

        // We wait for 3 seconds (2 should be enough)
        Thread.sleep(3000L);

        elasticsearchClient.refresh(getCrawlerName());

        // We should have now our docs
        SearchResponse response = elasticsearchClient.search(getCrawlerName(), "foo:bar");
        assertThat(response.getHits().getTotal(), is(10L));

        bulkProcessor.close();
    }

    @Test
    public void testBulkWithErrors() throws IOException, InterruptedException {
        // Create the index first
        elasticsearchClient.createIndex(getCrawlerName());
        elasticsearchClient.waitForHealthyIndex(getCrawlerName());

        AtomicReference<BulkResponse> bulkResponse = new AtomicReference<>();

        BulkProcessor bulkProcessor = new BulkProcessor.Builder(elasticsearchClient,
                new BulkProcessor.Listener() {
                    @Override public void beforeBulk(long executionId, BulkRequest request) { }
                    @Override public void afterBulk(long executionId, BulkRequest request, BulkResponse response) {
                        bulkResponse.set(response);
                    }
                    @Override public void afterBulk(long executionId, BulkRequest request, Throwable failure) { }
                    @Override public void setBulkProcessor(BulkProcessor bulkProcessor) { }
                })
                .setBulkActions(100)
                .setFlushInterval(TimeValue.timeValueMillis(200))
                .build();
        bulkProcessor.add(new IndexRequest(getCrawlerName(), "doc", "id").source("{\"foo\":\"bar\""));
        bulkProcessor.close();


        BulkResponse response = bulkResponse.get();
        Throwable message = response.buildFailureMessage();

        assertThat(message.getMessage(), containsString("1 failures"));

        // If we run the test with a TRACE level, we can check more things
        if (LogManager.getLogger(BulkResponse.class).isTraceEnabled()) {
            assertThat(message.getMessage(), containsString("failed to parse"));
        }
    }

    @Test
    public void testBulkUsesContentType() throws Exception {
        // Create the index first
        elasticsearchClient.createIndex(getCrawlerName());
        elasticsearchClient.waitForHealthyIndex(getCrawlerName());

        BulkRequest bulkRequest = new BulkRequest();
        bulkRequest.add(new IndexRequest(getCrawlerName(), "doc", "id").source("{\"foo\":\"bar\"}"));

        // This call was failing when we were not passing the ContentType.APPLICATION_JSON in the bulk method
        elasticsearchClient.bulk(bulkRequest, null);
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
     * @throws InterruptedException in case of error
     */
    private void waitForAllShardsAssigned() throws InterruptedException {
        awaitBusy(() -> {
            try {
                elasticsearchClient.search(getCrawlerName(), (String) null);
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
