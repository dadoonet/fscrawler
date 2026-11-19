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
package fr.pilato.elasticsearch.crawler.fs.beans;

import com.carrotsearch.randomizedtesting.jupiter.RandomizedTest;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DocUtilsArrayMergeTest extends AbstractFSCrawlerTestCase {
    @ParameterizedTest
    @ValueSource(ints = {-1, 1, 3})
    void sidecarKeywordArrayWinsRegardlessOfExtractedArrayLength(int difference) {
        int size = RandomizedTest.randomIntInRange(randomizedRandomForTests, 2, 5);
        Doc doc = new Doc();
        doc.getMeta().setKeywords(values(size));
        List<String> sidecar = values(size + difference);
        Doc merged = merge(doc, JsonUtil.prettyMapper.writeValueAsString(Map.of("meta", Map.of("keywords", sidecar))));
        Assertions.assertThat(merged.getMeta().getKeywords()).containsExactlyElementsOf(sidecar);
    }

    @Test
    void arraysOfObjectsAreAtomicInsteadOfPositionallyMerged() {
        Doc doc = new Doc();
        String extracted = word(), sidecar = word();
        doc.setExternal(Map.of("items", List.of(Map.of("extracted", extracted))));
        Doc merged = merge(
                doc,
                JsonUtil.prettyMapper.writeValueAsString(
                        Map.of("external", Map.of("items", List.of(Map.of("sidecar", sidecar))))));
        Assertions.assertThat(merged.getExternal().get("items")).isEqualTo(List.of(Map.of("sidecar", sidecar)));
    }

    @Test
    void emptySidecarArrayWinsWhileMissingNestedFieldsAreFilled() {
        Doc doc = new Doc();
        doc.getMeta().setKeywords(values(3));
        String title = word();
        doc.getMeta().setTitle(title);
        Doc merged = merge(doc, "{\"meta\":{\"keywords\":[]}}");
        Assertions.assertThat(merged.getMeta().getKeywords()).isEmpty();
        Assertions.assertThat(merged.getMeta().getTitle()).isEqualTo(title);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "[1]", "42", "\"value\""})
    void rejectsNonObjectMetadata(String metadata) {
        Assertions.assertThatThrownBy(() -> merge(new Doc(), metadata))
                .isInstanceOf(FsCrawlerIllegalConfigurationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{}"})
    void absentMetadataIsStillANoOp(String metadata) {
        Doc doc = new Doc(word());
        Assertions.assertThat(merge(doc, metadata)).isSameAs(doc);
    }

    private String word() {
        return RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 5, 15);
    }

    private List<String> values(int count) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < count; i++) values.add(word());
        return values;
    }

    private Doc merge(Doc doc, String json) {
        return DocUtils.getMergedDoc(
                doc, "metadata.json", new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }
}
