/*
 * Licensed to David Pilato (the "Author") under one
 * or more contributor license agreements. See the NOTICE file
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
 *
 * Made from 🇫🇷🇪🇺 with ❤️ - 2011-2026
 */
package fr.pilato.elasticsearch.crawler.fs.client;

import com.carrotsearch.randomizedtesting.jupiter.DetectThreadLeaks;
import com.carrotsearch.randomizedtesting.jupiter.RandomizedTest;
import com.carrotsearch.randomizedtesting.jupiter.SystemThreadFilter;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import fr.pilato.elasticsearch.crawler.fs.settings.BulkOperation;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import fr.pilato.elasticsearch.crawler.fs.test.framework.IntelliJThreadsFilter;
import fr.pilato.elasticsearch.crawler.fs.test.framework.JNACleanerThreadFilter;
import fr.pilato.elasticsearch.crawler.fs.test.framework.JUnitThreadsFilter;
import fr.pilato.elasticsearch.crawler.fs.test.framework.Slow;
import fr.pilato.elasticsearch.crawler.fs.test.framework.TestContainerThreadFilter;
import fr.pilato.elasticsearch.crawler.fs.test.framework.WindowsSpecificThreadFilter;
import fr.pilato.elasticsearch.crawler.fs.test.framework.WireMockThreadFilter;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Slow
@DetectThreadLeaks.ExcludeThreads({
    WireMockThreadFilter.class,
    SystemThreadFilter.class,
    WindowsSpecificThreadFilter.class,
    TestContainerThreadFilter.class,
    JNACleanerThreadFilter.class,
    IntelliJThreadsFilter.class,
    JUnitThreadsFilter.class
})
@Execution(ExecutionMode.SAME_THREAD)
class ElasticsearchBulkItemFailureTest extends AbstractFSCrawlerTestCase {
    private static WireMockServer server;
    private static String version;
    private String index;
    private String id;
    private String reason;

    @BeforeAll
    static void startServer() throws IOException {
        var properties = new Properties();
        try (var stream =
                ElasticsearchBulkItemFailureTest.class.getResourceAsStream("/elasticsearch.version.properties")) {
            properties.load(stream);
        }
        version = properties.getProperty("version");
        server = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        server.start();
        WireMock.configureFor("localhost", server.port());
    }

    @AfterAll
    static void stopServer() {
        if (server != null) server.stop();
    }

    @BeforeEach
    void configureResponses() {
        server.resetAll();
        index = "bug04-" + randomText().toLowerCase(Locale.ROOT);
        id = randomText();
        reason = "Invalid document " + randomText();
        WireMock.stubFor(WireMock.get(WireMock.urlEqualTo("/"))
                .willReturn(WireMock.okJson("{\"version\":{\"number\":\"" + version + "\"}}")));
    }

    @Test
    void mixedHttpSuccessWithTerminalItemFailureFailsFlush() throws Exception {
        String goodId = id + "-good";
        respond(items(success(goodId), failure(id, "mapper_parsing_exception", reason)));
        try (var client = startClient()) {
            client.indexRawJson(index, goodId, "{}", null);
            client.indexRawJson(index, id, "{}", null);
            var failure = Assertions.catchThrowable(client::flushAndEnsureBulkSucceeded);
            Assertions.assertThat(failure).isInstanceOf(ElasticsearchClientException.class);
            Assertions.assertThat(failure.getCause())
                    .hasMessageContaining(index)
                    .hasMessageContaining(id)
                    .hasMessageContaining("mapper_parsing_exception")
                    .hasMessageContaining("400")
                    .hasMessageContaining(reason);
        }
        WireMock.verify(1, WireMock.postRequestedFor(WireMock.urlPathEqualTo("/" + index + "/_bulk")));
    }

    @Test
    void laterSuccessfulBatchCannotHideTerminalFailureOrItsGeneration() throws Exception {
        respond(items(failure(id, "mapper_parsing_exception", reason)));
        try (var client = startClient()) {
            long before = client.getBulkFailureGeneration();
            client.indexRawJson(index, id, "{}", null);
            client.flush();
            respond(items(success(id)));
            client.indexRawJson(index, id, "{}", null);
            client.flush();
            Assertions.assertThatThrownBy(client::ensureBulkSucceeded).isInstanceOf(ElasticsearchClientException.class);
            Assertions.assertThatThrownBy(client::ensureBulkSucceeded).isInstanceOf(ElasticsearchClientException.class);
            Assertions.assertThat(client.getBulkFailureGeneration()).isGreaterThan(before);
            client.clearFatalBulkFailure();
            client.ensureBulkSucceeded();
            Assertions.assertThatThrownBy(() -> client.ensureBulkSucceededSince(before))
                    .isInstanceOf(ElasticsearchClientException.class);
            client.ensureBulkSucceededSince(client.getBulkFailureGeneration());
        }
        WireMock.verify(2, WireMock.postRequestedFor(WireMock.urlPathEqualTo("/" + index + "/_bulk")));
    }

