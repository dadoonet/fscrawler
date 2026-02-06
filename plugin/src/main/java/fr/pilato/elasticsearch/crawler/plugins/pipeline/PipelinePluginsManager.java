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
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsMigrator;
import fr.pilato.elasticsearch.crawler.fs.settings.pipeline.FilterSection;
import fr.pilato.elasticsearch.crawler.fs.settings.pipeline.InputSection;
import fr.pilato.elasticsearch.crawler.fs.settings.pipeline.OutputSection;
import fr.pilato.elasticsearch.crawler.plugins.pipeline.filter.FilterPlugin;
import fr.pilato.elasticsearch.crawler.plugins.pipeline.input.InputPlugin;
import fr.pilato.elasticsearch.crawler.plugins.pipeline.output.OutputPlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Stream;

/**
 * Manager for pipeline plugins.
 * Discovers, loads, and configures input, filter, and output plugins.
 * Creates Pipeline instances from FsSettings.
 */
public class PipelinePluginsManager implements AutoCloseable {

    private static final Logger logger = LogManager.getLogger(PipelinePluginsManager.class);

    private final PluginManager pluginManager;
    private final Map<String, Class<? extends InputPlugin>> inputPluginTypes = new HashMap<>();
    private final Map<String, Class<? extends FilterPlugin>> filterPluginTypes = new HashMap<>();
    private final Map<String, Class<? extends OutputPlugin>> outputPluginTypes = new HashMap<>();

    public PipelinePluginsManager() {
        this.pluginManager = new DefaultPluginManager();
    }

    /**
     * Loads all plugins from the plugins directory.
     */
    public void loadPlugins() {
        logger.debug("Loading pipeline plugins");
        pluginManager.loadPlugins();
    }

    /**
     * Starts all plugins and discovers available extensions.
     * Discovers plugins from:
     * 1. PF4J plugin manager (external JARs in plugins directory)
     * 2. Java ServiceLoader (built-in plugins on classpath via META-INF/services)
     */
    public void startPlugins() {
        logger.debug("Starting pipeline plugins");
        pluginManager.startPlugins();

        // Discover plugins from PF4J (external JARs)
        discoverPf4jPlugins();

        // Discover plugins from ServiceLoader (built-in plugins on classpath)
        discoverServiceLoaderPlugins();

        logger.info("Pipeline plugins discovered: {} inputs, {} filters, {} outputs",
                inputPluginTypes.size(), filterPluginTypes.size(), outputPluginTypes.size());
    }

    /**
     * Discovers plugins using PF4J extension mechanism (for external JAR plugins).
     */
    private void discoverPf4jPlugins() {
        // Discover input plugins from PF4J
        for (InputPlugin plugin : pluginManager.getExtensions(InputPlugin.class)) {
            String type = plugin.getType();
            if (!inputPluginTypes.containsKey(type)) {
                logger.debug("Found InputPlugin extension from PF4J for type [{}]", type);
                inputPluginTypes.put(type, plugin.getClass());
            }
        }

        // Discover filter plugins from PF4J
        for (FilterPlugin plugin : pluginManager.getExtensions(FilterPlugin.class)) {
            String type = plugin.getType();
            if (!filterPluginTypes.containsKey(type)) {
                logger.debug("Found FilterPlugin extension from PF4J for type [{}]", type);
                filterPluginTypes.put(type, plugin.getClass());
            }
        }

        // Discover output plugins from PF4J
        for (OutputPlugin plugin : pluginManager.getExtensions(OutputPlugin.class)) {
            String type = plugin.getType();
            if (!outputPluginTypes.containsKey(type)) {
                logger.debug("Found OutputPlugin extension from PF4J for type [{}]", type);
                outputPluginTypes.put(type, plugin.getClass());
            }
        }
    }

