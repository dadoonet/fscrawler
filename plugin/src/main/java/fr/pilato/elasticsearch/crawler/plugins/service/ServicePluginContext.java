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

package fr.pilato.elasticsearch.crawler.plugins.service;

import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPluginsManager;

/**
 * Context provided to service plugins when they are started.
 * Gives access to crawler services and settings so that plugins (e.g. REST) can integrate.
 * <p>
 * Document and management services are exposed as Object to avoid plugin module depending on core;
 * implementations (e.g. RestServicePlugin) cast them to the concrete types from the rest/core modules.
 */
public interface ServicePluginContext {

    /**
     * Global FSCrawler settings for the running job.
     */
    FsSettings getFsSettings();

    /**
     * Management service (e.g. Elasticsearch version, cluster operations).
     * Cast to fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerManagementService in plugins that need it.
     */
    Object getManagementService();

    /**
     * Document service (index, delete, search).
     * Cast to fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentService in plugins that need it.
     */
    Object getDocumentService();

    /**
     * Plugin manager for resolving providers and pipeline.
     */
    FsCrawlerPluginsManager getPluginsManager();
}
