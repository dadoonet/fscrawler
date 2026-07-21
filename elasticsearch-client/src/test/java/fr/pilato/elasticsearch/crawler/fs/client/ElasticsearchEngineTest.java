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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.carrotsearch.randomizedtesting.jupiter.RandomizedTest;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import fr.pilato.elasticsearch.crawler.fs.test.framework.VerySlow;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * Reproduces bulk indexing of documents whose JSON contains a single string value larger than Jackson's default
 * {@code StreamReadConstraints} max string length (100_000_000 on Jackson 3.1.x). The payload binary is written under
 * {@code target/} and must not be committed.
 */
@Execution(ExecutionMode.SAME_THREAD)
class ElasticsearchEngineTest extends AbstractFSCrawlerTestCase {

    private static final Logger logger = LogManager.getLogger();
    private static final long MIN_PAYLOAD_BYTES = 150L * 1024 * 1024;
    private static final long MAX_PAYLOAD_BYTES = 300L * 1024 * 1024;
    private static final int WRITE_CHUNK = 1024 * 1024;

    @Test
    void bulkWithCreateOperation() throws Exception {
        AtomicReference<String> capturedNdjson = new AtomicReference<>();
        IElasticsearchClient client = mock(IElasticsearchClient.class);
        doAnswer(invocation -> {
                    capturedNdjson.set(invocation.getArgument(1));
                    return "{\"errors\":false,\"items\":[{\"create\":{\"_index\":\"idx\",\"_id\":\"1\"}}]}";
                })
                .when(client)
                .bulk(anyString(), anyString());

        ElasticsearchBulkRequest request = new ElasticsearchBulkRequest();
        request.add(new ElasticsearchCreateOperation("idx", "1", "my-pipeline", "{\"foo\":\"bar\"}"));

        ElasticsearchBulkResponse response = new ElasticsearchEngine(client).bulk(request);

        Assertions.assertThat(response.isErrors()).isFalse();
        verify(client).bulk(eq("idx"), anyString());
        String ndjson = capturedNdjson.get();
        String[] lines = ndjson.split("\n", -1);
        Assertions.assertThat(lines[0]).startsWith("{\"create\":");
        Assertions.assertThat(lines[0]).doesNotContain("\"_index\"");
        Assertions.assertThat(lines[0]).contains("\"_id\":\"1\"");
        Assertions.assertThat(lines[0]).contains("\"pipeline\":\"my-pipeline\"");
        Assertions.assertThat(lines[1]).isEqualTo("{\"foo\":\"bar\"}");
    }

    @Test
    void bulkKeepsIndexInBodyWhenIndicesDiffer() throws Exception {
        AtomicReference<String> capturedNdjson = new AtomicReference<>();
        IElasticsearchClient client = mock(IElasticsearchClient.class);
        doAnswer(invocation -> {
                    capturedNdjson.set(invocation.getArgument(1));
                    return "{\"errors\":false,\"items\":[]}";
                })
                .when(client)
                .bulk(isNull(), anyString());

        String indexA = RandomizedTest.randomAsciiLettersOfLength(randomizedRandomForTests, 6);
        String indexB = RandomizedTest.randomAsciiLettersOfLength(randomizedRandomForTests, 6);
        ElasticsearchBulkRequest request = new ElasticsearchBulkRequest();
        request.add(new ElasticsearchIndexOperation(indexA, "1", null, "{\"a\":1}"));
        request.add(new ElasticsearchIndexOperation(indexB, "2", null, "{\"b\":2}"));

        new ElasticsearchEngine(client).bulk(request);

        verify(client).bulk(isNull(), anyString());
        Assertions.assertThat(capturedNdjson.get())
                .contains("\"_index\":\"" + indexA + "\"")
                .contains("\"_index\":\"" + indexB + "\"");
    }

