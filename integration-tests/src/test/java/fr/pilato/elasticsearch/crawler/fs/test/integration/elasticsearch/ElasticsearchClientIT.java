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

import com.carrotsearch.randomizedtesting.RandomizedTest;
import fr.pilato.elasticsearch.crawler.fs.client.ESBoolQuery;
import fr.pilato.elasticsearch.crawler.fs.client.ESMatchQuery;
import fr.pilato.elasticsearch.crawler.fs.client.ESPrefixQuery;
import fr.pilato.elasticsearch.crawler.fs.client.ESRangeQuery;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchHit;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.client.ESTermQuery;
import fr.pilato.elasticsearch.crawler.fs.client.ESTermsAggregation;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchBulkRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchBulkResponse;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClient;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientException;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchDeleteOperation;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchEngine;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchIndexOperation;
import fr.pilato.elasticsearch.crawler.fs.client.IElasticsearchClient;
import fr.pilato.elasticsearch.crawler.fs.framework.bulk.FsCrawlerBulkResponse;
import fr.pilato.elasticsearch.crawler.fs.settings.Elasticsearch;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.ServerUrl;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractITCase;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotAuthorizedException;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.util.List;
import java.util.Properties;

import static fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClient.CHECK_NODES_EVERY;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.INDEX_SUFFIX_FOLDER;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.readPropertiesFromClassLoader;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.fail;

/**
 * Test elasticsearch HTTP client
 */
public class ElasticsearchClientIT extends AbstractITCase {

    private final IElasticsearchClient esClient = managementService.getClient();

    @Before
    public void cleanExistingIndex() throws IOException, ElasticsearchClientException {
        logger.info(" -> Removing existing index [{}*]", getCrawlerName());
        esClient.deleteIndex(getCrawlerName());
        esClient.deleteIndex(getCrawlerName() + INDEX_SUFFIX_FOLDER);
    }

    @Test
    public void testDeleteIndex() throws IOException, ElasticsearchClientException {
        esClient.deleteIndex("does-not-exist-index");
        esClient.createIndex(getCrawlerName(), false, null);
        assertThat(esClient.isExistingIndex(getCrawlerName()), is(true));
        esClient.deleteIndex(getCrawlerName());
        assertThat(esClient.isExistingIndex(getCrawlerName()), is(false));
    }

    @Test
    public void testWaitForHealthyIndex() throws IOException, ElasticsearchClientException {
        esClient.createIndex(getCrawlerName(), false, null);
        esClient.waitForHealthyIndex(getCrawlerName());
        try {
            esClient.waitForHealthyIndex("does-not-exist-index");
            fail("We should have raised a ClientErrorException");
        } catch (ClientErrorException e) {
            assertThat(e.getResponse().getStatus(), is(408));
        }
    }

    @Test
    public void testCreateIndex() throws IOException, ElasticsearchClientException {
        esClient.createIndex(getCrawlerName(), false, null);
        boolean exists = esClient.isExistingIndex(getCrawlerName());
        assertThat(exists, is(true));
    }

    @Test
    public void testCreateIndexWithSettings() throws IOException, ElasticsearchClientException {
        esClient.createIndex(getCrawlerName(), false, "{\n" +
                "  \"settings\": {\n" +
                "    \"number_of_shards\": 1,\n" +
                "    \"number_of_replicas\": 1\n" +
                "  }\n" +
                "}\n");
        boolean exists = esClient.isExistingIndex(getCrawlerName());
        assertThat(exists, is(true));
    }

    @Test
    public void testRefresh() throws IOException, ElasticsearchClientException {
        esClient.createIndex(getCrawlerName(), false, null);
        esClient.refresh(getCrawlerName());
    }

    @Test
    public void testCreateIndexAlreadyExistsShouldFail() throws IOException, ElasticsearchClientException {
        esClient.createIndex(getCrawlerName(), false, null);
        esClient.waitForHealthyIndex(getCrawlerName());
        try {
            esClient.createIndex(getCrawlerName(), false, null);
            fail("we should reject creation of an already existing index");
        } catch (ElasticsearchClientException e) {
            assertThat(e.getMessage(), containsString("already exists"));
        }
    }

    @Test
    public void testCreateIndexAlreadyExistsShouldBeIgnored() throws IOException, ElasticsearchClientException {
        esClient.createIndex(getCrawlerName(), false, null);
        esClient.waitForHealthyIndex(getCrawlerName());
        esClient.createIndex(getCrawlerName(), true, null);
    }

