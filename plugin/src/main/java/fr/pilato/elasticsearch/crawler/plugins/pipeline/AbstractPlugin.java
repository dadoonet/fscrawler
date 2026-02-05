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

import java.util.List;
import java.util.Map;

/**
 * Abstract base class for all pipeline plugins (input, filter, output).
 * Provides common functionality including configuration handling and utility methods.
 */
public abstract class AbstractPlugin {

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
                    getPluginCategory() + " plugin id is required");
        }
    }

    /**
     * Returns the plugin category for error messages (e.g., "Input", "Filter", "Output").
     * @return the plugin category
     */
    protected abstract String getPluginCategory();

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
}
