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

import fr.pilato.elasticsearch.crawler.fs.settings.ChainedPasswordProviderSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.PasswordProviders;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerExtensionPasswordProvider;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPlugin;
import fr.pilato.elasticsearch.crawler.plugins.PasswordProviderLookup;
import fr.pilato.elasticsearch.crawler.plugins.PasswordSession;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.pf4j.Extension;

public class PasswordChainedPlugin extends FsCrawlerPlugin {

    @Override
    protected String getName() {
        return "password-chained";
    }

    @Extension
    public static class Provider implements FsCrawlerExtensionPasswordProvider {

        private static final Logger logger = LogManager.getLogger();

        private List<String> providerTypes = List.of();
        private PasswordProviderLookup lookup = type -> null;

        @Override
        public String getType() {
            return "chained";
        }

        @Override
        public void start(FsSettings settings, PasswordProviderLookup lookup) {
            providerTypes = resolveProviderTypes(settings);
            this.lookup = lookup;
        }

        @Override
        public PasswordSession open(String documentPath) {
            return new ChainedPasswordSession(documentPath, providerTypes, lookup);
        }

        @Override
        public void close() {
            providerTypes = List.of();
            lookup = type -> null;
        }

        private static List<String> resolveProviderTypes(FsSettings settings) {
            if (settings == null || settings.getPasswords() == null) {
                return List.of();
            }

            PasswordProviders providers = settings.getPasswords().getProviders();
            if (providers == null) {
                return List.of();
            }

            ChainedPasswordProviderSettings chained = providers.getChained();
            if (chained == null || chained.getProviders() == null) {
                return List.of();
            }

            return List.copyOf(chained.getProviders());
        }

        private static class ChainedPasswordSession implements PasswordSession {

            private final String documentPath;
            private final List<String> providerTypes;
            private final PasswordProviderLookup lookup;
            private int index;
            private PasswordSession currentSession;

            private ChainedPasswordSession(
                    String documentPath, List<String> providerTypes, PasswordProviderLookup lookup) {
                this.documentPath = documentPath;
                this.providerTypes = providerTypes;
                this.lookup = lookup;
            }

            @Override
            public Optional<String> next() {
                while (true) {
                    if (currentSession == null && !openNextSession()) {
                        return Optional.empty();
                    }

                    Optional<String> password = currentSession.next();
                    if (password.isPresent()) {
                        return password;
                    }

                    closeCurrentSession();
                }
            }

            @Override
            public void close() {
                closeCurrentSession();
            }

            private boolean openNextSession() {
                while (index < providerTypes.size()) {
                    String providerType = providerTypes.get(index++);
                    FsCrawlerExtensionPasswordProvider provider = lookup.get(providerType);
                    if (provider == null) {
                        logger.warn("Can not find chained password provider [{}]; skipping.", providerType);
                        continue;
                    }

                    currentSession = provider.open(documentPath);
                    return true;
                }

                return false;
            }

            private void closeCurrentSession() {
                if (currentSession == null) {
                    return;
                }

                currentSession.close();
                currentSession = null;
            }
        }
    }
}