    @Test
    public void testCreateIndexWithErrors() throws IOException {
        try {
            esClient.createIndex(getCrawlerName(), false, "{this is wrong}");
            fail("we should reject creation of an already existing index");
        } catch (ElasticsearchClientException e) {
            assertThat(e.getMessage(), containsString("error while creating index"));
        }
    }

    @Test
    public void testSearch() throws IOException, ElasticsearchClientException {
        esClient.createIndex(getCrawlerName(), false, "{\n" +
                "  \"mappings\": {\n" +
                "    \"properties\": {\n" +
                "      \"foo\": {\n" +
                "        \"properties\": {\n" +
                "          \"bar\": {\n" +
                "            \"type\": \"text\",\n" +
                "            \"store\": true,\n" +
                "            \"fields\": {\n" +
                "              \"raw\": { \n" +
                "                \"type\":  \"keyword\"\n" +
                "              }\n" +
                "            }\n" +
                "          }\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}");

        esClient.indexSingle(getCrawlerName(), "1", "{ \"foo\": { \"bar\": \"bar\" } }", null);
        esClient.indexSingle(getCrawlerName(), "2", "{ \"foo\": { \"bar\": \"baz\" } }", null);
        esClient.indexSingle(getCrawlerName(), "3", "{ \"number\": 1 }", null);
        esClient.indexSingle(getCrawlerName(), "4", "{ \"number\": 2 }", null);

        esClient.refresh(getCrawlerName());

        // match_all
        {
            ESSearchResponse response = esClient.search(new ESSearchRequest().withIndex(getCrawlerName()));
            assertThat(response.getTotalHits(), is(4L));

            for (ESSearchHit hit : response.getHits()) {
                assertThat(hit.getIndex(), is(getCrawlerName()));
                assertThat(hit.getId(), isOneOf("1", "2", "3", "4"));
                assertThat(hit.getVersion(), is(1L));
                assertThat(hit.getSource(), not(isEmptyOrNullString()));
                assertThat(hit.getHighlightFields().isEmpty(), is(true));
                assertThat(hit.getStoredFields(), nullValue());
            }
        }

        // term
        {
            ESSearchResponse response = esClient.search(new ESSearchRequest().withIndex(getCrawlerName())
                    .withESQuery(new ESTermQuery("foo.bar", "bar")));
            assertThat(response.getTotalHits(), is(1L));
            assertThat(response.getHits().get(0).getIndex(), is(getCrawlerName()));
            assertThat(response.getHits().get(0).getId(), is("1"));
            assertThat(response.getHits().get(0).getVersion(), is(1L));
            assertThat(response.getHits().get(0).getSource(), not(isEmptyOrNullString()));
            assertThat(response.getHits().get(0).getHighlightFields().isEmpty(), is(true));
            assertThat(response.getHits().get(0).getStoredFields(), nullValue());
        }

        // match
        {
            ESSearchResponse response = esClient.search(new ESSearchRequest().withIndex(getCrawlerName())
                    .withESQuery(new ESMatchQuery("foo.bar", "bar")));
            assertThat(response.getTotalHits(), is(1L));
            assertThat(response.getHits().get(0).getIndex(), is(getCrawlerName()));
            assertThat(response.getHits().get(0).getId(), is("1"));
            assertThat(response.getHits().get(0).getVersion(), is(1L));
            assertThat(response.getHits().get(0).getSource(), not(isEmptyOrNullString()));
            assertThat(response.getHits().get(0).getHighlightFields().isEmpty(), is(true));
            assertThat(response.getHits().get(0).getStoredFields(), nullValue());
        }

        // prefix
        {
            ESSearchResponse response = esClient.search(new ESSearchRequest().withIndex(getCrawlerName())
                    .withESQuery(new ESPrefixQuery("foo.bar", "ba")));
            assertThat(response.getTotalHits(), is(2L));

            for (ESSearchHit hit : response.getHits()) {
                assertThat(hit.getIndex(), is(getCrawlerName()));
                assertThat(hit.getId(), isOneOf("1", "2"));
                assertThat(hit.getVersion(), is(1L));
                assertThat(hit.getSource(), not(isEmptyOrNullString()));
                assertThat(hit.getHighlightFields().isEmpty(), is(true));
                assertThat(hit.getStoredFields(), nullValue());
            }
        }

        // range - lower than 2
        {
            ESSearchResponse response = esClient.search(new ESSearchRequest().withIndex(getCrawlerName())
                    .withESQuery(new ESRangeQuery("number").withLt(2)));
            assertThat(response.getTotalHits(), is(1L));
            assertThat(response.getHits().get(0).getIndex(), is(getCrawlerName()));
            assertThat(response.getHits().get(0).getId(), is("3"));
            assertThat(response.getHits().get(0).getVersion(), is(1L));
            assertThat(response.getHits().get(0).getSource(), not(isEmptyOrNullString()));
            assertThat(response.getHits().get(0).getHighlightFields().isEmpty(), is(true));
            assertThat(response.getHits().get(0).getStoredFields(), nullValue());
        }

        // range - greater or equal to 2
        {
            ESSearchResponse response = esClient.search(new ESSearchRequest().withIndex(getCrawlerName())
                    .withESQuery(new ESRangeQuery("number").withGte(2)));
            assertThat(response.getTotalHits(), is(1L));
            assertThat(response.getHits().get(0).getIndex(), is(getCrawlerName()));
            assertThat(response.getHits().get(0).getId(), is("4"));
            assertThat(response.getHits().get(0).getVersion(), is(1L));
            assertThat(response.getHits().get(0).getSource(), not(isEmptyOrNullString()));
            assertThat(response.getHits().get(0).getHighlightFields().isEmpty(), is(true));
            assertThat(response.getHits().get(0).getStoredFields(), nullValue());
        }

        // bool with prefix and match
        {
            ESSearchResponse response = esClient.search(new ESSearchRequest().withIndex(getCrawlerName())
                    .withESQuery(new ESBoolQuery()
                            .addMust(new ESPrefixQuery("foo.bar", "ba"))
                            .addMust(new ESMatchQuery("foo.bar", "bar"))
                    ));
            assertThat(response.getTotalHits(), is(1L));
            assertThat(response.getHits().get(0).getIndex(), is(getCrawlerName()));
            assertThat(response.getHits().get(0).getId(), is("1"));
            assertThat(response.getHits().get(0).getVersion(), is(1L));
            assertThat(response.getHits().get(0).getSource(), not(isEmptyOrNullString()));
            assertThat(response.getHits().get(0).getHighlightFields().isEmpty(), is(true));
            assertThat(response.getHits().get(0).getStoredFields(), nullValue());
        }

        // Highlighting
        {
            ESSearchResponse response = esClient.search(new ESSearchRequest().withIndex(getCrawlerName())
                    .withESQuery(new ESMatchQuery("foo.bar", "bar"))
                    .addHighlighter("foo.bar")
            );
            assertThat(response.getTotalHits(), is(1L));
            assertThat(response.getHits().get(0).getIndex(), is(getCrawlerName()));
            assertThat(response.getHits().get(0).getId(), is("1"));
            assertThat(response.getHits().get(0).getVersion(), is(1L));
            assertThat(response.getHits().get(0).getSource(), not(isEmptyOrNullString()));
            assertThat(response.getHits().get(0).getHighlightFields().size(), is(1));
            assertThat(response.getHits().get(0).getHighlightFields(), hasKey("foo.bar"));
            assertThat(response.getHits().get(0).getHighlightFields().get("foo.bar"), iterableWithSize(1));
            assertThat(response.getHits().get(0).getHighlightFields().get("foo.bar"), hasItem("<em>bar</em>"));
            assertThat(response.getHits().get(0).getStoredFields(), nullValue());
        }

        // Fields
        {
            ESSearchResponse response = esClient.search(new ESSearchRequest().withIndex(getCrawlerName())
                    .withESQuery(new ESPrefixQuery("foo.bar", "ba"))
                    .addStoredField("foo.bar")
            );
            assertThat(response.getTotalHits(), is(2L));

            for (ESSearchHit hit : response.getHits()) {
                assertThat(hit.getIndex(), is(getCrawlerName()));
                assertThat(hit.getId(), isOneOf("1", "2"));
                assertThat(hit.getVersion(), is(1L));
                assertThat(hit.getSource(), isEmptyOrNullString());
                assertThat(hit.getHighlightFields().isEmpty(), is(true));
                assertThat(hit.getStoredFields(), notNullValue());
                assertThat(hit.getStoredFields(), hasKey(is("foo.bar")));
                assertThat(hit.getStoredFields(), hasEntry(is("foo.bar"), hasItem(isOneOf("bar", "baz"))));
            }
        }

        // Fields with _source
        {
            ESSearchResponse response = esClient.search(new ESSearchRequest().withIndex(getCrawlerName())
                    .withESQuery(new ESPrefixQuery("foo.bar", "ba"))
                    .addStoredField("_source")
                    .addStoredField("foo.bar")
            );
            assertThat(response.getTotalHits(), is(2L));

            for (ESSearchHit hit : response.getHits()) {
                assertThat(hit.getIndex(), is(getCrawlerName()));
                assertThat(hit.getId(), isOneOf("1", "2"));
                assertThat(hit.getVersion(), is(1L));
                assertThat(hit.getSource(), not(isEmptyOrNullString()));
                assertThat(hit.getHighlightFields().isEmpty(), is(true));
                assertThat(hit.getStoredFields(), notNullValue());
                assertThat(hit.getStoredFields(), hasKey(is("foo.bar")));
                assertThat(hit.getStoredFields(), hasEntry(is("foo.bar"), hasItem(isOneOf("bar", "baz"))));
            }
        }

        // Aggregation
        {
            ESSearchResponse response = esClient.search(new ESSearchRequest().withIndex(getCrawlerName())
                    .withAggregation(new ESTermsAggregation("foobar1", "foo.bar.raw"))
                    .withAggregation(new ESTermsAggregation("foobar2", "foo.bar.raw"))
                    .withSize(0)
            );
            assertThat(response.getTotalHits(), is(4L));
            assertThat(response.getAggregations(), notNullValue());
            assertThat(response.getAggregations().size(), is(2));
            assertThat(response.getAggregations(), hasKey("foobar1"));
            assertThat(response.getAggregations().get("foobar1").getName(), is("foobar1"));
            assertThat(response.getAggregations().get("foobar1").getBuckets(), hasSize(2));
            assertThat(response.getAggregations().get("foobar1").getBuckets(), hasItems(
                    new ESTermsAggregation.ESTermsBucket("bar", 1),
                    new ESTermsAggregation.ESTermsBucket("baz", 1)
            ));
            assertThat(response.getAggregations(), hasKey("foobar2"));
            assertThat(response.getAggregations().get("foobar2").getName(), is("foobar2"));
            assertThat(response.getAggregations().get("foobar2").getBuckets(), hasSize(2));
            assertThat(response.getAggregations().get("foobar2").getBuckets(), hasItems(
                    new ESTermsAggregation.ESTermsBucket("bar", 1),
                    new ESTermsAggregation.ESTermsBucket("baz", 1)
            ));
        }
    }

