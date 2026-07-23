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

import com.carrotsearch.randomizedtesting.jupiter.RandomizedTest;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerExtensionPasswordProvider;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPluginsManager;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class FsCrawlerCliPasswordNoopPluginTest extends AbstractFSCrawlerTestCase {

    @Test
    void builtInNoopPasswordPluginLoadsFromCliClasspath() throws Exception {
        FsSettings settings = FsSettingsLoader.load();
        String documentPath = RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 8, 16);

        try (FsCrawlerPluginsManager pluginsManager = new FsCrawlerPluginsManager()) {
            pluginsManager.loadPlugins();
            pluginsManager.startPlugins();
            pluginsManager.startPasswordProviders(settings);

            FsCrawlerExtensionPasswordProvider provider = pluginsManager.findPasswordProvider("noop");
            try (var session = provider.open(documentPath)) {
                Assertions.assertThat(session.next()).isEmpty();
            }
        }
    }
}
