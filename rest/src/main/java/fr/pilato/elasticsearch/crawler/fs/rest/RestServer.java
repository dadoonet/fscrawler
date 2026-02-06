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

package fr.pilato.elasticsearch.crawler.fs.rest;

import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentService;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerManagementService;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPluginsManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;

import java.net.URI;
import java.util.Map;

public class RestServer implements AutoCloseable {
    private static final Logger logger = LogManager.getLogger();
    private final FsSettings settings;
    private final FsCrawlerManagementService managementService;
    private final FsCrawlerDocumentService documentService;
    private final FsCrawlerPluginsManager pluginsManager;
    private final Map<String, String> pluginStatus;
    private HttpServer httpServer = null;

    /**
     * Create the Rest Server, but it does not start it.
     * @param settings FSCrawler settings
     * @param managementService The management service
     * @param documentService The document service
     * @param pluginsManager The plugins manager instance
     */
    public RestServer(FsSettings settings,
                      FsCrawlerManagementService managementService,
                      FsCrawlerDocumentService documentService,
                      FsCrawlerPluginsManager pluginsManager) {
        this(settings, managementService, documentService, pluginsManager, null);
    }

    /**
     * Create the Rest Server with optional plugin status for the status API.
     * @param pluginStatus Optional map with keys "inputs", "filters", "outputs", "services" (e.g. "local ✅", "rest ❌"); null to omit from status
     */
    public RestServer(FsSettings settings,
                      FsCrawlerManagementService managementService,
                      FsCrawlerDocumentService documentService,
                      FsCrawlerPluginsManager pluginsManager,
                      Map<String, String> pluginStatus) {
        this.settings = settings;
        this.managementService = managementService;
        this.documentService = documentService;
        this.pluginsManager = pluginsManager;
        this.pluginStatus = pluginStatus;
    }

    /**
     * Starts Grizzly HTTP server exposing JAX-RS resources defined in this application.
     */
    public void start() {
        // We create the service only one
        if (httpServer == null) {
            // create a resource config that scans for JAX-RS resources and providers
            // in fr.pilato.elasticsearch.crawler.fs.rest package
            final ResourceConfig rc = new ResourceConfig()
                    .registerInstances(
                            new ServerStatusApi(managementService, settings, pluginStatus),
                            new DocumentApi(settings, documentService, pluginsManager))
                    .register(MultiPartFeature.class)
                    .register(RestJsonProvider.class)
                    .register(JacksonFeature.class)
                    .register(new CORSFilter(settings.getRest()))
                    .packages("fr.pilato.elasticsearch.crawler.fs.rest");

            // create and start a new instance of grizzly http server
            // exposing the Jersey application at BASE_URI
            httpServer = GrizzlyHttpServerFactory.createHttpServer(URI.create(settings.getRest().getUrl()), rc);
            logger.info("FSCrawler Rest service started on [{}]", settings.getRest().getUrl());
        } else {
            logger.warn("FSCrawler Rest service already started. This might indicate a bug.");
        }
    }

    public void close() {
        if (httpServer != null) {
            httpServer.shutdownNow();
            httpServer = null;
            logger.debug("FS crawler Rest service stopped");
        }
    }
}
