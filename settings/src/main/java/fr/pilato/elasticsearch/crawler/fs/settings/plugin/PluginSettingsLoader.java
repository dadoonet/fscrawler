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

package fr.pilato.elasticsearch.crawler.fs.settings.plugin;

import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import fr.pilato.elasticsearch.crawler.fs.settings.GlobalSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.github.gestalt.config.Gestalt;
import org.github.gestalt.config.builder.GestaltBuilder;
import org.github.gestalt.config.exceptions.GestaltException;
import org.github.gestalt.config.source.ClassPathConfigSourceBuilder;
import org.github.gestalt.config.source.EnvironmentConfigSourceBuilder;
import org.github.gestalt.config.source.FileConfigSourceBuilder;
import org.github.gestalt.config.source.SystemPropertiesConfigSourceBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utility class for loading plugin settings using Gestalt.
 * Each plugin has a fixed settings class structure, making Gestalt ideal for loading.
 * <p>
 * Features:
 * <ul>
 *   <li>Loads from YAML, JSON, or properties files</li>
 *   <li>Supports environment variable overrides (prefixed with FSCRAWLER_)</li>
 *   <li>Supports system property overrides</li>
 *   <li>Validates settings type matches expected plugin type</li>
 * </ul>
 */
public class PluginSettingsLoader {

    private static final Logger logger = LogManager.getLogger(PluginSettingsLoader.class);

    private static final String ENV_PREFIX = "FSCRAWLER_";

    /**
     * Loads plugin settings from a configuration file.
     *
     * @param configFile Path to the configuration file (YAML, JSON, or properties)
     * @param settingsClass The settings class to load into
     * @param <T> The type of settings
     * @return The loaded settings
     * @throws IOException if the file cannot be read
     * @throws FsCrawlerIllegalConfigurationException if the configuration is invalid
     */
    public static <T extends PluginSettings> T load(Path configFile, Class<T> settingsClass) 
            throws IOException, FsCrawlerIllegalConfigurationException {
        return load(configFile, settingsClass, null, null, true);
    }

    /**
     * Loads plugin settings from a configuration file with defaults and global settings.
     *
     * @param configFile Path to the configuration file (YAML, JSON, or properties)
     * @param settingsClass The settings class to load into
     * @param defaultsResource Classpath resource for default values (can be null)
     * @param globalSettingsFile Path to global settings file for variable interpolation (can be null)
     * @param <T> The type of settings
     * @return The loaded settings
     * @throws IOException if the file cannot be read
     * @throws FsCrawlerIllegalConfigurationException if the configuration is invalid
     */
    public static <T extends PluginSettings> T load(Path configFile, Class<T> settingsClass, 
            String defaultsResource, Path globalSettingsFile) 
            throws IOException, FsCrawlerIllegalConfigurationException {
        return load(configFile, settingsClass, defaultsResource, globalSettingsFile, true);
    }

    /**
     * Loads plugin settings from a configuration file.
     *
     * @param configFile Path to the configuration file (YAML, JSON, or properties)
     * @param settingsClass The settings class to load into
     * @param applyOverrides Whether to apply environment variable and system property overrides
     * @param <T> The type of settings
     * @return The loaded settings
     * @throws IOException if the file cannot be read
     * @throws FsCrawlerIllegalConfigurationException if the configuration is invalid
     */
    public static <T extends PluginSettings> T load(Path configFile, Class<T> settingsClass, boolean applyOverrides) 
            throws IOException, FsCrawlerIllegalConfigurationException {
        return load(configFile, settingsClass, null, null, applyOverrides);
    }

