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
package fr.pilato.elasticsearch.crawler.plugins.password.staticprovider;

import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.PasswordProviders;
import fr.pilato.elasticsearch.crawler.fs.settings.StaticPasswordProviderSettings;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerExtensionPasswordProvider;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPlugin;
import fr.pilato.elasticsearch.crawler.plugins.PasswordProviderLookup;
import fr.pilato.elasticsearch.crawler.plugins.PasswordSession;
import java.util.List;
import java.util.Optional;
import org.pf4j.Extension;

public class PasswordStaticPlugin extends FsCrawlerPlugin {

    @Override
    protected String getName() {
        return "password-static";
    }

    @Extension
    public static class Provider implements FsCrawlerExtensionPasswordProvider {

        private List<String> passwords = List.of();

        @Override
        public String getType() {
            return "static";
        }

        @Override
        public void start(FsSettings settings, PasswordProviderLookup lookup) {
            passwords = resolvePasswords(settings);
        }

        @Override
        public PasswordSession open(String documentPath) {
            List<String> configuredPasswords = passwords;

            return new PasswordSession() {
                private int index;

                @Override
                public Optional<String> next() {
                    return index < configuredPasswords.size()
                            ? Optional.of(configuredPasswords.get(index++))
                            : Optional.empty();
                }

                @Override
                public void close() {}
            };
        }

        @Override
        public void close() {
            passwords = List.of();
        }

        private static List<String> resolvePasswords(FsSettings settings) {
            if (settings == null || settings.getPasswords() == null) {
                return List.of();
            }

            PasswordProviders providers = settings.getPasswords().getProviders();
            if (providers == null) {
                return List.of();
            }

            StaticPasswordProviderSettings staticSettings = providers.getStatic();
            if (staticSettings == null) {
                return List.of();
            }

            return List.copyOf(staticSettings.resolvedValues());
        }
    }
}
