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
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractITCase;
import fr.pilato.elasticsearch.crawler.fs.thirdparty.wpsearch.WPSearchClient;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assume.assumeFalse;

/**
 * Test Workplace Search HTTP client
 */
public class WPSearchClientIT extends AbstractITCase {

    private static WPSearchClient client;

    @BeforeClass
    public static void startClient() {
        checkWorkplaceSettings();

        client = new WPSearchClient(testWorkplaceAccessToken, testWorkplaceKey)
                .withHost(testWorkplaceUrl);
        client.start();
    }

    @AfterClass
    public static void stopClient() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    public void testSendADocument() {
        Map<String, Object> document = new HashMap<>();
        document.put("id", "testSendADocument");
        document.put("body", "Foo Bar Baz");
        client.indexDocument(document);
    }

    @Test
    public void testSearch() {
        Map<String, Object> document = new HashMap<>();
        String uniqueId = RandomizedTest.randomAsciiLettersOfLength(10);
        document.put("id", "testSearch");
        document.put("title", "To be searched " + uniqueId);
        document.put("body", "Foo Bar Baz " + uniqueId);
        client.indexDocument(document);
        client.search(uniqueId);
    }

    @Test
    public void testSendAndRemoveADocument() throws InterruptedException {
        Map<String, Object> document = new HashMap<>();
        document.put("id", "testSendAndRemoveADocument");
        document.put("title", "To be deleted " + RandomizedTest.randomAsciiLettersOfLength(10));
        client.indexDocument(document);
        client.destroyDocument("testSendAndRemoveADocument");
    }
}
