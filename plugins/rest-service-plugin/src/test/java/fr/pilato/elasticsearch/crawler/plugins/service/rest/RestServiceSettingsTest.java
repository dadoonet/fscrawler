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

package fr.pilato.elasticsearch.crawler.plugins.service.rest;

import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import fr.pilato.elasticsearch.crawler.fs.settings.plugin.PluginSettingsLoader;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

/**
 * Tests for loading REST service plugin settings from YAML/JSON files.
 */
public class RestServiceSettingsTest {

    private final Path configPath;

    public RestServiceSettingsTest() throws URISyntaxException {
        configPath = Path.of(RestServiceSettingsTest.class.getResource("/config").toURI());
    }

    @Test
    public void loadYamlSimple() throws IOException, FsCrawlerIllegalConfigurationException {
        Path configFile = configPath.resolve("yaml-simple/rest.yaml");
        RestServiceSettings settings = PluginSettingsLoader.load(
                configFile, RestServiceSettings.class, false);

        assertThat(settings.getVersion()).isEqualTo(1);
        assertThat(settings.getId()).isEqualTo("rest-service");
        assertThat(settings.getType()).isEqualTo("rest");
        assertThat(settings.getUrl()).isEqualTo("http://127.0.0.1:8080/fscrawler");
    }

    @Test
    public void loadYamlFull() throws IOException, FsCrawlerIllegalConfigurationException {
        Path configFile = configPath.resolve("yaml-full/rest.yaml");
        RestServiceSettings settings = PluginSettingsLoader.load(
                configFile, RestServiceSettings.class, false);

        assertThat(settings.getVersion()).isEqualTo(1);
        assertThat(settings.getId()).isEqualTo("rest-service-full");
        assertThat(settings.getType()).isEqualTo("rest");
        assertThat(settings.getUrl()).isEqualTo("http://0.0.0.0:9090/fscrawler");
        assertThat(settings.getEnableCors()).isTrue();
        assertThat(settings.getEnabled()).isTrue();
    }

    @Test
    public void loadJsonSimple() throws IOException, FsCrawlerIllegalConfigurationException {
        Path configFile = configPath.resolve("json-simple/rest.json");
        RestServiceSettings settings = PluginSettingsLoader.load(
                configFile, RestServiceSettings.class, false);

        assertThat(settings.getVersion()).isEqualTo(1);
        assertThat(settings.getId()).isEqualTo("rest-json");
        assertThat(settings.getType()).isEqualTo("rest");
        assertThat(settings.getUrl()).isEqualTo("http://127.0.0.1:8080/fscrawler");
    }

    @Test
    public void loadWrongType() {
        Path configFile = configPath.resolve("yaml-wrong/rest.yaml");
        // Type mismatch: file says "elasticsearch" but we expect "rest"
        assertThatExceptionOfType(FsCrawlerIllegalConfigurationException.class)
                .isThrownBy(() -> PluginSettingsLoader.load(
                        configFile, RestServiceSettings.class, false));
    }

    @Test
    public void loadNonExistentFile() {
        Path configFile = configPath.resolve("nonexistent/rest.yaml");
        assertThatExceptionOfType(IOException.class)
                .isThrownBy(() -> PluginSettingsLoader.load(
                        configFile, RestServiceSettings.class, false));
    }
}
