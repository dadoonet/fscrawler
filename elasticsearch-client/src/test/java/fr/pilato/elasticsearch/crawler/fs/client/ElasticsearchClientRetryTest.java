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

package fr.pilato.elasticsearch.crawler.fs.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.carrotsearch.randomizedtesting.annotations.ThreadLeakFilters;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import fr.pilato.elasticsearch.crawler.fs.test.framework.WireMockThreadFilter;
import jakarta.ws.rs.ServiceUnavailableException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for the retry mechanism on GET/HEAD requests.
 * Uses WireMock to simulate server errors (503) and verify retry behavior.
 */
@ThreadLeakFilters(filters = {WireMockThreadFilter.class})
public class ElasticsearchClientRetryTest extends AbstractFSCrawlerTestCase {
    private static final Logger logger = LogManager.getLogger();
    private static WireMockServer wireMockServer;
    private static String elasticsearchVersion;

    @BeforeClass
    public static void startWireMock() throws IOException {
        // Load the Elasticsearch version from properties
        Properties props = new Properties();
        props.load(ElasticsearchClientRetryTest.class.getResourceAsStream("/elasticsearch.version.properties"));
        elasticsearchVersion = props.getProperty("version");
        logger.info("Using Elasticsearch version {} for mock responses", elasticsearchVersion);

        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
        logger.info("WireMock server started on port {}", wireMockServer.port());
    }

