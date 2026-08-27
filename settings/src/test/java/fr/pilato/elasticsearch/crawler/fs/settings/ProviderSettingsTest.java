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

import com.carrotsearch.randomizedtesting.jupiter.RandomizedTest;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class ProviderSettingsTest extends AbstractFSCrawlerTestCase {

    @Test
    void prefersOverlayOverJobOverDeprecatedServer() {
        String type = randomToken();
        String field = randomToken();
        String overlayValue = "overlay-" + randomToken();
        String jobValue = "job-" + randomToken();
        String serverValue = "server-" + randomToken();

        FsSettings settings = jobWith(type, field, jobValue);
        ProviderSettings lookup = ProviderSettings.of(type, settings, Map.of(field, overlayValue));

        Assertions.assertThat(lookup.string(field, serverValue)).isEqualTo(overlayValue);
        Assertions.assertThat(lookup.deprecationWarnings())
                .anyMatch(msg -> msg.contains("server." + field)
                        && msg.contains("fs.providers." + type + "." + field)
                        && msg.contains("ignored")
                        && !msg.contains(serverValue));
    }

    @Test
    void fallsBackToDeprecatedServerWithQuotedHint() {
        String type = randomToken();
        String field = randomToken();
        String serverValue = randomToken();

        ProviderSettings lookup = ProviderSettings.of(type, FsSettingsLoader.load());

        Assertions.assertThat(lookup.string(field, serverValue)).isEqualTo(serverValue);
        Assertions.assertThat(lookup.deprecationWarnings())
                .containsExactly("Setting server." + field
                        + " is deprecated and will be removed in a future version. Please use fs.providers." + type
                        + "." + field + ": \"" + serverValue + "\" instead.");
    }

    @Test
    void secretNeverEchoesDeprecatedValue() {
        String type = randomToken();
        String field = randomToken();
        String serverSecret = randomToken();

        ProviderSettings lookup = ProviderSettings.of(type, FsSettingsLoader.load());

        Assertions.assertThat(lookup.secret(field, serverSecret)).isEqualTo(serverSecret);
        Assertions.assertThat(lookup.deprecationWarnings())
                .containsExactly("Setting server." + field
                        + " is deprecated and will be removed in a future version. Please use fs.providers." + type
                        + "." + field + " instead.")
                .noneMatch(msg -> msg.contains(serverSecret));
    }

    @Test
    void integerUsesDefaultWhenMissingAndDoesNotAssumeSshOrFtp() {
        String type = randomToken();
        String field = randomToken();
        int defaultValue = RandomizedTest.randomIntInRange(randomizedRandomForTests, 1, 65535);

        ProviderSettings lookup = ProviderSettings.of(type, FsSettingsLoader.load());

        Assertions.assertThat(lookup.integer(field, defaultValue)).isEqualTo(defaultValue);
        Assertions.assertThat(lookup.deprecationWarnings()).isEmpty();
    }

    @Test
    void integerPrefersJobThenDeprecatedServer() {
        String type = randomToken();
        String field = randomToken();
        int jobValue = RandomizedTest.randomIntInRange(randomizedRandomForTests, 2000, 3000);
        int serverValue = RandomizedTest.randomIntInRange(randomizedRandomForTests, 4000, 5000);
        int defaultValue = RandomizedTest.randomIntInRange(randomizedRandomForTests, 1, 1000);

        FsSettings settings = jobWith(type, field, jobValue);
        ProviderSettings lookup = ProviderSettings.of(type, settings);

        Assertions.assertThat(lookup.integer(field, defaultValue, serverValue)).isEqualTo(jobValue);
        Assertions.assertThat(lookup.deprecationWarnings())
                .anyMatch(msg -> msg.contains("server." + field)
                        && msg.contains("fs.providers." + type + "." + field)
                        && msg.contains("ignored"));
    }

    @Test
    void integerFallsBackToDeprecatedServerWithoutQuotes() {
        String type = randomToken();
        String field = randomToken();
        int serverValue = RandomizedTest.randomIntInRange(randomizedRandomForTests, 1024, 65535);
        int defaultValue = RandomizedTest.randomIntInRange(randomizedRandomForTests, 1, 1000);

        ProviderSettings lookup = ProviderSettings.of(type, FsSettingsLoader.load());

        Assertions.assertThat(lookup.integer(field, defaultValue, serverValue)).isEqualTo(serverValue);
        Assertions.assertThat(lookup.deprecationWarnings())
                .containsExactly("Setting server." + field
                        + " is deprecated and will be removed in a future version. Please use fs.providers." + type
                        + "." + field + ": " + serverValue + " instead.");
    }

    @Test
    void overlayOnlyIgnoresJobAndServer() {
        String type = randomToken();
        String field = randomToken();
        String overlayValue = "overlay-" + randomToken();
        String jobValue = "job-" + randomToken();

        FsSettings settings = jobWith(type, field, jobValue);
        ProviderSettings lookup = ProviderSettings.of(type, settings, Map.of(field, overlayValue));

        Assertions.assertThat(lookup.overlayString(field)).isEqualTo(overlayValue);
        Assertions.assertThat(lookup.overlayString("missing-" + randomToken())).isNull();
        Assertions.assertThat(lookup.deprecationWarnings()).isEmpty();
    }

    @Test
    void stringDefaultAppliesWhenNothingIsSet() {
        String type = randomToken();
        String field = randomToken();
        String defaultValue = randomToken();

        ProviderSettings lookup = ProviderSettings.of(type, FsSettingsLoader.load());

        Assertions.assertThat(lookup.string(field, null, defaultValue)).isEqualTo(defaultValue);
        Assertions.assertThat(lookup.deprecationWarnings()).isEmpty();
    }

    @Test
    void pemPathIsAnOrdinaryStringFieldForAnyType() {
        String type = randomToken();
        String pemPath = "/keys/" + randomToken() + ".pem";
        FsSettings settings = jobWith(type, "pem_path", pemPath);

        ProviderSettings lookup = ProviderSettings.of(type, settings);

        Assertions.assertThat(lookup.string("pem_path")).isEqualTo(pemPath);
        Assertions.assertThat(lookup.deprecationWarnings()).isEmpty();
    }

    @Test
    void doesNotWarnWhenDeprecatedServerValueIsAbsent() {
        String type = randomToken();
        String field = randomToken();
        String jobValue = randomToken();

        FsSettings settings = jobWith(type, field, jobValue);
        ProviderSettings lookup = ProviderSettings.of(type, settings);

        Assertions.assertThat(lookup.string(field)).isEqualTo(jobValue);
        Assertions.assertThat(lookup.deprecationWarnings()).isEmpty();
    }

    private static FsSettings jobWith(String type, String field, Object value) {
        FsSettings settings = FsSettingsLoader.load();
        Map<String, Object> config = new LinkedHashMap<>();
        config.put(field, value);
        settings.getFs().setProviders(Map.of(type, config));
        return settings;
    }

    private String randomToken() {
        return RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 6, 12)
                .toLowerCase(Locale.ROOT);
    }
}
