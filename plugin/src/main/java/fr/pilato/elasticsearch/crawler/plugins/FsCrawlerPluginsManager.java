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
package fr.pilato.elasticsearch.crawler.plugins;

import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsMigrator;
import fr.pilato.elasticsearch.crawler.fs.settings.pipeline.FilterSection;
import fr.pilato.elasticsearch.crawler.fs.settings.pipeline.InputSection;
import fr.pilato.elasticsearch.crawler.fs.settings.pipeline.OutputSection;
import fr.pilato.elasticsearch.crawler.plugins.pipeline.Pipeline;
import fr.pilato.elasticsearch.crawler.plugins.pipeline.filter.FilterPlugin;
import fr.pilato.elasticsearch.crawler.plugins.pipeline.input.InputPlugin;
import fr.pilato.elasticsearch.crawler.plugins.pipeline.output.OutputPlugin;
import fr.pilato.elasticsearch.crawler.plugins.service.ServicePlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Unified plugin manager for FsCrawler: FsProvider, pipeline (input/filter/output), and service plugins.
 * Uses a single PF4J PluginManager for discovery; pipeline and service plugins are also discovered via ServiceLoader.
 */
public class FsCrawlerPluginsManager implements AutoCloseable {

    private static final Logger logger = LogManager.getLogger(FsCrawlerPluginsManager.class);

    private final PluginManager pluginManager;
    private final Map<String, FsCrawlerExtensionFsProvider> fsProviders = new HashMap<>();
    private final Map<String, Class<? extends InputPlugin>> inputPluginTypes = new HashMap<>();
    private final Map<String, Class<? extends FilterPlugin>> filterPluginTypes = new HashMap<>();
    private final Map<String, Class<? extends OutputPlugin>> outputPluginTypes = new HashMap<>();
    private final Map<String, Class<? extends ServicePlugin>> servicePluginTypes = new HashMap<>();

    /** Loaded service plugin instances (populated when creating pipeline from directory). */
    private final List<ServicePlugin> servicePlugins = new ArrayList<>();

    public FsCrawlerPluginsManager() {
        pluginManager = new DefaultPluginManager();
    }

    public void loadPlugins() {
        logger.debug("Loading plugins");
        pluginManager.loadPlugins();
    }

    public void startPlugins() {
        logger.debug("Starting plugins");
        pluginManager.startPlugins();

        discoverPf4jPlugins();
        discoverServiceLoaderPlugins();

        logger.info("Plugins discovered: {} fs providers, {} inputs, {} filters, {} outputs, {} services",
                fsProviders.size(), inputPluginTypes.size(), filterPluginTypes.size(),
                outputPluginTypes.size(), servicePluginTypes.size());
    }

    private void discoverPf4jPlugins() {
        for (FsCrawlerExtensionFsProvider extension : pluginManager.getExtensions(FsCrawlerExtensionFsProvider.class)) {
            logger.debug("Found FsCrawlerExtensionFsProvider from PF4J for type [{}], supportsCrawling=[{}]",
                    extension.getType(), extension.supportsCrawling());
            fsProviders.put(extension.getType(), extension);
        }
        for (InputPlugin plugin : pluginManager.getExtensions(InputPlugin.class)) {
            String type = plugin.getType();
            if (!inputPluginTypes.containsKey(type)) {
                logger.debug("Found InputPlugin extension from PF4J for type [{}]", type);
                inputPluginTypes.put(type, plugin.getClass());
            }
        }
        for (FilterPlugin plugin : pluginManager.getExtensions(FilterPlugin.class)) {
            String type = plugin.getType();
            if (!filterPluginTypes.containsKey(type)) {
                logger.debug("Found FilterPlugin extension from PF4J for type [{}]", type);
                filterPluginTypes.put(type, plugin.getClass());
            }
        }
        for (OutputPlugin plugin : pluginManager.getExtensions(OutputPlugin.class)) {
            String type = plugin.getType();
            if (!outputPluginTypes.containsKey(type)) {
                logger.debug("Found OutputPlugin extension from PF4J for type [{}]", type);
                outputPluginTypes.put(type, plugin.getClass());
            }
        }
        for (ServicePlugin plugin : pluginManager.getExtensions(ServicePlugin.class)) {
            String type = plugin.getType();
            if (!servicePluginTypes.containsKey(type)) {
                logger.debug("Found ServicePlugin extension from PF4J for type [{}]", type);
                servicePluginTypes.put(type, plugin.getClass());
            }
        }
    }

