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

package fr.pilato.elasticsearch.crawler.fs.settings;

import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
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
 * Loader for global settings using Gestalt.
 * Global settings are shared across all plugins and are loaded from:
 * <ul>
 *   <li>Default properties (from classpath)</li>
 *   <li>User configuration file (YAML or JSON)</li>
 *   <li>Environment variables (FSCRAWLER_ prefix)</li>
 *   <li>System properties</li>
 * </ul>
 */
public class GlobalSettingsLoader {

    private static final Logger logger = LogManager.getLogger(GlobalSettingsLoader.class);

    private static final String ENV_PREFIX = "FSCRAWLER_";

    /**
     * Loads global settings from a configuration file with defaults.
     *
     * @param configFile Path to the configuration file (YAML or JSON)
     * @return The loaded settings
     * @throws IOException if the file cannot be read
     * @throws FsCrawlerIllegalConfigurationException if the configuration is invalid
     */
    public static GlobalSettings load(Path configFile) throws IOException, FsCrawlerIllegalConfigurationException {
        return load(configFile, true);
    }

    /**
     * Loads global settings from a configuration file.
     *
     * @param configFile Path to the configuration file (YAML or JSON)
     * @param applyOverrides Whether to apply environment variable and system property overrides
     * @return The loaded settings
     * @throws IOException if the file cannot be read
     * @throws FsCrawlerIllegalConfigurationException if the configuration is invalid
     */
    public static GlobalSettings load(Path configFile, boolean applyOverrides) 
            throws IOException, FsCrawlerIllegalConfigurationException {
        
        if (!Files.exists(configFile)) {
            throw new IOException("Configuration file not found: " + configFile);
        }

        logger.debug("Loading global settings from [{}]", configFile);

        try {
            GestaltBuilder builder = new GestaltBuilder()
                    .setTreatMissingValuesAsErrors(false)
                    .setTreatMissingDiscretionaryValuesAsErrors(false);

            // First load defaults from classpath
            builder.addSource(ClassPathConfigSourceBuilder.builder()
                    .setResource(GlobalSettings.DEFAULT_PROPERTIES)
                    .build());

            // Then load user configuration (overrides defaults)
            builder.addSource(FileConfigSourceBuilder.builder()
                    .setPath(configFile)
                    .build());

            // Optionally add environment variables and system properties for overrides
            if (applyOverrides) {
                builder.addSource(EnvironmentConfigSourceBuilder.builder()
                        .setPrefix(ENV_PREFIX)
                        .setRemovePrefix(true)
                        .build());

                builder.addSource(SystemPropertiesConfigSourceBuilder.builder().build());
            }

            Gestalt gestalt = builder.build();
            gestalt.loadConfigs();

            GlobalSettings settings = gestalt.getConfig("", GlobalSettings.class);

            logger.debug("Successfully loaded global settings: {}", settings);
            return settings;

        } catch (GestaltException e) {
            throw new FsCrawlerIllegalConfigurationException(
                    "Failed to load global settings from " + configFile + ": " + e.getMessage(), e);
        }
    }

    /**
     * Checks if a V2 settings directory exists.
     *
     * @param jobDir The job directory
     * @return true if the _settings directory exists
     */
    public static boolean hasV2Settings(Path jobDir) {
        return Files.isDirectory(jobDir.resolve(GlobalSettings.SETTINGS_DIR));
    }

    /**
     * Gets the path to the global settings file.
     *
     * @param jobDir The job directory
     * @return Path to the global settings file (YAML preferred)
     */
    public static Path getGlobalSettingsPath(Path jobDir) {
        Path settingsDir = jobDir.resolve(GlobalSettings.SETTINGS_DIR);
        Path yamlPath = settingsDir.resolve(GlobalSettings.GLOBAL_SETTINGS_YAML);
        Path jsonPath = settingsDir.resolve(GlobalSettings.GLOBAL_SETTINGS_JSON);
        
        if (Files.exists(yamlPath)) {
            return yamlPath;
        } else if (Files.exists(jsonPath)) {
            return jsonPath;
        }
        
        // Return YAML path by default (for new installations)
        return yamlPath;
    }
}
