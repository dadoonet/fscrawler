/*
 * Licensed to David Pilato (the "Author") under one
 * or more contributor license agreements. See the NOTICE file
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
 *
 * Made from 🇫🇷🇪🇺 with ❤️ - 2011-2026
 */
package fr.pilato.elasticsearch.crawler.fs.settings;

import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import fr.pilato.elasticsearch.crawler.fs.framework.MetaFileHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.github.gestalt.config.Gestalt;
import org.github.gestalt.config.builder.GestaltBuilder;
import org.github.gestalt.config.source.ClassPathConfigSourceBuilder;
import org.github.gestalt.config.source.EnvironmentConfigSourceBuilder;
import org.github.gestalt.config.source.FileConfigSourceBuilder;
import org.github.gestalt.config.source.SystemPropertiesConfigSourceBuilder;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

/** Provides utility methods to read and write settings files */
public class FsSettingsLoader extends MetaFileHandler {

    private static final Logger logger = LogManager.getLogger();

    public static final String SETTINGS_JSON = "_settings.json";
    public static final String SETTINGS_YAML = "_settings.yaml";
    public static final String SETTINGS_DIR = "_settings";
    public static final String DEFAULT_SETTINGS =
            "/fr/pilato/elasticsearch/crawler/fs/settings/fscrawler-default.properties";
    public static final String EXAMPLE_SETTINGS = "/fr/pilato/elasticsearch/crawler/fs/settings/fscrawler-default.yaml";

    public FsSettingsLoader(Path root) {
        super(root);
    }

    /**
     * We read settings in ~/.fscrawler/{job_name}/_settings.[json|yml]
     *
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
        logger.debug(
                "Can not read settings from [{}] with either /_settings.yaml, /_settings.json, /_settings/*."
                        + " Falling back to default settings.",
                root.resolve(jobName).toAbsolutePath());
        return load();
    }

    /**
     * We write settings to ~/.fscrawler/{job_name}/_settings.yaml
     *
     * @param name the job_name
     * @param settings Settings to write (settings.getName() contains the job name)
     * @throws IOException in case of error while reading
     */
    public void write(String name, FsSettings settings) throws IOException {
        writeFile(name, SETTINGS_YAML, FsSettingsParser.toYaml(settings));
    }

    /**
     * Read all files in ~/.fscrawler/{job_name}/_settings directory
     *
     * @param settingsDir is _settings directory
     * @return The list of setting files
     */
    private List<Path> readDir(Path settingsDir) throws IOException {
        try (Stream<Path> files = Files.list(settingsDir)) {
            return files.toList();
        }
    }

