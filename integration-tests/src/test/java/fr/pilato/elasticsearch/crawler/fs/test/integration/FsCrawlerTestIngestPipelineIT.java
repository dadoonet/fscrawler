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

import fr.pilato.elasticsearch.crawler.fs.settings.Elasticsearch;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Test;

import java.util.Collections;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.INDEX_SUFFIX_FOLDER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeThat;

/**
 * Test crawler with ingest pipelines
 */
public class FsCrawlerTestIngestPipelineIT extends AbstractFsCrawlerITCase {

    /**
     * Test case for #234: https://github.com/dadoonet/fscrawler/issues/234 : Support ingest pipeline processing
     */
    @Test
    public void test_ingest_pipeline() throws Exception {
        assumeVersion6AtLeast();
        String crawlerName = getCrawlerName();

        // We can only run this test against a 5.0 cluster or >
        assumeThat("We skip the test as we are not running it with a 5.0 cluster or >",
                elasticsearchClient.isIngestSupported(), is(true));

        // Create an empty ingest pipeline
        String pipeline = "{\n" +
                "  \"description\" : \"describe pipeline\",\n" +
                "  \"processors\" : [\n" +
                "    {\n" +
                "      \"rename\": {\n" +
                "        \"field\": \"content\",\n" +
                "        \"target_field\": \"my_content_field\"\n" +
                "      }\n" +
                "    }\n" +
                "  ]\n" +
                "}";
        StringEntity entity = new StringEntity(pipeline, ContentType.APPLICATION_JSON);

        elasticsearchClient.getLowLevelClient().performRequest("PUT", "_ingest/pipeline/" + crawlerName,
                Collections.emptyMap(), entity);

        Elasticsearch elasticsearch = elasticsearchBuilder();
        elasticsearch.setPipeline(crawlerName);

        startCrawler(crawlerName, fsBuilder().build(), elasticsearch);

        // We expect to have one file
        countTestHelper(new SearchRequest(getCrawlerName()).source(new SearchSourceBuilder()
                .query(QueryBuilders.matchQuery("my_content_field", "perniciosoque"))), 1L, currentTestResourceDir);

        // We expect to have one folder
        SearchResponse response = elasticsearchClient.search(new SearchRequest(getCrawlerName() + INDEX_SUFFIX_FOLDER));
        assertThat(response.getHits().getTotalHits(), is(1L));
    }

    /**
     * Test case for #392: https://github.com/dadoonet/fscrawler/issues/392 : Support ingest pipeline processing
     */
    @Test
    public void test_ingest_pipeline_392() throws Exception {
        String crawlerName = getCrawlerName();

        // We can only run this test against a 5.0 cluster or >
        assumeThat("We skip the test as we are not running it with a 5.0 cluster or >",
                elasticsearchClient.isIngestSupported(), is(true));

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
        StringEntity entity = new StringEntity(pipeline, ContentType.APPLICATION_JSON);

        elasticsearchClient.getLowLevelClient().performRequest("PUT", "_ingest/pipeline/" + crawlerName,
                Collections.emptyMap(), entity);

        Elasticsearch elasticsearch = elasticsearchBuilder();
        elasticsearch.setPipeline(crawlerName);

        startCrawler(crawlerName, fsBuilder().build(), elasticsearch);

        // We expect to have one file
        countTestHelper(new SearchRequest(getCrawlerName()).source(new SearchSourceBuilder()
                .query(QueryBuilders.termQuery("ip_addr", "10.21.23.123"))), 1L, currentTestResourceDir);
    }

    /**
     * Test case for #490: https://github.com/dadoonet/fscrawler/issues/490 : Missing ES pipeline
     */
    @Test
    public void test_ingest_missing_pipeline_490() throws Exception {
        String crawlerName = getCrawlerName();

        // We can only run this test against a 5.0 cluster or >
        assumeThat("We skip the test as we are not running it with a 5.0 cluster or >",
                elasticsearchClient.isIngestSupported(), is(true));

        Elasticsearch elasticsearch = elasticsearchBuilder();
        elasticsearch.setPipeline(crawlerName);

        try {
            startCrawler(crawlerName, fsBuilder().build(), elasticsearch);
            fail("We should have caught a RuntimeException");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), containsString("You defined pipeline:" + crawlerName + ", but it does not exist."));
        }
    }
}