    @Test
    public void testFindVersion() throws IOException, ElasticsearchClientException {
        String version = esClient.getVersion();
        logger.info("Current elasticsearch version: [{}]", version);

        // If we did not use an external URL but the docker instance we can test for sure that the version is the expected one
        if (System.getProperty("tests.cluster.url") == null) {
            Properties properties = readPropertiesFromClassLoader("elasticsearch.version.properties");
            assertThat(version, is(properties.getProperty("version")));
        }
    }

    @Test
    public void testPipeline() throws IOException, ElasticsearchClientException {
        String crawlerName = getCrawlerName();

        // Create an empty ingest pipeline
        String pipeline = "{\n" +
                "  \"description\": \"Testing Grok on PDF upload\",\n" +
                "  \"processors\": [\n" +
                "    {\n" +
                "      \"gsub\": {\n" +
                "        \"field\": \"content\",\n" +
                "        \"pattern\": \"\\n\",\n" +
                "        \"replacement\": \"-\"\n" +
                "      }\n" +
                "    },\n" +
                "    {\n" +
                "      \"grok\": {\n" +
                "        \"field\": \"content\",\n" +
                "        \"patterns\": [\n" +
                "          \"%{DATA}%{IP:ip_addr} %{GREEDYDATA}\"\n" +
                "        ]\n" +
                "      }\n" +
                "    }\n" +
                "  ]\n" +
                "}";
        esClient.performLowLevelRequest("PUT", "/_ingest/pipeline/" + crawlerName, pipeline);

        assertThat(esClient.isExistingPipeline(crawlerName), is(true));
        assertThat(esClient.isExistingPipeline(crawlerName + "_foo"), is(false));
    }