    /**
     * Load settings from files. It could be both json or yaml files
     *
     * @param configFiles list of files to read
     * @return The settings
     * @throws FsCrawlerIllegalConfigurationException when we can not read settings
     */
    public static FsSettings load(Path... configFiles) throws FsCrawlerIllegalConfigurationException {
        // Pre-validate YAML files for syntax errors to provide meaningful messages with line/column info
        for (Path configFile : configFiles) {
            String fileName = configFile.getFileName().toString();
            if (fileName.endsWith(".yaml") || fileName.endsWith(".yml")) {
                try (InputStream is = Files.newInputStream(configFile)) {
                    // compose() validates syntax without constructing Java objects (safe, no tag processing)
                    new Yaml().compose(new InputStreamReader(is));
                } catch (YAMLException e) {
                    throw new FsCrawlerIllegalConfigurationException(
                            "Syntax error in configuration file [" + configFile.getFileName() + "]: " + e.getMessage(),
                            e);
                } catch (IOException e) {
                    // Let Gestalt handle file I/O errors
                }
            }
        }

        try {
            GestaltBuilder builder = new GestaltBuilder()
                    .addSource(ClassPathConfigSourceBuilder.builder()
                            .setResource(DEFAULT_SETTINGS)
                            .build());

            // This allows automatic configuration using System properties like -Dname=foobar or -Dfs.url=/tmp
            builder.addSource(SystemPropertiesConfigSourceBuilder.builder().build());
            // This allows automatic configuration using Env variables like FSCRAWLER_NAME=foobar or
            // FSCRAWLER_FS_URL=/tmp
            builder.addSource(EnvironmentConfigSourceBuilder.builder()
                    .setPrefix("FSCRAWLER_")
                    .setRemovePrefix(true)
                    .build());

            for (Path configFile : configFiles) {
                builder.addSource(
                        FileConfigSourceBuilder.builder().setPath(configFile).build());
            }

            Gestalt gestalt = builder.build();

            // Loads and parses the configurations, this will throw exceptions if there are any errors.
            gestalt.loadConfigs();

            FsSettings settings = new FsSettings();

            settings.setName(gestalt.getConfigOptional("name", String.class).orElse(null));
            settings.setFs(gestalt.getConfigOptional("fs", Fs.class).orElse(null));
            settings.setPasswords(loadPasswords(gestalt, configFiles));
            settings.setElasticsearch(gestalt.getConfigOptional("elasticsearch", Elasticsearch.class)
                    .orElse(null));
            settings.setTags(gestalt.getConfigOptional("tags", Tags.class).orElse(null));
            settings.setServer(gestalt.getConfigOptional("server", Server.class).orElse(null));
            settings.setRest(gestalt.getConfigOptional("rest", Rest.class).orElse(null));

            logger.debug(
                    "Successfully loaded settings from classpath [fscrawler-default.properties] and files {}",
                    (Object) configFiles);
            logger.trace("Loaded settings [{}]", settings);

            return settings;
        } catch (Exception e) {
            throw new FsCrawlerIllegalConfigurationException(
                    "Can not load settings. " + "Please make sure that your setting file(s) are properly formatted.",
                    e);
        }
    }

    /**
     * Load password settings without a closed typed graph for {@code providers}.
     *
     * <p>{@code passwords.provider} comes from Gestalt (YAML + env/sysprops). {@code passwords.providers} is an opaque
     * map keyed by plugin type and is loaded from YAML/JSON job files via SnakeYAML: Gestalt cannot decode mixed nested
     * maps/lists into {@code Map&lt;String, Object&gt;}. Each password plugin parses its own section.
     */
    private static Passwords loadPasswords(Gestalt gestalt, Path... configFiles) {
        Optional<String> provider = gestalt.getConfigOptional("passwords.provider", String.class);
        Map<String, Object> providers = loadPasswordProvidersFromFiles(configFiles);

        if (provider.isEmpty() && providers == null) {
            return null;
        }

        Passwords passwords = new Passwords();
        passwords.setProvider(provider.orElse("noop"));
        passwords.setProviders(providers);
        return passwords;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadPasswordProvidersFromFiles(Path... configFiles) {
        if (configFiles == null || configFiles.length == 0) {
            return null;
        }

        Yaml yaml = new Yaml();
        Map<String, Object> merged = new LinkedHashMap<>();
        for (Path configFile : configFiles) {
            if (configFile == null || Files.notExists(configFile)) {
                continue;
            }
            try (InputStream inputStream = Files.newInputStream(configFile)) {
                Object loaded = yaml.load(inputStream);
                if (!(loaded instanceof Map<?, ?> root)) {
                    continue;
                }
                Object passwordsNode = root.get("passwords");
                if (!(passwordsNode instanceof Map<?, ?> passwordsMap)) {
                    continue;
                }
                Object providersNode = passwordsMap.get("providers");
                if (!(providersNode instanceof Map<?, ?> providersMap)) {
                    continue;
                }
                deepMerge(merged, (Map<String, Object>) providersMap);
            } catch (IOException e) {
                logger.debug("Can not read [{}] while loading passwords.providers: {}", configFile, e.getMessage());
            }
        }
        return merged.isEmpty() ? null : merged;
    }

    @SuppressWarnings("unchecked")
    private static void deepMerge(Map<String, Object> target, Map<String, Object> source) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            Object existing = target.get(key);
            if (existing instanceof Map<?, ?> existingMap && value instanceof Map<?, ?> valueMap) {
                Map<String, Object> nested = new LinkedHashMap<>((Map<String, Object>) existingMap);
                deepMerge(nested, (Map<String, Object>) valueMap);
                target.put(key, nested);
            } else {
                target.put(key, value);
            }
        }
    }
}
