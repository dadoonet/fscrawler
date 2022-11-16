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
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.thirdparty.wpsearch.WPSearchClient;
import jakarta.ws.rs.ProcessingException;
import org.junit.Test;

import java.net.ConnectException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.extractMajorVersion;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.readPropertiesFromClassLoader;
import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.parseJsonAsDocumentContext;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.fail;

/**
 * Test Workplace Search HTTP client
 */
public class WPSearchClientIT extends AbstractWorkplaceSearchITCase {

    @Test
    public void testGetVersion() {
        try (WPSearchClient client = createClient()) {
            String version = client.getVersion();
            logger.info("Current workplace search version: [{}]", version);

            // If we did not use an external URL but the docker instance we can test for sure that the version is the expected one
            if (System.getProperty("tests.cluster.url") == null) {
                Properties properties = readPropertiesFromClassLoader("elasticsearch.version.properties");
                assertThat(version, is(properties.getProperty("version")));
            }
        }
    }

    @Test
    public void testNonRunningService() {
        try {
            String nonExistingService = "http://127.0.0.1:3012";
            createClient(nonExistingService);
            fail("We should have raised an exception as the service " + nonExistingService + " should not be running");
        } catch (ProcessingException e) {
            assertThat(e.getCause(), instanceOf(ConnectException.class));
        }
    }

    @Test
    public void testGetSourceById() throws Exception {
        try (WPSearchClient client = createClient()) {
            // We first create a source, so we can use it later.
            String id = client.createCustomSource(sourceName);

            // This is what we want to test actually
            String source = client.getCustomSourceById(id);
            assertThat(source, not(isEmptyOrNullString()));
        }
    }

    @Test
    public void testGetSourceByName() throws Exception {
        try (WPSearchClient client = createClient()) {
            // We first create a source, so we can use it later.
            String id = client.createCustomSource(sourceName);

            // This is what we want to test actually
            List<String> sourceIds = client.getCustomSourcesByName(sourceName);
            assertThat(sourceIds, hasSize(1));
            assertThat(id, isIn(sourceIds));
        }
    }

    @Test
    public void testWithSomeFakeDocuments() throws Exception {
        try (WPSearchClient client = createClient()) {
            // We configure the custom source.
            String customSourceId = client.createCustomSource(sourceName);
            client.configureCustomSource(customSourceId, sourceName);

            // Index some documents
            client.indexDocument(fakeDocumentAsMap(RandomizedTest.randomAsciiLettersOfLength(10), "Foo", "EN", "foo", "Foo"));
            client.indexDocument(fakeDocumentAsMap(RandomizedTest.randomAsciiLettersOfLength(10), "Bar", "FR", "bar", "Bar"));
            client.indexDocument(fakeDocumentAsMap(RandomizedTest.randomAsciiLettersOfLength(10), "Baz", "DE", "baz", "Baz"));
            client.indexDocument(fakeDocumentAsMap(RandomizedTest.randomAsciiLettersOfLength(10), "Foo Bar Baz", "EN", "foobarbaz", "Foo", "Bar", "Baz"));

            // We need to wait until it's done
            String json = countTestHelper(client, customSourceId, 4L, TimeValue.timeValueSeconds(5));

            // We read the ids of the documents, so we can remove them then
            List<String> ids = JsonPath.read(json, "$.results[*].id.raw");

            // Search for some specific use cases
            checker(client.search("Foo", null), 2,
                    Arrays.asList("foo.txt", "foobarbaz.txt"),
                    Arrays.asList("Foo", "Foo Bar Baz"));
            checker(client.search("Bar", null), 3, // 3 because of fuzziness, we are getting back Baz as well
                    Arrays.asList("bar.txt", "baz.txt", "foobarbaz.txt"),
                    Arrays.asList("Bar", "Baz", "Foo Bar Baz"));
            checker(client.search("Baz", null), 3, // 3 because of fuzziness, we are getting back Bar as well
                    Arrays.asList("bar.txt", "baz.txt", "foobarbaz.txt"),
                    Arrays.asList("Bar", "Baz", "Foo Bar Baz"));
            checker(client.search("Foo Bar Baz", null), 3, // 3 because Foo is meaningless apparently
                    Arrays.asList("bar.txt", "baz.txt", "foobarbaz.txt"),
                    Arrays.asList("Bar", "Baz", "Foo Bar Baz"));

            for (int i = 0; i < ids.size(); i++) {
                // Let's remove one document and wait until it's done
                logger.info("   --> removing one document");
                client.destroyDocument(ids.get(i));
                countTestHelper(client, customSourceId, Long.valueOf(ids.size() - 1 - i), TimeValue.timeValueSeconds(5));
            }
        }
    }