    /**
     * Discovers plugins using Java ServiceLoader (for built-in plugins on classpath).
     * This allows plugins to be discovered without external JAR packaging.
     */
    private void discoverServiceLoaderPlugins() {
        // Discover input plugins from ServiceLoader
        ServiceLoader<InputPlugin> inputLoader = ServiceLoader.load(InputPlugin.class);
        for (InputPlugin plugin : inputLoader) {
            String type = plugin.getType();
            if (!inputPluginTypes.containsKey(type)) {
                logger.debug("Found InputPlugin from ServiceLoader for type [{}]", type);
                inputPluginTypes.put(type, plugin.getClass());
            }
        }

        // Discover filter plugins from ServiceLoader
        ServiceLoader<FilterPlugin> filterLoader = ServiceLoader.load(FilterPlugin.class);
        for (FilterPlugin plugin : filterLoader) {
            String type = plugin.getType();
            if (!filterPluginTypes.containsKey(type)) {
                logger.debug("Found FilterPlugin from ServiceLoader for type [{}]", type);
                filterPluginTypes.put(type, plugin.getClass());
            }
        }

        // Discover output plugins from ServiceLoader
        ServiceLoader<OutputPlugin> outputLoader = ServiceLoader.load(OutputPlugin.class);
        for (OutputPlugin plugin : outputLoader) {
            String type = plugin.getType();
            if (!outputPluginTypes.containsKey(type)) {
                logger.debug("Found OutputPlugin from ServiceLoader for type [{}]", type);
                outputPluginTypes.put(type, plugin.getClass());
            }
        }
    }

    /**
     * Creates a Pipeline from FsSettings (legacy mode).
     * <p>
     * This method uses the centralized configuration approach where all plugin
     * settings are in the FsSettings object.
     *
     * @param settings the FsSettings configuration
     * @return a configured Pipeline
     * @throws FsCrawlerIllegalConfigurationException if configuration is invalid
     */
    public Pipeline createPipeline(FsSettings settings) throws FsCrawlerIllegalConfigurationException {
        logger.debug("Creating pipeline for job [{}] from FsSettings", settings.getName());

        List<InputPlugin> inputs = new ArrayList<>();
        List<FilterPlugin> filters = new ArrayList<>();
        List<OutputPlugin> outputs = new ArrayList<>();

        // Create input plugins
        if (settings.getInputs() != null) {
            for (InputSection section : settings.getInputs()) {
                InputPlugin plugin = createInputPlugin(section, settings);
                inputs.add(plugin);
            }
        }

        // Create filter plugins
        if (settings.getFilters() != null) {
            for (FilterSection section : settings.getFilters()) {
                FilterPlugin plugin = createFilterPlugin(section, settings);
                filters.add(plugin);
            }
        }

        // Create output plugins
        if (settings.getOutputs() != null) {
            for (OutputSection section : settings.getOutputs()) {
                OutputPlugin plugin = createOutputPlugin(section, settings);
                outputs.add(plugin);
            }
        }

        logger.debug("Pipeline created with {} inputs, {} filters, {} outputs",
                inputs.size(), filters.size(), outputs.size());

        return new Pipeline(inputs, filters, outputs);
    }

    /**
     * Creates a Pipeline from per-plugin configuration files.
     * <p>
     * This method scans the _settings directory for plugin configuration files:
     * <ul>
     *   <li>_settings/inputs/*.yaml - Input plugin configurations</li>
     *   <li>_settings/filters/*.yaml - Filter plugin configurations</li>
     *   <li>_settings/outputs/*.yaml - Output plugin configurations</li>
     * </ul>
     * Files are loaded in alphabetical order (use numeric prefixes like 01-, 02- to control order).
     *
     * @param settingsDir Path to the _settings directory
     * @param jobName The job name for logging
     * @return a configured Pipeline
     * @throws FsCrawlerIllegalConfigurationException if configuration is invalid
     * @throws IOException if config files cannot be read
     */
    public Pipeline createPipelineFromDirectory(Path settingsDir, String jobName) 
            throws FsCrawlerIllegalConfigurationException, IOException {
        logger.debug("Creating pipeline for job [{}] from directory [{}]", jobName, settingsDir);

        if (!Files.exists(settingsDir)) {
            throw new FsCrawlerIllegalConfigurationException(
                    "Settings directory not found: " + settingsDir);
        }

        List<InputPlugin> inputs = new ArrayList<>();
        List<FilterPlugin> filters = new ArrayList<>();
        List<OutputPlugin> outputs = new ArrayList<>();

        // Load input plugins
        Path inputsDir = settingsDir.resolve(FsSettingsMigrator.INPUTS_DIR);
        if (Files.exists(inputsDir)) {
            inputs = loadInputPluginsFromDirectory(inputsDir);
        }

        // Load filter plugins
        Path filtersDir = settingsDir.resolve(FsSettingsMigrator.FILTERS_DIR);
        if (Files.exists(filtersDir)) {
            filters = loadFilterPluginsFromDirectory(filtersDir);
        }

        // Load output plugins
        Path outputsDir = settingsDir.resolve(FsSettingsMigrator.OUTPUTS_DIR);
        if (Files.exists(outputsDir)) {
            outputs = loadOutputPluginsFromDirectory(outputsDir);
        }

        logger.debug("Pipeline created from directory with {} inputs, {} filters, {} outputs",
                inputs.size(), filters.size(), outputs.size());

        return new Pipeline(inputs, filters, outputs);
    }

