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
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentService;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentServiceElasticsearchImpl;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentServiceWorkplaceSearchImpl;
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

/**
 * @author dadoonet (David Pilato)
 */
public class FsCrawlerImpl implements AutoCloseable {

    @Deprecated
    public static final String INDEX_TYPE_FOLDER = "folder";

    private static final Logger logger = LogManager.getLogger(FsCrawlerImpl.class);

    public static final int LOOP_INFINITE = -1;
    public static final long MAX_SLEEP_RETRY_TIME = TimeValue.timeValueSeconds(30).millis();

    private final FsSettings settings;
    private final boolean rest;
    private final Path config;
    private final Integer loop;

    private Thread fsCrawlerThread;

    private final FsCrawlerDocumentService documentService;
    private final FsCrawlerManagementService managementService;

    private FsParser fsParser;

    public FsCrawlerImpl(Path config, FsSettings settings, Integer loop, boolean rest) {
        FsCrawlerUtil.createDirIfMissing(config);

        this.config = config;
        this.settings = settings;
        this.loop = loop;
        this.rest = rest;

        this.managementService = new FsCrawlerManagementServiceElasticsearchImpl(config, settings);

        if (settings.getWorkplaceSearch() == null) {
            // The documentService is using the esSearch instance
            this.documentService = new FsCrawlerDocumentServiceElasticsearchImpl(config, settings);
        } else {
            // The documentService is using the wpSearch instance
            this.documentService = new FsCrawlerDocumentServiceWorkplaceSearchImpl(config, settings);
        }

        // We don't go further as we have critical errors
        // It's just a double check as settings must be validated before creating the instance
        if (FsCrawlerValidator.validateSettings(logger, settings, rest)) {
            throw new RuntimeException("Settings are incorrect and should have been verified with FsCrawlerValidator.validateSettings before.");
        }

        // Generate the directory where we write status and other files
        Path jobSettingsFolder = config.resolve(settings.getName());
        try {
            Files.createDirectories(jobSettingsFolder);
        } catch (IOException e) {
            throw new RuntimeException("Can not create the job config directory", e);
        }
    }

    public FsCrawlerDocumentService getDocumentService() {
        return documentService;
    }

    public FsCrawlerManagementService getManagementService() {
        return managementService;
    }

    public void start() throws Exception {
        logger.info("Starting FS crawler");
        if (loop < 0) {
            logger.info("FS crawler started in watch mode. It will run unless you stop it with CTRL+C.");
        }

        if (loop == 0 && !rest) {
            logger.warn("Number of runs is set to 0 and rest layer has not been started. Exiting");
            return;
        }

        managementService.start();
        documentService.start();
        documentService.createSchema();

        // Start the crawler thread - but not if only in rest mode
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
        fsCrawlerThread.start();
    }

    public void close() throws InterruptedException, IOException {
        logger.debug("Closing FS crawler [{}]", settings.getName());

        if (fsParser != null) {
            fsParser.close();

            synchronized(fsParser.getSemaphore()) {
                fsParser.getSemaphore().notifyAll();
            }
        }

        if (this.fsCrawlerThread != null) {
            while (fsCrawlerThread.isAlive()) {
                // We check that the crawler has been closed effectively
                logger.debug("FS crawler thread is still running");
                if (logger.isDebugEnabled()) {
                    Thread.dumpStack();
                }
                Thread.sleep(500);
            }
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