    private void discoverServiceLoaderPlugins() {
        ServiceLoader<FsCrawlerExtensionFsProvider> fsLoader = ServiceLoader.load(FsCrawlerExtensionFsProvider.class);
        for (FsCrawlerExtensionFsProvider extension : fsLoader) {
            String type = extension.getType();
            if (!fsProviders.containsKey(type)) {
                logger.debug("Found FsCrawlerExtensionFsProvider from ServiceLoader for type [{}], supportsCrawling=[{}]",
                        type, extension.supportsCrawling());
                fsProviders.put(type, extension);
            }
        }
        ServiceLoader<InputPlugin> inputLoader = ServiceLoader.load(InputPlugin.class);
        for (InputPlugin plugin : inputLoader) {
            String type = plugin.getType();
            if (!inputPluginTypes.containsKey(type)) {
                logger.debug("Found InputPlugin from ServiceLoader for type [{}]", type);
                inputPluginTypes.put(type, plugin.getClass());
            }
        }
        ServiceLoader<FilterPlugin> filterLoader = ServiceLoader.load(FilterPlugin.class);
        for (FilterPlugin plugin : filterLoader) {
            String type = plugin.getType();
            if (!filterPluginTypes.containsKey(type)) {
                logger.debug("Found FilterPlugin from ServiceLoader for type [{}]", type);
                filterPluginTypes.put(type, plugin.getClass());
            }
        }
        ServiceLoader<OutputPlugin> outputLoader = ServiceLoader.load(OutputPlugin.class);
        for (OutputPlugin plugin : outputLoader) {
            String type = plugin.getType();
            if (!outputPluginTypes.containsKey(type)) {
                logger.debug("Found OutputPlugin from ServiceLoader for type [{}]", type);
                outputPluginTypes.put(type, plugin.getClass());
            }
        }
        ServiceLoader<ServicePlugin> serviceLoader = ServiceLoader.load(ServicePlugin.class);
        for (ServicePlugin plugin : serviceLoader) {
            String type = plugin.getType();
            if (!servicePluginTypes.containsKey(type)) {
                logger.debug("Found ServicePlugin from ServiceLoader for type [{}]", type);
                servicePluginTypes.put(type, plugin.getClass());
            }
        }
    }

    @Override
    public void close() {
        logger.debug("Stopping service plugins");
        stopServices();
        logger.debug("Stopping plugins");
        pluginManager.stopPlugins();
    }

    // ========== FsProvider API ==========

    public FsCrawlerExtensionFsProvider findFsProvider(String type) {
        logger.debug("Load FsProvider extension for type [{}]", type);
        FsCrawlerExtensionFsProvider provider = fsProviders.get(type);
        if (provider == null) {
            logger.warn("Can not find FsProvider for type [{}]", type);
            throw new FsCrawlerIllegalConfigurationException("No FsProvider found for type [" + type + "]");
        }
        return provider;
    }

    public FsCrawlerExtensionFsProvider findFsProviderForCrawling(String type) {
        FsCrawlerExtensionFsProvider provider = findFsProvider(type);
        if (!provider.supportsCrawling()) {
            logger.warn("FsProvider [{}] does not support directory crawling", type);
            throw new FsCrawlerIllegalConfigurationException(
                    "Provider [" + type + "] does not support directory crawling. " +
                    "Only local, ftp, and ssh providers support crawling.");
        }
        return provider;
    }

