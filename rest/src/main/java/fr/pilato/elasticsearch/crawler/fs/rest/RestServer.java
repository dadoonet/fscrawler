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

import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientBase;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;

import java.net.URI;

public class RestServer {

    private static HttpServer httpServer = null;
    private static final Logger logger = LogManager.getLogger();

    /**
     * Starts Grizzly HTTP server exposing JAX-RS resources defined in this application.
     * @param settings FSCrawler settings
     * @param esClient Elasticsearch client
     */
    public static void start(FsSettings settings, ElasticsearchClientBase esClient) {
        // We create the service only one
        if (httpServer == null) {
            // create a resource config that scans for JAX-RS resources and providers
            // in fr.pilato.elasticsearch.crawler.fs.rest package
            final ResourceConfig rc = new ResourceConfig()
                    .registerInstances(
                            new ServerStatusApi(esClient, settings),
                            new UploadApi(settings, esClient))
                    .register(MultiPartFeature.class)
                    .register(RestJsonProvider.class)
                    .register(JacksonFeature.class)
                    .packages("fr.pilato.elasticsearch.crawler.fs.rest");

            // create and start a new instance of grizzly http server
            // exposing the Jersey application at BASE_URI
            httpServer = GrizzlyHttpServerFactory.createHttpServer(URI.create(settings.getRest().url()), rc);
            logger.info("FS crawler Rest service started on [{}]", settings.getRest().url());
        }
    }

    public static synchronized void close() {
        if (httpServer != null) {
            httpServer.shutdownNow();
            httpServer = null;
            logger.debug("FS crawler Rest service stopped");
        }
    }
}
