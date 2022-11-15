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
import fr.pilato.elasticsearch.crawler.fs.client.ESTermQuery;
import fr.pilato.elasticsearch.crawler.fs.settings.Elasticsearch;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.junit.Test;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.INDEX_SUFFIX_FOLDER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;

/**
 * Test crawler with ingest pipelines
 */
public class FsCrawlerTestIngestPipelineIT extends AbstractFsCrawlerITCase {

    /**
     * Test case for #234: <a href="https://github.com/dadoonet/fscrawler/issues/234">https://github.com/dadoonet/fscrawler/issues/234</a> : Support ingest pipeline processing
     */
    @Test
    public void test_ingest_pipeline() throws Exception {
        String crawlerName = getCrawlerName();

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
        managementService.getClient().performLowLevelRequest("PUT", "/_ingest/pipeline/" + crawlerName, pipeline);

        Elasticsearch elasticsearch = endCrawlerDefinition(crawlerName);
        elasticsearch.setPipeline(crawlerName);

        crawler = startCrawler(crawlerName, startCrawlerDefinition().build(), elasticsearch, null);

        // We expect to have one file
        countTestHelper(new ESSearchRequest()
                .withIndex(getCrawlerName())
                .withESQuery(new ESMatchQuery("my_content_field", "perniciosoque")), 1L, currentTestResourceDir);

        // We expect to have one folder
        ESSearchResponse response = documentService.search(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_FOLDER));
        assertThat(response.getTotalHits(), is(1L));
    }

    /**
     * Test case for #392: <a href="https://github.com/dadoonet/fscrawler/issues/392">https://github.com/dadoonet/fscrawler/issues/392</a> : Support ingest pipeline processing
     */
    @Test
    public void test_ingest_pipeline_392() throws Exception {
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
        managementService.getClient().performLowLevelRequest("PUT", "/_ingest/pipeline/" + crawlerName, pipeline);

        Elasticsearch elasticsearch = endCrawlerDefinition(crawlerName);
        elasticsearch.setPipeline(crawlerName);

        crawler = startCrawler(crawlerName, startCrawlerDefinition().build(), elasticsearch, null);

        // We expect to have one file
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName())
                .withESQuery(new ESTermQuery("ip_addr", "127.0.0.1")), 1L, currentTestResourceDir);
    }

    /**
     * Test case for #490: <a href="https://github.com/dadoonet/fscrawler/issues/490">https://github.com/dadoonet/fscrawler/issues/490</a> : Missing ES pipeline
     */
    @Test
    public void test_ingest_missing_pipeline_490() throws Exception {
        String crawlerName = getCrawlerName();

        Elasticsearch elasticsearch = endCrawlerDefinition(crawlerName);
        elasticsearch.setPipeline(crawlerName);

        try {
            crawler = startCrawler(crawlerName, startCrawlerDefinition().build(), elasticsearch, null);
            fail("We should have caught a RuntimeException");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), containsString("You defined pipeline:" + crawlerName + ", but it does not exist."));
        }
    }
}
