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

import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import fr.pilato.elasticsearch.crawler.fs.thirdparty.wpsearch.WPSearchAdminClient;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assume.assumeNoException;

public class AbstractWorkplaceSearchITCase extends AbstractFsCrawlerITCase {

    private final static String DEFAULT_TEST_WPSEARCH_URL = "http://127.0.0.1:3002";
    protected final static String testWorkplaceUrl = System.getProperty("tests.workplace.url", DEFAULT_TEST_WPSEARCH_URL);

    protected static WPSearchAdminClient adminClient;
    private static String customSourceId;
    protected static String testWorkplaceAccessToken;
    protected static String testWorkplaceKey;

    @BeforeClass
    public static void createCustomSource() throws Exception {
        adminClient = new WPSearchAdminClient()
                .withHost(testWorkplaceUrl)
                .withPassword(testClusterPass);
        try {
            adminClient.start();
            Map<String, Object> customSource = adminClient.createCustomSource("fscrawler_integration_tests");
            customSourceId = (String) customSource.get("id");
            testWorkplaceAccessToken = (String) customSource.get("accessToken");
            testWorkplaceKey = (String) customSource.get("key");

            assertThat(customSourceId, notNullValue());
            assertThat(testWorkplaceAccessToken, notNullValue());
            assertThat(testWorkplaceKey, notNullValue());
        } catch (Exception e) {
            assumeNoException("We are skipping the test as we were not able to create a Workplace Search client", e);
        }
    }

    @AfterClass
    public static void removeCustomSource() {
        try {
            adminClient.removeCustomSource(customSourceId);
        } catch (Exception e) {
            staticLogger.warn("We have not been able to remove {}: {}", customSourceId, e.getMessage());
        }
        if (adminClient != null) {
            adminClient.close();
            adminClient = null;
        }
    }
}
