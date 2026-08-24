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

import com.carrotsearch.randomizedtesting.jupiter.DetectThreadLeaks;
import com.carrotsearch.randomizedtesting.jupiter.RandomizedTest;
import com.carrotsearch.randomizedtesting.jupiter.SystemThreadFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import fr.pilato.elasticsearch.crawler.fs.test.framework.ForkJoinPoolThreadFilter;
import fr.pilato.elasticsearch.crawler.fs.test.framework.IntelliJThreadsFilter;
import fr.pilato.elasticsearch.crawler.fs.test.framework.JNACleanerThreadFilter;
import fr.pilato.elasticsearch.crawler.fs.test.framework.JUnitThreadsFilter;
import fr.pilato.elasticsearch.crawler.fs.test.framework.JdkHttpClientSelectorThreadFilter;
import fr.pilato.elasticsearch.crawler.fs.test.framework.KeepAliveTimerThreadFilter;
import fr.pilato.elasticsearch.crawler.fs.test.framework.TestContainerThreadFilter;
import fr.pilato.elasticsearch.crawler.fs.test.framework.WindowsSpecificThreadFilter;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The deterministic variant must force greedy decoding ({@code temperature: 0}) on the request the upstream
 * {@link OpenAIVLMParser} builds, while leaving everything else untouched. Without the field the VLM server samples at
 * its default temperature, which makes small OCR models hallucinate.
 */
@DetectThreadLeaks.ExcludeThreads({
    JdkHttpClientSelectorThreadFilter.class,
    SystemThreadFilter.class,
    ForkJoinPoolThreadFilter.class,
    WindowsSpecificThreadFilter.class,
    TestContainerThreadFilter.class,
    JNACleanerThreadFilter.class,
    IntelliJThreadsFilter.class,
    JUnitThreadsFilter.class,
    KeepAliveTimerThreadFilter.class
})
class DeterministicOpenAIVLMParserTest extends AbstractFSCrawlerTestCase {

    @Test
    void temperature_zero_is_forced_on_the_request() throws Exception {
        VLMOCRConfig config = new VLMOCRConfig();
        DeterministicOpenAIVLMParser parser = new DeterministicOpenAIVLMParser(config);
        String base64Data = RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 16, 64);

        AbstractVLMParser.HttpCall call = parser.buildHttpCall(config, "image/png", base64Data);

        JsonNode body = new ObjectMapper().readTree(call.json());
        Assertions.assertThat(body.get("temperature").asDouble()).isEqualTo(0.0);
        // The rest of the request built by the parent class is preserved
        Assertions.assertThat(body.get("model").asText()).isEqualTo(config.getModel());
        Assertions.assertThat(body.get("max_tokens").asInt()).isEqualTo(config.getMaxTokens());
        Assertions.assertThat(body.get("messages")).hasSize(1);
        Assertions.assertThat(body.get("messages").get(0).toString()).contains(base64Data);
    }
}
