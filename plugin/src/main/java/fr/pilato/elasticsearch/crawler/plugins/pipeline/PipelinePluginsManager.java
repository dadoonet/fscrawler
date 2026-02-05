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
     */
    public void startPlugins() {
        logger.debug("Starting pipeline plugins");
        pluginManager.startPlugins();

        // Discover input plugins
        for (InputPlugin plugin : pluginManager.getExtensions(InputPlugin.class)) {
            logger.debug("Found InputPlugin extension for type [{}]", plugin.getType());
            inputPluginTypes.put(plugin.getType(), plugin.getClass());
        }

        // Discover filter plugins
        for (FilterPlugin plugin : pluginManager.getExtensions(FilterPlugin.class)) {
            logger.debug("Found FilterPlugin extension for type [{}]", plugin.getType());
            filterPluginTypes.put(plugin.getType(), plugin.getClass());
        }

        // Discover output plugins
        for (OutputPlugin plugin : pluginManager.getExtensions(OutputPlugin.class)) {
            logger.debug("Found OutputPlugin extension for type [{}]", plugin.getType());
            outputPluginTypes.put(plugin.getType(), plugin.getClass());
        }

        logger.info("Pipeline plugins started: {} inputs, {} filters, {} outputs",
                inputPluginTypes.size(), filterPluginTypes.size(), outputPluginTypes.size());
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
        outputPluginTypes.put(type, pluginClass);
    }
}