    @Test
    public void testBulk() throws IOException, ElasticsearchClientException {
        {
            long nbItems = RandomizedTest.randomLongBetween(5, 20);

            ElasticsearchBulkRequest bulkRequest = new ElasticsearchBulkRequest();

            // Add some index op
            for (int i = 0; i < nbItems; i++) {
                bulkRequest.add(new ElasticsearchIndexOperation(getCrawlerName(),
                        "" + i,
                        null,
                        "{\"foo\":{\"bar\":\"bar\"},\"number\": " + i + "}"));
            }

            ElasticsearchEngine engine = new ElasticsearchEngine(esClient);
            ElasticsearchBulkResponse bulkResponse = engine.bulk(bulkRequest);
            assertThat(bulkResponse.hasFailures(), is(false));
            assertThat(bulkResponse.getItems(), not(emptyIterable()));

            esClient.refresh(getCrawlerName());
            ESSearchResponse response = esClient.search(new ESSearchRequest().withIndex(getCrawlerName()));
            assertThat(response.getTotalHits(), is(nbItems));
        }
        {
            esClient.deleteIndex(getCrawlerName());
            long nbItems = RandomizedTest.randomLongBetween(5, 20);
            long nbItemsToDelete = RandomizedTest.randomLongBetween(1, nbItems);

            ElasticsearchBulkRequest bulkRequest = new ElasticsearchBulkRequest();

            // Add some index op
            for (int i = 0; i < nbItems; i++) {
                bulkRequest.add(new ElasticsearchIndexOperation(getCrawlerName(),
                        "" + i,
                        null,
                        "{\"foo\":{\"bar\":\"bar\"},\"number\": " + i + "}"));
            }
            // Add some delete op
            for (int i = 0; i < nbItemsToDelete; i++) {
                bulkRequest.add(new ElasticsearchDeleteOperation(getCrawlerName(), "" + i));
            }

            ElasticsearchEngine engine = new ElasticsearchEngine(esClient);
            ElasticsearchBulkResponse bulkResponse = engine.bulk(bulkRequest);
            assertThat(bulkResponse.hasFailures(), is(false));
            assertThat(bulkResponse.getItems(), not(emptyIterable()));

            esClient.refresh(getCrawlerName());
            ESSearchResponse response = esClient.search(new ESSearchRequest().withIndex(getCrawlerName()));
            assertThat(response.getTotalHits(), is(nbItems-nbItemsToDelete));
        }
        {
            esClient.deleteIndex(getCrawlerName());
            long nbItems = RandomizedTest.randomLongBetween(5, 20);

            ElasticsearchBulkRequest bulkRequest = new ElasticsearchBulkRequest();

            // Add some index op
            for (int i = 0; i < nbItems; i++) {
                bulkRequest.add(new ElasticsearchIndexOperation(getCrawlerName(),
                        "" + i,
                        null,
                        "{\n" +
                                "          \"foo\" : {\n" +
                                "            \"bar\" : \"baz\"\n" +
                                "          }\n" +
                                "        }"));
            }

            ElasticsearchEngine engine = new ElasticsearchEngine(esClient);
            ElasticsearchBulkResponse bulkResponse = engine.bulk(bulkRequest);
            assertThat(bulkResponse.hasFailures(), is(false));
            assertThat(bulkResponse.getItems(), not(emptyIterable()));

            esClient.refresh(getCrawlerName());
            ESSearchResponse response = esClient.search(new ESSearchRequest().withIndex(getCrawlerName()));
            assertThat(response.getTotalHits(), is(nbItems));
        }
        {
            esClient.deleteIndex(getCrawlerName());
            String indexSettings = "{\n" +
                    "  \"mappings\": {\n" +
                    "    \"properties\": {\n" +
                    "      \"foo\": {\n" +
                    "        \"properties\": {\n" +
                    "          \"number\": {\n" +
                    "            \"type\": \"long\"\n" +
                    "          }\n" +
                    "        }\n" +
                    "      }\n" +
                    "    }\n" +
                    "  }\n" +
                    "}";

            esClient.createIndex(getCrawlerName(), false, indexSettings);

            long nbItems = RandomizedTest.randomLongBetween(5, 20);

            ElasticsearchBulkRequest bulkRequest = new ElasticsearchBulkRequest();

            // Add some index op
            // Every 5 ops, we had a failing document
            long nbErrors = 0;
            for (int i = 0; i < nbItems; i++) {
                if (i > 0 && i % 5 == 0) {
                    bulkRequest.add(new ElasticsearchIndexOperation(getCrawlerName(),
                            "" + i,
                            null,
                            "{\"foo\":{\"bar\":\"bar\"},\"number\":\"bar\"}"));
                    nbErrors++;
                } else {
                    bulkRequest.add(new ElasticsearchIndexOperation(getCrawlerName(),
                            "" + i,
                            null,
                            "{\"foo\":{\"bar\":\"bar\"},\"number\": " + i + "}"));
                }
            }

            ElasticsearchEngine engine = new ElasticsearchEngine(esClient);
            ElasticsearchBulkResponse bulkResponse = engine.bulk(bulkRequest);
            assertThat(bulkResponse.hasFailures(), is(true));
            assertThat(bulkResponse.getItems(), not(emptyIterable()));
            long errors = bulkResponse.getItems().stream().filter(FsCrawlerBulkResponse.BulkItemResponse::isFailed).count();
            assertThat(errors, is(nbErrors));

            esClient.refresh(getCrawlerName());
            ESSearchResponse response = esClient.search(new ESSearchRequest().withIndex(getCrawlerName()));
            assertThat(response.getTotalHits(), is(nbItems - nbErrors));
        }
    }

