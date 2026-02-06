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

package fr.pilato.elasticsearch.crawler.plugins.input.local;

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
 * Tests for loading local input plugin settings from various file formats.
 */
public class LocalInputSettingsTest {

    private static final Logger logger = LogManager.getLogger();

    private final Path configPath;

    public LocalInputSettingsTest() throws URISyntaxException {
        configPath = Path.of(LocalInputSettingsTest.class.getResource("/config").toURI());
    }

    @Test
    public void loadYamlSimple() throws IOException, FsCrawlerIllegalConfigurationException {
        Path configFile = configPath.resolve("yaml-simple/local.yaml");
        LocalInputSettings settings = PluginSettingsLoader.load(
                configFile, LocalInputSettings.class, false);

        logger.info("Loaded settings: {}", settings);

        assertThat(settings.getVersion()).isEqualTo(1);
        assertThat(settings.getId()).isEqualTo("test-local-input");
        assertThat(settings.getType()).isEqualTo("local");
        assertThat(settings.getPath()).isEqualTo("/data/documents");
    }

    @Test
    public void loadYamlFull() throws IOException, FsCrawlerIllegalConfigurationException {
        Path configFile = configPath.resolve("yaml-full/local.yaml");
        LocalInputSettings settings = PluginSettingsLoader.load(
                configFile, LocalInputSettings.class, false);

        logger.info("Loaded settings: {}", settings);

        // Basic settings
        assertThat(settings.getVersion()).isEqualTo(1);
        assertThat(settings.getId()).isEqualTo("test-full-local");
        assertThat(settings.getType()).isEqualTo("local");

        // Required settings
        assertThat(settings.getPath()).isEqualTo("/data/documents/archive");

        // Common input settings
        assertThat(settings.getUpdateRate()).isEqualTo("30m");
        assertThat(settings.getIncludes()).isEqualTo(List.of("*.pdf", "*.docx", "*.txt"));
        assertThat(settings.getExcludes()).isEqualTo(List.of("*~", "*.tmp", ".*"));
        assertThat(settings.getTags()).isEqualTo(List.of("documents", "archive"));

        // Local-specific settings
        assertThat(settings.getFollowSymlinks()).isTrue();
        assertThat(settings.getAclSupport()).isTrue();
        assertThat(settings.getAttributesSupport()).isTrue();
    }

    @Test
    public void loadJsonSimple() throws IOException, FsCrawlerIllegalConfigurationException {
        Path configFile = configPath.resolve("json-simple/local.json");
        LocalInputSettings settings = PluginSettingsLoader.load(
                configFile, LocalInputSettings.class, false);

        logger.info("Loaded settings: {}", settings);

        assertThat(settings.getVersion()).isEqualTo(1);
        assertThat(settings.getId()).isEqualTo("test-json-local");
        assertThat(settings.getType()).isEqualTo("local");
        assertThat(settings.getPath()).isEqualTo("/data/json/documents");
    }

    @Test
    public void loadWrongType() {
        Path configFile = configPath.resolve("yaml-wrong/local.yaml");
        
        assertThatExceptionOfType(FsCrawlerIllegalConfigurationException.class)
                .isThrownBy(() -> PluginSettingsLoader.load(
                        configFile, LocalInputSettings.class, false))
                .withMessageContaining("type mismatch")
                .withMessageContaining("wrong_type");
    }

    @Test
    public void loadNonExistentFile() {
        Path configFile = configPath.resolve("does-not-exist/local.yaml");
        
        assertThatExceptionOfType(IOException.class)
                .isThrownBy(() -> PluginSettingsLoader.load(
                        configFile, LocalInputSettings.class, false))
                .withMessageContaining("not found");
    }

    // Note: System property override tests are handled in FsSettingsLoaderTest
    // Plugin-level system property overrides can conflict with common JVM system properties
    // (e.g., "path" is a common system property in some environments)
}
