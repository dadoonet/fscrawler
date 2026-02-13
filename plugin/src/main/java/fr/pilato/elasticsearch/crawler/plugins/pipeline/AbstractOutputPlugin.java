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
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPluginException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

/**
 * Abstract base class for output plugins.
 * Provides common functionality and default implementations.
 */
public abstract class AbstractOutputPlugin implements OutputPlugin {

    protected final Logger logger = LogManager.getLogger(getClass());

    protected String id;
    protected String when;
    protected FsSettings globalSettings;
    protected Map<String, Object> rawConfig;

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getWhen() {
        return when;
    }

    @Override
    public void setWhen(String when) {
        this.when = when;
    }

    @Override
    public void configure(Map<String, Object> rawConfig, FsSettings globalSettings) {
        this.rawConfig = rawConfig;
        this.globalSettings = globalSettings;

        // Read common output configuration
        if (rawConfig != null) {
            this.when = getConfigValue(rawConfig, "when", String.class, null);
        }

        // Get type-specific configuration
        @SuppressWarnings("unchecked")
        Map<String, Object> typeConfig = rawConfig != null ? 
                (Map<String, Object>) rawConfig.get(getType()) : null;
        if (typeConfig != null) {
            configureTypeSpecific(typeConfig);
        }
    }

    /**
     * Configure type-specific settings from the configuration map.
     * Subclasses should override this to read their specific settings.
     *
     * @param typeConfig the type-specific configuration map
     */
    protected abstract void configureTypeSpecific(Map<String, Object> typeConfig);

    @Override
    public void validateConfiguration() throws FsCrawlerIllegalConfigurationException {
        if (id == null || id.isEmpty()) {
            throw new FsCrawlerIllegalConfigurationException("Output plugin id is required");
        }
    }

    @Override
    public void start() throws FsCrawlerPluginException {
        logger.debug("Starting output plugin [{}] of type [{}]", id, getType());
    }

    @Override
    public void stop() throws FsCrawlerPluginException {
        logger.debug("Stopping output plugin [{}] of type [{}]", id, getType());
    }

    @Override
    public void flush() throws FsCrawlerPluginException {
        logger.debug("Flushing output plugin [{}] of type [{}]", id, getType());
    }

    // ========== Utility methods ==========

    @SuppressWarnings("unchecked")
    protected <T> T getConfigValue(Map<String, Object> config, String key, Class<T> type, T defaultValue) {
        Object value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        if (type == String.class) {
            return type.cast(value.toString());
        }
        if (type == Integer.class && value instanceof Number) {
            return type.cast(((Number) value).intValue());
        }
        if (type == Boolean.class) {
            if (value instanceof Boolean) {
                return type.cast(value);
            }
            return type.cast(Boolean.parseBoolean(value.toString()));
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    protected List<String> getConfigList(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value instanceof List) {
            return (List<String>) value;
        }
        return null;
    }
}
