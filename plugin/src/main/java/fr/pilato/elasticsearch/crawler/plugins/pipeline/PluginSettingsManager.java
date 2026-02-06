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
import fr.pilato.elasticsearch.crawler.fs.settings.plugin.PluginSettings;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Interface for managing plugin settings lifecycle.
 * Each plugin type can have its own implementation to handle loading,
 * saving, validating, and migrating settings.
 *
 * @param <S> The settings type for this plugin (extends PluginSettings)
 */
public interface PluginSettingsManager<S extends PluginSettings> {

    /**
     * Loads plugin settings from a configuration file.
     *
     * @param configFile Path to the configuration file
     * @return The loaded settings
     * @throws IOException if the file cannot be read
     * @throws FsCrawlerIllegalConfigurationException if the configuration is invalid
     */
    S loadSettings(Path configFile) throws IOException, FsCrawlerIllegalConfigurationException;

    /**
     * Saves plugin settings to a configuration file.
     *
     * @param settings The settings to save
     * @param configFile Path to save the configuration file
     * @throws IOException if the file cannot be written
     */
    void saveSettings(S settings, Path configFile) throws IOException;

    /**
     * Migrates legacy V1 FsSettings to this plugin's settings format.
     *
     * @param v1Settings The legacy settings to migrate from
     * @return The migrated settings for this plugin
     */
    S migrateFromV1(FsSettings v1Settings);

    /**
     * Returns the current settings version supported by this plugin.
     * Used to determine if migration is needed when loading older config files.
     *
     * @return The current settings version number
     */
    int getSettingsVersion();

    /**
     * Validates the plugin settings.
     *
     * @param settings The settings to validate
     * @throws FsCrawlerIllegalConfigurationException if validation fails
     */
    void validateSettings(S settings) throws FsCrawlerIllegalConfigurationException;

    /**
     * Returns the settings class managed by this manager.
     *
     * @return The settings class
     */
    Class<S> getSettingsClass();
}
