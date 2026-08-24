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
package org.apache.tika.parser.vlm;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.carrotsearch.randomizedtesting.jupiter.DetectThreadLeaks;
import com.carrotsearch.randomizedtesting.jupiter.RandomizedTest;
import com.carrotsearch.randomizedtesting.jupiter.SystemThreadFilter;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import fr.pilato.elasticsearch.crawler.fs.test.framework.ForkJoinPoolThreadFilter;
import fr.pilato.elasticsearch.crawler.fs.test.framework.IntelliJThreadsFilter;
import fr.pilato.elasticsearch.crawler.fs.test.framework.JNACleanerThreadFilter;
import fr.pilato.elasticsearch.crawler.fs.test.framework.JUnitThreadsFilter;
import fr.pilato.elasticsearch.crawler.fs.test.framework.KeepAliveTimerThreadFilter;
import fr.pilato.elasticsearch.crawler.fs.test.framework.TestContainerThreadFilter;
import fr.pilato.elasticsearch.crawler.fs.test.framework.TikaHttpJdkThreadFilter;
import fr.pilato.elasticsearch.crawler.fs.test.framework.WindowsSpecificThreadFilter;
import fr.pilato.elasticsearch.crawler.fs.test.framework.WireMockThreadFilter;
import java.io.InputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * End-to-end unit test for {@link DeterministicOpenAIVLMParser}: health check, chat completions call,
 * response parsing. Uses WireMock instead of a real VLM server.
 */
@DetectThreadLeaks.ExcludeThreads({
    WireMockThreadFilter.class,
    TikaHttpJdkThreadFilter.class,
    SystemThreadFilter.class,
    ForkJoinPoolThreadFilter.class,
    WindowsSpecificThreadFilter.class,
    TestContainerThreadFilter.class,
    JNACleanerThreadFilter.class,
    IntelliJThreadsFilter.class,
    JUnitThreadsFilter.class,
    KeepAliveTimerThreadFilter.class
})
@Execution(ExecutionMode.SAME_THREAD)
class DeterministicOpenAIVLMParserWireMockTest extends AbstractFSCrawlerTestCase {
    private static final Logger logger = LogManager.getLogger();

    private static WireMockServer wireMockServer;

    @BeforeAll
    static void startWireMock() {
        wireMockServer =
                new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
        logger.info("WireMock server started on port {}", wireMockServer.port());
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
            logger.info("WireMock server stopped");
        }
    }

    @BeforeEach
    void resetWireMock() {
        wireMockServer.resetAll();
    }

    @Test
    void extracts_text_from_mocked_openai_endpoint_with_temperature_zero() throws Exception {
        String ocrText = RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 8, 32);

        WireMock.stubFor(get(urlEqualTo("/v1/models"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"data\":[{\"id\":\"test-model\"}]}")));

        WireMock.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "choices": [{
                                    "message": {
                                      "content": "%s"
                                    }
                                  }],
                                  "usage": {
                                    "prompt_tokens": 42,
                                    "completion_tokens": 7
                                  }
                                }
                                """
                                .formatted(ocrText))));

        VLMOCRConfig config = new VLMOCRConfig();
        config.setBaseUrl("http://localhost:" + wireMockServer.port());
        config.setModel("test-model");
        config.setMaxTokens(256);
        config.setTimeoutMillis(5_000);

        DeterministicOpenAIVLMParser parser = new DeterministicOpenAIVLMParser(config);
        parser.initialize();
        Assertions.assertThat(parser.isServerAvailable()).isTrue();

        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "test-ocr.png");
        metadata.set("Content-Type", "image/png");
        BodyContentHandler handler = new BodyContentHandler();

        try (InputStream input = getClass().getClassLoader().getResourceAsStream("documents/test-ocr.png")) {
            Assertions.assertThat(input).isNotNull();
            parser.parse(TikaInputStream.get(input, metadata), handler, metadata, new ParseContext());
        }

        Assertions.assertThat(handler.toString()).contains(ocrText);
        Assertions.assertThat(metadata.get(AbstractVLMParser.VLM_MODEL.getName())).isEqualTo("test-model");
        Assertions.assertThat(metadata.get(AbstractVLMParser.VLM_PROMPT_TOKENS.getName())).isEqualTo("42");
        Assertions.assertThat(metadata.get(AbstractVLMParser.VLM_COMPLETION_TOKENS.getName())).isEqualTo("7");

        WireMock.verify(getRequestedFor(urlEqualTo("/v1/models")));
        WireMock.verify(postRequestedFor(urlEqualTo("/v1/chat/completions"))
                .withRequestBody(matchingJsonPath("$.temperature", equalToJson("0"))));
    }
}
