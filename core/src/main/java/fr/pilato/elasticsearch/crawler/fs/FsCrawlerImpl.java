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

import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentService;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentServiceElasticsearchImpl;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerManagementService;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerManagementServiceElasticsearchImpl;
import fr.pilato.elasticsearch.crawler.fs.settings.FsCrawlerValidator;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.Server;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.awaitility.Awaitility.await;

/**
 * @author dadoonet (David Pilato)
 */
public class FsCrawlerImpl implements AutoCloseable {

    @Deprecated
    public static final String INDEX_TYPE_FOLDER = "folder";

    private static final Logger logger = LogManager.getLogger();

    public static final int LOOP_INFINITE = -1;

    private final FsSettings settings;
    private final boolean rest;
    private final Integer loop;

    private final FsCrawlerDocumentService documentService;
    private final FsCrawlerManagementService managementService;
    private final FsParser fsParser;
    private final Thread fsCrawlerThread;

    public FsCrawlerImpl(Path config, FsSettings settings, Integer loop, boolean rest) {
        FsCrawlerUtil.createDirIfMissing(config);

        this.settings = settings;
        this.loop = loop;
        this.rest = rest;

        this.managementService = new FsCrawlerManagementServiceElasticsearchImpl(settings);
        this.documentService = new FsCrawlerDocumentServiceElasticsearchImpl(settings);

        // We don't go further as we have critical errors
        // It's just a double check as settings must be validated before creating the instance
        if (FsCrawlerValidator.validateSettings(logger, settings)) {
            throw new RuntimeException("Settings are incorrect and should have been verified with FsCrawlerValidator.validateSettings before.");
        }

        // Generate the directory where we write status and other files
        Path jobSettingsFolder = config.resolve(settings.getName());
        try {
            Files.createDirectories(jobSettingsFolder);
        } catch (IOException e) {
            throw new RuntimeException("Can not create the job config directory", e);
        }

        // Set default temp directory if not configured
        if (settings.getFs().getTempDir() == null) {
            Path tempDir = jobSettingsFolder.resolve("tmp");
            settings.getFs().setTempDir(tempDir.toString());
            logger.debug("Using default temp directory: [{}]", tempDir);
        }

        // Create the fsParser instance depending on the settings
        if (loop != 0) {
            // What is the protocol used?
            if (settings.getServer() == null || Server.PROTOCOL.LOCAL.equals(settings.getServer().getProtocol())) {
                // Local FS
                fsParser = new FsParserLocal(settings, config, managementService, documentService, loop);
            } else if (Server.PROTOCOL.SSH.equals(settings.getServer().getProtocol())) {
                // Remote SSH FS
                fsParser = new FsParserSsh(settings, config, managementService, documentService, loop);
            } else if (Server.PROTOCOL.FTP.equals(settings.getServer().getProtocol())) {
                // Remote FTP FS
                fsParser = new FsParserFTP(settings, config, managementService, documentService, loop);
            } else {
                // Non supported protocol
                throw new RuntimeException(settings.getServer().getProtocol() + " is not supported yet. Please use " +
                        Server.PROTOCOL.LOCAL + " or " + Server.PROTOCOL.SSH);
            }
        } else {
            // We start a No-OP parser
            fsParser = new FsParserNoop(settings);
        }
        fsCrawlerThread = new Thread(fsParser, "fs-crawler");
    }

    public FsCrawlerDocumentService getDocumentService() {
        return documentService;
    }

    public FsCrawlerManagementService getManagementService() {
        return managementService;
    }

    public void start() throws Exception {
        if (loop == 0 && !rest) {
            logger.warn("Number of runs is set to 0 and rest layer has not been started. Exiting");
            return;
        }

        managementService.start();
        documentService.start();
        documentService.createSchema();

        logger.info("FSCrawler is now connected to Elasticsearch version [{}]", managementService.getVersion());

        logger.debug("Starting FSCrawler for job [{}]", settings.getName());
        if (loop < 0) {
            logger.info("FSCrawler started in watch mode. It will run unless you stop it with CTRL+C.");
        }

        fsCrawlerThread.start();
        fsParser.closed = false;
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

        logger.info("FS crawler [{}] stopped", settings.getName());
    }

    public FsParser getFsParser() {
        return fsParser;
    }
}
