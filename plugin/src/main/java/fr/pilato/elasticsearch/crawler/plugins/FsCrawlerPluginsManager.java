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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginManager;

import java.util.HashMap;

public class FsCrawlerPluginsManager implements AutoCloseable {

    private static final Logger logger = LogManager.getLogger();
    private final PluginManager pluginManager;
    private final HashMap<String, FsCrawlerExtensionFsProvider> fsProviders;

    public FsCrawlerPluginsManager() {
        pluginManager = new DefaultPluginManager();
        fsProviders = new HashMap<>();
    }

    public void loadPlugins() {
        logger.debug("Loading plugins");
        pluginManager.loadPlugins();
    }

    public void startPlugins() {
        logger.debug("Starting plugins");
        pluginManager.startPlugins();

        for (FsCrawlerExtensionFsProvider extension : pluginManager.getExtensions(FsCrawlerExtensionFsProvider.class)) {
            logger.debug("Found FsCrawlerExtensionFsProvider extension for type [{}]", extension.getType());
            fsProviders.put(extension.getType(), extension);
        }
    }

    public void close() {
        logger.debug("Stopping plugins");
        pluginManager.stopPlugins();
    }

    public FsCrawlerExtensionFsProvider findFsProvider(String type) {
        logger.debug("Load extension for type [{}]", type);
        FsCrawlerExtensionFsProvider fsCrawlerExtensionFsProvider = fsProviders.get(type);
        if (fsCrawlerExtensionFsProvider == null) {
            throw new FsCrawlerIllegalConfigurationException("No FsProvider found for type [" + type + "]");
        }
        return fsCrawlerExtensionFsProvider;
    }
}
