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
package fr.pilato.elasticsearch.crawler.fs.cli;

import fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import java.io.IOException;
import java.nio.file.Files;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class FsCrawlerJobContainmentTest extends AbstractFSCrawlerTestCase {
    @Test
    void cliCreationRejectsTraversalBeforeCreatingDirectory() throws Exception {
        var root = Files.createDirectory(rootTmpDir.resolve("metadata"));
        Assertions.assertThatThrownBy(() -> FsCrawlerCli.createJob("../outside", root))
                .isInstanceOf(IOException.class);
        Assertions.assertThat(Files.exists(rootTmpDir.resolve("outside"))).isFalse();
    }

    @Test
    void settingsLookupRejectsTraversalInsteadOfFallingBackToDefaults() throws Exception {
        var root = Files.createDirectory(rootTmpDir.resolve("metadata"));
        Assertions.assertThatThrownBy(() -> new FsSettingsLoader(root).read("../outside"))
                .isInstanceOf(IOException.class);
    }

    @Test
    void crawlerConstructionRejectsTraversalBeforeAcquiringResources() throws Exception {
        var root = Files.createDirectory(rootTmpDir.resolve("metadata"));
        var settings = FsSettingsLoader.load();
        settings.setName("../outside");
        Assertions.assertThatThrownBy(() -> {
                    try (FsCrawlerImpl crawler = new FsCrawlerImpl(root, settings, 0, false)) {}
                })
                .isInstanceOf(FsCrawlerIllegalConfigurationException.class);
        Assertions.assertThat(Files.exists(rootTmpDir.resolve("outside"))).isFalse();
    }
}
