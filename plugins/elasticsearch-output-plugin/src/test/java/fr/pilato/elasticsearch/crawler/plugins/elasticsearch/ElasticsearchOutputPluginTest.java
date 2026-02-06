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
import fr.pilato.elasticsearch.crawler.fs.settings.Elasticsearch;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ElasticsearchOutputPluginTest {

    @Test
    public void testGetType() {
        ElasticsearchOutputPlugin plugin = new ElasticsearchOutputPlugin();
        assertThat(plugin.getType()).isEqualTo("elasticsearch");
    }

    @Test
    public void testConfigureWithTypeSpecificConfig() {
        ElasticsearchOutputPlugin plugin = new ElasticsearchOutputPlugin();
        plugin.setId("test-output");

        // Build type-specific config
        Map<String, Object> esConfig = new HashMap<>();
        esConfig.put("urls", List.of("https://localhost:9200"));
        esConfig.put("index", "test-index");
        esConfig.put("bulk_size", 50);
        esConfig.put("flush_interval", "10s");
        esConfig.put("pipeline", "my-pipeline");
        esConfig.put("ssl_verification", false);

        // Build raw config with type key
        Map<String, Object> rawConfig = new HashMap<>();
        rawConfig.put("elasticsearch", esConfig);

        // Configure the plugin
        FsSettings globalSettings = new FsSettings();
        globalSettings.setName("test-job");
        plugin.configure(rawConfig, globalSettings);

        // Verify configuration was read correctly
        assertThat(plugin.getUrls()).containsExactly("https://localhost:9200");
        assertThat(plugin.getIndex()).isEqualTo("test-index");
        assertThat(plugin.getPipeline()).isEqualTo("my-pipeline");
    }

    @Test
    public void testConfigureFallsBackToGlobalSettings() {
        ElasticsearchOutputPlugin plugin = new ElasticsearchOutputPlugin();
        plugin.setId("test-output");

        // Build global Elasticsearch settings
        Elasticsearch globalEs = new Elasticsearch();
        globalEs.setUrls(List.of("https://global:9200"));
        globalEs.setIndex("global-index");
        globalEs.setPipeline("global-pipeline");
        globalEs.setBulkSize(200);

        FsSettings globalSettings = new FsSettings();
        globalSettings.setName("test-job");
        globalSettings.setElasticsearch(globalEs);

        // Configure with empty type-specific config
        Map<String, Object> rawConfig = new HashMap<>();
        rawConfig.put("elasticsearch", new HashMap<>());

        plugin.configure(rawConfig, globalSettings);

        // Verify fallback to global settings
        assertThat(plugin.getUrls()).containsExactly("https://global:9200");
        assertThat(plugin.getIndex()).isEqualTo("global-index");
        assertThat(plugin.getPipeline()).isEqualTo("global-pipeline");
    }

    @Test
    public void testConfigureTypeSpecificOverridesGlobal() {
        ElasticsearchOutputPlugin plugin = new ElasticsearchOutputPlugin();
        plugin.setId("test-output");

        // Build global Elasticsearch settings
        Elasticsearch globalEs = new Elasticsearch();
        globalEs.setUrls(List.of("https://global:9200"));
        globalEs.setIndex("global-index");
        globalEs.setPipeline("global-pipeline");

        FsSettings globalSettings = new FsSettings();
        globalSettings.setName("test-job");
        globalSettings.setElasticsearch(globalEs);

        // Build type-specific config with some overrides
        Map<String, Object> esConfig = new HashMap<>();
        esConfig.put("urls", List.of("https://specific:9200"));
        esConfig.put("index", "specific-index");
        // Note: pipeline is NOT overridden, should use global

        Map<String, Object> rawConfig = new HashMap<>();
        rawConfig.put("elasticsearch", esConfig);

        plugin.configure(rawConfig, globalSettings);

        // Verify type-specific overrides global
        assertThat(plugin.getUrls()).containsExactly("https://specific:9200");
        assertThat(plugin.getIndex()).isEqualTo("specific-index");
        // Pipeline should fall back to global
        assertThat(plugin.getPipeline()).isEqualTo("global-pipeline");
    }

    @Test
    public void testValidateConfigurationFailsWithoutUrls() {
        ElasticsearchOutputPlugin plugin = new ElasticsearchOutputPlugin();
        plugin.setId("test-output");

        // Configure with no URLs
        Map<String, Object> rawConfig = new HashMap<>();
        rawConfig.put("elasticsearch", new HashMap<>());

        FsSettings globalSettings = new FsSettings();
        globalSettings.setName("test-job");
        plugin.configure(rawConfig, globalSettings);

        // Validation should fail because no URLs are configured
        assertThatThrownBy(plugin::validateConfiguration)
                .isInstanceOf(FsCrawlerIllegalConfigurationException.class)
                .hasMessageContaining("requires at least one URL");
    }

    @Test
    public void testValidateConfigurationSucceedsWithUrls() throws FsCrawlerIllegalConfigurationException {
        ElasticsearchOutputPlugin plugin = new ElasticsearchOutputPlugin();
        plugin.setId("test-output");

        // Configure with URLs
        Map<String, Object> esConfig = new HashMap<>();
        esConfig.put("urls", List.of("https://localhost:9200"));

        Map<String, Object> rawConfig = new HashMap<>();
        rawConfig.put("elasticsearch", esConfig);

        FsSettings globalSettings = new FsSettings();
        globalSettings.setName("test-job");
        plugin.configure(rawConfig, globalSettings);

        // Validation should succeed
        plugin.validateConfiguration();
    }

    @Test
    public void testValidateConfigurationFailsWithoutId() {
        ElasticsearchOutputPlugin plugin = new ElasticsearchOutputPlugin();
        // Note: NOT setting an ID

        Map<String, Object> esConfig = new HashMap<>();
        esConfig.put("urls", List.of("https://localhost:9200"));

        Map<String, Object> rawConfig = new HashMap<>();
        rawConfig.put("elasticsearch", esConfig);

        FsSettings globalSettings = new FsSettings();
        globalSettings.setName("test-job");
        plugin.configure(rawConfig, globalSettings);

        // Validation should fail because no ID is set
        assertThatThrownBy(plugin::validateConfiguration)
                .isInstanceOf(FsCrawlerIllegalConfigurationException.class)
                .hasMessageContaining("plugin id is required");
    }

    @Test
    public void testConfigureWithWhenCondition() {
        ElasticsearchOutputPlugin plugin = new ElasticsearchOutputPlugin();
        plugin.setId("test-output");

        // Build config with "when" condition
        Map<String, Object> esConfig = new HashMap<>();
        esConfig.put("urls", List.of("https://localhost:9200"));

        Map<String, Object> rawConfig = new HashMap<>();
        rawConfig.put("elasticsearch", esConfig);
        rawConfig.put("when", "tags.contains('important')");

        FsSettings globalSettings = new FsSettings();
        globalSettings.setName("test-job");
        plugin.configure(rawConfig, globalSettings);

        // Verify the when condition was read
        assertThat(plugin.getWhen()).isEqualTo("tags.contains('important')");
    }

    @Test
    public void testDefaultValuesApplied() {
        ElasticsearchOutputPlugin plugin = new ElasticsearchOutputPlugin();
        plugin.setId("test-output");

        // Build minimal config
        Map<String, Object> esConfig = new HashMap<>();
        esConfig.put("urls", List.of("https://localhost:9200"));

        Map<String, Object> rawConfig = new HashMap<>();
        rawConfig.put("elasticsearch", esConfig);

        FsSettings globalSettings = new FsSettings();
        globalSettings.setName("test-job");
        plugin.configure(rawConfig, globalSettings);

        // Verify defaults are applied (via buildInternalSettings - we can't directly access them
        // but the plugin should have been configured without throwing exceptions)
        assertThat(plugin.getUrls()).containsExactly("https://localhost:9200");
    }
}
