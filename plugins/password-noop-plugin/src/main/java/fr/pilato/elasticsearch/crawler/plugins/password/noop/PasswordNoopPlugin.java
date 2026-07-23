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
package fr.pilato.elasticsearch.crawler.plugins.password.noop;

import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerExtensionPasswordProvider;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPlugin;
import fr.pilato.elasticsearch.crawler.plugins.PasswordProviderLookup;
import fr.pilato.elasticsearch.crawler.plugins.PasswordSession;
import java.util.Optional;
import org.pf4j.Extension;

public class PasswordNoopPlugin extends FsCrawlerPlugin {

    @Override
    protected String getName() {
        return "password-noop";
    }

    @Extension
    public static class Provider implements FsCrawlerExtensionPasswordProvider {

        @Override
        public String getType() {
            return "noop";
        }

        @Override
        public void start(FsSettings settings, PasswordProviderLookup lookup) {
            // No configuration or chaining is required for the noop provider.
        }

        @Override
        public PasswordSession open(String documentPath) {
            return new PasswordSession() {
                @Override
                public Optional<String> next() {
                    return Optional.empty();
                }

                @Override
                public void close() {}
            };
        }

        @Override
        public void close() {}
    }
}
