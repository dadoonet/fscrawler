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

import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.pf4j.Extension;

@Extension
public class TestPasswordProvider implements FsCrawlerExtensionPasswordProvider {

    private static final AtomicInteger START_CALLS = new AtomicInteger();
    private static final AtomicInteger CLOSE_CALLS = new AtomicInteger();
    private static volatile FsSettings lastSettings;
    private static volatile PasswordProviderLookup lastLookup;

    static void reset() {
        START_CALLS.set(0);
        CLOSE_CALLS.set(0);
        lastSettings = null;
        lastLookup = null;
    }

    static int startCalls() {
        return START_CALLS.get();
    }

    static int closeCalls() {
        return CLOSE_CALLS.get();
    }

    static FsSettings lastSettings() {
        return lastSettings;
    }

    static PasswordProviderLookup lastLookup() {
        return lastLookup;
    }

    @Override
    public String getType() {
        return "test-pwd";
    }

    @Override
    public void start(FsSettings settings, PasswordProviderLookup lookup) {
        START_CALLS.incrementAndGet();
        lastSettings = settings;
        lastLookup = lookup;
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
    public void close() {
        CLOSE_CALLS.incrementAndGet();
    }
}