    @Test
    void bulkWithPrettyPrintedJson() throws Exception {
        String prettyJson = """
                {
                  "foo": "bar",
                  "note": "line1\\nline2",
                  "nested": {
                    "x": 1
                  }
                }
                """;
        Assertions.assertThat(prettyJson).contains("\n");

        AtomicReference<String> capturedNdjson = new AtomicReference<>();
        IElasticsearchClient client = mock(IElasticsearchClient.class);
        doAnswer(invocation -> {
                    capturedNdjson.set(invocation.getArgument(1));
                    return "{\"errors\":false,\"items\":[{\"index\":{\"_index\":\"idx\",\"_id\":\"1\"}}]}";
                })
                .when(client)
                .bulk(anyString(), anyString());

        ElasticsearchBulkRequest request = new ElasticsearchBulkRequest();
        request.add(new ElasticsearchIndexOperation("idx", "1", null, prettyJson));

        ElasticsearchBulkResponse response = new ElasticsearchEngine(client).bulk(request);

        Assertions.assertThat(response.isErrors()).isFalse();
        String ndjson = capturedNdjson.get();
        String[] lines = ndjson.split("\n", -1);
        // action meta + document + trailing empty segment after final \n
        Assertions.assertThat(lines).hasSize(3);
        Assertions.assertThat(lines[0]).startsWith("{\"index\":");
        Assertions.assertThat(lines[1])
                .doesNotContain("\r")
                .contains("\"foo\": \"bar\"")
                .contains("\"note\": \"line1\\nline2\"")
                .contains("\"x\": 1");
        Assertions.assertThat(lines[2]).isEmpty();
    }

    @Test
    void toSingleLineJsonRemovesStructuralNewlinesOnly() {
        String pretty = """
                {
                  "a": "b\\nc"
                }
                """;
        Assertions.assertThat(ElasticsearchEngine.toSingleLineJson(pretty)).isEqualTo("{  \"a\": \"b\\nc\"}");
        Assertions.assertThat(ElasticsearchEngine.toSingleLineJson("{\"a\":\"b\"}"))
                .isEqualTo("{\"a\":\"b\"}");
    }

    @Test
    @VerySlow
    void bulkWithLargeJsonStringValue() throws Exception {
        long payloadSize =
                RandomizedTest.randomLongInRange(randomizedRandomForTests, MIN_PAYLOAD_BYTES, MAX_PAYLOAD_BYTES);
        Path binary = Path.of("target", "large-bulk-payload-" + payloadSize + ".bin");
        Files.createDirectories(binary.getParent());

        logger.info("Generating binary payload of {} bytes at {}", payloadSize, binary.toAbsolutePath());
        writeAsciiBinary(binary, payloadSize);

        String json;
        try {
            json = buildJsonDocument(binary);
            logger.info("Built JSON document of {} characters", json.length());

            AtomicLong capturedNdjsonLength = new AtomicLong();
            IElasticsearchClient client = mock(IElasticsearchClient.class);
            doAnswer(invocation -> {
                        String ndjson = invocation.getArgument(1);
                        capturedNdjsonLength.set(ndjson.length());
                        Assertions.assertThat(ndjson).startsWith("{\"index\":");
                        Assertions.assertThat(ndjson).contains("\"content\":\"");
                        return "{\"errors\":false,\"items\":[{\"index\":{\"_index\":\"idx\",\"_id\":\"1\"}}]}";
                    })
                    .when(client)
                    .bulk(anyString(), anyString());

            ElasticsearchBulkRequest request = new ElasticsearchBulkRequest();
            request.add(new ElasticsearchIndexOperation("idx", "1", null, json));

            ElasticsearchEngine engine = new ElasticsearchEngine(client);
            ElasticsearchBulkResponse response = engine.bulk(request);

            Assertions.assertThat(response.isErrors()).isFalse();
            Assertions.assertThat(response.hasFailures()).isFalse();
            Assertions.assertThat(capturedNdjsonLength.get()).isGreaterThan(payloadSize);
        } finally {
            Files.deleteIfExists(binary);
        }
    }

    private static void writeAsciiBinary(Path binary, long payloadSize) throws IOException {
        byte[] chunk = new byte[WRITE_CHUNK];
        Arrays.fill(chunk, (byte) 'x');
        try (OutputStream out = Files.newOutputStream(binary)) {
            long remaining = payloadSize;
            while (remaining > 0) {
                int n = (int) Math.min(chunk.length, remaining);
                out.write(chunk, 0, n);
                remaining -= n;
            }
        }
    }

    private static String buildJsonDocument(Path binary) throws IOException {
        // Safe printable ASCII so the value needs no JSON escaping; one byte == one char.
        String content = Files.readString(binary, StandardCharsets.ISO_8859_1);
        return "{\"content\":\"" + content + "\"}";
    }
}
