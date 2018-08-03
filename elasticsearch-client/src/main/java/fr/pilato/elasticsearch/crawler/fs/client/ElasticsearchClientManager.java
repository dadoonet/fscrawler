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

import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.Version;
import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.unit.TimeValue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.INDEX_SETTINGS_FILE;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.INDEX_SETTINGS_FOLDER_FILE;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.readJsonFile;

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

    /**
     * We can probably remove that bulk processor as we now support ingest pipeline per request
     * @return a BulkProcessor instance
     */
    @Deprecated
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
            client = new ElasticsearchClient(ElasticsearchClient.buildRestClient(settings.getElasticsearch()));
            // We set what will be elasticsearch behavior as it depends on the cluster version
            client.setElasticsearchBehavior();
        } catch (Exception e) {
            logger.warn("failed to create elasticsearch client, disabling crawler...");
            throw e;
        }

        if (settings.getElasticsearch().getPipeline() != null) {
            // Check that we don't try using an ingest pipeline with a non compatible version
            if (!client.isIngestSupported()) {
                throw new RuntimeException("You defined pipeline:" + settings.getElasticsearch().getPipeline() +
                        ", but your elasticsearch cluster does not support this feature.");
            }

            // Check that the pipeline exists
            if (!client.isExistingPipeline(settings.getElasticsearch().getPipeline())) {
                throw new RuntimeException("You defined pipeline:" + settings.getElasticsearch().getPipeline() +
                        ", but it does not exist.");
            }
        }

        bulkProcessorDoc = BulkProcessor.builder(client::bulkAsync, new DebugListener(logger))
                .setBulkActions(settings.getElasticsearch().getBulkSize())
                .setFlushInterval(TimeValue.timeValueMillis(settings.getElasticsearch().getFlushInterval().millis()))
                .setBulkSize(new ByteSizeValue(settings.getElasticsearch().getByteSize().getBytes()))
                // TODO fix when elasticsearch will support global pipelines
//                .setPipeline(settings.getElasticsearch().getPipeline())
                .build();
        bulkProcessorFolder = BulkProcessor.builder(client::bulkAsync, new DebugListener(logger))
                .setBulkActions(settings.getElasticsearch().getBulkSize())
                .setBulkSize(new ByteSizeValue(settings.getElasticsearch().getByteSize().getBytes()))
                .setFlushInterval(TimeValue.timeValueMillis(settings.getElasticsearch().getFlushInterval().millis()))
                .build();
    }

    class DebugListener implements BulkProcessor.Listener {
        private final Logger logger;

        DebugListener(Logger logger) {
            this.logger = logger;
        }

        @Override public void beforeBulk(long executionId, BulkRequest request) {
            logger.trace("Sending a bulk request of [{}] requests", request.numberOfActions());
        }

        @Override public void afterBulk(long executionId, BulkRequest request, BulkResponse response) {
            logger.trace("Executed bulk request with [{}] requests", request.numberOfActions());
            if (response.hasFailures()) {
                final int[] failures = {0};
                response.iterator().forEachRemaining(bir -> {
                    if (bir.isFailed()) {
                        failures[0]++;
                        logger.debug("Error caught for [{}]/[{}]/[{}]: {}", bir.getIndex(),
                                bir.getType(), bir.getId(), bir.getFailureMessage());
                    };
                });
                logger.warn("Got [{}] failures of [{}] requests", failures[0], request.numberOfActions());
            }
        }

        @Override public void afterBulk(long executionId, BulkRequest request, Throwable failure) {
            logger.warn("Got a hard failure when executing the bulk request", failure);
        }
    }

    public void createIndices() throws Exception {
        String elasticsearchVersion;
        Path jobMappingDir = config.resolve(settings.getName()).resolve("_mappings");

        // Let's read the current version of elasticsearch cluster
        Version version = client.info().getVersion();
        logger.debug("FS crawler connected to an elasticsearch [{}] node.", version.toString());

        elasticsearchVersion = Byte.toString(version.major);

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
            String indexSettings = readJsonFile(jobMappingDir, config, elasticsearchVersion, indexSettingsFile);

            client.createIndex(indexName, true, indexSettings);
        } catch (Exception e) {
            logger.warn("failed to create index [{}], disabling crawler...", indexName);
            throw e;
        }
    }

    public void close() throws InterruptedException {
        logger.debug("Closing Elasticsearch client manager");
        if (bulkProcessorDoc != null) {
            try {
                bulkProcessorDoc.awaitClose(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                logger.warn("Did not succeed in closing the bulk processor for documents", e);
                throw e;
            }
        }
        if (bulkProcessorFolder != null) {
            try {
                bulkProcessorFolder.awaitClose(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                logger.warn("Did not succeed in closing the bulk processor for folders", e);
                throw e;
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
