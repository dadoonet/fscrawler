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
package fr.pilato.elasticsearch.crawler.fs;

import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.settings.Passwords;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerExtensionPasswordProvider;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPluginsManager;
import fr.pilato.elasticsearch.crawler.plugins.PasswordProviderLookup;
import fr.pilato.elasticsearch.crawler.plugins.PasswordSession;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.Test;

class FsCrawlerImplTest extends AbstractFSCrawlerTestCase {
    @SuppressWarnings("resource")
    @Test
    void checksum_non_existing_algorithm() {
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getFs().setChecksum("FSCRAWLER");
        AssertionsForClassTypes.assertThatExceptionOfType(FsCrawlerIllegalConfigurationException.class)
                .isThrownBy(() -> new FsCrawlerImpl(rootTmpDir, fsSettings, FsCrawlerImpl.LOOP_INFINITE, false));
    }

    /**
     * Test case for issue #185: <a
     * href="https://github.com/dadoonet/fscrawler/issues/185">https://github.com/dadoonet/fscrawler/issues/185</a> :
     * Add xml_support setting
     */
    @SuppressWarnings("resource")
    @Test
    void xml_and_json_enabled() {
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getFs().setXmlSupport(true);
        fsSettings.getFs().setJsonSupport(true);
        AssertionsForClassTypes.assertThatExceptionOfType(FsCrawlerIllegalConfigurationException.class)
                .isThrownBy(() -> new FsCrawlerImpl(rootTmpDir, fsSettings, FsCrawlerImpl.LOOP_INFINITE, false));
    }

    @SuppressWarnings("resource")
    @Test
    void unknown_password_provider_fails_fast() {
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.setName(jobName);
        Passwords passwords = new Passwords();
        passwords.setProvider("missing-password-provider");
        fsSettings.setPasswords(passwords);

        Assertions.assertThatThrownBy(() -> resolvePasswordProvider(new StubPluginsManager(), fsSettings))
                .isInstanceOf(FsCrawlerIllegalConfigurationException.class)
                .hasMessage("No PasswordProvider found for type [missing-password-provider]");
    }

    @Test
    void null_passwords_section_resolves_noop_provider() throws Exception {
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.setName(jobName);
        fsSettings.setPasswords(null);

        FsCrawlerExtensionPasswordProvider passwordProvider =
                resolvePasswordProvider(new StubPluginsManager(), fsSettings);
        Assertions.assertThat(passwordProvider).isNotNull();
        Assertions.assertThat(passwordProvider.getType()).isEqualTo("noop");
    }

    private static FsCrawlerExtensionPasswordProvider resolvePasswordProvider(
            FsCrawlerPluginsManager pluginsManager, FsSettings fsSettings) throws Exception {
        Method resolvePasswordProvider = FsCrawlerImpl.class.getDeclaredMethod(
                "resolvePasswordProvider", FsCrawlerPluginsManager.class, FsSettings.class);
        resolvePasswordProvider.setAccessible(true);
        try {
            return (FsCrawlerExtensionPasswordProvider)
                    resolvePasswordProvider.invoke(null, pluginsManager, fsSettings);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception exception) {
                throw exception;
            }
            throw e;
        }
    }

    private static class StubPluginsManager extends FsCrawlerPluginsManager {
        @Override
        public void startPasswordProviders(FsSettings settings) {
            // Stub: password providers are resolved via findPasswordProvider only.
        }

        @Override
        public FsCrawlerExtensionPasswordProvider findPasswordProvider(String type) {
            if ("noop".equals(type)) {
                return new StubPasswordProvider(type);
            }
            throw new FsCrawlerIllegalConfigurationException("No PasswordProvider found for type [" + type + "]");
        }
    }

    private record StubPasswordProvider(String type) implements FsCrawlerExtensionPasswordProvider {
        @Override
        public String getType() {
            return type;
        }

        @Override
        public void start(FsSettings settings, PasswordProviderLookup lookup) {
            // Stub noop provider needs no startup work.
        }

        @Override
        public PasswordSession open(String documentPath) {
            return new PasswordSession() {
                @Override
                public java.util.Optional<String> next() {
                    return java.util.Optional.empty();
                }

                @Override
                public void close() {
                    // Stub session has no resources.
                }
            };
        }

        @Override
        public void close() {
            // Stub provider has no resources.
        }
    }
}
