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

import static org.junit.Assert.assertEquals;

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
            assertEquals("Checking Ocr", expected.getFs().getOcr(), settings.getFs().getOcr());
        }
        assertEquals("Checking Fs", expected.getFs(), settings.getFs());
        assertEquals("Checking Server", expected.getServer(), settings.getServer());
        assertEquals("Checking Tags", expected.getTags(), settings.getTags());
        assertEquals("Checking Elasticsearch", expected.getElasticsearch(), settings.getElasticsearch());
        assertEquals("Checking Rest", expected.getRest(), settings.getRest());
        assertEquals("Checking whole settings", expected, settings);
    }

    @Test
    public void testParseEmptySettings() throws IOException {
        settingsTester(FsSettingsLoader.load());
    }

    @Test
    public void testParseSettingsElasticsearchTwoNodes() throws IOException {
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getElasticsearch().setNodes(Arrays.asList(
                new ServerUrl("https://127.0.0.1:9200"),
                new ServerUrl("https://127.0.0.1:9201")));
        settingsTester(fsSettings);
    }

    @Test
    public void testParseSettingsElasticsearchWithPathPrefix() throws IOException {
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getElasticsearch().setPathPrefix("/path/to/elasticsearch");
        settingsTester(fsSettings);
    }
}
