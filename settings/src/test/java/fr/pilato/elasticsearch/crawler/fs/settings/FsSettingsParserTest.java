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
package fr.pilato.elasticsearch.crawler.fs.settings;

import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.assertj.core.api.Assertions;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FsSettingsParserTest extends AbstractFSCrawlerTestCase {
    private static final Logger logger = LogManager.getLogger();

    @ClassRule
    public static final TemporaryFolder folder = new TemporaryFolder();

    protected static Path rootTmpDir;

    @BeforeClass
    public static void createTmpDir() throws IOException {
        folder.create();
        rootTmpDir = Paths.get(folder.getRoot().toURI());
    }

    private void settingsTester(FsSettings source) throws IOException {
        String yaml = FsSettingsParser.toYaml(source);
        logger.debug("generated yaml:\n{}", yaml);
        Path settingsFile = rootTmpDir.resolve("settings.yaml");
        Files.writeString(settingsFile, yaml);
        FsSettings generated = FsSettingsLoader.load(settingsFile);
        checkSettings(source, generated);
    }

    private void checkSettings(FsSettings expected, FsSettings settings) {
        logger.debug("Settings loaded: {}", settings);
        logger.debug("Settings expected: {}", expected);

        if (expected.getFs() != null) {
            Assertions.assertThat(settings.getFs().getOcr())
                    .as("Checking Ocr")
                    .isEqualTo(expected.getFs().getOcr());
        }
        Assertions.assertThat(settings.getFs()).as("Checking Fs").isEqualTo(expected.getFs());
        Assertions.assertThat(settings.getTags()).as("Checking Tags").isEqualTo(expected.getTags());
        Assertions.assertThat(settings.getServer()).as("Checking Server").isEqualTo(expected.getServer());
        Assertions.assertThat(settings.getElasticsearch())
                .as("Checking Elasticsearch")
                .isEqualTo(expected.getElasticsearch());
        Assertions.assertThat(settings.getRest()).as("Checking Rest").isEqualTo(expected.getRest());
        Assertions.assertThat(settings).as("Checking whole settings").isEqualTo(expected);
    }

    @Test
    public void parseEmptySettings() throws IOException {
        settingsTester(FsSettingsLoader.load());
    }

    @Test
    public void parseSettingsElasticsearchTwoNodes() throws IOException {
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getElasticsearch().setUrls(List.of("https://127.0.0.1:9200", "https://127.0.0.1:9201"));
        settingsTester(fsSettings);
    }

    @Test
    public void parseSettingsElasticsearchWithPathPrefix() throws IOException {
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getElasticsearch().setPathPrefix("/path/to/elasticsearch");
        settingsTester(fsSettings);
    }

    @Test
    public void parseSettingsWithStaticMetadata() throws IOException {
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getTags().setStaticMetaFilename("/path/to/metadatafile.yml");
        settingsTester(fsSettings);
    }

    @Test
    public void parseSettingsWithAclSupport() throws IOException {
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getFs().setAttributesSupport(true);
        fsSettings.getFs().setAclSupport(true);
        settingsTester(fsSettings);
    }
}