    @Test
    public void testDeleteSingle() throws IOException, ElasticsearchClientException {
        esClient.indexSingle(getCrawlerName(), "1", "{ \"foo\": { \"bar\": \"bar\" } }", null);
        esClient.indexSingle(getCrawlerName(), "2", "{ \"foo\": { \"bar\": \"baz\" } }", null);
        esClient.indexSingle(getCrawlerName(), "3", "{ \"number\": 1 }", null);
        esClient.indexSingle(getCrawlerName(), "4", "{ \"number\": 2 }", null);

        esClient.refresh(getCrawlerName());
        ESSearchResponse response = esClient.search(new ESSearchRequest().withIndex(getCrawlerName()));
        assertThat(response.getTotalHits(), is(4L));

        esClient.deleteSingle(getCrawlerName(), "1");
        esClient.refresh(getCrawlerName());

        response = esClient.search(new ESSearchRequest().withIndex(getCrawlerName()));
        assertThat(response.getTotalHits(), is(3L));

        try {
            esClient.deleteSingle(getCrawlerName(), "99999");
            fail("We should have raised an " + ElasticsearchClientException.class.getSimpleName());
        } catch (ElasticsearchClientException e) {
            assertThat(e.getMessage(), is("Document " + getCrawlerName() + "/99999 does not exist"));
        }
        esClient.refresh(getCrawlerName());

        response = esClient.search(new ESSearchRequest().withIndex(getCrawlerName()));
        assertThat(response.getTotalHits(), is(3L));
    }

