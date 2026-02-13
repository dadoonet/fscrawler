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

import java.util.Map;

/**
 * Base interface for all configurable pipeline plugins.
 * Each plugin is responsible for reading and validating its own configuration section.
 */
public interface ConfigurablePlugin {

    /**
     * Returns the type identifier of this plugin.
     * This is used for plugin discovery and routing.
     * Examples: "local", "ssh", "tika", "elasticsearch"
     *
     * @return the plugin type identifier
     */
    String getType();

    /**
     * Returns the unique identifier of this plugin instance within the pipeline.
     * This is used to distinguish between multiple instances of the same plugin type.
     *
     * @return the plugin instance identifier
     */
    String getId();

    /**
     * Sets the unique identifier for this plugin instance.
     *
     * @param id the plugin instance identifier
     */
    void setId(String id);

    /**
     * Configures the plugin from its specific configuration section.
     * The plugin reads its own settings from the rawConfig map.
     *
     * @param rawConfig The raw configuration map for this plugin instance.
     *                  The key is the plugin type, value is the configuration map.
     * @param globalSettings The global FsSettings for fallback (backward compatibility with v1)
     */
    void configure(Map<String, Object> rawConfig, FsSettings globalSettings);

    /**
     * Validates that the configuration is complete and consistent.
     * Should be called after {@link #configure(Map, FsSettings)}.
     *
     * @throws FsCrawlerIllegalConfigurationException if the configuration is invalid
     */
    void validateConfiguration() throws FsCrawlerIllegalConfigurationException;
}
