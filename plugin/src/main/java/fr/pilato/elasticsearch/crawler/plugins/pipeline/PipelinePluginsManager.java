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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

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
     * Creates a Pipeline from FsSettings.
     *
     * @param settings the FsSettings configuration
     * @return a configured Pipeline
     * @throws FsCrawlerIllegalConfigurationException if configuration is invalid
     */
    public Pipeline createPipeline(FsSettings settings) throws FsCrawlerIllegalConfigurationException {
        logger.debug("Creating pipeline for job [{}]", settings.getName());

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
}
