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
import fr.pilato.elasticsearch.crawler.fs.settings.ServerUrl;
import fr.pilato.elasticsearch.crawler.fs.settings.WorkplaceSearch;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Map;

import static fr.pilato.elasticsearch.crawler.fs.client.WorkplaceSearchClientUtil.generateDefaultCustomSourceName;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Test workplace search
 */
public class WPSearchIT extends AbstractWorkplaceSearchITCase {
    private String sourceName;

    @BeforeClass
    public static void cleanAllTestResources() {
        // Just for dev only. In case we need to remove tons of workplace search custom sources at once
        // cleanExistingCustomSources("fscrawler_*");
    }

    @Before
    public void generateJobName() {
        sourceName = getRandomCrawlerName();
    }

    @After
    public void cleanUpCustomSource() {
        if (sourceName != null) {
            cleanExistingCustomSources(sourceName);
        }
    }

    @Test
    public void testWorkplaceSearch() throws Exception {
        String crawlerName = sourceName;
        Fs fs = startCrawlerDefinition().build();
        FsSettings fsSettings = FsSettings.builder(crawlerName)
                .setFs(fs)
                .setElasticsearch(Elasticsearch.builder()
                        .addNode(new ServerUrl(testClusterUrl))
                        .setUsername(testClusterUser)
                        .setPassword(testClusterPass)
                        .build())
                .setWorkplaceSearch(WorkplaceSearch.builder()
                        .setServer(new ServerUrl(testWorkplaceUrl))
                        .setBulkSize(1)
                        .setFlushInterval(TimeValue.timeValueSeconds(1))
                        .build())
                .build();
        sourceName = generateDefaultCustomSourceName(crawlerName);

        try (FsCrawlerDocumentService documentService = new FsCrawlerDocumentServiceWorkplaceSearchImpl(metadataDir, fsSettings)) {
            documentService.start();

            String customSourceId = getSourceIdFromSourceName(sourceName);
            assertThat("Custom source id should be found for source " + sourceName, customSourceId, notNullValue());

            startCrawler(documentService, crawlerName, customSourceId, fsSettings, TimeValue.timeValueSeconds(10));
            ESSearchResponse searchResponse = countTestHelper(documentService, new ESSearchRequest().withIndex(".ent-search-engine-documents-source-" + customSourceId),
                    1L, null, TimeValue.timeValueSeconds(20));

            Map<String, Object> source = searchResponse.getHits().get(0).getSourceAsMap();
            assertThat(source, hasEntry(is("path"), notNullValue()));
            assertThat(source, hasEntry("extension", "txt"));
            assertThat(source, hasKey(startsWith("size")));
            assertThat(source, hasKey(startsWith("text_size")));
            assertThat(source, hasEntry(is("mime_type"), notNullValue()));
            assertThat(source, hasEntry("name", "roottxtfile.txt"));
            assertThat(source, hasEntry(is("created_at"), notNullValue()));
            assertThat(source, hasEntry(is("body"), notNullValue()));
            assertThat(source, hasEntry(is("last_modified"), notNullValue()));
            assertThat(source, hasEntry("url", "http://127.0.0.1/roottxtfile.txt"));
        } catch (FsCrawlerIllegalConfigurationException e) {
            Assume.assumeNoException("We don't have a compatible client for this version of the stack.", e);
        }
    }

