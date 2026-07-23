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
package fr.pilato.elasticsearch.crawler.plugins;

import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FsCrawlerPluginsManagerPasswordProviderTest extends AbstractFSCrawlerTestCase {

    @BeforeEach
    void resetProviderState() {
        TestPasswordProvider.reset();
    }

    @Test
    void findsAndStartsPasswordProviderFromSpi() {
        FsSettings settings = FsSettingsLoader.load();

        try (FsCrawlerPluginsManager pluginsManager = new FsCrawlerPluginsManager()) {
            pluginsManager.loadPlugins();
            pluginsManager.startPlugins();
            pluginsManager.startPasswordProviders(settings);

            FsCrawlerExtensionPasswordProvider provider = pluginsManager.findPasswordProvider("test-pwd");
            Assertions.assertThat(provider).isInstanceOf(TestPasswordProvider.class);
            Assertions.assertThat(TestPasswordProvider.startCalls()).isEqualTo(1);
            Assertions.assertThat(TestPasswordProvider.lastSettings()).isSameAs(settings);
            Assertions.assertThat(TestPasswordProvider.lastLookup()).isNotNull();
            Assertions.assertThat(TestPasswordProvider.lastLookup().get("test-pwd"))
                    .isSameAs(provider);
        }

        Assertions.assertThat(TestPasswordProvider.closeCalls()).isEqualTo(1);
    }

    @Test
    void throwsWhenPasswordProviderTypeIsUnknown() {
        try (FsCrawlerPluginsManager pluginsManager = new FsCrawlerPluginsManager()) {
            pluginsManager.loadPlugins();
            pluginsManager.startPlugins();

            Assertions.assertThatThrownBy(() -> pluginsManager.findPasswordProvider("missing-password-provider"))
                    .isInstanceOf(FsCrawlerIllegalConfigurationException.class)
                    .hasMessage("No PasswordProvider found for type [missing-password-provider]");
        }
    }
}
