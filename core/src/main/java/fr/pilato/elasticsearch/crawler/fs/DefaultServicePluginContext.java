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

import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentService;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerManagementService;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPluginsManager;
import fr.pilato.elasticsearch.crawler.plugins.service.ServicePluginContext;

/**
 * Default implementation of {@link ServicePluginContext} used by the crawler runner.
 */
public class DefaultServicePluginContext implements ServicePluginContext {

    private final FsSettings fsSettings;
    private final FsCrawlerManagementService managementService;
    private final FsCrawlerDocumentService documentService;
    private final FsCrawlerPluginsManager pluginsManager;

    public DefaultServicePluginContext(FsSettings fsSettings,
                                       FsCrawlerManagementService managementService,
                                       FsCrawlerDocumentService documentService,
                                       FsCrawlerPluginsManager pluginsManager) {
        this.fsSettings = fsSettings;
        this.managementService = managementService;
        this.documentService = documentService;
        this.pluginsManager = pluginsManager;
    }

    @Override
    public FsSettings getFsSettings() {
        return fsSettings;
    }

    @Override
    public Object getManagementService() {
        return managementService;
    }

    @Override
    public Object getDocumentService() {
        return documentService;
    }

    @Override
    public FsCrawlerPluginsManager getPluginsManager() {
        return pluginsManager;
    }
}
