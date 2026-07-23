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

import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.Passwords;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Base for password providers that own their {@code passwords.providers.<type>} settings.
 *
 * <p>Mirrors {@link FsCrawlerExtensionFsProviderAbstract}: subclasses implement {@link #parseSettings()} and
 * {@link #validateSettings()} against the opaque provider map.
 */
public abstract class FsCrawlerExtensionPasswordProviderAbstract implements FsCrawlerExtensionPasswordProvider {

    private static final Logger logger = LogManager.getLogger();

    protected FsSettings fsSettings;
    protected PasswordProviderLookup lookup;
    /** Configuration map for this provider type, or {@code null} when absent. */
    protected Map<String, Object> providerConfig;

    protected abstract void parseSettings();

    protected abstract void validateSettings();

    @Override
    public final void start(FsSettings settings, PasswordProviderLookup lookup) {
        this.fsSettings = settings;
        this.lookup = lookup;
        this.providerConfig = extractProviderConfig(settings, getType());
        logger.trace("Starting password provider [{}] with config {}", getType(), providerConfig);

        try {
            parseSettings();
            validateSettings();
        } catch (RuntimeException e) {
            if (e instanceof FsCrawlerIllegalConfigurationException) {
                throw e;
            }
            throw new FsCrawlerIllegalConfigurationException(
                    "Invalid passwords.providers." + getType() + " settings: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        fsSettings = null;
        lookup = null;
        providerConfig = null;
    }

    protected static Map<String, Object> extractProviderConfig(FsSettings settings, String type) {
        if (settings == null) {
            return null;
        }
        Passwords passwords = settings.getPasswords();
        if (passwords == null) {
            return null;
        }
        return passwords.getProviderConfig(type);
    }

    protected static String asString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    protected static List<String> asStringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
            return List.copyOf(result);
        }
        return List.of(String.valueOf(value));
    }

    protected boolean isActiveProvider() {
        if (fsSettings == null || fsSettings.getPasswords() == null) {
            return false;
        }
        String active = fsSettings.getPasswords().getProvider();
        return getType().equals(active);
    }
}
