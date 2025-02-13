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

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchHit;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.settings.Fs;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Test store_source crawler setting
 */
public class FsCrawlerTestStoreSourceIT extends AbstractFsCrawlerITCase {

    @Test
    public void test_store_source() throws Exception {
        Fs fs = startCrawlerDefinition()
                .setStoreSource(true)
                .build();
        crawler = startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null, null);

        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
        for (ESSearchHit hit : searchResponse.getHits()) {
            // We check that the field is in _source
            assertThat(JsonPath.read(hit.getSource(), "$.attachment"), notNullValue());
        }
    }

    @Test
    public void test_do_not_store_source() throws Exception {
        crawler = startCrawler();

        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);

        ESSearchResponse searchResponse = documentService.search(new ESSearchRequest().withIndex(getCrawlerName()));
        for (ESSearchHit hit : searchResponse.getHits()) {
            // We check that the field is not part of _source
            expectThrows(PathNotFoundException.class, () -> JsonPath.read(hit.getSource(), "$.attachment"));
        }
    }

    @Test
    public void test_store_source_no_index_content() throws Exception {
        Fs fs = startCrawlerDefinition()
                .setStoreSource(true)
                .setIndexContent(false)
                .build();
        crawler = startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null, null);

        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
        for (ESSearchHit hit : searchResponse.getHits()) {
            // We check that the field is in _source
            assertThat(JsonPath.read(hit.getSource(), "$.attachment"), notNullValue());
        }
    }
}
