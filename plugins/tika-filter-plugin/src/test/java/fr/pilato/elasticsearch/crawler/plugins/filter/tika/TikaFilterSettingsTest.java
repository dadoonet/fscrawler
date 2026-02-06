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

package fr.pilato.elasticsearch.crawler.plugins.filter.tika;

import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import fr.pilato.elasticsearch.crawler.fs.settings.plugin.PluginSettingsLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

/**
 * Tests for loading Tika filter plugin settings from various file formats.
 */
public class TikaFilterSettingsTest {

    private static final Logger logger = LogManager.getLogger();

    private final Path configPath;

    public TikaFilterSettingsTest() throws URISyntaxException {
        configPath = Path.of(TikaFilterSettingsTest.class.getResource("/config").toURI());
    }

    @Test
    public void loadYamlSimple() throws IOException, FsCrawlerIllegalConfigurationException {
        Path configFile = configPath.resolve("yaml-simple/tika.yaml");
        TikaFilterSettings settings = PluginSettingsLoader.load(
                configFile, TikaFilterSettings.class, false);

        logger.info("Loaded settings: {}", settings);

        assertThat(settings.getVersion()).isEqualTo(1);
        assertThat(settings.getId()).isEqualTo("test-tika");
        assertThat(settings.getType()).isEqualTo("tika");
        assertThat(settings.getIndexContent()).isTrue();
    }

    @Test
    public void loadYamlFull() throws IOException, FsCrawlerIllegalConfigurationException {
        Path configFile = configPath.resolve("yaml-full/tika.yaml");
        TikaFilterSettings settings = PluginSettingsLoader.load(
                configFile, TikaFilterSettings.class, false);

        logger.info("Loaded settings: {}", settings);

        // Basic settings
        assertThat(settings.getVersion()).isEqualTo(1);
        assertThat(settings.getId()).isEqualTo("test-full-tika");
        assertThat(settings.getType()).isEqualTo("tika");

        // Content extraction settings
        assertThat(settings.getIndexContent()).isTrue();
        assertThat(settings.getIndexedChars()).isEqualTo("100000");
        assertThat(settings.getLangDetect()).isTrue();
        assertThat(settings.getStoreSource()).isTrue();
        assertThat(settings.getRawMetadata()).isTrue();
        assertThat(settings.getTikaConfigPath()).isEqualTo("/path/to/tika-config.xml");

        // OCR settings
        assertThat(settings.getOcr()).isNotNull();
        assertThat(settings.getOcr().getEnabled()).isTrue();
        assertThat(settings.getOcr().getLanguage()).isEqualTo("fra");
        assertThat(settings.getOcr().getPath()).isEqualTo("/usr/bin/tesseract");
        assertThat(settings.getOcr().getDataPath()).isEqualTo("/usr/share/tessdata");
        assertThat(settings.getOcr().getOutputType()).isEqualTo("txt");
        assertThat(settings.getOcr().getPdfStrategy()).isEqualTo("ocr_and_text");
        assertThat(settings.getOcr().getPageSegMode()).isEqualTo("1");
        assertThat(settings.getOcr().getPreserveInterwordSpacing()).isTrue();
    }

    @Test
    public void loadJsonSimple() throws IOException, FsCrawlerIllegalConfigurationException {
        Path configFile = configPath.resolve("json-simple/tika.json");
        TikaFilterSettings settings = PluginSettingsLoader.load(
                configFile, TikaFilterSettings.class, false);

        logger.info("Loaded settings: {}", settings);

        assertThat(settings.getVersion()).isEqualTo(1);
        assertThat(settings.getId()).isEqualTo("test-json-tika");
        assertThat(settings.getType()).isEqualTo("tika");
        assertThat(settings.getIndexContent()).isTrue();
        assertThat(settings.getLangDetect()).isFalse();
    }

    @Test
    public void loadWrongType() {
        Path configFile = configPath.resolve("yaml-wrong/tika.yaml");
        
        assertThatExceptionOfType(FsCrawlerIllegalConfigurationException.class)
                .isThrownBy(() -> PluginSettingsLoader.load(
                        configFile, TikaFilterSettings.class, false))
                .withMessageContaining("type mismatch")
                .withMessageContaining("wrong_type");
    }

    @Test
    public void loadNonExistentFile() {
        Path configFile = configPath.resolve("does-not-exist/tika.yaml");
        
        assertThatExceptionOfType(IOException.class)
                .isThrownBy(() -> PluginSettingsLoader.load(
                        configFile, TikaFilterSettings.class, false))
                .withMessageContaining("not found");
    }

    // Note: System property override tests are handled in FsSettingsLoaderTest
    // Plugin-level system property overrides can conflict with common JVM system properties
}
