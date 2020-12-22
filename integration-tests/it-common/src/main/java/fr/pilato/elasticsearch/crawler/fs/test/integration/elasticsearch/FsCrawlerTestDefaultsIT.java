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

import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.beans.File;
import fr.pilato.elasticsearch.crawler.fs.beans.Meta;
import fr.pilato.elasticsearch.crawler.fs.client.ESMatchQuery;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchHit;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.client.ESTermQuery;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.junit.Test;

import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.extractFromPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

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
            assertThat(hit.getSourceAsMap().get(Doc.FIELD_NAMES.ATTRIBUTES), nullValue());
        }
    }

    @Test
    public void test_default_metadata() throws Exception {
        startCrawler();

        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
        for (ESSearchHit hit : searchResponse.getHits()) {
            assertThat(hit.getSourceAsMap().get(Doc.FIELD_NAMES.ATTACHMENT), nullValue());

            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.FILENAME), notNullValue());
            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.CONTENT_TYPE), notNullValue());
            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.URL), notNullValue());
            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.FILESIZE), notNullValue());
            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.INDEXING_DATE), notNullValue());
            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.INDEXED_CHARS), nullValue());
            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.CREATED), notNullValue());
            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.LAST_MODIFIED), notNullValue());
            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.LAST_ACCESSED), notNullValue());

            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.META).get(Meta.FIELD_NAMES.TITLE), notNullValue());
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
        ESSearchResponse response = documentService.getClient().search(new ESSearchRequest()
                .withIndex(getCrawlerName())
                .withESQuery(new ESMatchQuery("content", "exemplo"))
                .addHighlighter("content"));
        assertThat(response.getTotalHits(), is(1L));

        ESSearchHit hit = response.getHits().get(0);
        assertThat(hit.getHighlightFields(), hasKey("content"));
        assertThat(hit.getHighlightFields().get("content").getFragments(), arrayWithSize(1));
        assertThat(hit.getHighlightFields().get("content").getFragments()[0], containsString("<em>exemplo</em>"));
    }
}
