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

import com.jayway.jsonpath.DocumentContext;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.client.ESSemanticQuery;
import fr.pilato.elasticsearch.crawler.fs.client.ESTermQuery;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.junit.Test;

import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.parseJsonAsDocumentContext;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assume.assumeTrue;

/**
 * Test if semantic search is working as we could expect, ie: not activated when it can't or
 * activated when it should.
 */
public class FsCrawlerTestSemanticIT extends AbstractFsCrawlerITCase {

    /**
     * Test for #1996: <a href="https://github.com/dadoonet/fscrawler/pull/1996">https://github.com/dadoonet/fscrawler/pull/1996</a>
     */
    @Test
    public void test_semantic() throws Exception {
        // We will execute this test from version 8.17 with a trial or enterprise license
        assumeTrue("We don't run this test when semantic search is not available",
                managementService.getClient().isSemanticSupported());

        FsSettings fsSettings = createTestSettings();
        fsSettings.getElasticsearch().setSemanticSearch(true);
        crawler = startCrawler(fsSettings, TimeValue.timeValueMinutes(5));

        // We expect to have 3 files
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 3L, null);

        // 2 pdf and 1 txt
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()).withESQuery(new ESTermQuery("file.extension", "pdf")), 2L, null);
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()).withESQuery(new ESTermQuery("file.extension", "txt")), 1L, null);

        // We should have semantic information
        ESSearchResponse response = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName())
                .withESQuery(new ESSemanticQuery("content_semantic", "Someone understanding loans and finances")),
                3L, null);
        DocumentContext document = parseJsonAsDocumentContext(response.getHits().get(0).getSource());
        assertThat(document.read("$.file.filename"), is("3547447.pdf"));
    }
}
