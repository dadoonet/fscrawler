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

import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.client.ESTermQuery;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClient;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractITCase;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Properties;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.readPropertiesFromClassLoader;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeThat;

/**
 * Test elasticsearch HTTP client
 */
public class ElasticsearchClientIT extends AbstractITCase {

    private ElasticsearchClient esClient = documentService.getClient();

    @Before
    public void cleanExistingIndex() throws IOException {
        logger.info(" -> Removing existing index [{}*]", getCrawlerName());
        esClient.deleteIndex(getCrawlerName() + "*");
    }

    @Test
    public void testCreateIndex() throws IOException {
        esClient.createIndex(getCrawlerName(), false, null);
        boolean exists = esClient.isExistingIndex(getCrawlerName());
        assertThat(exists, is(true));
    }

    @Test
    public void testCreateIndexWithSettings() throws IOException {
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
    public void testRefresh() throws IOException {
        esClient.createIndex(getCrawlerName(), false, null);
        refresh();
    }

    @Test
    public void testCreateIndexAlreadyExistsShouldFail() throws IOException {
        esClient.createIndex(getCrawlerName(), false, null);
        esClient.waitForHealthyIndex(getCrawlerName());
        try {
            esClient.createIndex(getCrawlerName(), false, null);
            fail("we should reject creation of an already existing index");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), containsString("already exists"));
        }
    }

    @Test
    public void testCreateIndexAlreadyExistsShouldBeIgnored() throws IOException {
        esClient.createIndex(getCrawlerName(), false, null);
        esClient.waitForHealthyIndex(getCrawlerName());
        esClient.createIndex(getCrawlerName(), true, null);
    }

    @Test
    public void testSearch() throws IOException {
        esClient.createIndex(getCrawlerName(), false, null);
        esClient.waitForHealthyIndex(getCrawlerName());

        esClient.indexSingle(getCrawlerName(), "1", "{ \"foo\": { \"bar\": \"bar\" } }");
        esClient.indexSingle(getCrawlerName(), "2", "{ \"foo\": { \"bar\": \"baz\" } }");

        esClient.refresh(getCrawlerName());

        // match_all
        ESSearchResponse response = esClient.search(new ESSearchRequest().withIndex(getCrawlerName()));
        assertThat(response.getTotalHits(), is(2L));

        // term
        response = esClient.search(new ESSearchRequest().withIndex(getCrawlerName()).withESQuery(new ESTermQuery("foo.bar", "bar")));
        assertThat(response.getTotalHits(), is(1L));
    }

    @Test
    public void testFindVersion() throws IOException {
        String version = esClient.getVersion();
        logger.info("Current elasticsearch version: [{}]", version);

        // If we did not use an external URL but the docker instance we can test for sure that the version is the expected one
        if (System.getProperty("tests.cluster.url") == null) {
            Properties properties = readPropertiesFromClassLoader("elasticsearch.version.properties");
            assertThat(version, is(properties.getProperty("version")));
        }
    }

    @Test
    public void testPipeline() throws IOException {
        String crawlerName = getCrawlerName();

        // We can only run this test against a 5.0 cluster or >
        assumeThat("We skip the test as we are not running it with a 5.0 cluster or >", esClient.isIngestSupported(), is(true));

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
}