    @AfterClass
    public static void stopWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
            logger.info("WireMock server stopped");
        }
    }

    private ElasticsearchClient createClient() throws IOException {
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.setName("test-retry");
        fsSettings.getElasticsearch().setUrls(List.of("http://localhost:" + wireMockServer.port()));
        fsSettings.getElasticsearch().setSslVerification(false);
        // Disable semantic search to avoid calling getLicense() during start()
        fsSettings.getElasticsearch().setSemanticSearch(false);
        return new ElasticsearchClient(fsSettings);
    }

    /**
     * Test that the client retries on 503 errors and eventually succeeds.
     * The mock is configured to return 503 twice, then succeed on the third attempt.
     * Since start() calls getVersion(), this tests the retry mechanism during client initialization.
     */
    @Test
    public void testRetryOnServerError() throws IOException, ElasticsearchClientException {
        // Setup: First 2 calls return 503, third call succeeds
        wireMockServer.resetAll();
        
        stubFor(get(urlEqualTo("/"))
                .inScenario("Retry Test")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse()
                        .withStatus(503)
                        .withBody("{\"error\": \"Service Unavailable\"}"))
                .willSetStateTo("First Failure"));

        stubFor(get(urlEqualTo("/"))
                .inScenario("Retry Test")
                .whenScenarioStateIs("First Failure")
                .willReturn(aResponse()
                        .withStatus(503)
                        .withBody("{\"error\": \"Service Unavailable\"}"))
                .willSetStateTo("Second Failure"));

        stubFor(get(urlEqualTo("/"))
                .inScenario("Retry Test")
                .whenScenarioStateIs("Second Failure")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"version\": {\"number\": \"" + elasticsearchVersion + "\"}}")));

        try (ElasticsearchClient client = createClient()) {
            // start() calls getVersion() internally, which will trigger retries
            client.start();
            // Verify the version was retrieved successfully
            assertThat(client.getVersion()).isEqualTo(elasticsearchVersion);
        }

        // Verify that 3 requests were made (2 failures + 1 success)
        verify(3, getRequestedFor(urlEqualTo("/")));
        logger.info("Test passed: retry mechanism worked correctly after 2 failures");
    }

    /**
     * Test that the client throws an exception after all retries are exhausted.
     */
    @Test
    public void testRetryExhausted() throws IOException {
        // Setup: All calls return 503
        wireMockServer.resetAll();
        
        stubFor(get(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withBody("{\"error\": \"Service Unavailable\"}")));

        try (ElasticsearchClient client = createClient()) {
            // start() calls getVersion() which should fail after retries are exhausted
            assertThatThrownBy(client::start)
                    .isInstanceOf(ServiceUnavailableException.class);
        }

        // Verify that multiple retry attempts were made
        verify(moreThan(1), getRequestedFor(urlEqualTo("/")));
        logger.info("Test passed: exception thrown after retries exhausted");
    }

    /**
     * Test that 4xx errors are not retried (only 5xx server errors should be retried).
     */
    @Test
    public void testNoRetryOn4xxErrors() throws IOException, ElasticsearchClientException {
        // Setup: Root endpoint works, but specific endpoint returns 404
        wireMockServer.resetAll();

        // First, we need to stub the root endpoint for client initialization
        stubFor(get(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"version\": {\"number\": \"" + elasticsearchVersion + "\"}}")));
        
        // isExistingIndex calls httpGet(index), so it's a GET on /test-index
        stubFor(get(urlEqualTo("/test-index"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\": \"index_not_found_exception\"}")));

        try (ElasticsearchClient client = createClient()) {
            // Initialize the client first
            client.start();
            
            // Reset the count after initialization
            wireMockServer.resetRequests();
            
            // This should fail immediately without retry (404 is a client error)
            boolean exists = client.isExistingIndex("test-index");
            assertThat(exists).isFalse();
        }

        // Verify that only 1 request was made (no retry on 404)
        verify(1, getRequestedFor(urlEqualTo("/test-index")));
        logger.info("Test passed: no retry on 404 client error");
    }

    /**
     * Test that 429 (Too Many Requests) errors trigger retry.
     * This is important for handling Elasticsearch rate limiting.
     * See: https://github.com/dadoonet/fscrawler/issues/2119
     */
    @Test
    public void testRetryOn429TooManyRequests() throws IOException, ElasticsearchClientException {
        wireMockServer.resetAll();
        
        // Use WireMock Scenarios to simulate: 429 -> 429 -> Success
        stubFor(get(urlEqualTo("/"))
                .inScenario("429 Retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\": \"Too Many Requests\"}"))
                .willSetStateTo("First 429"));

        stubFor(get(urlEqualTo("/"))
                .inScenario("429 Retry")
                .whenScenarioStateIs("First 429")
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\": \"Too Many Requests\"}"))
                .willSetStateTo("Second 429"));

        stubFor(get(urlEqualTo("/"))
                .inScenario("429 Retry")
                .whenScenarioStateIs("Second 429")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"version\": {\"number\": \"" + elasticsearchVersion + "\"}}")));

        try (ElasticsearchClient client = createClient()) {
            client.start();
            assertThat(client.getVersion()).isEqualTo(elasticsearchVersion);
        }

        // Verify that 3 requests were made (2 failures + 1 success)
        verify(3, getRequestedFor(urlEqualTo("/")));
        logger.info("Test passed: retry mechanism worked correctly on 429");
    }

    /**
     * Test that successful requests don't trigger any retry.
     */
    @Test
    public void testImmediateSuccessNoRetry() throws IOException, ElasticsearchClientException {
        // Setup: First call succeeds immediately
        wireMockServer.resetAll();
        
        stubFor(get(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"version\": {\"number\": \"" + elasticsearchVersion + "\"}}")));

        try (ElasticsearchClient client = createClient()) {
            client.start();
            assertThat(client.getVersion()).isEqualTo(elasticsearchVersion);
        }

        // Verify that only 1 request was made
        verify(1, getRequestedFor(urlEqualTo("/")));
        logger.info("Test passed: immediate success without retry");
    }

    /**
     * Test that HEAD requests work correctly (they return null body but should not retry).
     * This tests the exists() method which uses httpHead().
     */
    @Test
    public void testHeadRequestSuccess() throws IOException, ElasticsearchClientException {
        wireMockServer.resetAll();

        // Stub for client initialization
        stubFor(get(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"version\": {\"number\": \"" + elasticsearchVersion + "\"}}")));

        // HEAD request returns 200 with no body (document exists)
        stubFor(head(urlEqualTo("/test-index/_doc/doc1"))
                .willReturn(aResponse()
                        .withStatus(200)));

        try (ElasticsearchClient client = createClient()) {
            client.start();
            wireMockServer.resetRequests();

            // exists() uses HEAD request
            boolean exists = client.exists("test-index", "doc1");
            assertThat(exists).isTrue();
        }

        // Verify only 1 HEAD request was made (no retry loop due to null response)
        verify(1, headRequestedFor(urlEqualTo("/test-index/_doc/doc1")));
        logger.info("Test passed: HEAD request works correctly without infinite retry");
    }

    /**
     * Test that HEAD requests with 503 errors are retried correctly.
     */
    @Test
    public void testHeadRequestRetryOnServerError() throws IOException, ElasticsearchClientException {
        wireMockServer.resetAll();

        // Stub for client initialization
        stubFor(get(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"version\": {\"number\": \"" + elasticsearchVersion + "\"}}")));

        // HEAD request: first returns 503, then 200
        stubFor(head(urlEqualTo("/test-index/_doc/doc1"))
                .inScenario("HEAD Retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse()
                        .withStatus(503))
                .willSetStateTo("Recovered"));

        stubFor(head(urlEqualTo("/test-index/_doc/doc1"))
                .inScenario("HEAD Retry")
                .whenScenarioStateIs("Recovered")
                .willReturn(aResponse()
                        .withStatus(200)));

        try (ElasticsearchClient client = createClient()) {
            client.start();
            wireMockServer.resetRequests();

            boolean exists = client.exists("test-index", "doc1");
            assertThat(exists).isTrue();
        }

        // Verify 2 HEAD requests were made (1 failure + 1 success)
        verify(2, headRequestedFor(urlEqualTo("/test-index/_doc/doc1")));
        logger.info("Test passed: HEAD request retry on server error works correctly");
    }

    /**
     * Test that connection errors are propagated immediately without silent retry.
     * This ensures the original error message is preserved.
     */
    @Test
    public void testConnectionErrorNotSilentlyRetried() throws IOException {
        // Use a port where nothing is listening to simulate connection error
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.setName("test-connection-error");
        fsSettings.getElasticsearch().setUrls(List.of("http://localhost:1")); // Port 1 should refuse connections
        fsSettings.getElasticsearch().setSslVerification(false);
        fsSettings.getElasticsearch().setSemanticSearch(false);

        try (ElasticsearchClient client = new ElasticsearchClient(fsSettings)) {
            long startTime = System.currentTimeMillis();
            assertThatThrownBy(client::start)
                    .isInstanceOf(ElasticsearchClientException.class)
                    .hasMessageContaining("Can not execute GET");
            long duration = System.currentTimeMillis() - startTime;

            // Should fail quickly (not wait for retry timeout of 10s)
            assertThat(duration).isLessThan(5000);
            logger.info("Test passed: connection error propagated immediately in {}ms", duration);
        }
    }
}
