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
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.parseJson;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Test Workplace Search HTTP client
 */
public class WPSearchClientIT extends AbstractWorkplaceSearchITCase {
    private static final String SOURCE_NAME = "fscrawler-wpsearch-client";

    @Before
    @After
    public void cleanUpCustomSource() {
        cleanExistingCustomSources(SOURCE_NAME);
    }

    @Test
    public void testGetSourceById() throws Exception {
        try (WPSearchClient client = createClient()) {
            // We first create a source so we can use it later.
            String id = client.createCustomSource(SOURCE_NAME);

            // This is what we want to test actually
            String source = client.getCustomSourceById(id);
            assertThat(source, not(isEmptyOrNullString()));
        }
    }

    @Test
    public void testGetSourceByName() throws Exception {
        try (WPSearchClient client = createClient()) {
            // We first create a source so we can use it later.
            String id = client.createCustomSource(SOURCE_NAME);

            // This is what we want to test actually
            List<String> sourceIds = client.getCustomSourcesByName(SOURCE_NAME);
            assertThat(sourceIds, hasSize(1));
            assertThat(id, isIn(sourceIds));
        }
    }

    @Test
    public void testWithSomeFakeDocuments() throws Exception {
        try (WPSearchClient client = createClient()) {
            // We configure the custom source.
            String customSourceId = client.createCustomSource(SOURCE_NAME);
            client.configureCustomSource(customSourceId, SOURCE_NAME);

            // Index some documents
            client.indexDocument(fakeDocument(RandomizedTest.randomAsciiLettersOfLength(10), "Foo", "EN", "foo", "Foo"));
            client.indexDocument(fakeDocument(RandomizedTest.randomAsciiLettersOfLength(10), "Bar", "FR", "bar", "Bar"));
            client.indexDocument(fakeDocument(RandomizedTest.randomAsciiLettersOfLength(10), "Baz", "DE", "baz", "Baz"));
            client.indexDocument(fakeDocument(RandomizedTest.randomAsciiLettersOfLength(10), "Foo Bar Baz", "EN", "foobarbaz", "Foo", "Bar", "Baz"));

            // We need to wait until it's done
            String json = countTestHelper(client, 4L, TimeValue.timeValueSeconds(5));

            // We read the ids of the documents so we can remove them then
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
                client.destroyDocument(customSourceId, ids.get(i));
                countTestHelper(client, Long.valueOf(ids.size() - 1 - i), TimeValue.timeValueSeconds(5));
            }
        }
    }

    @Test
    public void testSearch() throws Exception {
        try (WPSearchClient client = createClient()) {
            // We first create a source so we can use it later.
            String customSourceId = client.createCustomSource(SOURCE_NAME);
            client.configureCustomSource(customSourceId, SOURCE_NAME);

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
            countTestHelper(new ESSearchRequest().withIndex(".ent-search-engine-documents-source-" + customSourceId), 3L, null);

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
                Map<String, List<String>> filters = new HashMap<>();
                filters.put("language", Collections.singletonList("FR"));
                String json = client.search(null, filters);
                List<String> ids = JsonPath.read(json, "$.results[*].id.raw");
                assertThat(ids, hasSize(1));
                assertThat(ids.get(0), is(uniqueId2));
            }
            // Search using both a query and a filter
            {
                Map<String, List<String>> filters = new HashMap<>();
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
            // We first create a source so we can use it later.
            String customSourceId = client.createCustomSource(SOURCE_NAME);
            client.configureCustomSource(customSourceId, SOURCE_NAME);

            Map<String, Object> document = new HashMap<>();
            document.put("id", "testSendAndRemoveADocument");
            document.put("title", "To be deleted " + RandomizedTest.randomAsciiLettersOfLength(10));
            client.indexDocument(document);
            client.destroyDocument(customSourceId, "testSendAndRemoveADocument");
        }
    }

    private static void checker(String json, int results, List<String> filenames, List<String> texts) {
        staticLogger.trace("{}", json);

        Object document = parseJson(json);
        List<String> urls = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        List<String> bodies = new ArrayList<>();

        filenames.forEach((filename) -> {
            urls.add("http://127.0.0.1/" + filename);
            titles.add(filename);
        });

        texts.forEach((text) -> {
            titles.add("Title for " + text);
            bodies.add("Content for " + text);
        });

        assertThat(JsonPath.read(document, "$.meta.page.total_results"), is(results));
        assertThat(JsonPath.read(document, "$.results[*].title.raw"), hasItem(isOneOf(titles.toArray())));
        assertThat(JsonPath.read(document, "$.results[*].body.raw"), hasItems(bodies.toArray()));
        assertThat(JsonPath.read(document, "$.results[*].size.raw"), notNullValue());
        assertThat(JsonPath.read(document, "$.results[*].text_size.raw"), notNullValue());
        assertThat(JsonPath.read(document, "$.results[*].mime_type.raw"), hasItem(startsWith("text/plain")));
        assertThat(JsonPath.read(document, "$.results[*].name.raw"), hasItems(filenames.toArray()));
        assertThat(JsonPath.read(document, "$.results[*].extension.raw"), hasItem("txt"));
        filenames.forEach((filename) -> assertThat(JsonPath.read(document, "$.results[*].path.raw"), hasItem(endsWith(filename))));
        assertThat(JsonPath.read(document, "$.results[*].url.raw"), hasItems(urls.toArray()));
        assertThat(JsonPath.read(document, "$.results[*].created_at.raw"), notNullValue());
        assertThat(JsonPath.read(document, "$.results[*].last_modified.raw"), notNullValue());
    }
}
