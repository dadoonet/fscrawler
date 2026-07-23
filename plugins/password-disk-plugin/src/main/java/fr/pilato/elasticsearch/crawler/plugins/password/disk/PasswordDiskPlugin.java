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
package fr.pilato.elasticsearch.crawler.plugins.password.disk;

import fr.pilato.elasticsearch.crawler.fs.settings.DiskPasswordProviderSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.PasswordProviders;
import fr.pilato.elasticsearch.crawler.fs.settings.Passwords;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerExtensionPasswordProvider;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPlugin;
import fr.pilato.elasticsearch.crawler.plugins.PasswordProviderLookup;
import fr.pilato.elasticsearch.crawler.plugins.PasswordSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.pf4j.Extension;

public class PasswordDiskPlugin extends FsCrawlerPlugin {

    @Override
    protected String getName() {
        return "password-disk";
    }

    @Extension
    public static class Provider implements FsCrawlerExtensionPasswordProvider {

        private static final Logger logger = LogManager.getLogger();

        private Path fsRoot;
        private Path diskRoot;

        @Override
        public String getType() {
            return "disk";
        }

        @Override
        public void start(FsSettings settings, PasswordProviderLookup lookup) {
            fsRoot = normalizePath(
                    settings != null && settings.getFs() != null
                            ? settings.getFs().getUrl()
                            : null);
            diskRoot = normalizePath(resolveDiskUrl(settings));
        }

        @Override
        public PasswordSession open(String documentPath) {
            return new DiskPasswordSession(candidatePaths(documentPath));
        }

        @Override
        public void close() {
            fsRoot = null;
            diskRoot = null;
        }

        private String resolveDiskUrl(FsSettings settings) {
            if (settings == null || settings.getFs() == null) {
                return null;
            }

            Passwords passwords = settings.getPasswords();
            PasswordProviders providers = passwords != null ? passwords.getProviders() : null;
            DiskPasswordProviderSettings diskSettings = providers != null ? providers.getDisk() : null;
            String configuredUrl = diskSettings != null ? diskSettings.getUrl() : null;
            if (configuredUrl == null || configuredUrl.isBlank()) {
                return settings.getFs().getUrl();
            }
            return configuredUrl;
        }

        private List<Path> candidatePaths(String documentPath) {
            if (documentPath == null || documentPath.isBlank() || diskRoot == null) {
                return List.of();
            }

            Path document = Path.of(documentPath).toAbsolutePath().normalize();
            Path relativePath = relativePath(document);
            if (relativePath == null) {
                return List.of();
            }

            List<Path> candidates = new ArrayList<>();
            candidates.add(diskRoot.resolve(relativePath + ".password"));

            Path parent = relativePath.getParent();
            while (parent != null) {
                candidates.add(diskRoot.resolve(parent).resolve(".password"));
                parent = parent.getParent();
            }

            candidates.add(diskRoot.resolve(".password"));
            return candidates;
        }

        private Path relativePath(Path document) {
            Path filename = document.getFileName();
            if (filename == null) {
                return null;
            }

            if (fsRoot == null) {
                return Path.of(filename.toString());
            }

            if (document.startsWith(fsRoot)) {
                return fsRoot.relativize(document);
            }

            logger.warn(
                    "Document [{}] is outside fs.url [{}]; trying filename-only password sidecar under [{}].",
                    document,
                    fsRoot,
                    diskRoot);
            return Path.of(filename.toString());
        }

        private static Path normalizePath(String path) {
            if (path == null || path.isBlank()) {
                return null;
            }
            return Path.of(path).toAbsolutePath().normalize();
        }

        private static class DiskPasswordSession implements PasswordSession {

            private final List<Path> candidates;
            private int index;

            private DiskPasswordSession(List<Path> candidates) {
                this.candidates = candidates;
            }

            @Override
            public Optional<String> next() {
                while (index < candidates.size()) {
                    Optional<String> password = readPassword(candidates.get(index++));
                    if (password.isPresent()) {
                        return password;
                    }
                }
                return Optional.empty();
            }

            @Override
            public void close() {}

            private Optional<String> readPassword(Path candidate) {
                if (Files.notExists(candidate)) {
                    return Optional.empty();
                }
                if (!Files.isRegularFile(candidate)) {
                    logger.warn("Password candidate [{}] is not a regular file; skipping.", candidate);
                    return Optional.empty();
                }

                try (BufferedReader reader = Files.newBufferedReader(candidate, StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String trimmed = line.trim();
                        if (!trimmed.isEmpty()) {
                            return Optional.of(trimmed);
                        }
                    }
                } catch (IOException e) {
                    logger.warn("Can not read password candidate [{}]: {}", candidate, e.getMessage());
                }
                return Optional.empty();
            }
        }
    }
}
