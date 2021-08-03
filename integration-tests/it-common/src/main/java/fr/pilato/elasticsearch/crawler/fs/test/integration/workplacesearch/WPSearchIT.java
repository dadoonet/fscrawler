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

import com.carrotsearch.randomizedtesting.RandomizedTest;
import com.jayway.jsonpath.JsonPath;
import fr.pilato.elasticsearch.crawler.fs.client.ESBoolQuery;
import fr.pilato.elasticsearch.crawler.fs.client.ESMatchQuery;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchHit;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.client.ESTermQuery;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentService;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentServiceWorkplaceSearchImpl;
import fr.pilato.elasticsearch.crawler.fs.settings.Elasticsearch;
import fr.pilato.elasticsearch.crawler.fs.settings.Fs;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.Server;
import fr.pilato.elasticsearch.crawler.fs.settings.ServerUrl;
import fr.pilato.elasticsearch.crawler.fs.settings.WorkplaceSearch;
import fr.pilato.elasticsearch.crawler.fs.thirdparty.wpsearch.WPSearchClient;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static fr.pilato.elasticsearch.crawler.fs.client.WorkplaceSearchClientUtil.generateDefaultCustomSourceName;
import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.parseJson;
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
                        .setNodes(Collections.singletonList(new ServerUrl(testClusterUrl)))
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

            startCrawler(crawlerName, fsSettings, TimeValue.timeValueSeconds(10));
            try (WPSearchClient client = createClient()) {
                // We need to wait until it's done
                String json = countTestHelper(client, 1L, TimeValue.timeValueSeconds(1));
                Object document = parseJson(json);
                // We can check the meta data to check the custom source id
                assertThat(JsonPath.read(document, "$.results[0]._meta.content_source_id"), is(customSourceId));

                // We can check the content
                assertThat(JsonPath.read(document, "$.results[0].title.raw"), is("roottxtfile.txt"));
                assertThat(JsonPath.read(document, "$.results[0].body.raw"), containsString("Gallienus"));
                assertThat(JsonPath.read(document, "$.results[0].size.raw"), notNullValue());
                assertThat(JsonPath.read(document, "$.results[0].text_size.raw"), nullValue());
                assertThat(JsonPath.read(document, "$.results[0].mime_type.raw"), startsWith("text/plain"));
                assertThat(JsonPath.read(document, "$.results[0].name.raw"), is("roottxtfile.txt"));
                assertThat(JsonPath.read(document, "$.results[0].extension.raw"), is("txt"));
                assertThat(JsonPath.read(document, "$.results[0].path.raw"), endsWith("roottxtfile.txt"));
                assertThat(JsonPath.read(document, "$.results[0].url.raw"), is("http://127.0.0.1/roottxtfile.txt"));
                assertThat(JsonPath.read(document, "$.results[0].created_at.raw"), notNullValue());
                assertThat(JsonPath.read(document, "$.results[0].last_modified.raw"), notNullValue());
            }
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
                        .setNodes(Collections.singletonList(new ServerUrl(testClusterUrl)))
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

            startCrawler(crawlerName, fsSettings, TimeValue.timeValueSeconds(10));
            try (WPSearchClient client = createClient()) {
                // We need to wait until it's done
                String json = countTestHelper(client, 1L, TimeValue.timeValueSeconds(1));
                Object document = parseJson(json);
                // We can check the meta data to check the custom source id
                assertThat(JsonPath.read(document, "$.results[0]._meta.content_source_id"), is(customSourceId));

                // We can check the content
                assertThat(JsonPath.read(document, "$.results[0].title.raw"), is("roottxtfile.txt"));
                assertThat(JsonPath.read(document, "$.results[0].body.raw"), containsString("Gallienus"));
                assertThat(JsonPath.read(document, "$.results[0].size.raw"), notNullValue());
                assertThat(JsonPath.read(document, "$.results[0].text_size.raw"), nullValue());
                assertThat(JsonPath.read(document, "$.results[0].mime_type.raw"), startsWith("text/plain"));
                assertThat(JsonPath.read(document, "$.results[0].name.raw"), is("roottxtfile.txt"));
                assertThat(JsonPath.read(document, "$.results[0].extension.raw"), is("txt"));
                assertThat(JsonPath.read(document, "$.results[0].path.raw"), endsWith("roottxtfile.txt"));
                assertThat(JsonPath.read(document, "$.results[0].url.raw"), is("http://127.0.0.1/roottxtfile.txt"));
                assertThat(JsonPath.read(document, "$.results[0].created_at.raw"), notNullValue());
                assertThat(JsonPath.read(document, "$.results[0].last_modified.raw"), notNullValue());
            }
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
                        .setNodes(Collections.singletonList(new ServerUrl(testClusterUrl)))
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

            startCrawler(getCrawlerName(), fsSettings, TimeValue.timeValueSeconds(10));
            try (WPSearchClient client = createClient()) {
                // We need to wait until it's done
                String json = countTestHelper(client, 1L, TimeValue.timeValueSeconds(1));
                Object document = parseJson(json);
                // We can check the meta data to check the custom source id
                assertThat(JsonPath.read(document, "$.results[0]._meta.content_source_id"), is(customSourceId));

                // We can check the content
                assertThat(JsonPath.read(document, "$.results[0].title.raw"), is("roottxtfile.txt"));
                assertThat(JsonPath.read(document, "$.results[0].body.raw"), containsString("Gallienus"));
                assertThat(JsonPath.read(document, "$.results[0].size.raw"), notNullValue());
                assertThat(JsonPath.read(document, "$.results[0].text_size.raw"), nullValue());
                assertThat(JsonPath.read(document, "$.results[0].mime_type.raw"), startsWith("text/plain"));
                assertThat(JsonPath.read(document, "$.results[0].name.raw"), is("roottxtfile.txt"));
                assertThat(JsonPath.read(document, "$.results[0].extension.raw"), is("txt"));
                assertThat(JsonPath.read(document, "$.results[0].path.raw"), endsWith("roottxtfile.txt"));
                assertThat(JsonPath.read(document, "$.results[0].url.raw"), is("http://127.0.0.1/roottxtfile.txt"));
                assertThat(JsonPath.read(document, "$.results[0].created_at.raw"), notNullValue());
                assertThat(JsonPath.read(document, "$.results[0].last_modified.raw"), notNullValue());
            }
        } catch (FsCrawlerIllegalConfigurationException e) {
            Assume.assumeNoException("We don't have a compatible client for this version of the stack.", e);
        }
    }

    @Test
    public void testCrud() throws Exception {
        String crawlerName = sourceName;
        Fs fs = startCrawlerDefinition().build();
        FsSettings fsSettings = FsSettings.builder(crawlerName)
                .setFs(fs)
                .setElasticsearch(Elasticsearch.builder()
                        .setNodes(Collections.singletonList(new ServerUrl(testClusterUrl)))
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

            String id = RandomizedTest.randomAsciiLettersOfLength(10);

            documentService.index(null, id, fakeDocument("Foo", "EN", "foo", "Foo"), null);
            documentService.flush();

            try (WPSearchClient client = createClient()) {
                // We need to wait until it's done
                countTestHelper(client, 1L, TimeValue.timeValueSeconds(5));
            }

            assertThat(documentService.exists(null, id), is(true));
            assertThat(documentService.exists(null, "nonexistingid"), is(false));
            ESSearchHit hit = documentService.get(null, id);

            assertThat(hit, notNullValue());
            Object document = parseJson(hit.getSourceAsString());
            documentChecker(document, List.of("foo.txt"), List.of("Foo"));
        } catch (FsCrawlerIllegalConfigurationException e) {
            Assume.assumeNoException("We don't have a compatible client for this version of the stack.", e);
        }
    }

    @Test
    public void testSearch() throws Exception {
        String crawlerName = sourceName;
        Fs fs = startCrawlerDefinition().build();
        FsSettings fsSettings = FsSettings.builder(crawlerName)
                .setFs(fs)
                .setElasticsearch(Elasticsearch.builder()
                        .setNodes(Collections.singletonList(new ServerUrl(testClusterUrl)))
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

            String id = RandomizedTest.randomAsciiLettersOfLength(10);

            documentService.index(null, id, fakeDocument("Foo", "EN", "foo", "Foo"), null);
            documentService.index(null, RandomizedTest.randomAsciiLettersOfLength(10), fakeDocument("Bar", "FR", "bar", "Bar"), null);
            documentService.index(null, RandomizedTest.randomAsciiLettersOfLength(10), fakeDocument("Baz", "DE", "baz", "Baz"), null);
            documentService.index(null, RandomizedTest.randomAsciiLettersOfLength(10), fakeDocument("Foo Bar Baz", "EN", "foobarbaz", "Foo", "Bar", "Baz"), null);

            try (WPSearchClient client = createClient()) {
                // We need to wait until it's done
                countTestHelper(client, 4L, TimeValue.timeValueSeconds(5));
            }

            {
                // Search for all
                ESSearchResponse response = documentService.search(new ESSearchRequest());
                assertThat(response.getTotalHits(), is(4L));
                assertThat(response.getHits(), hasSize(4));
                for (ESSearchHit hit : response.getHits()) {
                    assertThat(hit, notNullValue());
                    documentChecker(hit.getSourceAsObject(),
                            Arrays.asList("foo.txt", "bar.txt", "baz.txt", "foobarbaz.txt"),
                            Arrays.asList("Foo", "Bar", "Baz", "Foo Bar Baz"));
                }
            }

            {
                // Search for full text (we don't specify a field name)
                ESSearchResponse response = documentService.search(new ESSearchRequest().withESQuery(
                        new ESMatchQuery(null, "foo")
                ));
                assertThat(response.getTotalHits(), is(2L));
                assertThat(response.getHits(), hasSize(2));
                for (ESSearchHit hit : response.getHits()) {
                    assertThat(hit, notNullValue());
                    documentChecker(hit.getSourceAsObject(),
                            Arrays.asList("foo.txt", "foobarbaz.txt"),
                            Arrays.asList("Foo", "Foo Bar Baz"));
                }
            }

            {
                // Filter (we specify a field name within a term query)
                ESSearchResponse response = documentService.search(new ESSearchRequest().withESQuery(
                        new ESTermQuery("author", "Mister Foo")
                ));
                assertThat(response.getTotalHits(), is(1L));
                assertThat(response.getHits(), hasSize(1));
                for (ESSearchHit hit : response.getHits()) {
                    assertThat(hit, notNullValue());
                    documentChecker(hit.getSourceAsObject(),
                            Arrays.asList("foo.txt", "foobarbaz.txt"),
                            Arrays.asList("Foo", "Foo Bar Baz"));
                }
            }

            {
                // Filter (we specify a field name within a term query)
                ESSearchResponse response = documentService.search(new ESSearchRequest().withESQuery(
                        new ESTermQuery("language", "EN")
                ));
                assertThat(response.getTotalHits(), is(2L));
                assertThat(response.getHits(), hasSize(2));
                for (ESSearchHit hit : response.getHits()) {
                    assertThat(hit, notNullValue());
                    documentChecker(hit.getSourceAsObject(),
                            Arrays.asList("foo.txt", "foobarbaz.txt"),
                            Arrays.asList("Foo", "Foo Bar Baz"));
                }
            }

            {
                // Filter (we specify a field name within 2 terms query wrapped in a bool query)
                ESSearchResponse response = documentService.search(new ESSearchRequest().withESQuery(
                        new ESBoolQuery()
                                .addMust(new ESTermQuery("language", "EN"))
                                .addMust(new ESTermQuery("author", "Mister Foo"))
                ));
                assertThat(response.getTotalHits(), is(1L));
                assertThat(response.getHits(), hasSize(1));
                for (ESSearchHit hit : response.getHits()) {
                    assertThat(hit, notNullValue());
                    documentChecker(hit.getSourceAsObject(),
                            Arrays.asList("foo.txt", "foobarbaz.txt"),
                            Arrays.asList("Foo", "Foo Bar Baz"));
                }
            }

            {
                // Full text with filters
                ESSearchResponse response = documentService.search(new ESSearchRequest().withESQuery(
                        new ESBoolQuery()
                                .addMust(new ESTermQuery("language", "EN"))
                                .addMust(new ESMatchQuery(null, "title"))
                                .addMust(new ESTermQuery("author", "Mister Foo"))
                ));
                assertThat(response.getTotalHits(), is(1L));
                assertThat(response.getHits(), hasSize(1));
                for (ESSearchHit hit : response.getHits()) {
                    assertThat(hit, notNullValue());
                    documentChecker(hit.getSourceAsObject(),
                            Arrays.asList("foo.txt", "foobarbaz.txt"),
                            Arrays.asList("Foo", "Foo Bar Baz"));
                }
            }

        } catch (FsCrawlerIllegalConfigurationException e) {
            Assume.assumeNoException("We don't have a compatible client for this version of the stack.", e);
        }
    }

    /**
     * You have to adapt this test to your own system (login / password and SSH connexion)
     * So this test is disabled by default
     */
    @Test @Ignore
    public void test_ssh() throws Exception {
        String username = "USERNAME";
        String password = "PASSWORD";
        String hostname = "localhost";

        String crawlerName = sourceName;
        Fs fs = startCrawlerDefinition().build();
        FsSettings fsSettings = FsSettings.builder(crawlerName)
                .setFs(fs)
                .setServer(Server.builder()
                        .setHostname(hostname)
                        .setUsername(username)
                        .setPassword(password)
                        .setProtocol(Server.PROTOCOL.SSH)
                        .build())
                .setElasticsearch(Elasticsearch.builder()
                        .setNodes(Collections.singletonList(new ServerUrl(testClusterUrl)))
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

            startCrawler(crawlerName, fsSettings, TimeValue.timeValueSeconds(10));
            try (WPSearchClient client = createClient()) {
                // We need to wait until it's done
                String json = countTestHelper(client, 2L, TimeValue.timeValueSeconds(1));
                Object document = parseJson(json);
                // We can check the meta data to check the custom source id
                assertThat(JsonPath.read(document, "$.results[0]._meta.content_source_id"), is(customSourceId));

                // We can check the content
                assertThat(JsonPath.read(document, "$.results[*].title.raw"), hasItem(isOneOf("roottxtfile.txt", "roottxtfile_multi_feed.txt")));
                assertThat(JsonPath.read(document, "$.results[*].body.raw"), hasItems(containsString("This file contains some words"), containsString("multi feed crawlers")));
                assertThat(JsonPath.read(document, "$.results[*].size.raw"), hasItem(notNullValue()));
                assertThat(JsonPath.read(document, "$.results[*].text_size.raw"), hasItem(nullValue()));
                assertThat(JsonPath.read(document, "$.results[*].mime_type.raw"), hasItem(startsWith("text/plain")));
                assertThat(JsonPath.read(document, "$.results[*].name.raw"), hasItem(isOneOf("roottxtfile.txt", "roottxtfile_multi_feed.txt")));
                assertThat(JsonPath.read(document, "$.results[*].extension.raw"), hasItem("txt"));
                Arrays.asList("roottxtfile.txt", "roottxtfile_multi_feed.txt").forEach((filename) -> assertThat(JsonPath.read(document, "$.results[*].path.raw"), hasItem(endsWith(filename))));
                assertThat(JsonPath.read(document, "$.results[*].url.raw"), hasItem(isOneOf("http://127.0.0.1/roottxtfile.txt", "http://127.0.0.1/subdir/roottxtfile_multi_feed.txt")));
                assertThat(JsonPath.read(document, "$.results[*].created_at.raw"), hasItem(nullValue()));
                assertThat(JsonPath.read(document, "$.results[*].last_modified.raw"), hasItem(notNullValue()));
            }
        } catch (FsCrawlerIllegalConfigurationException e) {
            Assume.assumeNoException("We don't have a compatible client for this version of the stack.", e);
        }
    }
}
