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


import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.client.WorkplaceSearchClient;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.thirdparty.wpsearch.WPSearchClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static fr.pilato.elasticsearch.crawler.fs.client.WorkplaceSearchClientUtil.docToJson;
import static fr.pilato.elasticsearch.crawler.fs.client.WorkplaceSearchClientUtil.generateDefaultCustomSourceName;

/**
 * Workplace Search Client for Clusters running v7.
 * It also starts an embedded Elasticsearch Client.
 */
public class WorkplaceSearchClientV7 implements WorkplaceSearchClient {

    private static final Logger logger = LogManager.getLogger(WorkplaceSearchClientV7.class);
    private final Path config;
    private final FsSettings settings;

    private WPSearchClient wpSearchClient = null;

    public WorkplaceSearchClientV7(Path config, FsSettings settings) {
        this.config = config;
        this.settings = settings;
    }

    @Override
    public void start() throws IOException {
        logger.debug("Starting Workplace Search V7 client");
        Path jobMappingDir = config.resolve(settings.getName()).resolve("_mappings");
        wpSearchClient = new WPSearchClient(config, jobMappingDir)
            .withHost(settings.getWorkplaceSearch().getServer().decodedUrl())
            .withUsername(settings.getWorkplaceSearch().getUsername(), settings.getElasticsearch().getUsername())
            .withPassword(settings.getWorkplaceSearch().getPassword(), settings.getElasticsearch().getPassword())
            .withBulkSize(settings.getWorkplaceSearch().getBulkSize())
            .withFlushInterval(settings.getWorkplaceSearch().getFlushInterval());
        wpSearchClient.start();

        // If the source name is provided, let's use it
        String sourceName = settings.getWorkplaceSearch().getName();
        if (sourceName == null) {
            // If not, we will use a default one
            sourceName = generateDefaultCustomSourceName(settings.getName());
        }

        wpSearchClient.configureCustomSource(settings.getWorkplaceSearch().getId(), sourceName);

        logger.debug("Workplace Search V7 client started");
    }

    @Override
    public void close() {
        logger.debug("Closing Workplace Search V7 client");
        if (wpSearchClient != null) {
            wpSearchClient.close();
        }
        logger.debug("Workplace Search V7 client closed");
    }

    @Override
    public void index(String id, Doc doc) {
        wpSearchClient.indexDocument(docToJson(id, doc, settings.getWorkplaceSearch().getUrlPrefix()));
    }

    @Override
    public void delete(String id) {
        wpSearchClient.destroyDocument(id);
    }

    @Override
    public String search(String query, Map<String, Object> filters) {
        return wpSearchClient.search(query, filters);
    }

    @Override
    public boolean exists(String id) {
        return wpSearchClient.getDocument(id) != null;
    }

    @Override
    public String get(String id) {
        return wpSearchClient.getDocument(id);
    }

    @Override
    public void flush() {
        wpSearchClient.flush();
    }
}
