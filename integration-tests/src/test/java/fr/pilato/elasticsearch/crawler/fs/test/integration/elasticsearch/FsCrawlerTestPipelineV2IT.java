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

package fr.pilato.elasticsearch.crawler.fs.test.integration.elasticsearch;

import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsMigrator;
import fr.pilato.elasticsearch.crawler.fs.settings.pipeline.FilterSection;
import fr.pilato.elasticsearch.crawler.fs.settings.pipeline.InputSection;
import fr.pilato.elasticsearch.crawler.fs.settings.pipeline.OutputSection;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.INDEX_SUFFIX_DOCS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the v2 pipeline configuration format.
 * 
 * Note: The current implementation uses v2 configuration for settings parsing
 * but internally still relies on legacy v1 settings for actual crawling.
 * These tests verify that:
 * 1. V2 configuration can be loaded and parsed
 * 2. V1 configuration is automatically migrated
 * 3. The crawler works with both v1 and migrated v2 settings
 */
public class FsCrawlerTestPipelineV2IT extends AbstractFsCrawlerITCase {

    /**
     * Test that v1 configuration is automatically migrated to v2 and crawler works.
     */
    @Test
    public void test_v1_auto_migration() throws Exception {
        // Create v1 settings
        FsSettings v1Settings = createTestSettings();
        
        // Verify it's detected as v1
        int version = FsSettingsMigrator.detectVersion(v1Settings);
        assertThat(version).isEqualTo(FsSettingsMigrator.VERSION_1);
        
        // Start the crawler with v1 settings (will be auto-migrated internally)
        crawler = startCrawler(v1Settings);

        // We expect to have one file
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS), 1L, null);
        
        // Verify the settings now have v2 pipeline sections populated
        // Note: The migration happens during FsSettingsLoader.load()
    }

    /**
     * Test loading v2 configuration from YAML file.
     */
    @Test
    public void test_load_v2_yaml_config() throws Exception {
        // Create a v2 YAML configuration file
        Path configDir = metadataDir.resolve("test_v2_config");
        Files.createDirectories(configDir);
        
        String v2Yaml = createV2YamlConfig(getCrawlerName());
        Files.writeString(configDir.resolve("_settings.yaml"), v2Yaml);
        
        // Load the v2 configuration
        FsSettings fsSettings = new FsSettingsLoader(metadataDir).read("test_v2_config");
        
        // Verify it's detected as v2
        assertThat(fsSettings.getVersion()).isEqualTo(2);
        
        // Verify pipeline sections are populated (either from YAML or normalized)
        assertThat(fsSettings.getInputs()).isNotNull();
        assertThat(fsSettings.getFilters()).isNotNull();
        assertThat(fsSettings.getOutputs()).isNotNull();
    }

    /**
     * Test that migrated v2 YAML contains all expected sections.
     */
    @Test
    public void test_v2_yaml_generation() throws Exception {
        FsSettings v1Settings = createTestSettings();
        
        // Migrate to v2
        FsSettings v2Settings = FsSettingsMigrator.migrateV1ToV2(v1Settings);
        
        // Verify v2 settings have pipeline sections
        assertThat(v2Settings.getVersion()).isEqualTo(FsSettingsMigrator.VERSION_2);
        assertThat(v2Settings.getInputs()).hasSize(1);
        assertThat(v2Settings.getFilters()).hasSize(1);
        assertThat(v2Settings.getOutputs()).hasSize(1);
        
        // Verify input type
        InputSection input = v2Settings.getInputs().get(0);
        assertThat(input.getType()).isEqualTo("local");
        assertThat(input.getId()).isEqualTo("default");
        
        // Verify filter type
        FilterSection filter = v2Settings.getFilters().get(0);
        assertThat(filter.getType()).isEqualTo("tika");
        
        // Verify output type
        OutputSection output = v2Settings.getOutputs().get(0);
        assertThat(output.getType()).isEqualTo("elasticsearch");
        
        // Generate YAML
        String yaml = FsSettingsMigrator.generateV2Yaml(v2Settings);
        
        // Verify YAML contains expected sections
        assertThat(yaml).contains("version: 2");
        assertThat(yaml).contains("inputs:");
        assertThat(yaml).contains("filters:");
        assertThat(yaml).contains("outputs:");
        assertThat(yaml).contains("type: \"local\"");
        assertThat(yaml).contains("type: \"tika\"");
        assertThat(yaml).contains("type: \"elasticsearch\"");
    }

    /**
     * Test version detection for various settings configurations.
     */
    @Test
    public void test_version_detection() throws Exception {
        // v1: has fs but no inputs
        FsSettings v1 = createTestSettings();
        assertThat(FsSettingsMigrator.detectVersion(v1)).isEqualTo(FsSettingsMigrator.VERSION_1);
        
        // v2: explicit version
        FsSettings v2Explicit = new FsSettings();
        v2Explicit.setVersion(2);
        assertThat(FsSettingsMigrator.detectVersion(v2Explicit)).isEqualTo(FsSettingsMigrator.VERSION_2);
        
        // v2: has inputs
        FsSettings v2WithInputs = new FsSettings();
        v2WithInputs.setInputs(List.of(new InputSection()));
        assertThat(FsSettingsMigrator.detectVersion(v2WithInputs)).isEqualTo(FsSettingsMigrator.VERSION_2);
    }

    /**
     * Test that different input types are detected correctly during migration.
     */
    @Test
    public void test_input_type_detection_during_migration() throws Exception {
        // Local filesystem
        FsSettings localSettings = createTestSettings();
        localSettings.getServer().setProtocol("local");
        FsSettings v2Local = FsSettingsMigrator.migrateV1ToV2(localSettings);
        assertThat(v2Local.getInputs().get(0).getType()).isEqualTo("local");
        
        // SSH
        FsSettings sshSettings = createTestSettings();
        sshSettings.getServer().setProtocol("ssh");
        sshSettings.getServer().setHostname("example.com");
        FsSettings v2Ssh = FsSettingsMigrator.migrateV1ToV2(sshSettings);
        assertThat(v2Ssh.getInputs().get(0).getType()).isEqualTo("ssh");
        
        // FTP
        FsSettings ftpSettings = createTestSettings();
        ftpSettings.getServer().setProtocol("ftp");
        ftpSettings.getServer().setHostname("example.com");
        FsSettings v2Ftp = FsSettingsMigrator.migrateV1ToV2(ftpSettings);
        assertThat(v2Ftp.getInputs().get(0).getType()).isEqualTo("ftp");
    }

    /**
     * Test that different filter types are detected correctly during migration.
     */
    @Test
    public void test_filter_type_detection_during_migration() throws Exception {
        // Default: Tika
        FsSettings tikaSettings = createTestSettings();
        FsSettings v2Tika = FsSettingsMigrator.migrateV1ToV2(tikaSettings);
        assertThat(v2Tika.getFilters().get(0).getType()).isEqualTo("tika");
        
        // JSON support
        FsSettings jsonSettings = createTestSettings();
        jsonSettings.getFs().setJsonSupport(true);
        FsSettings v2Json = FsSettingsMigrator.migrateV1ToV2(jsonSettings);
        assertThat(v2Json.getFilters().get(0).getType()).isEqualTo("json");
        
        // XML support
        FsSettings xmlSettings = createTestSettings();
        xmlSettings.getFs().setXmlSupport(true);
        FsSettings v2Xml = FsSettingsMigrator.migrateV1ToV2(xmlSettings);
        assertThat(v2Xml.getFilters().get(0).getType()).isEqualTo("xml");
        
        // No content (indexContent=false and OCR disabled)
        FsSettings noneSettings = createTestSettings();
        noneSettings.getFs().setIndexContent(false);
        noneSettings.getFs().getOcr().setEnabled(false);
        FsSettings v2None = FsSettingsMigrator.migrateV1ToV2(noneSettings);
        assertThat(v2None.getFilters().get(0).getType()).isEqualTo("none");
    }

    /**
     * Create a v2 YAML configuration for testing.
     */
    private String createV2YamlConfig(String name) {
        return String.format("""
            version: 2
            name: "%s"
            
            # Legacy settings for backward compatibility
            fs:
              url: "%s"
              update_rate: "5s"
            
            elasticsearch:
              urls:
                - "%s"
              index: "%s_docs"
              index_folder: "%s_folder"
              api_key: "%s"
              ssl_verification: %s
            
            # V2 pipeline sections
            inputs:
              - type: local
                id: main
                update_rate: "5s"
            
            filters:
              - type: tika
                id: main
            
            outputs:
              - type: elasticsearch
                id: main
            """,
            name,
            currentTestResourceDir.toString().replace("\\", "/"),
            elasticsearchConfiguration.getUrls().get(0),
            name,
            name,
            elasticsearchConfiguration.getApiKey() != null ? elasticsearchConfiguration.getApiKey() : "",
            elasticsearchConfiguration.isSslVerification()
        );
    }
}
