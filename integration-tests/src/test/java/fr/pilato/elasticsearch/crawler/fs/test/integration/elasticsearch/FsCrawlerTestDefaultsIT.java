/*
 * Licensed to David Pilato (the "Author") under one
 * or more contributor license agreements. See the NOTICE file
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
 *
 * Made from 🇫🇷🇪🇺 with ❤️ - 2011-2026
 */
package fr.pilato.elasticsearch.crawler.fs.test.integration.elasticsearch;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import fr.pilato.elasticsearch.crawler.fs.client.ESMatchQuery;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchHit;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.client.ESTermQuery;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil;
import fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.assertj.core.api.Assertions;
import org.junit.Test;

/** Test crawler default settings */
public class FsCrawlerTestDefaultsIT extends AbstractFsCrawlerITCase {

    @Test
    public void defaults() throws Exception {
        crawler = startCrawler();

        // We expect to have one file
        ESSearchResponse searchResponse = countTestHelper(
                new ESSearchRequest().withIndex(getCrawlerName() + FsCrawlerUtil.INDEX_SUFFIX_DOCS), 1L, null);

        // The default configuration should not add file attributes
        for (ESSearchHit hit : searchResponse.getHits()) {
            Assertions.assertThatThrownBy(() -> JsonPath.read(hit.getSource(), "$.attributes"))
                    .isInstanceOf(PathNotFoundException.class);
        }
    }

    @Test
    public void default_metadata() throws Exception {
        crawler = startCrawler();

        ESSearchResponse searchResponse = countTestHelper(
                new ESSearchRequest().withIndex(getCrawlerName() + FsCrawlerUtil.INDEX_SUFFIX_DOCS), 1L, null);
        for (ESSearchHit hit : searchResponse.getHits()) {
            DocumentContext document = JsonUtil.parseJsonAsDocumentContext(hit.getSource());
            Assertions.assertThatThrownBy(() -> document.read("$.attachment"))
                    .isInstanceOf(PathNotFoundException.class);

            Assertions.assertThat((String) document.read("$.file.filename")).isNotEmpty();
            Assertions.assertThat((String) document.read("$.file.content_type")).isNotEmpty();
            Assertions.assertThat((String) document.read("$.file.url")).isNotEmpty();
            Assertions.assertThat((Integer) document.read("$.file.filesize")).isGreaterThan(0);
            Assertions.assertThat((String) document.read("$.file.indexing_date"))
                    .isNotEmpty();
            Assertions.assertThatThrownBy(() -> document.read("$.file.indexed_chars"))
                    .isInstanceOf(PathNotFoundException.class);
            Assertions.assertThat((String) document.read("$.file.created")).isNotEmpty();
            Assertions.assertThat((String) document.read("$.file.last_modified"))
                    .isNotEmpty();
            Assertions.assertThat((String) document.read("$.file.last_accessed"))
                    .isNotEmpty();
            Assertions.assertThat((String) document.read("$.meta.title")).isNotEmpty();
        }
    }

    @Test
    public void filename_analyzer() throws Exception {
        crawler = startCrawler();

        // We should have one doc
        ESSearchResponse response = countTestHelper(
                new ESSearchRequest()
                        .withIndex(getCrawlerName() + FsCrawlerUtil.INDEX_SUFFIX_DOCS)
                        .withESQuery(new ESTermQuery("file.filename", "roottxtfile.txt")),
                1L,
                null);
        Assertions.assertThat(response.getTotalHits()).isEqualTo(1);
    }

    /**
     * Test case for #183: <a
     * href="https://github.com/dadoonet/fscrawler/issues/183">https://github.com/dadoonet/fscrawler/issues/183</a> :
     * Optimize document and folder mappings We want to make sure we can highlight documents even if we don't store
     * fields
     */
    @Test
    public void highlight_documents() throws Exception {
        crawler = startCrawler();

        // We expect to have one file
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName() + FsCrawlerUtil.INDEX_SUFFIX_DOCS), 1L, null);

        // Let's test highlighting
        ESSearchResponse response = client.search(new ESSearchRequest()
                .withIndex(getCrawlerName() + FsCrawlerUtil.INDEX_SUFFIX_DOCS)
                .withESQuery(new ESMatchQuery("content", "exemplo"))
                .addHighlighter("content"));
        Assertions.assertThat(response.getTotalHits()).isEqualTo(1L);
        Assertions.assertThat(response.getHits())
                .singleElement()
                .satisfies(hit -> Assertions.assertThat(hit.getHighlightFields())
                        .extractingByKey("content")
                        .satisfies(h -> Assertions.assertThat(h)
                                .singleElement()
                                .satisfies(t -> Assertions.assertThat(t).contains("<em>exemplo</em>"))));
    }
}
