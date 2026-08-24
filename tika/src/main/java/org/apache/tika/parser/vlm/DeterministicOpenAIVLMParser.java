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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.tika.config.JsonConfig;

/**
 * An {@link OpenAIVLMParser} that forces greedy decoding ({@code "temperature": 0}) on every chat completions request.
 *
 * <p>The upstream {@link OpenAIVLMParser} does not send a {@code temperature} field, so the server falls back to its
 * default sampling temperature (typically {@code 1.0}). For OCR this is not a style preference: small vision models
 * hallucinate heavily under sampling. Measured on a scanned Brazilian receipt against a local PaddleOCR-VL-1.6-0.9B
 * (vLLM): the default temperature produced Chinese hallucinations, {@code <|LOC_*|>} layout tokens and endless
 * repetition, while {@code temperature: 0} on the very same request returned the actual text. OCR is a deterministic
 * transcription task — greedy decoding is the only sensible setting, hence it is hardcoded rather than configurable.
 *
 * <p>Registered as {@code openai-vlm-deterministic-parser} (see {@code META-INF/tika/parsers.idx}); the JSON
 * configuration schema is exactly the one of {@code openai-vlm-parser}. This class lives in the
 * {@code org.apache.tika.parser.vlm} package to reuse the package-private {@link AbstractVLMParser.HttpCall} plumbing
 * and should be dropped if upstream Tika ever makes the sampling parameters configurable (TIKA follow-up to #2490).
 */
public class DeterministicOpenAIVLMParser extends OpenAIVLMParser {
    private static final long serialVersionUID = 1L;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public DeterministicOpenAIVLMParser() {
        super();
    }

    public DeterministicOpenAIVLMParser(VLMOCRConfig config) {
        super(config);
    }

    public DeterministicOpenAIVLMParser(JsonConfig jsonConfig) {
        super(jsonConfig);
    }

    @Override
    protected HttpCall buildHttpCall(VLMOCRConfig config, String mimeType, String base64Data) {
        HttpCall call = super.buildHttpCall(config, mimeType, base64Data);
        try {
            ObjectNode body = (ObjectNode) MAPPER.readTree(call.json());
            body.put("temperature", 0.0);
            return new HttpCall(call.url(), MAPPER.writeValueAsString(body), call.headers());
        } catch (JsonProcessingException e) {
            // The body we re-read was just serialized by the parent class: failing here means a
            // programming error, not a runtime condition a caller could recover from.
            throw new IllegalStateException("Can not rewrite the OpenAI VLM request body", e);
        }
    }
}
