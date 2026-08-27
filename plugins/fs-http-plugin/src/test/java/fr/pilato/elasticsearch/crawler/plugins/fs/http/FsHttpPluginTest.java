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
package fr.pilato.elasticsearch.crawler.plugins.fs.http;

import com.carrotsearch.randomizedtesting.jupiter.RandomizedTest;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import java.util.Locale;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class FsHttpPluginTest extends AbstractFSCrawlerTestCase {

    @Test
    void crawlerStartDoesNotRequireUrl() {
        FsHttpPlugin.FsCrawlerExtensionFsProviderHttp plugin = new FsHttpPlugin.FsCrawlerExtensionFsProviderHttp();
        plugin.start(FsSettingsLoader.load());
        Assertions.assertThat(plugin.getType()).isEqualTo("http");
    }

    @Test
    void overlayWithoutUrlFails() {
        FsHttpPlugin.FsCrawlerExtensionFsProviderHttp plugin = new FsHttpPlugin.FsCrawlerExtensionFsProviderHttp();
        String extraField = randomToken();
        String extraValue = randomToken();

        Assertions.assertThatThrownBy(() -> plugin.start(FsSettingsLoader.load(), Map.of(extraField, extraValue)))
                .isInstanceOf(FsCrawlerIllegalConfigurationException.class)
                .hasMessageContaining("HTTP URL is missing");
    }

    private String randomToken() {
        return RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 6, 12)
                .toLowerCase(Locale.ROOT);
    }
}
