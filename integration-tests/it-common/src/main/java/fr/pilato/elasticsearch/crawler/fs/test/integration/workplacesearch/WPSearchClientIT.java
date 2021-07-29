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
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.thirdparty.wpsearch.WPSearchClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
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
    public void testSearch() throws Exception {
        try (WPSearchClient client = createClient()) {
            // We first create a source so we can use it later.
            String customSourceId = client.createCustomSource(SOURCE_NAME);
            client.configureCustomSource(customSourceId, SOURCE_NAME);
            Map<String, Object> document = new HashMap<>();
            String uniqueId = RandomizedTest.randomAsciiLettersOfLength(10);
            document.put("id", "testSearch");
            document.put("title", "To be searched " + uniqueId);
            document.put("body", "Foo Bar Baz " + uniqueId);
            client.indexDocument(document);

            // We need to wait until it's done
            countTestHelper(new ESSearchRequest().withIndex(".ent-search-engine-documents-source-" + customSourceId), 1L, null);
            String json = client.search(uniqueId);

            List<String> ids = JsonPath.read(json, "$.results[*].id.raw");

            assertThat(ids, hasSize(1));
            assertThat(ids.get(0), is("testSearch"));
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
}
