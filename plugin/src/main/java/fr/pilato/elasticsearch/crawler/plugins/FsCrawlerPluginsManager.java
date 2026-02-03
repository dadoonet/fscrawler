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
            logger.debug("Found FsCrawlerExtensionFsProvider extension for type [{}], supportsCrawling=[{}]",
                    extension.getType(), extension.supportsCrawling());
            fsProviders.put(extension.getType(), extension);
        }
    }

    public void close() {
        logger.debug("Stopping plugins");
        pluginManager.stopPlugins();
    }

    /**
     * Find a filesystem provider for single file operations (REST API).
     *
     * @param type the provider type (e.g., "local", "http", "s3")
     * @return the provider for the given type
     * @throws FsCrawlerIllegalConfigurationException if no provider is found for the type
     */
    public FsCrawlerExtensionFsProvider findFsProvider(String type) {
        logger.debug("Load FsProvider extension for type [{}]", type);
        FsCrawlerExtensionFsProvider fsCrawlerExtensionFsProvider = fsProviders.get(type);
        if (fsCrawlerExtensionFsProvider == null) {
            logger.warn("Can not find FsProvider for type [{}]", type);
            throw new FsCrawlerIllegalConfigurationException("No FsProvider found for type [" + type + "]");
        }
        return fsCrawlerExtensionFsProvider;
    }

    /**
     * Find a filesystem provider for directory crawling operations.
     * <p>
     * This method finds a provider and validates that it supports crawling.
     * </p>
     *
     * @param type the provider type (e.g., "local", "ftp", "ssh")
     * @return the provider for the given type (guaranteed to support crawling)
     * @throws FsCrawlerIllegalConfigurationException if no provider is found or if it doesn't support crawling
     */
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
}
