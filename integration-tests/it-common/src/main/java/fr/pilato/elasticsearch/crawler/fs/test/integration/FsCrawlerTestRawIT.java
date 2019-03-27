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

import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchHit;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.settings.Fs;
import org.junit.Test;

import static com.carrotsearch.randomizedtesting.RandomizedTest.rarely;
import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.extractFromPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * Test json support crawler setting
 */
public class FsCrawlerTestRawIT extends AbstractFsCrawlerITCase {

    /**
     * Test case for issue #439: https://github.com/dadoonet/fscrawler/issues/439 : Date Mapping issue in RAW field
     */
    @Test
    public void test_mapping() throws Exception {
        startCrawler();

        // We don't really care here about the fact that one document has been indexed
        // But let's add manually some documents

        // The 1st document simulates that we are indexing a String field which contains something like a date
        String json1 = "{\n" +
                "  \"meta\": {\n" +
                "    \"raw\": {\n" +
                "      \"fscrawler_date\": \"1971-12-26\"\n" +
                "    }\n" +
                "  }\n" +
                "}\n";

        // The 2nd document index a String to the same field
        String json2 = "{\n" +
                "  \"meta\": {\n" +
                "    \"raw\": {\n" +
                "      \"fscrawler_date\": \"David\"\n" +
                "    }\n" +
                "  }\n" +
                "}\n";

        // This will cause an Elasticsearch Exception as the String is not a Date
        // If the mapping is incorrect
        esClient.index(getCrawlerName(), "1", json1, null);
        esClient.index(getCrawlerName(), "2", json2, null);
        esClient.flush();
    }

    @Test
    public void test_disable_raw() throws Exception {
        Fs.Builder builder = startCrawlerDefinition();
        if (rarely()) {
            // Sometimes we explicitly disable it but this is also the default value
            builder.setRawMetadata(false);
        }
        startCrawler(getCrawlerName(), builder.build(), endCrawlerDefinition(getCrawlerName()), null);
        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
        for (ESSearchHit hit : searchResponse.getHits()) {
            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.META).get("raw"), nullValue());
        }
    }

    @Test
    public void test_enable_raw() throws Exception {
        Fs fs = startCrawlerDefinition()
                .setRawMetadata(true)
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);
        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
        for (ESSearchHit hit : searchResponse.getHits()) {
            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.META).get("raw"), notNullValue());
        }
    }
}
