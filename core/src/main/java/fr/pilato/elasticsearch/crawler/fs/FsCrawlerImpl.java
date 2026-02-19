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

package fr.pilato.elasticsearch.crawler.fs;

import fr.pilato.elasticsearch.crawler.fs.beans.FsCrawlerCheckpointFileHandler;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentService;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentServiceElasticsearchImpl;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerManagementService;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerManagementServiceElasticsearchImpl;
import fr.pilato.elasticsearch.crawler.fs.settings.FsCrawlerValidator;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.Server;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerExtensionFsProvider;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPluginsManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

import static org.awaitility.Awaitility.await;

/**
 * @author dadoonet (David Pilato)
 */
public class FsCrawlerImpl implements AutoCloseable {

    private static final Logger logger = LogManager.getLogger();

    public static final int LOOP_INFINITE = -1;

    private final FsSettings settings;
    private final boolean rest;
    private final Integer loop;

    private final FsCrawlerDocumentService documentService;
    private final FsCrawlerManagementService managementService;
    private final FsCrawlerPluginsManager pluginsManager;
    private final FsParser fsParser;
    private final Thread fsCrawlerThread;

    public FsCrawlerImpl(Path config, FsSettings settings, Integer loop, boolean rest) {
        FsCrawlerUtil.createDirIfMissing(config);

        this.settings = settings;
        this.loop = loop;
        this.rest = rest;

        this.managementService = new FsCrawlerManagementServiceElasticsearchImpl(settings);
        this.documentService = new FsCrawlerDocumentServiceElasticsearchImpl(settings);

        // Initialize and start the plugin manager
        this.pluginsManager = new FsCrawlerPluginsManager();
        pluginsManager.loadPlugins();
        pluginsManager.startPlugins();

        // We don't go further as we have critical errors
        // It's just a double check as settings must be validated before creating the instance
        if (FsCrawlerValidator.validateSettings(logger, settings)) {
            throw new RuntimeException("Settings are incorrect and should have been verified with FsCrawlerValidator.validateSettings before.");
        }

        // Generate the directory where we write status and other files
        Path jobSettingsFolder = config.resolve(settings.getName());
        FsCrawlerUtil.createDirIfMissing(jobSettingsFolder);

        // Migrate the old status file to the new checkpoint file if needed
        new FsCrawlerCheckpointFileHandler(config).migrateLegacyStatus(settings.getName());

        // Set default temp directory if not configured
        if (settings.getFs().getTempDir() == null) {
            Path tempDir = jobSettingsFolder.resolve("tmp");
            settings.getFs().setTempDir(tempDir.toString());
            logger.debug("Using default temp directory: [{}]", tempDir);
        }

        // Create the fsParser instance depending on the settings
        if (loop != 0) {
            // Determine the protocol type
            String protocolType = determineProtocolType(settings);
            logger.debug("Using crawler plugin for protocol type [{}]", protocolType);

            // Get the crawler plugin from the plugin manager (validates that it supports crawling)
            FsCrawlerExtensionFsProvider crawlerPlugin = pluginsManager.findFsProviderForCrawling(protocolType);

            // Initialize the plugin with settings
            crawlerPlugin.start(settings, "{}");

            // Create the parser with the plugin
            fsParser = new FsParserAbstract(settings, config, managementService, documentService, loop, crawlerPlugin);
        } else {
            // We start a No-OP parser
            fsParser = new FsParserNoop(settings);
        }
        fsCrawlerThread = new Thread(fsParser, "fs-crawler");
    }

    /**
     * Determine the provider type from settings.
     * <p>
     * Uses fs.provider if specified, otherwise falls back to server.protocol (deprecated).
     * </p>
     *
     * @param settings the FSCrawler settings
     * @return the provider type string (e.g., "local", "ftp", "ssh")
     * @throws FsCrawlerIllegalConfigurationException if server settings are required but missing
     */
    private static String determineProtocolType(FsSettings settings) {
        // Check if fs.provider is explicitly set (new way)
        String provider = settings.getFs().getProvider();
        if (provider != null && !provider.isEmpty()) {
            logger.debug("Using fs.provider [{}]", provider);
            // Validate server settings for remote providers
            validateServerSettings(provider, settings);
            return provider;
        }

        // Fall back to server.protocol (deprecated)
        if (settings.getServer() != null) {
            String protocol = settings.getServer().getProtocol();
            if (protocol != null && !Server.PROTOCOL.LOCAL.equals(protocol)) {
                logger.warn("Setting server.protocol is deprecated and will be removed in a future version. " +
                        "Please use fs.provider: \"{}\" instead.", protocol);
                return protocol;
            }
        }

        // Default to local
        return "local";
    }

    /**
     * Validate that server settings are properly configured for remote providers.
     *
     * @param provider the provider type
     * @param settings the FSCrawler settings
     * @throws FsCrawlerIllegalConfigurationException if server settings are required but missing
     */
    private static void validateServerSettings(String provider, FsSettings settings) {
        // Remote providers require server settings
        if ("ftp".equals(provider) || "ssh".equals(provider)) {
            if (settings.getServer() == null) {
                throw new FsCrawlerIllegalConfigurationException(
                        "Provider [" + provider + "] requires server settings (hostname, username, etc.)");
            }
            if (settings.getServer().getHostname() == null || settings.getServer().getHostname().isEmpty()) {
                throw new FsCrawlerIllegalConfigurationException(
                        "Provider [" + provider + "] requires server.hostname to be set");
            }
        }
    }

    public FsCrawlerDocumentService getDocumentService() {
        return documentService;
    }

    public FsCrawlerManagementService getManagementService() {
        return managementService;
    }

    public FsCrawlerPluginsManager getPluginsManager() {
        return pluginsManager;
    }

    public void start() throws Exception {
        managementService.start();
        documentService.start();
        documentService.createSchema();

        if (loop == 0 && !rest) {
            logger.warn("Number of runs is set to 0 and rest layer has not been started. Exiting");
            return;
        }

        logger.info("FSCrawler is now connected to Elasticsearch version [{}]", managementService.getVersion());

        logger.debug("Starting FSCrawler for job [{}]", settings.getName());
        if (loop < 0) {
            logger.info("FSCrawler started in watch mode. It will run unless you stop it with CTRL+C.");
        }

        fsCrawlerThread.start();
        fsParser.closed.set(false);
    }

    @Override
    public void close() throws InterruptedException, IOException {
        logger.debug("Closing FS crawler [{}]", settings.getName());

        if (fsParser != null) {
            fsParser.close();

            synchronized(fsParser.getSemaphore()) {
                fsParser.getSemaphore().notifyAll();
            }
        }

        if (this.fsCrawlerThread != null) {
            await()
                    .pollInterval(Duration.ofMillis(500))
                    .forever()
                    .until(() -> {
                        if (fsCrawlerThread.isAlive()) {
                            // We check that the crawler has been closed effectively
                            logger.debug("FS crawler thread is still running");
                            if (logger.isDebugEnabled()) {
                                Thread.dumpStack();
                            }
                            return false;
                        }
                        return true;
                    });
            logger.debug("FS crawler thread is now stopped");
        }

        managementService.close();
        documentService.close();
        logger.debug("ES Client Manager stopped");

        pluginsManager.close();
        logger.debug("Plugins Manager stopped");

        logger.info("FS crawler [{}] stopped", settings.getName());
    }

    public FsParser getFsParser() {
        return fsParser;
    }
}
