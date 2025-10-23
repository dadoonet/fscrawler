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

package fr.pilato.elasticsearch.crawler.fs.settings;

import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
        Path settingsFile = rootTmpDir.resolve("settings.yaml");
        Files.writeString(settingsFile, yaml);
        FsSettings generated = FsSettingsLoader.load(settingsFile);
        checkSettings(source, generated);
    }

    private void checkSettings(FsSettings expected, FsSettings settings) {
        logger.debug("Settings loaded: {}", settings);
        logger.debug("Settings expected: {}", expected);

        if (expected.getFs() != null) {
            assertThat(settings.getFs().getOcr()).as("Checking Ocr").isEqualTo(expected.getFs().getOcr());
        }
        assertThat(settings.getFs()).as("Checking Fs").isEqualTo(expected.getFs());
        assertThat(settings.getServer()).as("Checking Server").isEqualTo(expected.getServer());
        assertThat(settings.getTags()).as("Checking Tags").isEqualTo(expected.getTags());
        assertThat(settings.getElasticsearch()).as("Checking Elasticsearch").isEqualTo(expected.getElasticsearch());
        assertThat(settings.getRest()).as("Checking Rest").isEqualTo(expected.getRest());
        assertThat(settings).as("Checking whole settings").isEqualTo(expected);
    }

    @Test
    public void parseEmptySettings() throws IOException {
        settingsTester(FsSettingsLoader.load());
    }

    @Test
    public void parseSettingsElasticsearchTwoNodes() throws IOException {
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getElasticsearch().setNodes(Arrays.asList(
                new ServerUrl("https://127.0.0.1:9200"),
                new ServerUrl("https://127.0.0.1:9201")));
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
        // Get the existing tags (which has default metaFilename) or create new one
        Tags tags = fsSettings.getTags();
        if (tags == null) {
            tags = new Tags();
        }
        Map<String, Object> staticMetadata = new HashMap<>();
        Map<String, Object> external = new HashMap<>();
        external.put("hostname", "server001");
        external.put("environment", "production");
        staticMetadata.put("external", external);
        tags.setStaticMetadata(staticMetadata);
        fsSettings.setTags(tags);
        settingsTester(fsSettings);
    }
}
