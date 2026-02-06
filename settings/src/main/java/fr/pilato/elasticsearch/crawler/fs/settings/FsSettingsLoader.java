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
import fr.pilato.elasticsearch.crawler.fs.framework.MetaFileHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.github.gestalt.config.Gestalt;
import org.github.gestalt.config.builder.GestaltBuilder;
import org.github.gestalt.config.source.ClassPathConfigSourceBuilder;
import org.github.gestalt.config.source.EnvironmentConfigSourceBuilder;
import org.github.gestalt.config.source.FileConfigSourceBuilder;
import org.github.gestalt.config.source.SystemPropertiesConfigSourceBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Provides utility methods to read and write settings files
 */
public class FsSettingsLoader extends MetaFileHandler {

    private static final Logger logger = LogManager.getLogger();

    public static final String SETTINGS_JSON = "_settings.json";
    public static final String SETTINGS_YAML = "_settings.yaml";
    public static final String SETTINGS_DIR = "_settings";
    public static final String DEFAULT_SETTINGS = "/fr/pilato/elasticsearch/crawler/fs/settings/fscrawler-default.properties";
    public static final String EXAMPLE_SETTINGS = "/fr/pilato/elasticsearch/crawler/fs/settings/fscrawler-default.yaml";

    public FsSettingsLoader(Path root) {
        super(root);
    }

    /**
     * We read settings in ~/.fscrawler/{job_name}/_settings.[json|yml]
     * @param jobName is the job_name
     * @return Settings settings (automatically migrated to v2 if needed)
     * @throws IOException in case of error while reading
     */
    public FsSettings read(String jobName) throws IOException {
        return read(jobName, true);
    }

    /**
     * We read settings in ~/.fscrawler/{job_name}/_settings.[json|yml]
     * @param jobName is the job_name
     * @param autoMigrate if true, v1 settings are automatically migrated to v2
     * @return Settings settings
     * @throws IOException in case of error while reading
     */
    public FsSettings read(String jobName, boolean autoMigrate) throws IOException {
        Path configYaml = root.resolve(jobName).resolve(SETTINGS_YAML);
        logger.trace("Trying to read settings from [{}] file", configYaml);
        if (Files.exists(configYaml)) {
            return load(autoMigrate, configYaml);
        }
        Path configJson = root.resolve(jobName).resolve(SETTINGS_JSON);
        logger.trace("Trying to read settings from [{}] file", configJson);
        if (Files.exists(configJson)) {
            return load(autoMigrate, configJson);
        }
        Path configDir = root.resolve(jobName).resolve(SETTINGS_DIR);
        logger.trace("Trying to read settings from [{}] directory", configDir);
        if (Files.exists(configDir)) {
            // V2 format: only load the global settings file
            // Plugin-specific settings are loaded by PipelinePluginsManager
            Path globalYaml = configDir.resolve(GlobalSettings.GLOBAL_SETTINGS_YAML);
            Path globalJson = configDir.resolve(GlobalSettings.GLOBAL_SETTINGS_JSON);
            if (Files.exists(globalYaml)) {
                return loadV2GlobalSettings(globalYaml);
            } else if (Files.exists(globalJson)) {
                return loadV2GlobalSettings(globalJson);
            }
            // Fallback: try to load all files (for backward compatibility)
            return load(autoMigrate, readDir(configDir).toArray(new Path[0]));
        }
        logger.debug("Can not read settings from [{}] with either /_settings.yaml, /_settings.json, /_settings/*." +
                        " Falling back to default settings.", root.resolve(jobName).toAbsolutePath());
        return load(autoMigrate);
    }

    /**
     * We write settings to ~/.fscrawler/{job_name}/_settings.yaml
     * @param name the job_name
     * @param settings Settings to write (settings.getName() contains the job name)
     * @throws IOException in case of error while reading
     */
    public void write(String name, FsSettings settings) throws IOException {
        writeFile(name, SETTINGS_YAML, FsSettingsParser.toYaml(settings));
    }