    @Test
    public void testGetDocument() throws Exception {
        try (WPSearchClient client = createClient()) {
            // We configure the custom source.
            String customSourceId = client.createCustomSource(sourceName);
            client.configureCustomSource(customSourceId, sourceName);

            String id = RandomizedTest.randomAsciiLettersOfLength(10);

            // Index a document
            client.indexDocument(fakeDocumentAsMap(id, "Foo", "EN", "foo", "Foo"));

            // We need to wait until it's done
            countTestHelper(client, customSourceId, 1L, TimeValue.timeValueSeconds(5));

            // We can now get the document
            documentChecker(parseJsonAsDocumentContext(client.getDocument(id)), "$", List.of("foo.txt"), List.of("Foo"));

            // Get a non existing document
            assertThat(client.getDocument("thisiddoesnotexist"), nullValue());
        }
    }

    @Test
    public void testSearch() throws Exception {
        try (WPSearchClient client = createClient()) {
            // We first create a source, so we can use it later.
            String customSourceId = client.createCustomSource(sourceName);
            client.configureCustomSource(customSourceId, sourceName);

            String uniqueId1 = RandomizedTest.randomAsciiLettersOfLength(10);
            {
                Map<String, Object> document = new HashMap<>();
                document.put("id", uniqueId1);
                document.put("language", "EN");
                document.put("title", "To be searched " + uniqueId1);
                document.put("body", "Foo Bar Baz " + uniqueId1);
                client.indexDocument(document);
            }

            String uniqueId2 = RandomizedTest.randomAsciiLettersOfLength(10);
            {
                Map<String, Object> document = new HashMap<>();
                document.put("id", uniqueId2);
                document.put("language", "FR");
                document.put("title", "To be searched " + uniqueId2);
                document.put("body", "Foo Bar Baz " + uniqueId2);
                client.indexDocument(document);
            }

            String uniqueId3 = RandomizedTest.randomAsciiLettersOfLength(10);
            {
                Map<String, Object> document = new HashMap<>();
                document.put("id", uniqueId3);
                document.put("language", "DE");
                document.put("title", "To be searched " + uniqueId3);
                document.put("body", "Foo Bar Baz " + uniqueId3);
                client.indexDocument(document);
            }

            // We need to wait until it's done
            String indexPrefix = ".ent-search-engine-documents-custom-";
            if (extractMajorVersion(client.getVersion()) < 8) {
                // With versions before 8.0, the index name was .ent-search-engine-documents-source-*
                indexPrefix = ".ent-search-engine-documents-source-";
            }

            countTestHelper(new ESSearchRequest().withIndex(indexPrefix + customSourceId), 3L, null);

            // Search using fulltext search
            {
                String json = client.search("Foo Bar", null);
                List<String> ids = JsonPath.read(json, "$.results[*].id.raw");
                assertThat(ids, hasSize(3));
            }
            // Search using fulltext search for document 1
            {
                String json = client.search(uniqueId1, null);
                List<String> ids = JsonPath.read(json, "$.results[*].id.raw");
                assertThat(ids, hasSize(1));
                assertThat(ids.get(0), is(uniqueId1));
            }
            // Search using a filter
            {
                Map<String, Object> filters = new HashMap<>();
                filters.put("language", Collections.singletonList("FR"));
                String json = client.search(null, filters);
                List<String> ids = JsonPath.read(json, "$.results[*].id.raw");
                assertThat(ids, hasSize(1));
                assertThat(ids.get(0), is(uniqueId2));
            }
            // Search using both a query and a filter
            {
                Map<String, Object> filters = new HashMap<>();
                filters.put("language", Collections.singletonList("DE"));
                String json = client.search("Foo Bar", filters);
                List<String> ids = JsonPath.read(json, "$.results[*].id.raw");
                assertThat(ids, hasSize(1));
                assertThat(ids.get(0), is(uniqueId3));
            }
        }
    }

    @Test
    public void testSendAndRemoveADocument() throws Exception {
        try (WPSearchClient client = createClient()) {
            // We first create a source, so we can use it later.
            String customSourceId = client.createCustomSource(sourceName);
            client.configureCustomSource(customSourceId, sourceName);

            Map<String, Object> document = new HashMap<>();
            document.put("id", "testSendAndRemoveADocument");
            document.put("title", "To be deleted " + RandomizedTest.randomAsciiLettersOfLength(10));
            client.indexDocument(document);
            client.destroyDocument("testSendAndRemoveADocument");
        }
    }

}
