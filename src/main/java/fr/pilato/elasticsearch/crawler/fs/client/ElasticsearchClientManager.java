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

package fr.pilato.elasticsearch.crawler.fs.client;

import fr.pilato.elasticsearch.crawler.fs.meta.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.util.FsCrawlerUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;

import static fr.pilato.elasticsearch.crawler.fs.util.FsCrawlerUtil.INDEX_SETTINGS_FILE;
import static fr.pilato.elasticsearch.crawler.fs.util.FsCrawlerUtil.INDEX_SETTINGS_FOLDER_FILE;
import static fr.pilato.elasticsearch.crawler.fs.util.FsCrawlerUtil.extractMajorVersionNumber;

public class ElasticsearchClientManager {
    private final Logger logger = LogManager.getLogger(ElasticsearchClientManager.class);
    private final Path config;
    private final FsSettings settings;

    private ElasticsearchClient client = null;
    private BulkProcessor bulkProcessorDoc = null;
    private BulkProcessor bulkProcessorFolder = null;

    public ElasticsearchClientManager(Path config, FsSettings settings) {
        this.config = config;
        this.settings = settings;
    }

    public ElasticsearchClient client() {
        if (client == null) {
            throw new RuntimeException("You must call start() before client()");
        }
        return client;
    }

    public BulkProcessor bulkProcessorDoc() {
        if (bulkProcessorDoc == null) {
            throw new RuntimeException("You must call start() before bulkProcessorDoc()");
        }
        return bulkProcessorDoc;
    }

    public BulkProcessor bulkProcessorFolder() {
        if (bulkProcessorFolder == null) {
            throw new RuntimeException("You must call start() before bulkProcessorFolder()");
        }
        return bulkProcessorFolder;
    }

    public void start() throws Exception {
        if (client != null) {
            // The client has already been initialized. Let's skip this again
            return;
        }

        try {
            // Create an elasticsearch client
            client = new ElasticsearchClient(settings.getElasticsearch());
            // We set what will be elasticsearch behavior as it depends on the cluster version
            client.setElasticsearchBehavior();
        } catch (Exception e) {
            logger.warn("failed to create elasticsearch client, disabling crawler...");
            throw e;
        }

        // Check that we don't try using an ingest pipeline with a non compatible version
        if (settings.getElasticsearch().getPipeline() != null && !client.isIngestSupported()) {
            throw new RuntimeException("You defined pipeline:" + settings.getElasticsearch().getPipeline() +
                    ", but your elasticsearch cluster does not support this feature.");
        }

        bulkProcessorDoc = BulkProcessor.retryBulkProcessor(client, settings.getElasticsearch().getBulkSize(),
                settings.getElasticsearch().getFlushInterval(), settings.getElasticsearch().getPipeline());
        bulkProcessorFolder = BulkProcessor.retryBulkProcessor(client, settings.getElasticsearch().getBulkSize(),
                settings.getElasticsearch().getFlushInterval(), null);
    }

    public void createIndices(FsSettings settings) throws Exception {
        String elasticsearchVersion;
        Path jobMappingDir = config.resolve(settings.getName()).resolve("_mappings");

        // Let's read the current version of elasticsearch cluster
        String version = client.findVersion();
        logger.debug("FS crawler connected to an elasticsearch [{}] node.", version);

        elasticsearchVersion = extractMajorVersionNumber(version);

        // If needed, we create the new settings for this files index
        if (settings.getFs().isAddAsInnerObject() == false || (!settings.getFs().isJsonSupport() && !settings.getFs().isXmlSupport())) {
            createIndex(jobMappingDir, elasticsearchVersion, INDEX_SETTINGS_FILE, settings.getElasticsearch().getIndex());
        } else {
            client.createIndex(settings.getElasticsearch().getIndex(), true, null);
        }

        // If needed, we create the new settings for this folder index
        if (settings.getFs().isIndexFolders()) {
            createIndex(jobMappingDir, elasticsearchVersion, INDEX_SETTINGS_FOLDER_FILE, settings.getElasticsearch().getIndexFolder());
        } else {
            client.createIndex(settings.getElasticsearch().getIndexFolder(), true, null);
        }
    }

    private void createIndex(Path jobMappingDir, String elasticsearchVersion, String indexSettingsFile, String indexName) throws Exception {
        try {
            // If needed, we create the new settings for this files index
            String indexSettings =
                    FsCrawlerUtil.readJsonFile(jobMappingDir, config, elasticsearchVersion, indexSettingsFile);

            client.createIndex(indexName, true, indexSettings);
        } catch (Exception e) {
            logger.warn("failed to create index [{}], disabling crawler...", indexName);
            throw e;
        }
    }

    public void close() {
        logger.debug("Closing Elasticsearch client manager");
        if (bulkProcessorDoc != null) {
            try {
                bulkProcessorDoc.close();
            } catch (InterruptedException e) {
                logger.warn("Can not close doc bulk processor", e);
            }
        }
        if (bulkProcessorFolder != null) {
            try {
                bulkProcessorFolder.close();
            } catch (InterruptedException e) {
                logger.warn("Can not close folder bulk processor", e);
            }
        }
        if (client != null) {
            try {
                client.shutdown();
            } catch (IOException e) {
                logger.warn("Can not close elasticsearch client", e);
            }
        }
    }
}