    /**
     * Read all files in ~/.fscrawler/{job_name}/_settings directory recursively.
     * Files are sorted alphabetically to ensure deterministic order.
     * Use numeric prefixes (e.g., 01-global.yaml, inputs/01-local.yaml) to control loading order.
     * <p>
     * V2 structure:
     * <pre>
     * _settings/
     *   01-global.yaml
     *   inputs/
     *     01-local.yaml
     *   filters/
     *     01-tika.yaml
     *   outputs/
     *     01-elasticsearch.yaml
     * </pre>
     * @param settingsDir is _settings directory
     * @return The list of setting files (only regular files, not directories), sorted alphabetically
     */
    private List<Path> readDir(Path settingsDir) throws IOException {
        try (Stream<Path> files = Files.walk(settingsDir)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return name.endsWith(".yaml") || name.endsWith(".yml") || 
                               name.endsWith(".json") || name.endsWith(".properties");
                    })
                    .sorted()
                    .toList();
        }
    }

    /**
     * Load settings from files. It could be both json or yaml files.
     * V1 settings are automatically migrated to v2.
     * @param configFiles list of files to read
     * @return The settings
     * @throws FsCrawlerIllegalConfigurationException when we can not read settings
     */
    public static FsSettings load(Path... configFiles) throws FsCrawlerIllegalConfigurationException {
        return load(true, configFiles);
    }

    /**
     * Load settings from files. It could be both json or yaml files
     * @param autoMigrate if true, v1 settings are automatically migrated to v2
     * @param configFiles list of files to read
     * @return The settings
     * @throws FsCrawlerIllegalConfigurationException when we can not read settings
     */
    public static FsSettings load(boolean autoMigrate, Path... configFiles) throws FsCrawlerIllegalConfigurationException {
        try {
            // Sources are loaded in order, with later sources overriding earlier ones.
            // Order: defaults -> config files -> env variables -> system properties
            GestaltBuilder builder = new GestaltBuilder()
                .addSource(ClassPathConfigSourceBuilder.builder().setResource(DEFAULT_SETTINGS).build());

            // Config files override defaults
            for (Path configFile : configFiles) {
                builder.addSource(FileConfigSourceBuilder.builder().setPath(configFile).build());
            }

            // Environment variables override config files (e.g., FSCRAWLER_NAME=foo, FSCRAWLER_FS_URL=/tmp)
            builder.addSource(EnvironmentConfigSourceBuilder.builder()
                    .setPrefix("FSCRAWLER_")
                    .setRemovePrefix(true)
                    .build());
            // System properties have highest priority (e.g., -Dname=foo, -Dfs.url=/tmp)
            builder.addSource(SystemPropertiesConfigSourceBuilder.builder().build());

            Gestalt gestalt = builder.build();

            // Loads and parses the configurations, this will throw exceptions if there are any errors.
            gestalt.loadConfigs();

            FsSettings settings = new FsSettings();

            // Load version if present
            settings.setVersion(gestalt.getConfigOptional("version", Integer.class).orElse(null));
            
            // Load legacy v1 settings
            settings.setName(gestalt.getConfigOptional("name", String.class).orElse(null));
            settings.setFs(gestalt.getConfigOptional("fs", Fs.class).orElse(null));
            settings.setElasticsearch(gestalt.getConfigOptional("elasticsearch", Elasticsearch.class).orElse(null));
            settings.setTags(gestalt.getConfigOptional("tags", Tags.class).orElse(null));
            settings.setServer(gestalt.getConfigOptional("server", Server.class).orElse(null));
            settings.setRest(gestalt.getConfigOptional("rest", Rest.class).orElse(null));
            
            // Note: V2 pipeline settings (inputs/filters/outputs) are not loaded here via Gestalt
            // because Gestalt cannot handle dynamic keys in the YAML structure.
            // The new per-plugin configuration approach (Phase 3+) will handle this properly.
            // For now, V1 settings are migrated to V2 format programmatically.

            boolean hasUserConfigFiles = configFiles != null && configFiles.length > 0;
            logger.debug("Successfully loaded settings from classpath [fscrawler-default.properties] and files {}",
                    (Object) configFiles);
            logger.trace("Loaded settings [{}]", settings);

            // Apply migration and normalization (only warn if there are actual user config files)
            if (autoMigrate) {
                settings = migrateAndNormalize(settings, hasUserConfigFiles);
            }

            // Apply v2 overrides from env vars and system properties
            // This is done after migration to ensure v2 sections exist
            V2OverrideApplier.applyOverrides(settings);

            return settings;
        } catch (Exception e) {
            throw new FsCrawlerIllegalConfigurationException("Can not load settings", e);
        }
    }
    
    /**
     * Load V2 settings from the _settings directory.
     * This loads global settings plus plugin settings and converts them to V1 structure
     * for backward compatibility with the core components.
     *
     * @param globalSettingsFile Path to the 01-global.yaml or 01-global.json file
     * @return The loaded FsSettings with V1-compatible structure
     * @throws FsCrawlerIllegalConfigurationException when we can not read settings
     */
    private static FsSettings loadV2GlobalSettings(Path globalSettingsFile) throws FsCrawlerIllegalConfigurationException {
        try {
            logger.debug("Loading V2 global settings from [{}]", globalSettingsFile);
            Path settingsDir = globalSettingsFile.getParent();
            
            // Build a Gestalt config from all V2 files plus defaults
            GestaltBuilder builder = new GestaltBuilder()
                .addSource(ClassPathConfigSourceBuilder.builder().setResource(DEFAULT_SETTINGS).build());

            // Add global settings file
            builder.addSource(FileConfigSourceBuilder.builder().setPath(globalSettingsFile).build());
            
            // Add plugin settings files with key remapping for V1 compatibility
            addV2PluginSettings(builder, settingsDir);
            
            // Environment variables and system properties have highest priority
            builder.addSource(EnvironmentConfigSourceBuilder.builder()
                        .setPrefix("FSCRAWLER_")
                        .setRemovePrefix(true)
                        .build())
                   .addSource(SystemPropertiesConfigSourceBuilder.builder().build());

            Gestalt gestalt = builder.build();
            gestalt.loadConfigs();

            FsSettings settings = new FsSettings();
            settings.setVersion(gestalt.getConfigOptional("version", Integer.class).orElse(FsSettingsMigrator.VERSION_2));
            settings.setName(gestalt.getConfigOptional("name", String.class).orElse(null));
            settings.setTags(gestalt.getConfigOptional("tags", Tags.class).orElse(null));
            settings.setRest(gestalt.getConfigOptional("rest", Rest.class).orElse(null));
            
            // Load V1-style fs settings from V2 plugin files (mapped by addV2PluginSettings)
            settings.setFs(gestalt.getConfigOptional("fs", Fs.class).orElse(null));
            settings.setElasticsearch(gestalt.getConfigOptional("elasticsearch", Elasticsearch.class).orElse(null));
            settings.setServer(gestalt.getConfigOptional("server", Server.class).orElse(null));
            
            // Apply additional overrides from env vars and system properties
            // This handles formats that Gestalt doesn't process correctly
            V2OverrideApplier.applyOverrides(settings);
            
            logger.debug("Successfully loaded V2 settings for job [{}]", settings.getName());
            return settings;
        } catch (Exception e) {
            throw new FsCrawlerIllegalConfigurationException("Can not load V2 settings", e);
        }
    }
    
    /**
     * Add V2 plugin settings files.
     * V2 plugin files use V1-compatible nested structure (e.g., "fs.url", "elasticsearch.index")
     * so they can be merged with global settings.
     */
    private static void addV2PluginSettings(GestaltBuilder builder, Path settingsDir) throws Exception {
        // Load input plugins
        Path inputsDir = settingsDir.resolve(GlobalSettings.INPUTS_DIR);
        if (Files.exists(inputsDir)) {
            try (Stream<Path> files = Files.list(inputsDir)) {
                for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                    logger.trace("Loading V2 input plugin settings from [{}]", file);
                    builder.addSource(FileConfigSourceBuilder.builder().setPath(file).build());
                }
            }
        }
        
        // Load output plugins
        Path outputsDir = settingsDir.resolve(GlobalSettings.OUTPUTS_DIR);
        if (Files.exists(outputsDir)) {
            try (Stream<Path> files = Files.list(outputsDir)) {
                for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                    logger.trace("Loading V2 output plugin settings from [{}]", file);
                    builder.addSource(FileConfigSourceBuilder.builder().setPath(file).build());
                }
            }
        }
        
        // Filter plugins don't map directly to V1 structure
        // They are loaded by PipelinePluginsManager
    }

    /**
     * Migrates v1 settings to v2 format if needed.
     * @param settings The loaded settings
     * @param hasUserConfigFiles true if the settings came from user config files (not just defaults)
     * @return The migrated settings
     */
    private static FsSettings migrateAndNormalize(FsSettings settings, boolean hasUserConfigFiles) {
        int version = FsSettingsMigrator.detectVersion(settings);
        
        if (version == FsSettingsMigrator.VERSION_1) {
            // Only show deprecation warning if user has actual config files to migrate
            if (hasUserConfigFiles) {
                logger.warn("Job [{}] uses deprecated settings format (v1). " +
                        "Please migrate to the new pipeline format (v2) using: " +
                        "fscrawler {} --migrate", settings.getName(), settings.getName());
            }
            
            FsSettings v2Settings = FsSettingsMigrator.migrateV1ToV2(settings);
            
            if (logger.isDebugEnabled()) {
                logger.debug("Automatically converted to v2 configuration for job [{}]:\n---\n{}---", 
                        settings.getName(),
                        FsSettingsMigrator.generateV2Yaml(v2Settings));
            }
            
            return v2Settings;
        }
        
        return settings;
    }
}
