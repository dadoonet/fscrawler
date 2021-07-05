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
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

/**
 * Test Workplace Search HTTP client
 */
public class WPSearchClientIT extends AbstractWorkplaceSearchITCase {

    @Test
    public void testSearch() throws Exception {
        Map<String, Object> document = new HashMap<>();
        String uniqueId = RandomizedTest.randomAsciiLettersOfLength(10);
        document.put("id", "testSearch");
        document.put("title", "To be searched " + uniqueId);
        document.put("body", "Foo Bar Baz " + uniqueId);
        client.indexDocument(document);

        // We need to wait until it's done
        countTestHelper(new ESSearchRequest().withIndex(".ent-search-engine-*"), 1L, null);
        String json = client.search(uniqueId);

        Object response = Configuration.defaultConfiguration().jsonProvider().parse(json);
        List<String> ids = JsonPath.read(response, "$.results[*].id.raw");

        assertThat(ids, hasSize(1));
        assertThat(ids.get(0), is("testSearch"));
    }

    @Test
    public void testSendAndRemoveADocument() {
        Map<String, Object> document = new HashMap<>();
        document.put("id", "testSendAndRemoveADocument");
        document.put("title", "To be deleted " + RandomizedTest.randomAsciiLettersOfLength(10));
        client.indexDocument(document);
        client.destroyDocument("testSendAndRemoveADocument");
    }
}