    @Test
    void successfullyRetriedItemDoesNotBecomeTerminalFailure() throws Exception {
        // Preserve the existing message-based retry contract; bounded structured retries are a separate change.
        String scenario = randomText();
        WireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/" + index + "/_bulk"))
                .inScenario(scenario)
                .whenScenarioStateIs(Scenario.STARTED)
                .willSetStateTo("recovered")
                .willReturn(WireMock.okJson(items(rejection(id)))));
        WireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/" + index + "/_bulk"))
                .inScenario(scenario)
                .whenScenarioStateIs("recovered")
                .willReturn(WireMock.okJson(items(success(id)))));
        try (var client = startClient()) {
            long before = client.getBulkFailureGeneration();
            client.indexRawJson(index, id, "{}", null);
            client.flushAndEnsureBulkSucceeded();
            Assertions.assertThat(client.getBulkFailureGeneration()).isEqualTo(before);
        }
        WireMock.verify(2, WireMock.postRequestedFor(WireMock.urlPathEqualTo("/" + index + "/_bulk")));
    }

    @Test
    void retryWithoutMatchingRequestOperationIsTerminal() throws Exception {
        respond(items(rejection(id + "-unmatched")));
        try (var client = startClient()) {
            client.indexRawJson(index, id, "{}", null);
            Assertions.assertThatThrownBy(client::flushAndEnsureBulkSucceeded)
                    .isInstanceOf(ElasticsearchClientException.class);
        }
        WireMock.verify(1, WireMock.postRequestedFor(WireMock.urlPathEqualTo("/" + index + "/_bulk")));
    }

    @Test
    void itemFailureWithoutReasonStillFailsFlush() throws Exception {
        respond(items(failure(id, "mapper_parsing_exception", null)));
        try (var client = startClient()) {
            client.indexRawJson(index, id, "{}", null);
            Assertions.assertThatThrownBy(client::flushAndEnsureBulkSucceeded)
                    .isInstanceOf(ElasticsearchClientException.class);
        }
    }

    @Test
    void acceptedCreateConflictDoesNotFailFlush() throws Exception {
        respond(
                items(
                        "{\"create\":{\"_index\":\"" + index + "\",\"_id\":\"" + id
                                + "\",\"status\":409,\"error\":{\"type\":\"version_conflict_engine_exception\",\"reason\":\"already exists\"}}}"));
        try (var client = startClient(BulkOperation.CREATE)) {
            client.indexRawJson(index, id, "{}", null);
            client.flushAndEnsureBulkSucceeded();
        }
    }

    @Test
    void retryRejectedDuringCloseIsRecordedAsTerminalFailure() throws Exception {
        respond(items(rejection(id)));
        try (var client = startClient()) {
            client.indexRawJson(index, id, "{}", null);
            client.close();
            Assertions.assertThatThrownBy(client::ensureBulkSucceeded).isInstanceOf(ElasticsearchClientException.class);
        }
        WireMock.verify(1, WireMock.postRequestedFor(WireMock.urlPathEqualTo("/" + index + "/_bulk")));
    }

    @Test
    void structuredRejectionRetriesWithoutTypeInReason() throws Exception {
        String scenario = randomText();
        WireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/" + index + "/_bulk"))
                .inScenario(scenario)
                .whenScenarioStateIs(Scenario.STARTED)
                .willSetStateTo("ok")
                .willReturn(WireMock.okJson(items(failure(id, "es_rejected_execution_exception", "queue full")))));
        WireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/" + index + "/_bulk"))
                .inScenario(scenario)
                .whenScenarioStateIs("ok")
                .willReturn(WireMock.okJson(items(success(id)))));
        try (var client = startClient()) {
            client.indexRawJson(index, id, "{}", null);
            client.flushAndEnsureBulkSucceeded();
        }
        WireMock.verify(2, WireMock.postRequestedFor(WireMock.urlPathEqualTo("/" + index + "/_bulk")));
    }

    @Test
    void rejectionRetriesAreBounded() throws Exception {
        // A finite sixth-response success lets the old implementation terminate safely.
        String scenario = randomText();
        for (int attempt = 0; attempt < 6; attempt++) {
            WireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/" + index + "/_bulk"))
                    .inScenario(scenario)
                    .whenScenarioStateIs(attempt == 0 ? Scenario.STARTED : "try" + attempt)
                    .willSetStateTo("try" + (attempt + 1))
                    .willReturn(WireMock.okJson(items(attempt == 5 ? success(id) : rejection(id)))));
        }
        try (var client = startClient()) {
            client.indexRawJson(index, id, "{}", null);
            Assertions.assertThatThrownBy(client::flushAndEnsureBulkSucceeded)
                    .isInstanceOf(ElasticsearchClientException.class);
        }
        WireMock.verify(4, WireMock.postRequestedFor(WireMock.urlPathEqualTo("/" + index + "/_bulk")));
    }

    @Test
    void permanentErrorMentioningRejectionIsNotRetried() throws Exception {
        respond(items(failure(id, "mapper_parsing_exception", "es_rejected_execution_exception in invalid field")));
        try (var client = startClient()) {
            client.indexRawJson(index, id, "{}", null);
            // Old reason matching would repeat indefinitely; make the next response successful.
            WireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/" + index + "/_bulk"))
                    .inScenario("permanent")
                    .whenScenarioStateIs(Scenario.STARTED)
                    .willSetStateTo("ok")
                    .willReturn(WireMock.okJson(
                            items(failure(id, "mapper_parsing_exception", "es_rejected_execution_exception")))));
            WireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/" + index + "/_bulk"))
                    .inScenario("permanent")
                    .whenScenarioStateIs("ok")
                    .willReturn(WireMock.okJson(items(success(id)))));
            Assertions.assertThatThrownBy(client::flushAndEnsureBulkSucceeded)
                    .isInstanceOf(ElasticsearchClientException.class);
        }
        WireMock.verify(1, WireMock.postRequestedFor(WireMock.urlPathEqualTo("/" + index + "/_bulk")));
    }

    @Test
    void repeatedIdRetriesTheRejectedPayloadOnly() throws Exception {
        String first = randomText();
        String second = randomText();
        String scenario = randomText();
        WireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/" + index + "/_bulk"))
                .inScenario(scenario)
                .whenScenarioStateIs(Scenario.STARTED)
                .willSetStateTo("ok")
                .willReturn(WireMock.okJson(items(success(id), rejection(id)))));
        WireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/" + index + "/_bulk"))
                .inScenario(scenario)
                .whenScenarioStateIs("ok")
                .willReturn(WireMock.okJson(items(success(id)))));
        try (var client = startClient()) {
            client.indexRawJson(index, id, "{\"value\":\"" + first + "\"}", null);
            client.indexRawJson(index, id, "{\"value\":\"" + second + "\"}", null);
            client.flushAndEnsureBulkSucceeded();
        }
        WireMock.verify(
                1,
                WireMock.postRequestedFor(WireMock.urlPathEqualTo("/" + index + "/_bulk"))
                        .withRequestBody(WireMock.containing(second))
                        .withRequestBody(WireMock.notMatching("(?s).*" + first + ".*")));
    }

    private ElasticsearchClient startClient() throws Exception {
        return startClient(BulkOperation.INDEX);
    }

    private ElasticsearchClient startClient(BulkOperation operation) throws Exception {
        var settings = FsSettingsLoader.load();
        settings.setName(randomText());
        settings.getElasticsearch().setUrls(List.of("http://localhost:" + server.port()));
        settings.getElasticsearch().setSemanticSearch(false);
        settings.getElasticsearch().setBulkOperation(operation);
        settings.getElasticsearch().setFlushInterval(null);
        settings.getElasticsearch().setBulkSize(100);
        var client = new ElasticsearchClient(settings);
        client.start();
        return client;
    }

    private void respond(String body) {
        WireMock.stubFor(
                WireMock.post(WireMock.urlPathEqualTo("/" + index + "/_bulk")).willReturn(WireMock.okJson(body)));
    }

    private String success(String documentId) {
        return "{\"index\":{\"_index\":\"" + index + "\",\"_id\":\"" + documentId + "\",\"status\":201}}";
    }

    private String failure(String documentId, String type, String message) {
        return "{\"index\":{\"_index\":\"" + index + "\",\"_id\":\"" + documentId
                + "\",\"status\":400,\"error\":{\"type\":\"" + type + "\",\"reason\":"
                + (message == null ? "null" : "\"" + message + "\"") + "}}}";
    }

    private String rejection(String documentId) {
        return failure(documentId, "es_rejected_execution_exception", "es_rejected_execution_exception: queue full")
                .replace("\"status\":400", "\"status\":429");
    }

    private String items(String... responses) {
        String entries = String.join(",", responses);
        return "{\"errors\":" + entries.contains("\"error\":") + ",\"items\":[" + entries + "]}";
    }

    private String randomText() {
        return RandomizedTest.randomAsciiLettersOfLength(randomizedRandomForTests, 12);
    }
}