    // ========== Pipeline API ==========

    public Pipeline createPipeline(FsSettings settings) throws FsCrawlerIllegalConfigurationException {
        logger.debug("Creating pipeline for job [{}] from FsSettings", settings.getName());

        List<InputPlugin> inputs = new ArrayList<>();
        List<FilterPlugin> filters = new ArrayList<>();
        List<OutputPlugin> outputs = new ArrayList<>();

        if (settings.getInputs() != null) {
            for (InputSection section : settings.getInputs()) {
                inputs.add(createInputPlugin(section, settings));
            }
        }
        if (settings.getFilters() != null) {
            for (FilterSection section : settings.getFilters()) {
                filters.add(createFilterPlugin(section, settings));
            }
        }
        if (settings.getOutputs() != null) {
            for (OutputSection section : settings.getOutputs()) {
                outputs.add(createOutputPlugin(section, settings));
            }
        }

        logger.debug("Pipeline created with {} inputs, {} filters, {} outputs",
                inputs.size(), filters.size(), outputs.size());
        return new Pipeline(inputs, filters, outputs);
    }

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

        Path inputsDir = settingsDir.resolve(FsSettingsMigrator.INPUTS_DIR);
        if (Files.exists(inputsDir)) {
            inputs = loadInputPluginsFromDirectory(inputsDir);
        }
        Path filtersDir = settingsDir.resolve(FsSettingsMigrator.FILTERS_DIR);
        if (Files.exists(filtersDir)) {
            filters = loadFilterPluginsFromDirectory(filtersDir);
        }
        Path outputsDir = settingsDir.resolve(FsSettingsMigrator.OUTPUTS_DIR);
        if (Files.exists(outputsDir)) {
            outputs = loadOutputPluginsFromDirectory(outputsDir);
        }

        servicePlugins.clear();
        Path servicesDir = settingsDir.resolve(FsSettingsMigrator.SERVICES_DIR);
        if (Files.exists(servicesDir)) {
            servicePlugins.addAll(loadServicePluginsFromDirectory(servicesDir));
        }

