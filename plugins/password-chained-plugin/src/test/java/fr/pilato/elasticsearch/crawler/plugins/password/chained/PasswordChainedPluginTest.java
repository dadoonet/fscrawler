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
package fr.pilato.elasticsearch.crawler.plugins.password.chained;

import com.carrotsearch.randomizedtesting.jupiter.RandomizedTest;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.Passwords;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerExtensionPasswordProvider;
import fr.pilato.elasticsearch.crawler.plugins.PasswordProviderLookup;
import fr.pilato.elasticsearch.crawler.plugins.PasswordSession;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class PasswordChainedPluginTest extends AbstractFSCrawlerTestCase {

    @Test
    void providerExhaustsEachChildSessionBeforeOpeningTheNextOne() throws Exception {
        String firstType = randomToken();
        String secondType = randomToken();
        String firstPassword = randomPassword();
        String secondPassword = randomPassword();
        String thirdPassword = randomPassword();
        String documentPath = randomToken();
        List<String> events = new ArrayList<>();

        PasswordProviderLookup lookup = Map.of(
                firstType,
                new StubProvider(firstType, List.of(firstPassword, secondPassword), events),
                secondType,
                new StubProvider(secondType, List.of(thirdPassword), events))::get;

        FsCrawlerExtensionPasswordProvider provider = instantiateProvider();
        provider.start(settings("chained", firstType, secondType), lookup);

        try (var session = provider.open(documentPath)) {
            Assertions.assertThat(provider.getType()).isEqualTo("chained");
            Assertions.assertThat(session.next()).contains(firstPassword);
            Assertions.assertThat(events).containsExactly("open:" + firstType + ":" + documentPath);

            Assertions.assertThat(session.next()).contains(secondPassword);
            Assertions.assertThat(events).containsExactly("open:" + firstType + ":" + documentPath);

            Assertions.assertThat(session.next()).contains(thirdPassword);
            Assertions.assertThat(events)
                    .containsExactly(
                            "open:" + firstType + ":" + documentPath,
                            "close-session:" + firstType,
                            "open:" + secondType + ":" + documentPath);

            Assertions.assertThat(session.next()).isEmpty();
            Assertions.assertThat(events)
                    .containsExactly(
                            "open:" + firstType + ":" + documentPath,
                            "close-session:" + firstType,
                            "open:" + secondType + ":" + documentPath,
                            "close-session:" + secondType);
        } finally {
            provider.close();
        }
    }

    @Test
    void providerStartFailsWhenAChainedChildProviderIsUnknown() throws Exception {
        String knownType = randomToken();
        String missingType = randomToken();
        PasswordProviderLookup lookup =
                Map.of(knownType, new StubProvider(knownType, List.of(randomPassword()), new ArrayList<>()))::get;

        FsCrawlerExtensionPasswordProvider provider = instantiateProvider();
        try {
            Assertions.assertThatThrownBy(() -> provider.start(settings("chained", knownType, missingType), lookup))
                    .isInstanceOf(FsCrawlerIllegalConfigurationException.class)
                    .hasMessage("passwords.providers.chained.providers contains unknown password provider ["
                            + missingType + "]");
        } finally {
            provider.close();
        }
    }

    @Test
    void providerStartFailsWhenActiveChainedHasNoProviders() throws Exception {
        FsCrawlerExtensionPasswordProvider provider = instantiateProvider();
        try {
            Assertions.assertThatThrownBy(() -> provider.start(settings("chained"), type -> null))
                    .isInstanceOf(FsCrawlerIllegalConfigurationException.class)
                    .hasMessage("passwords.provider [chained] requires passwords.providers.chained.providers");
        } finally {
            provider.close();
        }
    }

    @Test
    void providerStartFailsWhenChainedProvidersContainSelfReference() throws Exception {
        FsCrawlerExtensionPasswordProvider provider = instantiateProvider();
        try {
            Assertions.assertThatThrownBy(() -> provider.start(settings("chained", "chained"), type -> null))
                    .isInstanceOf(FsCrawlerIllegalConfigurationException.class)
                    .hasMessage("passwords.providers.chained.providers cannot contain [chained]");
        } finally {
            provider.close();
        }
    }

    private FsSettings settings(String activeProvider, String... chainedProviders) {
        Passwords passwords = new Passwords();
        passwords.setProvider(activeProvider);
        if (chainedProviders.length > 0) {
            passwords.setProviders(Map.of("chained", Map.of("providers", List.of(chainedProviders))));
        } else {
            passwords.setProviders(Map.of("chained", Map.of()));
        }

        FsSettings settings = new FsSettings();
        settings.setPasswords(passwords);
        return settings;
    }

    private static FsCrawlerExtensionPasswordProvider instantiateProvider() throws Exception {
        return (FsCrawlerExtensionPasswordProvider)
                Class.forName("fr.pilato.elasticsearch.crawler.plugins.password.chained.PasswordChainedPlugin$Provider")
                        .getDeclaredConstructor()
                        .newInstance();
    }

    private String randomToken() {
        return RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 6, 12)
                .toLowerCase(Locale.ROOT);
    }

    private String randomPassword() {
        return RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 8, 16);
    }

    private static class StubProvider implements FsCrawlerExtensionPasswordProvider {

        private final String type;
        private final List<String> passwords;
        private final List<String> events;

        private StubProvider(String type, List<String> passwords, List<String> events) {
            this.type = type;
            this.passwords = passwords;
            this.events = events;
        }

        @Override
        public String getType() {
            return type;
        }

        @Override
        public void start(FsSettings settings, PasswordProviderLookup lookup) {}

        @Override
        public PasswordSession open(String documentPath) {
            events.add("open:" + type + ":" + documentPath);

            return new PasswordSession() {
                private int index;
                private boolean closed;

                @Override
                public Optional<String> next() {
                    return index < passwords.size() ? Optional.of(passwords.get(index++)) : Optional.empty();
                }

                @Override
                public void close() {
                    if (closed) {
                        return;
                    }
                    closed = true;
                    events.add("close-session:" + type);
                }
            };
        }

        @Override
        public void close() {}
    }
}