    /**
     * Checks if a per-plugin configuration directory exists.
     *
     * @param configDir The base configuration directory for the job
     * @return true if _settings/ directory exists with plugin subdirectories
     */
    public boolean hasPerPluginConfig(Path configDir) {
        Path settingsDir = configDir.resolve(FsSettingsMigrator.SETTINGS_DIR);
        if (!Files.exists(settingsDir)) {
            return false;
        }
        // Check if at least one plugin subdirectory exists with files
        Path inputsDir = settingsDir.resolve(FsSettingsMigrator.INPUTS_DIR);
        Path filtersDir = settingsDir.resolve(FsSettingsMigrator.FILTERS_DIR);
        Path outputsDir = settingsDir.resolve(FsSettingsMigrator.OUTPUTS_DIR);
        
        return (Files.exists(inputsDir) && hasConfigFiles(inputsDir)) ||
               (Files.exists(filtersDir) && hasConfigFiles(filtersDir)) ||
               (Files.exists(outputsDir) && hasConfigFiles(outputsDir));
    }

    /**
     * Checks if a directory contains configuration files.
     */
    private boolean hasConfigFiles(Path dir) {
        try (Stream<Path> files = Files.list(dir)) {
            return files.anyMatch(this::isConfigFile);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Checks if a path is a configuration file (yaml, yml, json, properties).
     */
    private boolean isConfigFile(Path path) {
        if (!Files.isRegularFile(path)) {
            return false;
        }
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".yaml") || name.endsWith(".yml") || 
               name.endsWith(".json") || name.endsWith(".properties");
    }

    /**
     * Loads input plugins from configuration files in a directory.
     */
    private List<InputPlugin> loadInputPluginsFromDirectory(Path inputsDir) 
            throws FsCrawlerIllegalConfigurationException, IOException {
        List<InputPlugin> plugins = new ArrayList<>();
        List<Path> configFiles = getConfigFilesInOrder(inputsDir);

        for (Path configFile : configFiles) {
            logger.debug("Loading input plugin from [{}]", configFile);
            InputPlugin plugin = loadInputPluginFromFile(configFile);
            plugins.add(plugin);
        }

        return plugins;
    }

    /**
     * Loads filter plugins from configuration files in a directory.
     */
    private List<FilterPlugin> loadFilterPluginsFromDirectory(Path filtersDir) 
            throws FsCrawlerIllegalConfigurationException, IOException {
        List<FilterPlugin> plugins = new ArrayList<>();
        List<Path> configFiles = getConfigFilesInOrder(filtersDir);

        for (Path configFile : configFiles) {
            logger.debug("Loading filter plugin from [{}]", configFile);
            FilterPlugin plugin = loadFilterPluginFromFile(configFile);
            plugins.add(plugin);
        }

        return plugins;
    }

    /**
     * Loads output plugins from configuration files in a directory.
     */
    private List<OutputPlugin> loadOutputPluginsFromDirectory(Path outputsDir) 
            throws FsCrawlerIllegalConfigurationException, IOException {
        List<OutputPlugin> plugins = new ArrayList<>();
        List<Path> configFiles = getConfigFilesInOrder(outputsDir);

        for (Path configFile : configFiles) {
            logger.debug("Loading output plugin from [{}]", configFile);
            OutputPlugin plugin = loadOutputPluginFromFile(configFile);
            plugins.add(plugin);
        }

        return plugins;
    }

    /**
     * Gets configuration files in a directory, sorted alphabetically.
     * This allows using numeric prefixes (01-, 02-) to control loading order.
     */
    private List<Path> getConfigFilesInOrder(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files
                    .filter(this::isConfigFile)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        }
    }

