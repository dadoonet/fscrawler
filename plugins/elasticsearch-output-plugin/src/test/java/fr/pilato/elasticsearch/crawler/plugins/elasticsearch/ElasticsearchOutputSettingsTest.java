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

package fr.pilato.elasticsearch.crawler.plugins.elasticsearch;

import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import fr.pilato.elasticsearch.crawler.fs.settings.plugin.PluginSettingsLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

/**
 * Tests for loading Elasticsearch output plugin settings from various file formats.
 */
public class ElasticsearchOutputSettingsTest {

    private static final Logger logger = LogManager.getLogger();

    private final Path configPath;

    public ElasticsearchOutputSettingsTest() throws URISyntaxException {
        configPath = Path.of(ElasticsearchOutputSettingsTest.class.getResource("/config").toURI());
    }

    @Test
    public void loadYamlSimple() throws IOException, FsCrawlerIllegalConfigurationException {
        Path configFile = configPath.resolve("yaml-simple/elasticsearch.yaml");
        ElasticsearchOutputSettings settings = PluginSettingsLoader.load(
                configFile, ElasticsearchOutputSettings.class, false);

        logger.info("Loaded settings: {}", settings);

        assertThat(settings.getVersion()).isEqualTo(1);
        assertThat(settings.getId()).isEqualTo("test-output");
        assertThat(settings.getType()).isEqualTo("elasticsearch");
        assertThat(settings.getIndex()).isEqualTo("test_docs");
    }

    @Test
    public void loadYamlFull() throws IOException, FsCrawlerIllegalConfigurationException {
        Path configFile = configPath.resolve("yaml-full/elasticsearch.yaml");
        ElasticsearchOutputSettings settings = PluginSettingsLoader.load(
                configFile, ElasticsearchOutputSettings.class, false);

        logger.info("Loaded settings: {}", settings);

        // Basic settings
        assertThat(settings.getVersion()).isEqualTo(1);
        assertThat(settings.getId()).isEqualTo("test-full-output");
        assertThat(settings.getType()).isEqualTo("elasticsearch");

        // Connection settings
        assertThat(settings.getUrls()).isEqualTo(List.of("https://localhost:9200", "https://localhost:9201"));
        assertThat(settings.getUsername()).isEqualTo("elastic");
        assertThat(settings.getPassword()).isEqualTo("changeme");
        assertThat(settings.getSslVerification()).isFalse();
        assertThat(settings.getCaCertificate()).isEqualTo("/path/to/ca.crt");
        assertThat(settings.getPathPrefix()).isEqualTo("/elasticsearch");

        // Index settings
        assertThat(settings.getIndex()).isEqualTo("my_documents");
        assertThat(settings.getIndexFolder()).isEqualTo("my_folders");
        assertThat(settings.getPipeline()).isEqualTo("my_pipeline");

        // Bulk settings
        assertThat(settings.getBulkSize()).isEqualTo(500);
        assertThat(settings.getFlushInterval()).isEqualTo("10s");
        assertThat(settings.getByteSize()).isEqualTo("50mb");

        // Template settings
        assertThat(settings.getPushTemplates()).isTrue();
        assertThat(settings.getForcePushTemplates()).isFalse();
        assertThat(settings.getSemanticSearch()).isTrue();
    }

    @Test
    public void loadJsonSimple() throws IOException, FsCrawlerIllegalConfigurationException {
        Path configFile = configPath.resolve("json-simple/elasticsearch.json");
        ElasticsearchOutputSettings settings = PluginSettingsLoader.load(
                configFile, ElasticsearchOutputSettings.class, false);

        logger.info("Loaded settings: {}", settings);

        assertThat(settings.getVersion()).isEqualTo(1);
        assertThat(settings.getId()).isEqualTo("test-json-output");
        assertThat(settings.getType()).isEqualTo("elasticsearch");
        assertThat(settings.getIndex()).isEqualTo("json_test_docs");
    }

    @Test
    public void loadWrongType() {
        Path configFile = configPath.resolve("yaml-wrong/elasticsearch.yaml");
        
        assertThatExceptionOfType(FsCrawlerIllegalConfigurationException.class)
                .isThrownBy(() -> PluginSettingsLoader.load(
                        configFile, ElasticsearchOutputSettings.class, false))
                .withMessageContaining("type mismatch")
                .withMessageContaining("wrong_type");
    }

    @Test
    public void loadNonExistentFile() {
        Path configFile = configPath.resolve("does-not-exist/elasticsearch.yaml");
        
        assertThatExceptionOfType(IOException.class)
                .isThrownBy(() -> PluginSettingsLoader.load(
                        configFile, ElasticsearchOutputSettings.class, false))
                .withMessageContaining("not found");
    }

    // Note: System property override tests are handled in FsSettingsLoaderTest
    // Plugin-level system property overrides can conflict with common JVM system properties
    // when Gestalt's SystemPropertiesConfigSource is used
}
