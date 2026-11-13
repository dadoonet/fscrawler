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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.carrotsearch.randomizedtesting.jupiter.RandomizedTest;
import fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil;
import fr.pilato.elasticsearch.crawler.fs.settings.BulkOperation;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import fr.pilato.elasticsearch.crawler.fs.test.framework.Slow;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;

@Slow
class ElasticsearchBulkMetadataEscapingTest extends AbstractFSCrawlerTestCase {
    static Stream<Arguments> specialIds() {
        return Stream.of(BulkOperation.values())
                .flatMap(operation -> Stream.of("\"", "\\n", "\n").map(value -> Arguments.of(operation, value)));
    }

    @ParameterizedTest
    @MethodSource("specialIds")
    void preservesIdAndOneActionPerOperation(BulkOperation operation, String special) throws Exception {
        String id = randomName() + special + randomName();
        assertSingle(operation, id, null);
    }

    @ParameterizedTest
    @EnumSource(BulkOperation.class)
    void preservesOrdinaryAndUnicodeIds(BulkOperation operation) throws Exception {
        assertSingle(operation, randomName() + "-é-文", null);
    }

    @ParameterizedTest
    @EnumSource(
            value = BulkOperation.class,
            names = {"INDEX", "CREATE"})
    void preservesPipelineSpecialCharacters(BulkOperation operation) throws Exception {
        assertSingle(operation, randomName(), randomName() + "\"\\n\n\r\t" + randomName());
    }

    @ParameterizedTest
    @EnumSource(
            value = BulkOperation.class,
            names = {"INDEX", "CREATE"})
    void omitsNullPipeline(BulkOperation operation) throws Exception {
        assertSingle(operation, randomName(), null);
    }

    @ParameterizedTest
    @EnumSource(
            value = BulkOperation.class,
            names = {"INDEX", "CREATE"})
    void preservesEmptyPipeline(BulkOperation operation) throws Exception {
        assertSingle(operation, randomName(), "");
    }

    @Test
    void encodesPerActionIndexInMixedIndexRequests() throws Exception {
        // Encoding contract only: this does not assert Elasticsearch accepts such index names.
        String index = randomName() + "\"\\n\n";
        String other = randomName();
        String id = randomName();
        ElasticsearchBulkRequest request = new ElasticsearchBulkRequest();
        request.add(new ElasticsearchIndexOperation(index, id, null, "{}"));
        request.add(new ElasticsearchDeleteOperation(other, id));
        Captured captured = capture(request);
        assertThat(captured.index()).isNull();
        String[] lines = captured.body().split("\n", -1);
        assertThat(lines).hasSize(4);
        assertThat(JsonUtil.mapper
                        .readTree(lines[0])
                        .path("index")
                        .path("_index")
                        .asText())
                .isEqualTo(index);
        assertThat(JsonUtil.mapper
                        .readTree(lines[2])
                        .path("delete")
                        .path("_index")
                        .asText())
                .isEqualTo(other);
    }

    @Test
    void idCannotInjectAnotherMetadataField() throws Exception {
        assertSingle(BulkOperation.INDEX, randomName() + "\",\"routing\":\"" + randomName(), null);
    }

    @Test
    void idCannotInjectAnAdditionalDeleteAction() throws Exception {
        String id = randomName() + "\"}}\n{\"delete\":{\"_index\":\"" + randomName() + "\",\"_id\":\"" + randomName();
        assertSingle(BulkOperation.DELETE, id, null);
    }

    private void assertSingle(BulkOperation operation, String id, String pipeline) throws Exception {
        String index = randomName();
        String source = "{\"content\":\"" + randomName() + "\\n" + randomName() + "\"}";
        ElasticsearchOperation action =
                switch (operation) {
                    case INDEX -> new ElasticsearchIndexOperation(index, id, pipeline, source);
                    case CREATE -> new ElasticsearchCreateOperation(index, id, pipeline, source);
                    case DELETE -> new ElasticsearchDeleteOperation(index, id);
                };
        ElasticsearchBulkRequest request = new ElasticsearchBulkRequest();
        request.add(action);
        Captured captured = capture(request);
        assertThat(captured.index()).isEqualTo(index);
        String[] lines = captured.body().split("\n", -1);
        assertThat(lines)
                .as("exactly one action, optional document, and trailing newline")
                .hasSize(operation == BulkOperation.DELETE ? 2 : 3);
        JsonNode header = JsonUtil.mapper.readTree(lines[0]);
        assertThat(header.size()).isEqualTo(1);
        JsonNode metadata = header.path(operation.asLowerCaseString());
        assertThat(metadata.path("_id").asText()).isEqualTo(id);
        assertThat(metadata.has("_index")).isFalse();
        assertThat(metadata.size()).isEqualTo(pipeline == null ? 1 : 2);
        assertThat(metadata.has("pipeline")).isEqualTo(pipeline != null);
        if (pipeline != null) assertThat(metadata.path("pipeline").asText()).isEqualTo(pipeline);
        if (operation != BulkOperation.DELETE) assertThat(lines[1]).isEqualTo(source);
        assertThat(lines[lines.length - 1]).isEmpty();
    }

    private Captured capture(ElasticsearchBulkRequest request) throws Exception {
        AtomicReference<Captured> captured = new AtomicReference<>();
        IElasticsearchClient client = mock(IElasticsearchClient.class);
        when(client.bulk(nullable(String.class), anyString())).thenAnswer(call -> {
            captured.set(new Captured(call.getArgument(0), call.getArgument(1)));
            return "{\"errors\":false,\"items\":[]}";
        });
        assertThat(new ElasticsearchEngine(client).bulk(request).isErrors()).isFalse();
        assertThat(captured.get()).isNotNull();
        return captured.get();
    }

    private String randomName() {
        return RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 8, 16)
                .toLowerCase(Locale.ROOT);
    }

    private record Captured(String index, String body) {}
}