    /**
     * Loads a single input plugin from a configuration file.
     */
    private InputPlugin loadInputPluginFromFile(Path configFile) 
            throws FsCrawlerIllegalConfigurationException, IOException {
        // First, we need to determine the plugin type from the file
        // For now, we extract the type from filename (e.g., 01-local.yaml -> local)
        // In the future, plugins will load their own typed settings
        String type = extractTypeFromFilename(configFile);
        
        Class<? extends InputPlugin> pluginClass = inputPluginTypes.get(type);
        if (pluginClass == null) {
            throw new FsCrawlerIllegalConfigurationException(
                    "Unknown input plugin type: " + type + " (from file " + configFile + "). " +
                    "Available: " + inputPluginTypes.keySet());
        }

        try {
            InputPlugin plugin = pluginClass.getDeclaredConstructor().newInstance();
            
            // If plugin supports per-plugin config, use loadSettings
            if (plugin.supportsPerPluginConfig()) {
                plugin.loadSettings(configFile);
            } else {
                // Fallback: log warning that plugin needs migration
                logger.warn("Input plugin [{}] does not support per-plugin config. " +
                        "Consider implementing loadSettings() for proper support.", type);
                throw new FsCrawlerIllegalConfigurationException(
                        "Input plugin " + type + " does not yet support per-plugin configuration. " +
                        "Use the legacy _settings.yaml format instead.");
            }
            
            plugin.validateConfiguration();
            return plugin;
        } catch (FsCrawlerIllegalConfigurationException | IOException e) {
            throw e;
        } catch (Exception e) {
            throw new FsCrawlerIllegalConfigurationException(
                    "Failed to create input plugin from " + configFile + ": " + e.getMessage(), e);
        }
    }

    /**
     * Loads a single filter plugin from a configuration file.
     */
    private FilterPlugin loadFilterPluginFromFile(Path configFile) 
            throws FsCrawlerIllegalConfigurationException, IOException {
        String type = extractTypeFromFilename(configFile);
        
        Class<? extends FilterPlugin> pluginClass = filterPluginTypes.get(type);
        if (pluginClass == null) {
            throw new FsCrawlerIllegalConfigurationException(
                    "Unknown filter plugin type: " + type + " (from file " + configFile + "). " +
                    "Available: " + filterPluginTypes.keySet());
        }

        try {
            FilterPlugin plugin = pluginClass.getDeclaredConstructor().newInstance();
            
            if (plugin.supportsPerPluginConfig()) {
                plugin.loadSettings(configFile);
            } else {
                logger.warn("Filter plugin [{}] does not support per-plugin config. " +
                        "Consider implementing loadSettings() for proper support.", type);
                throw new FsCrawlerIllegalConfigurationException(
                        "Filter plugin " + type + " does not yet support per-plugin configuration. " +
                        "Use the legacy _settings.yaml format instead.");
            }
            
            plugin.validateConfiguration();
            return plugin;
        } catch (FsCrawlerIllegalConfigurationException | IOException e) {
            throw e;
        } catch (Exception e) {
            throw new FsCrawlerIllegalConfigurationException(
                    "Failed to create filter plugin from " + configFile + ": " + e.getMessage(), e);
        }
    }

    /**
     * Loads a single output plugin from a configuration file.
     */
    private OutputPlugin loadOutputPluginFromFile(Path configFile) 
            throws FsCrawlerIllegalConfigurationException, IOException {
        String type = extractTypeFromFilename(configFile);
        
        Class<? extends OutputPlugin> pluginClass = outputPluginTypes.get(type);
        if (pluginClass == null) {
            throw new FsCrawlerIllegalConfigurationException(
                    "Unknown output plugin type: " + type + " (from file " + configFile + "). " +
                    "Available: " + outputPluginTypes.keySet());
        }

        try {
            OutputPlugin plugin = pluginClass.getDeclaredConstructor().newInstance();
            
            if (plugin.supportsPerPluginConfig()) {
                plugin.loadSettings(configFile);
            } else {
                logger.warn("Output plugin [{}] does not support per-plugin config. " +
                        "Consider implementing loadSettings() for proper support.", type);
                throw new FsCrawlerIllegalConfigurationException(
                        "Output plugin " + type + " does not yet support per-plugin configuration. " +
                        "Use the legacy _settings.yaml format instead.");
            }
            
            plugin.validateConfiguration();
            return plugin;
        } catch (FsCrawlerIllegalConfigurationException | IOException e) {
            throw e;
        } catch (Exception e) {
            throw new FsCrawlerIllegalConfigurationException(
                    "Failed to create output plugin from " + configFile + ": " + e.getMessage(), e);
        }
    }

