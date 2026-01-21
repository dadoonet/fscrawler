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
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.junit.Test;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.INDEX_SUFFIX_DOCS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test store_source crawler setting
 */
public class FsCrawlerTestStoreSourceIT extends AbstractFsCrawlerITCase {

    @Test
    public void store_source() throws Exception {
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setStoreSource(true);
        crawler = startCrawler(fsSettings);

        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS), 1L, null);
        for (ESSearchHit hit : searchResponse.getHits()) {
            // We check that the field is in _source
            assertThat((String) JsonPath.read(hit.getSource(), "$.attachment")).isNotEmpty();
        }
    }

    @Test
    public void do_not_store_source() throws Exception {
        crawler = startCrawler();

        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS), 1L, null);

        ESSearchResponse searchResponse = client.search(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS));
        for (ESSearchHit hit : searchResponse.getHits()) {
            // We check that the field is not part of _source
            assertThatThrownBy(() -> JsonPath.read(hit.getSource(), "$.attachment")).isInstanceOf(PathNotFoundException.class);
        }
    }

    @Test
    public void store_source_no_index_content() throws Exception {
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setStoreSource(true);
        fsSettings.getFs().setIndexContent(false);
        crawler = startCrawler(fsSettings);

        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS), 1L, null);
        for (ESSearchHit hit : searchResponse.getHits()) {
            // We check that the field is in _source
            assertThat((String) JsonPath.read(hit.getSource(), "$.attachment")).isNotEmpty();
        }
    }
}
