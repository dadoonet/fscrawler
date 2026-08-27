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

import com.carrotsearch.randomizedtesting.jupiter.RandomizedTest;
import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@SuppressWarnings("removal")
class RemoteProviderCrawlerStartTest extends AbstractFSCrawlerTestCase {

    @Test
    void crawlerStartReadsFsSshWithoutValidatingAFile() {
        String hostname = randomToken() + ".example.com";
        String username = randomToken();
        int port = RandomizedTest.randomIntInRange(randomizedRandomForTests, 1024, 65535);

        FsSettings settings = FsSettingsLoader.load();
        Map<String, Object> ssh = new LinkedHashMap<>();
        ssh.put("hostname", hostname);
        ssh.put("port", port);
        ssh.put("username", username);
        settings.getFs().setProviders(Map.of("ssh", ssh));

        RecordingRemoteProvider provider = new RecordingRemoteProvider("ssh");
        provider.start(settings, "{}");

        Assertions.assertThat(provider.getEffectiveHostname()).isEqualTo(hostname);
        Assertions.assertThat(provider.getEffectivePort()).isEqualTo(port);
        Assertions.assertThat(provider.getEffectiveUsername()).isEqualTo(username);
        Assertions.assertThat(provider.validatedFile.get()).isFalse();
        Assertions.assertThat(provider.openedConnection.get()).isFalse();
    }

    @Test
    void crawlerStartFallsBackToDeprecatedServer() {
        String hostname = randomToken() + ".example.com";
        FsSettings settings = FsSettingsLoader.load();
        settings.getServer().setHostname(hostname);
        settings.getServer().setUsername(randomToken());

        RecordingRemoteProvider provider = new RecordingRemoteProvider("ssh");
        provider.start(settings, "{}");

        Assertions.assertThat(provider.getEffectiveHostname()).isEqualTo(hostname);
        Assertions.assertThat(provider.validatedFile.get()).isFalse();
    }

    private String randomToken() {
        return RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 6, 12)
                .toLowerCase(Locale.ROOT);
    }

    private static final class RecordingRemoteProvider extends FsCrawlerExtensionRemoteProviderAbstract {
        private final String type;
        private final AtomicBoolean validatedFile = new AtomicBoolean();
        private final AtomicBoolean openedConnection = new AtomicBoolean();

        private RecordingRemoteProvider(String type) {
            this.type = type;
        }

        @Override
        public String getType() {
            return type;
        }

        @Override
        protected long getFilesize() {
            return 0;
        }

        @Override
        protected void doValidateFile() {
            validatedFile.set(true);
        }

        @Override
        public void openConnection() {
            openedConnection.set(true);
        }

        @Override
        public InputStream readFile() {
            return InputStream.nullInputStream();
        }

        @Override
        public Doc createDocument() {
            return new Doc();
        }
    }
}