        logger.debug("Pipeline created from directory with {} inputs, {} filters, {} outputs, {} services",
                inputs.size(), filters.size(), outputs.size(), servicePlugins.size());
        return new Pipeline(inputs, filters, outputs);
    }

    public boolean hasPerPluginConfig(Path configDir) {
        Path settingsDir = configDir.resolve(FsSettingsMigrator.SETTINGS_DIR);
        if (!Files.exists(settingsDir)) {
            return false;
        }
        Path inputsDir = settingsDir.resolve(FsSettingsMigrator.INPUTS_DIR);
        Path filtersDir = settingsDir.resolve(FsSettingsMigrator.FILTERS_DIR);
        Path outputsDir = settingsDir.resolve(FsSettingsMigrator.OUTPUTS_DIR);
        Path servicesDir = settingsDir.resolve(FsSettingsMigrator.SERVICES_DIR);
        return (Files.exists(inputsDir) && hasConfigFiles(inputsDir)) ||
               (Files.exists(filtersDir) && hasConfigFiles(filtersDir)) ||
               (Files.exists(outputsDir) && hasConfigFiles(outputsDir)) ||
               (Files.exists(servicesDir) && hasConfigFiles(servicesDir));
    }

    public void startServices() throws FsCrawlerPluginException {
        for (ServicePlugin plugin : servicePlugins) {
            if (plugin.isEnabled()) {
                try {
                    plugin.start();
                    logger.info("Service plugin [{}] ({}) started", plugin.getId(), plugin.getType());
                } catch (FsCrawlerPluginException e) {
                    logger.error("Failed to start service plugin [{}] ({}): {}",
                            plugin.getId(), plugin.getType(), e.getMessage());
                    throw e;
                }
            } else {
                logger.debug("Service plugin [{}] ({}) is disabled, not starting", plugin.getId(), plugin.getType());
            }
        }
    }

    public void stopServices() {
        for (ServicePlugin plugin : servicePlugins) {
            if (plugin.isRunning()) {
                try {
                    plugin.stop();
                    logger.debug("Service plugin [{}] ({}) stopped", plugin.getId(), plugin.getType());
                } catch (FsCrawlerPluginException e) {
                    logger.warn("Error stopping service plugin [{}] ({}): {}",
                            plugin.getId(), plugin.getType(), e.getMessage());
                }
            }
        }
    }

    public List<ServicePlugin> getServicePlugins() {
        return Collections.unmodifiableList(new ArrayList<>(servicePlugins));
    }

    // ========== Registration ==========

    public void registerInputPlugin(String type, Class<? extends InputPlugin> pluginClass) {
        logger.debug("Manually registering InputPlugin for type [{}]", type);
        inputPluginTypes.put(type, pluginClass);
    }

    public void registerFilterPlugin(String type, Class<? extends FilterPlugin> pluginClass) {
        logger.debug("Manually registering FilterPlugin for type [{}]", type);
        filterPluginTypes.put(type, pluginClass);
    }

    public void registerOutputPlugin(String type, Class<? extends OutputPlugin> pluginClass) {
        logger.debug("Manually registering OutputPlugin for type [{}]", type);
        outputPluginTypes.put(type, pluginClass);
    }

    public void registerServicePlugin(String type, Class<? extends ServicePlugin> pluginClass) {
        logger.debug("Manually registering ServicePlugin for type [{}]", type);
        servicePluginTypes.put(type, pluginClass);
    }

    // ========== Query ==========

    public Set<String> getAvailableInputTypes() {
        return Collections.unmodifiableSet(inputPluginTypes.keySet());
    }

    public Set<String> getAvailableFilterTypes() {
        return Collections.unmodifiableSet(filterPluginTypes.keySet());
    }

    public Set<String> getAvailableOutputTypes() {
        return Collections.unmodifiableSet(outputPluginTypes.keySet());
    }

    public Set<String> getAvailableServiceTypes() {
        return Collections.unmodifiableSet(servicePluginTypes.keySet());
    }

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

    public List<ServicePlugin> getDefaultServicePlugins() {
        List<ServicePlugin> plugins = new ArrayList<>();
        for (Class<? extends ServicePlugin> pluginClass : servicePluginTypes.values()) {
            try {
                ServicePlugin plugin = pluginClass.getDeclaredConstructor().newInstance();
                if (plugin.supportsPerPluginConfig() && plugin.getDefaultYamlResource() != null) {
                    plugins.add(plugin);
                }
            } catch (Exception e) {
                logger.warn("Could not instantiate service plugin {}: {}", pluginClass.getName(), e.getMessage());
            }
        }
        return plugins;
    }

    // ========== Private pipeline creation from FsSettings ==========

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

    // ========== Private directory loading ==========

    private boolean hasConfigFiles(Path dir) {
        try (Stream<Path> files = Files.list(dir)) {
            return files.anyMatch(this::isConfigFile);
        } catch (IOException e) {
            return false;
        }
    }

    private boolean isConfigFile(Path path) {
        if (!Files.isRegularFile(path)) {
            return false;
        }
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".yaml") || name.endsWith(".yml") ||
               name.endsWith(".json") || name.endsWith(".properties");
    }

    private List<Path> getConfigFilesInOrder(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files
                    .filter(this::isConfigFile)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        }
    }

    private String extractTypeFromFilename(Path configFile) {
        String filename = configFile.getFileName().toString();
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0) {
            filename = filename.substring(0, dotIndex);
        }
        int dashIndex = filename.indexOf('-');
        if (dashIndex > 0 && dashIndex < filename.length() - 1) {
            String prefix = filename.substring(0, dashIndex);
            if (prefix.matches("\\d+")) {
                return filename.substring(dashIndex + 1);
            }
        }
        return filename;
    }

    private List<InputPlugin> loadInputPluginsFromDirectory(Path inputsDir)
            throws FsCrawlerIllegalConfigurationException, IOException {
        List<InputPlugin> plugins = new ArrayList<>();
        for (Path configFile : getConfigFilesInOrder(inputsDir)) {
            logger.debug("Loading input plugin from [{}]", configFile);
            plugins.add(loadInputPluginFromFile(configFile));
        }
        return plugins;
    }

    private List<FilterPlugin> loadFilterPluginsFromDirectory(Path filtersDir)
            throws FsCrawlerIllegalConfigurationException, IOException {
        List<FilterPlugin> plugins = new ArrayList<>();
        for (Path configFile : getConfigFilesInOrder(filtersDir)) {
            logger.debug("Loading filter plugin from [{}]", configFile);
            plugins.add(loadFilterPluginFromFile(configFile));
        }
        return plugins;
    }

    private List<OutputPlugin> loadOutputPluginsFromDirectory(Path outputsDir)
            throws FsCrawlerIllegalConfigurationException, IOException {
        List<OutputPlugin> plugins = new ArrayList<>();
        for (Path configFile : getConfigFilesInOrder(outputsDir)) {
            logger.debug("Loading output plugin from [{}]", configFile);
            plugins.add(loadOutputPluginFromFile(configFile));
        }
        return plugins;
    }

    private List<ServicePlugin> loadServicePluginsFromDirectory(Path servicesDir)
            throws FsCrawlerIllegalConfigurationException, IOException {
        List<ServicePlugin> plugins = new ArrayList<>();
        for (Path configFile : getConfigFilesInOrder(servicesDir)) {
            logger.debug("Loading service plugin from [{}]", configFile);
            plugins.add(loadServicePluginFromFile(configFile));
        }
        return plugins;
    }

    private InputPlugin loadInputPluginFromFile(Path configFile)
            throws FsCrawlerIllegalConfigurationException, IOException {
        String type = extractTypeFromFilename(configFile);
        Class<? extends InputPlugin> pluginClass = inputPluginTypes.get(type);
        if (pluginClass == null) {
            throw new FsCrawlerIllegalConfigurationException(
                    "Unknown input plugin type: " + type + " (from file " + configFile + "). " +
                    "Available: " + inputPluginTypes.keySet());
        }
        try {
            InputPlugin plugin = pluginClass.getDeclaredConstructor().newInstance();
            if (plugin.supportsPerPluginConfig()) {
                plugin.loadSettings(configFile);
            } else {
                logger.warn("Input plugin [{}] does not support per-plugin config.", type);
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
                logger.warn("Filter plugin [{}] does not support per-plugin config.", type);
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
                logger.warn("Output plugin [{}] does not support per-plugin config.", type);
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

    private ServicePlugin loadServicePluginFromFile(Path configFile)
            throws FsCrawlerIllegalConfigurationException, IOException {
        String type = extractTypeFromFilename(configFile);
        Class<? extends ServicePlugin> pluginClass = servicePluginTypes.get(type);
        if (pluginClass == null) {
            throw new FsCrawlerIllegalConfigurationException(
                    "Unknown service plugin type: " + type + " (from file " + configFile + "). " +
                    "Available: " + servicePluginTypes.keySet());
        }
        try {
            ServicePlugin plugin = pluginClass.getDeclaredConstructor().newInstance();
            if (plugin.supportsPerPluginConfig()) {
                plugin.loadSettings(configFile);
            } else {
                logger.warn("Service plugin [{}] does not support per-plugin config.", type);
                throw new FsCrawlerIllegalConfigurationException(
                        "Service plugin " + type + " does not yet support per-plugin configuration.");
            }
            plugin.validateConfiguration();
            return plugin;
        } catch (FsCrawlerIllegalConfigurationException | IOException e) {
            throw e;
        } catch (Exception e) {
            throw new FsCrawlerIllegalConfigurationException(
                    "Failed to create service plugin from " + configFile + ": " + e.getMessage(), e);
        }
    }
}
