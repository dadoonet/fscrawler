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
import fr.pilato.elasticsearch.crawler.fs.settings.pipeline.FilterSection;
import fr.pilato.elasticsearch.crawler.fs.settings.pipeline.InputSection;
import fr.pilato.elasticsearch.crawler.fs.settings.pipeline.OutputSection;
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
     * @return Settings settings
     * @throws IOException in case of error while reading
     */
    public FsSettings read(String jobName) throws IOException {
        Path configYaml = root.resolve(jobName).resolve(SETTINGS_YAML);
        logger.trace("Trying to read settings from [{}] file", configYaml);
        if (Files.exists(configYaml)) {
            return load(configYaml);
        }
        Path configJson = root.resolve(jobName).resolve(SETTINGS_JSON);
        logger.trace("Trying to read settings from [{}] file", configJson);
        if (Files.exists(configJson)) {
            return load(configJson);
        }
        Path configDir = root.resolve(jobName).resolve(SETTINGS_DIR);
        logger.trace("Trying to read settings from [{}] directory", configDir);
        if (Files.exists(configDir)) {
            return load(readDir(configDir).toArray(new Path[0]));
        }
        logger.debug("Can not read settings from [{}] with either /_settings.yaml, /_settings.json, /_settings/*." +
                        " Falling back to default settings.", root.resolve(jobName).toAbsolutePath());
        return load();
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
     * Read all files in ~/.fscrawler/{job_name}/_settings directory.
     * Files are sorted alphabetically to ensure deterministic order.
     * Use numeric prefixes (e.g., 00-common.yaml, 10-input-local.yaml) to control loading order.
     * @param settingsDir is _settings directory
     * @return The list of setting files, sorted alphabetically
     */
    private List<Path> readDir(Path settingsDir) throws IOException {
        try (Stream<Path> files = Files.list(settingsDir)) {
            return files.sorted().toList();
        }
    }

    /**
     * Load settings from files. It could be both json or yaml files
     * @param configFiles list of files to read
     * @return The settings
     * @throws FsCrawlerIllegalConfigurationException when we can not read settings
     */
    public static FsSettings load(Path... configFiles) throws FsCrawlerIllegalConfigurationException {
        try {
            GestaltBuilder builder = new GestaltBuilder()
                .addSource(ClassPathConfigSourceBuilder.builder().setResource(DEFAULT_SETTINGS).build());

            // This allows automatic configuration using System properties like -Dname=foobar or -Dfs.url=/tmp
            builder.addSource(SystemPropertiesConfigSourceBuilder.builder().build());
            // This allows automatic configuration using Env variables like FSCRAWLER_NAME=foobar or FSCRAWLER_FS_URL=/tmp
            builder.addSource(EnvironmentConfigSourceBuilder.builder()
                    .setPrefix("FSCRAWLER_")
                    .setRemovePrefix(true)
                    .build());

            for (Path configFile : configFiles) {
                builder.addSource(FileConfigSourceBuilder.builder().setPath(configFile).build());
            }

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
            
            // Load v2 pipeline settings
            settings.setInputs(gestalt.getConfigOptional("inputs", new org.github.gestalt.config.reflect.TypeCapture<List<InputSection>>(){}).orElse(null));
            settings.setFilters(gestalt.getConfigOptional("filters", new org.github.gestalt.config.reflect.TypeCapture<List<FilterSection>>(){}).orElse(null));
            settings.setOutputs(gestalt.getConfigOptional("outputs", new org.github.gestalt.config.reflect.TypeCapture<List<OutputSection>>(){}).orElse(null));

            logger.debug("Successfully loaded settings from classpath [fscrawler-default.properties] and files {}",
                    (Object) configFiles);
            logger.trace("Loaded settings [{}]", settings);

            // Apply migration and normalization
            settings = migrateAndNormalize(settings);

            return settings;
        } catch (Exception e) {
            throw new FsCrawlerIllegalConfigurationException("Can not load settings", e);
        }
    }
    
    /**
     * Migrates v1 settings to v2 format if needed.
     * @param settings The loaded settings
     * @return The migrated settings
     */
    private static FsSettings migrateAndNormalize(FsSettings settings) {
        int version = FsSettingsMigrator.detectVersion(settings);
        
        if (version == FsSettingsMigrator.VERSION_1) {
            logger.warn("Job [{}] uses deprecated settings format (v1). " +
                    "Please migrate to the new pipeline format (v2).", settings.getName());
            
            FsSettings v2Settings = FsSettingsMigrator.migrateV1ToV2(settings);
            
            if (logger.isInfoEnabled()) {
                logger.info("Suggested new configuration for job [{}]:\n---\n{}---", 
                        settings.getName(),
                        FsSettingsMigrator.generateV2Yaml(v2Settings));
            }
            
            return v2Settings;
        }
        
        return settings;
    }
}