    @Test
    public void testExists() throws IOException, ElasticsearchClientException {
        esClient.indexSingle(getCrawlerName(), "1", "{ \"foo\": { \"bar\": \"bar\" } }", null);
        esClient.refresh(getCrawlerName());
        assertThat(esClient.exists(getCrawlerName(), "1"), is(true));
        assertThat(esClient.exists(getCrawlerName(), "999"), is(false));
    }

    @Test
    public void testWithOnlyOneRunningNode() throws ElasticsearchClientException, IOException {
        // Build a client with a non-running node
        Elasticsearch elasticsearch = Elasticsearch.builder()
                .setNodes(List.of(
                        new ServerUrl("http://127.0.0.1:9206"),
                        new ServerUrl(testClusterUrl)))
                .setUsername(testClusterUser)
                .setPassword(testClusterPass)
                .setSslVerification(false)
                .build();
        FsSettings fsSettings = FsSettings.builder("esClient").setElasticsearch(elasticsearch).build();
        try (IElasticsearchClient localClient = new ElasticsearchClient(metadataDir, fsSettings)) {
            localClient.start();
            localClient.isExistingIndex("foo");
            localClient.isExistingIndex("bar");
            localClient.isExistingIndex("baz");
        }
    }

    @Test
    public void testWithTwoRunningNodes() throws ElasticsearchClientException, IOException {
        // Build a client with 2 running nodes (well, the same one is used twice) and one non-running node
        Elasticsearch elasticsearch = Elasticsearch.builder()
                .setNodes(List.of(
                        new ServerUrl(testClusterUrl),
                        new ServerUrl("http://127.0.0.1:9206"),
                        new ServerUrl(testClusterUrl)))
                .setUsername(testClusterUser)
                .setPassword(testClusterPass)
                .setSslVerification(false)
                .build();
        FsSettings fsSettings = FsSettings.builder("esClient").setElasticsearch(elasticsearch).build();
        try (IElasticsearchClient localClient = new ElasticsearchClient(metadataDir, fsSettings)) {
            localClient.start();
            assertThat(localClient.getAvailableNodes(), hasSize(3));
            localClient.isExistingIndex("foo");
            assertThat(localClient.getAvailableNodes(), hasSize(2));

            for (int i = 0; i < CHECK_NODES_EVERY - 3; i++) {
                localClient.isExistingIndex("foo");
                assertThat("Run " + i, localClient.getAvailableNodes(), hasSize(2));
            }

            for (int i = 0; i < 10; i++) {
                localClient.isExistingIndex("foo");
                assertThat("Run " + i, localClient.getAvailableNodes(), hasSize(3));
                for (int j = 0; j < CHECK_NODES_EVERY - 2; j++) {
                    localClient.isExistingIndex("foo");
                    assertThat("Run " + i + "-" + j, localClient.getAvailableNodes(), hasSize(2));
                }
            }
        }
    }

