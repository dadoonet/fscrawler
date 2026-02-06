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

package fr.pilato.elasticsearch.crawler.fs.settings.plugin;

import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for PluginSettingsLoader.
 */
public class PluginSettingsLoaderTest {

    /**
     * Simple test settings class for testing.
     */
    public static class TestPluginSettings extends PluginSettings {
        private String basePath;
        private List<String> patterns;
        private Boolean enabled;

        @Override
        public String getExpectedType() {
            return "test";
        }

        @Override
        public int getCurrentVersion() {
            return 1;
        }

        public String getBasePath() {
            return basePath;
        }

        public void setBasePath(String basePath) {
            this.basePath = basePath;
        }

        public List<String> getPatterns() {
            return patterns;
        }

        public void setPatterns(List<String> patterns) {
            this.patterns = patterns;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }
    }

    @Test
    public void testLoadYamlSettings() throws IOException, FsCrawlerIllegalConfigurationException {
        // Create a temp YAML file
        Path tempFile = Files.createTempFile("test-plugin", ".yaml");
        try {
            String yaml = """
                version: 1
                id: test-plugin
                type: test
                basePath: /data/documents
                patterns:
                  - "*.pdf"
                  - "*.docx"
                enabled: true
                """;
            Files.writeString(tempFile, yaml);

            // Load settings
            TestPluginSettings settings = PluginSettingsLoader.load(tempFile, TestPluginSettings.class);

            assertThat(settings.getVersion()).isEqualTo(1);
            assertThat(settings.getId()).isEqualTo("test-plugin");
            assertThat(settings.getType()).isEqualTo("test");
            assertThat(settings.getBasePath()).isEqualTo("/data/documents");
            assertThat(settings.getPatterns()).containsExactly("*.pdf", "*.docx");
            assertThat(settings.getEnabled()).isTrue();
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    public void testLoadJsonSettings() throws IOException, FsCrawlerIllegalConfigurationException {
        // Create a temp JSON file
        Path tempFile = Files.createTempFile("test-plugin", ".json");
        try {
            String json = """
                {
                    "version": 1,
                    "id": "test-plugin-json",
                    "type": "test",
                    "basePath": "/data/json",
                    "enabled": false
                }
                """;
            Files.writeString(tempFile, json);

            // Load settings
            TestPluginSettings settings = PluginSettingsLoader.load(tempFile, TestPluginSettings.class);

            assertThat(settings.getVersion()).isEqualTo(1);
            assertThat(settings.getId()).isEqualTo("test-plugin-json");
            assertThat(settings.getType()).isEqualTo("test");
            assertThat(settings.getBasePath()).isEqualTo("/data/json");
            assertThat(settings.getEnabled()).isFalse();
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    public void testValidationFailsWithoutId() throws IOException {
        Path tempFile = Files.createTempFile("test-plugin", ".yaml");
        try {
            String yaml = """
                version: 1
                type: test
                basePath: /data/documents
                """;
            Files.writeString(tempFile, yaml);

            assertThatThrownBy(() -> PluginSettingsLoader.load(tempFile, TestPluginSettings.class))
                    .isInstanceOf(FsCrawlerIllegalConfigurationException.class)
                    .hasMessageContaining("id");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    public void testValidationFailsWithWrongType() throws IOException {
        Path tempFile = Files.createTempFile("test-plugin", ".yaml");
        try {
            String yaml = """
                version: 1
                id: test-plugin
                type: wrong-type
                basePath: /data/documents
                """;
            Files.writeString(tempFile, yaml);

            assertThatThrownBy(() -> PluginSettingsLoader.load(tempFile, TestPluginSettings.class))
                    .isInstanceOf(FsCrawlerIllegalConfigurationException.class)
                    .hasMessageContaining("type mismatch");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    public void testFileNotFound() {
        Path nonExistent = Path.of("/non/existent/file.yaml");

        assertThatThrownBy(() -> PluginSettingsLoader.load(nonExistent, TestPluginSettings.class))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("not found");
    }

    @Test
    public void testGetFileExtension() {
        assertThat(PluginSettingsLoader.getFileExtension(Path.of("/path/to/file.yaml"))).isEqualTo("yaml");
        assertThat(PluginSettingsLoader.getFileExtension(Path.of("/path/to/file.yml"))).isEqualTo("yml");
        assertThat(PluginSettingsLoader.getFileExtension(Path.of("/path/to/file.json"))).isEqualTo("json");
        assertThat(PluginSettingsLoader.getFileExtension(Path.of("/path/to/file.properties"))).isEqualTo("properties");
        assertThat(PluginSettingsLoader.getFileExtension(Path.of("/path/to/file"))).isEqualTo("");
    }

    @Test
    public void testWriterYaml() throws IOException {
        Path tempFile = Files.createTempFile("test-plugin", ".yaml");
        try {
            TestPluginSettings settings = new TestPluginSettings();
            settings.setVersion(1);
            settings.setId("test-writer");
            settings.setType("test");
            settings.setBasePath("/data/output");
            settings.setEnabled(true);
            settings.setPatterns(List.of("*.txt"));

            PluginSettingsWriter.write(settings, tempFile);

            String content = Files.readString(tempFile);
            assertThat(content).contains("version: 1");
            assertThat(content).contains("id: \"test-writer\"");
            assertThat(content).contains("type: \"test\"");
            assertThat(content).contains("basePath: \"/data/output\"");
            assertThat(content).contains("enabled: true");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
