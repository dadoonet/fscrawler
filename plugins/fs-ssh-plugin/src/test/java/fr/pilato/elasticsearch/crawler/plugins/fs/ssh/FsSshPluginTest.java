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
package fr.pilato.elasticsearch.crawler.plugins.fs.ssh;

import com.carrotsearch.randomizedtesting.jupiter.RandomizedTest;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import java.util.Locale;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class FsSshPluginTest extends AbstractFSCrawlerTestCase {
    @Test
    void getType() {
        FsSshPlugin.FsCrawlerExtensionFsProviderSsh sshPlugin = new FsSshPlugin.FsCrawlerExtensionFsProviderSsh();
        Assertions.assertThat(sshPlugin.getType()).isEqualTo("ssh");
    }

    @Test
    void crawlerStartDefaultsSftpUrlOnPort22() {
        String hostname = randomToken() + ".example.com";
        String username = randomToken();
        FsSettings settings = FsSettingsLoader.load();
        settings.getFs().setProviders(Map.of("ssh", Map.of("hostname", hostname, "username", username)));

        FsSshPlugin.FsCrawlerExtensionFsProviderSsh sshPlugin = new FsSshPlugin.FsCrawlerExtensionFsProviderSsh();
        sshPlugin.start(settings);

        Assertions.assertThat(sshPlugin.toFileUrl("/doc.pdf"))
                .isEqualTo("sftp://" + hostname + ":" + FsSshPlugin.FsCrawlerExtensionFsProviderSsh.DEFAULT_PORT
                        + "/doc.pdf");
    }

    @Test
    void crawlerStartRequiresHostname() {
        FsSettings settings = FsSettingsLoader.load();
        FsSshPlugin.FsCrawlerExtensionFsProviderSsh sshPlugin = new FsSshPlugin.FsCrawlerExtensionFsProviderSsh();

        Assertions.assertThatThrownBy(() -> sshPlugin.start(settings))
                .isInstanceOf(FsCrawlerIllegalConfigurationException.class)
                .hasMessageContaining("fs.providers.ssh.hostname");
    }

    @Test
    void crawlerStartRequiresUsername() {
        String hostname = randomToken() + ".example.com";
        FsSettings settings = FsSettingsLoader.load();
        settings.getFs().setProviders(Map.of("ssh", Map.of("hostname", hostname)));

        FsSshPlugin.FsCrawlerExtensionFsProviderSsh sshPlugin = new FsSshPlugin.FsCrawlerExtensionFsProviderSsh();

        Assertions.assertThatThrownBy(() -> sshPlugin.start(settings))
                .isInstanceOf(FsCrawlerIllegalConfigurationException.class)
                .hasMessageContaining("fs.providers.ssh.username");
    }

    @Test
    void crawlerStartRejectsBlankUsername() {
        String hostname = randomToken() + ".example.com";
        FsSettings settings = FsSettingsLoader.load();
        settings.getFs().setProviders(Map.of("ssh", Map.of("hostname", hostname, "username", "")));

        FsSshPlugin.FsCrawlerExtensionFsProviderSsh sshPlugin = new FsSshPlugin.FsCrawlerExtensionFsProviderSsh();

        Assertions.assertThatThrownBy(() -> sshPlugin.start(settings))
                .isInstanceOf(FsCrawlerIllegalConfigurationException.class)
                .hasMessageContaining("fs.providers.ssh.username");
    }

    @Test
    void overlayWithoutPathFails() {
        String hostname = randomToken() + ".example.com";
        String username = randomToken();
        FsSettings settings = FsSettingsLoader.load();
        settings.getFs().setProviders(Map.of("ssh", Map.of("hostname", hostname, "username", username)));

        FsSshPlugin.FsCrawlerExtensionFsProviderSsh sshPlugin = new FsSshPlugin.FsCrawlerExtensionFsProviderSsh();

        Assertions.assertThatThrownBy(
                        () -> sshPlugin.start(settings, Map.of("hostname", hostname, "username", username)))
                .isInstanceOf(FsCrawlerIllegalConfigurationException.class)
                .hasMessageContaining("ssh path is missing");
    }

    private String randomToken() {
        return RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 6, 12)
                .toLowerCase(Locale.ROOT);
    }
}
