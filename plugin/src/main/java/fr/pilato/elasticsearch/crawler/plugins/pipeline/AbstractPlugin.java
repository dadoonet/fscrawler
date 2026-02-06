/*
 * Licensed to David Pilato (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
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
 */

package fr.pilato.elasticsearch.crawler.plugins.pipeline;

import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Abstract base class for all pipeline plugins (input, filter, output).
 * Provides common functionality including configuration handling and utility methods.
 * <p>
 * This class implements both legacy configuration (via rawConfig map) and the new
 * per-plugin configuration (via dedicated config files). Subclasses should override
 * the per-plugin methods when migrating to typed settings.
 */
public abstract class AbstractPlugin implements ConfigurablePlugin {

    protected final Logger logger = LogManager.getLogger(getClass());

    protected String id;
    protected FsSettings globalSettings;
    protected Map<String, Object> rawConfig;

    /**
     * Returns the plugin type identifier (e.g., "local", "tika", "elasticsearch").
     * @return the type identifier
     */
    public abstract String getType();

    /**
     * Returns the plugin id.
     * @return the plugin id
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the plugin id.
     * @param id the plugin id
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Returns the global FSCrawler settings.
     * @return the global settings
     */
    public FsSettings getGlobalSettings() {
        return globalSettings;
    }

    /**
     * Returns the raw configuration map.
     * @return the raw configuration
     */
    public Map<String, Object> getRawConfig() {
        return rawConfig;
    }

    /**
     * Configures the plugin with the provided settings.
     * This method follows the template method pattern:
     * 1. Stores raw config and global settings
     * 2. Calls {@link #configureCommon(Map)} for common configuration
     * 3. Calls {@link #configureTypeSpecific(Map)} for type-specific configuration
     *
     * @param rawConfig the raw configuration map
     * @param globalSettings the global FSCrawler settings
     */
    public void configure(Map<String, Object> rawConfig, FsSettings globalSettings) {
        this.rawConfig = rawConfig;
        this.globalSettings = globalSettings;

        // Configure common settings
        if (rawConfig != null) {
            configureCommon(rawConfig);
        }

        // Get and configure type-specific settings
        @SuppressWarnings("unchecked")
        Map<String, Object> typeConfig = rawConfig != null ?
                (Map<String, Object>) rawConfig.get(getType()) : null;
        if (typeConfig != null) {
            configureTypeSpecific(typeConfig);
        }
    }

    /**
     * Configure common settings from the configuration map.
     * Subclasses should override this to read their common settings.
     * Default implementation does nothing.
     *
     * @param config the configuration map
     */
    protected void configureCommon(Map<String, Object> config) {
        // Default: no common configuration
    }

    /**
     * Configure type-specific settings from the configuration map.
     * Subclasses should override this to read their specific settings.
     *
     * @param typeConfig the type-specific configuration map
     */
    protected abstract void configureTypeSpecific(Map<String, Object> typeConfig);

    /**
     * Validates the plugin configuration.
     * Subclasses should override this to add specific validation.
     *
     * @throws FsCrawlerIllegalConfigurationException if configuration is invalid
     */
    public void validateConfiguration() throws FsCrawlerIllegalConfigurationException {
        if (id == null || id.isEmpty()) {
            throw new FsCrawlerIllegalConfigurationException(
                    getPluginCategoryLabel() + " plugin id is required");
        }
    }

    /**
     * Returns the plugin category label for error messages (e.g., "Input", "Filter", "Output").
     * @return the plugin category label
     */
    protected abstract String getPluginCategoryLabel();

    // ========== Utility methods ==========

    /**
     * Gets a configuration value with type conversion.
     *
     * @param config the configuration map
     * @param key the configuration key
     * @param type the expected type
     * @param defaultValue the default value if not found
     * @param <T> the type parameter
     * @return the configuration value or default
     */
    @SuppressWarnings("unchecked")
    protected <T> T getConfigValue(Map<String, Object> config, String key, Class<T> type, T defaultValue) {
        Object value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        // Handle type conversion for common cases
        if (type == String.class) {
            return type.cast(value.toString());
        }
        if (type == Integer.class && value instanceof Number) {
            return type.cast(((Number) value).intValue());
        }
        if (type == Long.class && value instanceof Number) {
            return type.cast(((Number) value).longValue());
        }
        if (type == Double.class && value instanceof Number) {
            return type.cast(((Number) value).doubleValue());
        }
        if (type == Boolean.class) {
            if (value instanceof Boolean) {
                return type.cast(value);
            }
            return type.cast(Boolean.parseBoolean(value.toString()));
        }
        return defaultValue;
    }

    /**
     * Gets a list configuration value.
     *
     * @param config the configuration map
     * @param key the configuration key
     * @return the list or null if not found
     */
    @SuppressWarnings("unchecked")
    protected List<String> getConfigList(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value instanceof List) {
            return (List<String>) value;
        }
        return null;
    }

    /**
     * Gets a nested map configuration value.
     *
     * @param config the configuration map
     * @param key the configuration key
     * @return the nested map or null if not found
     */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> getConfigMap(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }

    // ========== Per-Plugin Configuration Methods ==========

    /**
     * {@inheritDoc}
     * <p>
     * Default implementation throws UnsupportedOperationException.
     * Subclasses should override this when migrating to typed settings.
     */
    @Override
    public void loadSettings(Path configFile) throws IOException, FsCrawlerIllegalConfigurationException {
        throw new UnsupportedOperationException(
                "Plugin " + getType() + " does not yet support per-plugin configuration. " +
                "Override loadSettings() to enable this feature.");
    }

    /**
     * {@inheritDoc}
     * <p>
     * Default implementation throws UnsupportedOperationException.
     * Subclasses should override this when migrating to typed settings.
     */
    @Override
    public void saveSettings(Path configFile) throws IOException {
        throw new UnsupportedOperationException(
                "Plugin " + getType() + " does not yet support per-plugin configuration. " +
                "Override saveSettings() to enable this feature.");
    }

    /**
     * {@inheritDoc}
     * <p>
     * Default implementation logs a warning.
     * Subclasses should override this to extract their settings from FsSettings.
     */
    @Override
    public void migrateFromV1(FsSettings v1Settings) {
        logger.warn("Plugin {} does not implement V1 migration. " +
                "Override migrateFromV1() to enable automatic migration.", getType());
    }

    /**
     * {@inheritDoc}
     * <p>
     * Default implementation returns false.
     * Subclasses should override this to return true after implementing typed settings.
     */
    @Override
    public boolean supportsPerPluginConfig() {
        return false;
    }
}
