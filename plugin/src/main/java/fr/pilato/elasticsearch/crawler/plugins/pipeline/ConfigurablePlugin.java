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

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Base interface for all configurable pipeline plugins.
 * Each plugin is responsible for reading and validating its own configuration section.
 * <p>
 * Plugins can be configured in two ways:
 * <ol>
 *   <li><b>Legacy mode</b>: Via {@link #configure(Map, FsSettings)} using a raw config map
 *       from a centralized _settings.yaml file.</li>
 *   <li><b>Per-plugin mode</b>: Via {@link #loadSettings(Path)} using a dedicated config file
 *       for each plugin instance (e.g., _settings/inputs/01-local.yaml).</li>
 * </ol>
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

    // ========== Legacy Configuration (rawConfig map) ==========

    /**
     * Configures the plugin from its specific configuration section.
     * The plugin reads its own settings from the rawConfig map.
     * <p>
     * This is the legacy configuration method used when loading from a centralized
     * _settings.yaml file.
     *
     * @param rawConfig The raw configuration map for this plugin instance.
     *                  The key is the plugin type, value is the configuration map.
     * @param globalSettings The global FsSettings for fallback (backward compatibility with v1)
     */
    void configure(Map<String, Object> rawConfig, FsSettings globalSettings);

    // ========== Per-Plugin Configuration (dedicated config file) ==========

    /**
     * Loads plugin settings from a dedicated configuration file.
     * <p>
     * Each plugin implements this to load its own typed settings class using Gestalt.
     * The config file contains only the settings for this specific plugin instance.
     *
     * @param configFile Path to the plugin configuration file (YAML, JSON, or properties)
     * @throws IOException if the file cannot be read
     * @throws FsCrawlerIllegalConfigurationException if the configuration is invalid
     */
    void loadSettings(Path configFile) throws IOException, FsCrawlerIllegalConfigurationException;

    /**
     * Saves plugin settings to a configuration file.
     * <p>
     * Each plugin implements this to persist its settings to a dedicated file.
     *
     * @param configFile Path to save the configuration file
     * @throws IOException if the file cannot be written
     */
    void saveSettings(Path configFile) throws IOException;

    /**
     * Creates plugin configuration from legacy V1 FsSettings.
     * <p>
     * Used during migration from the old centralized configuration format to the
     * new per-plugin configuration format. Each plugin extracts its relevant
     * settings from the global FsSettings.
     *
     * @param v1Settings The legacy V1 settings to migrate from
     */
    void migrateFromV1(FsSettings v1Settings);

    /**
     * Checks if this plugin supports the per-plugin configuration mode.
     * <p>
     * Plugins that have not yet been migrated to use typed settings classes
     * should return false. This allows for gradual migration.
     *
     * @return true if the plugin supports loadSettings/saveSettings, false otherwise
     */
    default boolean supportsPerPluginConfig() {
        return false;
    }

    // ========== Default Resources ==========

    /**
     * Returns the classpath resource path for the default YAML configuration template.
     * This template is copied to the job settings directory when running --setup.
     *
     * @return the classpath resource path, or null if not available
     */
    default String getDefaultYamlResource() {
        return null;
    }

    /**
     * Returns the classpath resource path for the default properties file.
     * This file contains default values that are merged with user configuration.
     *
     * @return the classpath resource path, or null if not available
     */
    default String getDefaultPropertiesResource() {
        return null;
    }

    /**
     * Returns the default filename for this plugin's settings file.
     * Used during --setup to create the initial configuration file.
     * Example: "01-local.yaml", "01-tika.yaml"
     *
     * @return the default filename, or null if not applicable
     */
    default String getDefaultSettingsFilename() {
        return null;
    }

    /**
     * Returns the plugin category for directory organization.
     * Used to determine where to place the settings file during --setup.
     *
     * @return "inputs", "filters", or "outputs"
     */
    default String getPluginCategory() {
        return null;
    }

    /**
     * Returns a short description of this plugin for display during --setup.
     * Example: "Local filesystem input configuration"
     *
     * @return a human-readable description
     */
    default String getDescription() {
        return getType() + " plugin";
    }

    // ========== Validation ==========

    /**
     * Validates that the configuration is complete and consistent.
     * Should be called after {@link #configure(Map, FsSettings)} or {@link #loadSettings(Path)}.
     *
     * @throws FsCrawlerIllegalConfigurationException if the configuration is invalid
     */
    void validateConfiguration() throws FsCrawlerIllegalConfigurationException;
}