    @Test
    public void testWorkplaceSearchWithCustomSourceId() throws Exception {
        String customSourceId = initSource(sourceName);
        String crawlerName = getRandomCrawlerName();
        Fs fs = startCrawlerDefinition().build();
        FsSettings fsSettings = FsSettings.builder(crawlerName)
                .setFs(fs)
                .setElasticsearch(Elasticsearch.builder()
                        .addNode(new ServerUrl(testClusterUrl))
                        .setUsername(testClusterUser)
                        .setPassword(testClusterPass)
                        .build())
                .setWorkplaceSearch(WorkplaceSearch.builder()
                        .setServer(new ServerUrl(testWorkplaceUrl))
                        .setId(customSourceId)
                        .setBulkSize(1)
                        .setFlushInterval(TimeValue.timeValueSeconds(1))
                        .build())
                .build();
        try (FsCrawlerDocumentService documentService = new FsCrawlerDocumentServiceWorkplaceSearchImpl(metadataDir, fsSettings)) {
            documentService.start();

            startCrawler(documentService, crawlerName, customSourceId, fsSettings, TimeValue.timeValueSeconds(10));
            ESSearchResponse searchResponse = countTestHelper(documentService, new ESSearchRequest().withIndex(".ent-search-engine-documents-source-" + customSourceId),
                    1L, null, TimeValue.timeValueSeconds(20));

            Map<String, Object> source = searchResponse.getHits().get(0).getSourceAsMap();
            assertThat(source, hasEntry(is("path"), notNullValue()));
            assertThat(source, hasEntry("extension", "txt"));
            assertThat(source, hasKey(startsWith("size")));
            assertThat(source, hasKey(startsWith("text_size")));
            assertThat(source, hasEntry(is("mime_type"), notNullValue()));
            assertThat(source, hasEntry("name", "roottxtfile.txt"));
            assertThat(source, hasEntry(is("created_at"), notNullValue()));
            assertThat(source, hasEntry(is("body"), notNullValue()));
            assertThat(source, hasEntry(is("last_modified"), notNullValue()));
            assertThat(source, hasEntry("url", "http://127.0.0.1/roottxtfile.txt"));
        } catch (FsCrawlerIllegalConfigurationException e) {
            Assume.assumeNoException("We don't have a compatible client for this version of the stack.", e);
        }
    }

    @Test
    public void testWorkplaceSearchWithCustomSourceName() throws Exception {
        Fs fs = startCrawlerDefinition().build();
        FsSettings fsSettings = FsSettings.builder(getCrawlerName())
                .setFs(fs)
                .setElasticsearch(Elasticsearch.builder()
                        .addNode(new ServerUrl(testClusterUrl))
                        .setUsername(testClusterUser)
                        .setPassword(testClusterPass)
                        .build())
                .setWorkplaceSearch(WorkplaceSearch.builder()
                        .setServer(new ServerUrl(testWorkplaceUrl))
                        .setName(sourceName)
                        .setBulkSize(1)
                        .setFlushInterval(TimeValue.timeValueSeconds(1))
                        .build())
                .build();
        try (FsCrawlerDocumentService documentService = new FsCrawlerDocumentServiceWorkplaceSearchImpl(metadataDir, fsSettings)) {
            documentService.start();

            String customSourceId = getSourceIdFromSourceName(sourceName);
            assertThat("Custom source id should be found for source " + sourceName, customSourceId, notNullValue());

            startCrawler(documentService, getCrawlerName(), customSourceId, fsSettings, TimeValue.timeValueSeconds(10));
            ESSearchResponse searchResponse = countTestHelper(documentService, new ESSearchRequest()
                            .withIndex(".ent-search-engine-documents-source-" + customSourceId),
                    1L, null, TimeValue.timeValueSeconds(20));

            Map<String, Object> source = searchResponse.getHits().get(0).getSourceAsMap();
            assertThat(source, hasEntry(is("path"), notNullValue()));
            assertThat(source, hasEntry("extension", "txt"));
            assertThat(source, hasKey(startsWith("size")));
            assertThat(source, hasKey(startsWith("text_size")));
            assertThat(source, hasEntry(is("mime_type"), notNullValue()));
            assertThat(source, hasEntry("name", "roottxtfile.txt"));
            assertThat(source, hasEntry(is("created_at"), notNullValue()));
            assertThat(source, hasEntry(is("body"), notNullValue()));
            assertThat(source, hasEntry(is("last_modified"), notNullValue()));
            assertThat(source, hasEntry("url", "http://127.0.0.1/roottxtfile.txt"));
        } catch (FsCrawlerIllegalConfigurationException e) {
            Assume.assumeNoException("We don't have a compatible client for this version of the stack.", e);
        }
    }
}