    /**
     * Extracts the plugin type from a filename.
     * Assumes format: NN-type.ext (e.g., 01-local.yaml -> local)
     */
    private String extractTypeFromFilename(Path configFile) {
        String filename = configFile.getFileName().toString();
        // Remove extension
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0) {
            filename = filename.substring(0, dotIndex);
        }
        // Remove numeric prefix (e.g., "01-")
        int dashIndex = filename.indexOf('-');
        if (dashIndex > 0 && dashIndex < filename.length() - 1) {
            // Check if prefix is numeric
            String prefix = filename.substring(0, dashIndex);
            if (prefix.matches("\\d+")) {
                return filename.substring(dashIndex + 1);
            }
        }
        return filename;
    }

    @Override
    public void close() {
        logger.debug("Stopping pipeline plugins");
        pluginManager.stopPlugins();
    }

    // ========== Private methods ==========

    private InputPlugin createInputPlugin(InputSection section, FsSettings settings) 
            throws FsCrawlerIllegalConfigurationException {
        String type = section.getType();
        Class<? extends InputPlugin> pluginClass = inputPluginTypes.get(type);

        if (pluginClass == null) {
            throw new FsCrawlerIllegalConfigurationException(
                    "Unknown input plugin type: " + type + ". Available: " + inputPluginTypes.keySet());
        }

        try {
            InputPlugin plugin = pluginClass.getDeclaredConstructor().newInstance();
            plugin.setId(section.getId());
            plugin.configure(section.getRawConfig(), settings);
            plugin.validateConfiguration();
            return plugin;
        } catch (FsCrawlerIllegalConfigurationException e) {
            throw e;
        } catch (Exception e) {
            throw new FsCrawlerIllegalConfigurationException(
                    "Failed to create input plugin of type " + type + ": " + e.getMessage(), e);
        }
    }

    private FilterPlugin createFilterPlugin(FilterSection section, FsSettings settings) 
            throws FsCrawlerIllegalConfigurationException {
        String type = section.getType();
        Class<? extends FilterPlugin> pluginClass = filterPluginTypes.get(type);

        if (pluginClass == null) {
            throw new FsCrawlerIllegalConfigurationException(
                    "Unknown filter plugin type: " + type + ". Available: " + filterPluginTypes.keySet());
        }

        try {
            FilterPlugin plugin = pluginClass.getDeclaredConstructor().newInstance();
            plugin.setId(section.getId());
            if (section.getWhen() != null) {
                plugin.setWhen(section.getWhen());
            }
            plugin.configure(section.getRawConfig(), settings);
            plugin.validateConfiguration();
            return plugin;
        } catch (FsCrawlerIllegalConfigurationException e) {
            throw e;
        } catch (Exception e) {
            throw new FsCrawlerIllegalConfigurationException(
                    "Failed to create filter plugin of type " + type + ": " + e.getMessage(), e);
        }
    }

    private OutputPlugin createOutputPlugin(OutputSection section, FsSettings settings) 
            throws FsCrawlerIllegalConfigurationException {
        String type = section.getType();
        Class<? extends OutputPlugin> pluginClass = outputPluginTypes.get(type);

        if (pluginClass == null) {
            throw new FsCrawlerIllegalConfigurationException(
                    "Unknown output plugin type: " + type + ". Available: " + outputPluginTypes.keySet());
        }

        try {
            OutputPlugin plugin = pluginClass.getDeclaredConstructor().newInstance();
            plugin.setId(section.getId());
            if (section.getWhen() != null) {
                plugin.setWhen(section.getWhen());
            }
            plugin.configure(section.getRawConfig(), settings);
            plugin.validateConfiguration();
            return plugin;
        } catch (FsCrawlerIllegalConfigurationException e) {
            throw e;
        } catch (Exception e) {
            throw new FsCrawlerIllegalConfigurationException(
                    "Failed to create output plugin of type " + type + ": " + e.getMessage(), e);
        }
    }

    // ========== Registration methods for manual plugin registration ==========

    /**
     * Manually registers an input plugin type.
     * Useful for built-in plugins or testing.
     *
     * @param type the plugin type identifier
     * @param pluginClass the plugin class
     */
    public void registerInputPlugin(String type, Class<? extends InputPlugin> pluginClass) {
        logger.debug("Manually registering InputPlugin for type [{}]", type);
        inputPluginTypes.put(type, pluginClass);
    }

    /**
     * Manually registers a filter plugin type.
     * Useful for built-in plugins or testing.
     *
     * @param type the plugin type identifier
     * @param pluginClass the plugin class
     */
    public void registerFilterPlugin(String type, Class<? extends FilterPlugin> pluginClass) {
        logger.debug("Manually registering FilterPlugin for type [{}]", type);
        filterPluginTypes.put(type, pluginClass);
    }

    /**
     * Manually registers an output plugin type.
     * Useful for built-in plugins or testing.
     *
     * @param type the plugin type identifier
     * @param pluginClass the plugin class
     */
    public void registerOutputPlugin(String type, Class<? extends OutputPlugin> pluginClass) {
        logger.debug("Manually registering OutputPlugin for type [{}]", type);
        outputPluginTypes.put(type, pluginClass);
    }

    // ========== Query methods ==========

    /**
     * Returns the available input plugin types.
     * @return set of input plugin type names
     */
    public java.util.Set<String> getAvailableInputTypes() {
        return java.util.Collections.unmodifiableSet(inputPluginTypes.keySet());
    }

    /**
     * Returns the available filter plugin types.
     * @return set of filter plugin type names
     */
    public java.util.Set<String> getAvailableFilterTypes() {
        return java.util.Collections.unmodifiableSet(filterPluginTypes.keySet());
    }

    /**
     * Returns the available output plugin types.
     * @return set of output plugin type names
     */
    public java.util.Set<String> getAvailableOutputTypes() {
        return java.util.Collections.unmodifiableSet(outputPluginTypes.keySet());
    }

    // ========== Setup helper methods ==========

    /**
     * Returns all default input plugins for setup purposes.
     * Creates fresh instances of each registered input plugin.
     *
     * @return list of input plugin instances
     */
    public List<InputPlugin> getDefaultInputPlugins() {
        List<InputPlugin> plugins = new ArrayList<>();
        for (Class<? extends InputPlugin> pluginClass : inputPluginTypes.values()) {
            try {
                InputPlugin plugin = pluginClass.getDeclaredConstructor().newInstance();
                if (plugin.supportsPerPluginConfig() && plugin.getDefaultYamlResource() != null) {
                    plugins.add(plugin);
                }
            } catch (Exception e) {
                logger.warn("Could not instantiate input plugin {}: {}", pluginClass.getName(), e.getMessage());
            }
        }
        return plugins;
    }

    /**
     * Returns all default filter plugins for setup purposes.
     * Creates fresh instances of each registered filter plugin.
     *
     * @return list of filter plugin instances
     */
    public List<FilterPlugin> getDefaultFilterPlugins() {
        List<FilterPlugin> plugins = new ArrayList<>();
        for (Class<? extends FilterPlugin> pluginClass : filterPluginTypes.values()) {
            try {
                FilterPlugin plugin = pluginClass.getDeclaredConstructor().newInstance();
                if (plugin.supportsPerPluginConfig() && plugin.getDefaultYamlResource() != null) {
                    plugins.add(plugin);
                }
            } catch (Exception e) {
                logger.warn("Could not instantiate filter plugin {}: {}", pluginClass.getName(), e.getMessage());
            }
        }
        return plugins;
    }

    /**
     * Returns all default output plugins for setup purposes.
     * Creates fresh instances of each registered output plugin.
     *
     * @return list of output plugin instances
     */
    public List<OutputPlugin> getDefaultOutputPlugins() {
        List<OutputPlugin> plugins = new ArrayList<>();
        for (Class<? extends OutputPlugin> pluginClass : outputPluginTypes.values()) {
            try {
                OutputPlugin plugin = pluginClass.getDeclaredConstructor().newInstance();
                if (plugin.supportsPerPluginConfig() && plugin.getDefaultYamlResource() != null) {
                    plugins.add(plugin);
                }
            } catch (Exception e) {
                logger.warn("Could not instantiate output plugin {}: {}", pluginClass.getName(), e.getMessage());
            }
        }
        return plugins;
    }
}
