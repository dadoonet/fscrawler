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

import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerExtensionPasswordProviderAbstract;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPlugin;
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
    public static class Provider extends FsCrawlerExtensionPasswordProviderAbstract {

        private List<String> passwords = List.of();

        @Override
        public String getType() {
            return "static";
        }

        @Override
        protected void parseSettings() {
            passwords = resolvePasswords();
        }

        @Override
        protected void validateSettings() {
            // Empty configuration is valid (session yields no candidates).
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
            super.close();
            passwords = List.of();
        }

        private List<String> resolvePasswords() {
            if (providerConfig == null) {
                return List.of();
            }

            List<String> values = asStringList(providerConfig.get("values"));
            if (!values.isEmpty()) {
                return values;
            }

            String value = asString(providerConfig.get("value"));
            return value == null ? List.of() : List.of(value);
        }
    }
}
