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

import static fr.pilato.elasticsearch.crawler.fs.util.FsCrawlerUtil.extractMajorVersionNumber;

public class ElasticsearchClientManager {
    private final Logger logger = LogManager.getLogger(ElasticsearchClientManager.class);
    private final Path config;
    private final FsSettings settings;

    private ElasticsearchClient client = null;
    private BulkProcessor bulkProcessor = null;

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

    public BulkProcessor bulkProcessor() {
        if (bulkProcessor == null) {
            throw new RuntimeException("You must call start() before bulkProcessor()");
        }
        return bulkProcessor;
    }

    public void start() throws Exception {
        try {
            // Create an elasticsearch client
            client = new ElasticsearchClient(settings.getElasticsearch());
            // We set what will be elasticsearch behavior as it depends on the cluster version
            client.setElasticsearchBehavior();
        } catch (Exception e) {
            logger.warn("failed to create index [{}], disabling crawler...", settings.getElasticsearch().getIndex());
            throw e;
        }

        // Check that we don't try using an ingest pipeline with a non compatible version
        if (settings.getElasticsearch().getPipeline() != null && !client.isIngestSupported()) {
            throw new RuntimeException("You defined pipeline:" + settings.getElasticsearch().getPipeline() +
                    ", but your elasticsearch cluster does not support this feature.");
        }

        bulkProcessor = BulkProcessor.simpleBulkProcessor(client, settings.getElasticsearch().getBulkSize(),
                settings.getElasticsearch().getFlushInterval(), settings.getElasticsearch().getPipeline());
    }

    public void createIndexAndMappings(FsSettings settings, boolean updateMapping) throws Exception {
        String elasticsearchVersion;
        try {
            client.createIndex(settings.getElasticsearch().getIndex(), true);

            // Let's read the current version of elasticsearch cluster
            String version = client.findVersion();
            logger.debug("FS crawler connected to an elasticsearch [{}] node.", version);

            elasticsearchVersion = extractMajorVersionNumber(version);
        } catch (Exception e) {
            logger.warn("failed to create index [{}], disabling crawler...", settings.getElasticsearch().getIndex());
            throw e;
        }

        try {
            // If needed, we create the new mapping for files
            Path jobMappingDir = config.resolve(settings.getName()).resolve("_mappings");
            if (!settings.getFs().isJsonSupport() && !settings.getFs().isXmlSupport()) {
                // Read file mapping from resources
                String mapping = FsCrawlerUtil.readMapping(jobMappingDir, config, elasticsearchVersion, FsCrawlerUtil.INDEX_TYPE_DOC);
                ElasticsearchClient.pushMapping(client, settings.getElasticsearch().getIndex(), settings.getElasticsearch().getType(),
                        mapping, updateMapping);
            }
            // If needed, we create the new mapping for folders
            if (settings.getFs().isIndexFolders()) {
                String mapping = FsCrawlerUtil.readMapping(jobMappingDir, config, elasticsearchVersion, FsCrawlerUtil.INDEX_TYPE_FOLDER);
                ElasticsearchClient.pushMapping(client, settings.getElasticsearch().getIndex(), FsCrawlerUtil.INDEX_TYPE_FOLDER,
                        mapping, updateMapping);
            }
        } catch (Exception e) {
            logger.warn("failed to {} mapping for [{}/{}], disabling crawler...", updateMapping ? "update" : "create",
                    settings.getElasticsearch().getIndex(), settings.getElasticsearch().getType());
            throw e;
        }
    }

    public void close() {
        if (bulkProcessor != null) {
            try {
                bulkProcessor.close();
            } catch (InterruptedException e) {
                logger.warn("Can not close bulk processor", e);
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