    /**
     * Loads plugin settings from a configuration file with defaults and global settings.
     *
     * @param configFile Path to the configuration file (YAML, JSON, or properties)
     * @param settingsClass The settings class to load into
     * @param defaultsResource Classpath resource for default values (can be null)
     * @param globalSettingsFile Path to global settings file for variable interpolation (can be null)
     * @param applyOverrides Whether to apply environment variable and system property overrides
     * @param <T> The type of settings
     * @return The loaded settings
     * @throws IOException if the file cannot be read
     * @throws FsCrawlerIllegalConfigurationException if the configuration is invalid
     */
    public static <T extends PluginSettings> T load(Path configFile, Class<T> settingsClass, 
            String defaultsResource, Path globalSettingsFile, boolean applyOverrides) 
            throws IOException, FsCrawlerIllegalConfigurationException {
        
        if (!Files.exists(configFile)) {
            throw new IOException("Configuration file not found: " + configFile);
        }

        logger.debug("Loading plugin settings from [{}] into [{}]", configFile, settingsClass.getSimpleName());

        try {
            GestaltBuilder builder = new GestaltBuilder()
                    // Allow optional fields (null values for missing config)
                    .setTreatMissingValuesAsErrors(false)
                    .setTreatMissingDiscretionaryValuesAsErrors(false);

            // First load defaults from classpath (if provided)
            if (defaultsResource != null) {
                builder.addSource(ClassPathConfigSourceBuilder.builder()
                        .setResource(defaultsResource)
                        .build());
            }

            // Load global settings for variable interpolation (e.g., ${name})
            if (globalSettingsFile != null && Files.exists(globalSettingsFile)) {
                builder.addSource(FileConfigSourceBuilder.builder()
                        .setPath(globalSettingsFile)
                        .build());
                // Also load global defaults
                builder.addSource(ClassPathConfigSourceBuilder.builder()
                        .setResource(GlobalSettings.DEFAULT_PROPERTIES)
                        .build());
            }

            // Add the plugin config file source (overrides defaults)
            builder.addSource(FileConfigSourceBuilder.builder()
                    .setPath(configFile)
                    .build());

            // Optionally add environment variables and system properties for overrides
            if (applyOverrides) {
                // Environment variables (e.g., FSCRAWLER_PLUGIN_PATH=/data)
                builder.addSource(EnvironmentConfigSourceBuilder.builder()
                        .setPrefix(ENV_PREFIX)
                        .setRemovePrefix(true)
                        .build());

                // System properties
                builder.addSource(SystemPropertiesConfigSourceBuilder.builder().build());
            }

            Gestalt gestalt = builder.build();
            gestalt.loadConfigs();

            // Load settings at root level (empty path)
            T settings = gestalt.getConfig("", settingsClass);

            // Validate the loaded settings
            validateSettings(settings, configFile);

            logger.debug("Successfully loaded plugin settings: {}", settings);
            return settings;

        } catch (GestaltException e) {
            throw new FsCrawlerIllegalConfigurationException(
                    "Failed to load plugin settings from " + configFile + ": " + e.getMessage(), e);
        }
    }

    /**
     * Validates the loaded settings.
     *
     * @param settings The settings to validate
     * @param configFile The source config file (for error messages)
     * @throws FsCrawlerIllegalConfigurationException if validation fails
     */
    private static <T extends PluginSettings> void validateSettings(T settings, Path configFile) 
            throws FsCrawlerIllegalConfigurationException {
        
        // Validate required fields
        if (settings.getId() == null || settings.getId().isEmpty()) {
            throw new FsCrawlerIllegalConfigurationException(
                    "Plugin settings in " + configFile + " must have an 'id' field");
        }

        if (settings.getType() == null || settings.getType().isEmpty()) {
            throw new FsCrawlerIllegalConfigurationException(
                    "Plugin settings in " + configFile + " must have a 'type' field");
        }

        // Validate type matches expected type
        String expectedType = settings.getExpectedType();
        if (!expectedType.equals(settings.getType())) {
            throw new FsCrawlerIllegalConfigurationException(
                    "Plugin type mismatch in " + configFile + ": expected '" + expectedType + 
                    "' but found '" + settings.getType() + "'");
        }

        // Set default version if not specified
        if (settings.getVersion() == null) {
            settings.setVersion(settings.getCurrentVersion());
        }

        // Check if migration is needed (version is older than current)
        if (settings.getVersion() < settings.getCurrentVersion()) {
            logger.warn("Plugin settings in {} are version {} but current version is {}. " +
                    "Consider migrating your configuration.", 
                    configFile, settings.getVersion(), settings.getCurrentVersion());
        }
    }

    /**
     * Checks if a plugin configuration file exists.
     *
     * @param configFile Path to check
     * @return true if the file exists
     */
    public static boolean exists(Path configFile) {
        return Files.exists(configFile);
    }

    /**
     * Gets the file extension from a path.
     *
     * @param path The path to check
     * @return The file extension (without dot), or empty string if none
     */
    public static String getFileExtension(Path path) {
        String fileName = path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1).toLowerCase();
        }
        return "";
    }
}
