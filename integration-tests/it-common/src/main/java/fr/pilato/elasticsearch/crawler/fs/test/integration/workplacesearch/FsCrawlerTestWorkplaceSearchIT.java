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

package fr.pilato.elasticsearch.crawler.fs.test.integration.workplacesearch;

import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentService;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentServiceWorkplaceSearchImpl;
import fr.pilato.elasticsearch.crawler.fs.settings.Elasticsearch;
import fr.pilato.elasticsearch.crawler.fs.settings.Fs;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.WorkplaceSearch;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

import static fr.pilato.elasticsearch.crawler.fs.settings.Elasticsearch.NODE_DEFAULT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeThat;

/**
 * Test workplace search
 */
public class FsCrawlerTestWorkplaceSearchIT extends AbstractFsCrawlerITCase {

    private FsCrawlerDocumentService oldDocumentService;
    private FsSettings fsSettings;

    @Before
    public void overrideDocumentService() throws IOException {
        oldDocumentService = documentService;
        Fs fs = startCrawlerDefinition().build();
        fsSettings = FsSettings.builder(getCrawlerName())
                .setFs(fs)
                .setElasticsearch(Elasticsearch.builder()
                        .addNode(NODE_DEFAULT)
                        .setUsername("elastic").setPassword("changeme").build())
                .setWorkplaceSearch(WorkplaceSearch.builder()
                        .setAccessToken(testWorkplaceAccessToken)
                        .setKey(testWorkplaceKey)
                        .build())
                .build();
        try {
            documentService = new FsCrawlerDocumentServiceWorkplaceSearchImpl(metadataDir, fsSettings);
            documentService.start();

            logger.info(" -> Removing existing index [.ent-search-engine-*]");
            documentService.getClient().deleteIndex(".ent-search-engine-*");
        } catch (FsCrawlerIllegalConfigurationException e) {
            documentService = oldDocumentService;
            Assume.assumeNoException("We don't have a compatible client for this version of the stack.", e);
        }
    }

    @After
    public void resetDocumentService() throws IOException {
        documentService.close();
        documentService = oldDocumentService;
    }

    @Test
    public void testWorkplaceSearch() throws Exception {
        assumeFalse("Workplace Search credentials not defined. Launch with -Dtests.workplace.access_token=XYZ -Dtests.workplace.key=XYZ",
                testWorkplaceAccessToken == null || testWorkplaceKey == null);

        startCrawler(getCrawlerName(), fsSettings, TimeValue.timeValueSeconds(10));
        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);

        Map<String, Object> source = searchResponse.getHits().get(0).getSourceAsMap();
        assertThat(source, hasEntry(is("path$string"), notNullValue()));
        assertThat(source, hasEntry("extension$string", "txt"));
        assertThat(source, hasEntry("size$float", 12230.0));
        assertThat(source, hasEntry(is("text_size$float"), nullValue()));
        assertThat(source, hasEntry(is("mime_type$string"), notNullValue()));
        assertThat(source, hasEntry("name$string", "roottxtfile.txt"));
        assertThat(source, hasEntry(is("created_at$string"), notNullValue()));
        assertThat(source, hasEntry(is("body$string"), notNullValue()));
        assertThat(source, hasEntry(is("last_modified$string"), notNullValue()));
        assertThat(source, hasEntry("url$string", "file:///roottxtfile.txt"));
    }
}