    @Test
    public void testWithNonRunningNodes() {
        // Build a client with a non-running node
        Elasticsearch elasticsearch = Elasticsearch.builder()
                .setNodes(List.of(
                        new ServerUrl("http://127.0.0.1:9206"),
                        new ServerUrl("http://127.0.0.1:9207")))
                .setUsername(testClusterUser)
                .setPassword(testClusterPass)
                .setSslVerification(false)
                .build();
        FsSettings fsSettings = FsSettings.builder("esClient").setElasticsearch(elasticsearch).build();

        try (IElasticsearchClient localClient = new ElasticsearchClient(metadataDir, fsSettings)) {
            localClient.start();
            fail("We should have raised a " + ElasticsearchClientException.class.getSimpleName());
        } catch (IOException ex) {
            fail("We should have raised a " + ElasticsearchClientException.class.getSimpleName());
        } catch (ElasticsearchClientException ex) {
            assertThat(ex.getMessage(), containsString("All nodes are failing"));
        }
    }

    @Test
    public void testWithNonRunningNode() {
        // Build a client with a non-running node
        Elasticsearch elasticsearch = Elasticsearch.builder()
                .setNodes(List.of(new ServerUrl("http://127.0.0.1:9206")))
                .setUsername(testClusterUser)
                .setPassword(testClusterPass)
                .setSslVerification(false)
                .build();
        FsSettings fsSettings = FsSettings.builder("esClient").setElasticsearch(elasticsearch).build();

        try (IElasticsearchClient localClient = new ElasticsearchClient(metadataDir, fsSettings)) {
            localClient.start();
            fail("We should have raised a " + ElasticsearchClientException.class.getSimpleName());
        } catch (IOException ex) {
            fail("We should have raised a " + ElasticsearchClientException.class.getSimpleName());
        } catch (ElasticsearchClientException ex) {
            assertThat(ex.getMessage(), containsString("Can not execute GET"));
            assertThat(ex.getCause().getCause(), instanceOf(ConnectException.class));
            assertThat(ex.getCause().getCause().getMessage(), containsString("Connection refused"));
        }
    }

    @Test
    public void testSecuredClusterWithBadCredentials() throws IOException, ElasticsearchClientException {
        // Build a client with a null password
        Elasticsearch elasticsearch = Elasticsearch.builder()
                .setNodes(List.of(new ServerUrl(testClusterUrl)))
                .setSslVerification(false)
                .build();
        FsSettings fsSettings = FsSettings.builder("esClient").setElasticsearch(elasticsearch).build();

        try (IElasticsearchClient localClient = new ElasticsearchClient(metadataDir, fsSettings)) {
            localClient.start();
            fail("We should have raised a " + ElasticsearchClientException.class.getSimpleName());
        } catch (NotAuthorizedException ex) {
            assertThat(ex.getMessage(), containsString("HTTP 401 Unauthorized"));
        }
    }
}
