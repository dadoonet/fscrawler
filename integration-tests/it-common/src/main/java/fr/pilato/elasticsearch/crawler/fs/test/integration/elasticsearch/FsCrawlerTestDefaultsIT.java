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
import fr.pilato.elasticsearch.crawler.fs.client.ESMatchQuery;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchHit;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.client.ESTermQuery;
import fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Test crawler default settings
 */
public class FsCrawlerTestDefaultsIT extends AbstractFsCrawlerITCase {

    @Test
    public void test_defaults() throws Exception {
        startCrawler();

        // We expect to have one file
        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);

        // The default configuration should not add file attributes
        for (ESSearchHit hit : searchResponse.getHits()) {
            expectThrows(PathNotFoundException.class, () -> JsonPath.read(hit.getSourceAsString(), "$.attributes"));
        }
    }

    @Test
    public void test_default_metadata() throws Exception {
        startCrawler();

        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
        for (ESSearchHit hit : searchResponse.getHits()) {
            Object document = JsonUtil.parseJson(hit.getSourceAsString());
            expectThrows(PathNotFoundException.class, () -> JsonPath.read(document, "$.attachment"));

            assertThat(JsonPath.read(document, "$.file.filename"), notNullValue());
            assertThat(JsonPath.read(document, "$.file.content_type"), notNullValue());
            assertThat(JsonPath.read(document, "$.file.url"), notNullValue());
            assertThat(JsonPath.read(document, "$.file.filesize"), notNullValue());
            assertThat(JsonPath.read(document, "$.file.indexing_date"), notNullValue());
            expectThrows(PathNotFoundException.class, () -> JsonPath.read(document, "$.file.indexed_chars"));
            assertThat(JsonPath.read(document, "$.file.created"), notNullValue());
            assertThat(JsonPath.read(document, "$.file.last_modified"), notNullValue());
            assertThat(JsonPath.read(document, "$.file.last_accessed"), notNullValue());

            assertThat(JsonPath.read(document, "$.meta.title"), notNullValue());
        }
    }

    @Test
    public void test_filename_analyzer() throws Exception {
        startCrawler();

        // We should have one doc
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()).withESQuery(new ESTermQuery("file.filename", "roottxtfile.txt")), 1L, null);
    }

    /**
     * Test case for #183: https://github.com/dadoonet/fscrawler/issues/183 : Optimize document and folder mappings
     * We want to make sure we can highlight documents even if we don't store fields
     */
    @Test
    public void test_highlight_documents() throws Exception {
        startCrawler();

        // We expect to have one file
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);

        // Let's test highlighting
        ESSearchResponse response = documentService.search(new ESSearchRequest()
                .withIndex(getCrawlerName())
                .withESQuery(new ESMatchQuery("content", "exemplo"))
                .addHighlighter("content"));
        assertThat(response.getTotalHits(), is(1L));

        ESSearchHit hit = response.getHits().get(0);
        assertThat(hit.getHighlightFields(), hasKey("content"));
        assertThat(hit.getHighlightFields().get("content"), iterableWithSize(1));
        assertThat(hit.getHighlightFields().get("content"), hasItem(containsString("<em>exemplo</em>")));
    }
}
