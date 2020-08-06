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

package fr.pilato.elasticsearch.crawler.fs.thirdparty.wpsearch;

import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.framework.bulk.FsCrawlerBulkProcessor;
import fr.pilato.elasticsearch.crawler.fs.framework.bulk.FsCrawlerRetryBulkProcessorListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import java.io.Closeable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Workplace Search Java client
 * We make it as dumb as possible as it should be replaced in the future by
 * an official implementation.
 */
public class WPSearchClient implements Closeable {

    private static final Logger logger = LogManager.getLogger(WPSearchClient.class);

    private final static String CLIENT_NAME = "elastic-workplace-search-java";
    private final static String CLIENT_VERSION = "7.8.1";
    private final static String DEFAULT_ENDPOINT = "/api/ws/v1/";
    private final static String DEFAULT_HOST = "http://127.0.0.1:3002";

    private Client client;
    private String userAgent;
    private String endpoint = DEFAULT_ENDPOINT;
    private String host = DEFAULT_HOST;
    private final String accessToken;
    final String urlForBulkCreate;
    final String urlForBulkDestroy;
    String urlForApi;
    final String urlForSearch;

    private FsCrawlerBulkProcessor<WPSearchOperation, WPSearchBulkRequest, WPSearchBulkResponse> bulkProcessor;

    /**
     * Create a client
     * @param accessToken   Access Token to use (get it from the Custom API interface)
     * @param key           Key to use (get it from the Custom API interface)
     */
    public WPSearchClient(String accessToken, String key) {
        this.accessToken = accessToken;
        this.urlForBulkCreate = "sources/" + key + "/documents/bulk_create";
        this.urlForBulkDestroy = "sources/" + key + "/documents/bulk_destroy";
        this.urlForSearch = "search";
        this.urlForApi = host + endpoint;
    }

    /**
     * If needed we can allow passing a specific user-agent
     * @param userAgent User Agent
     * @return the current instance
     */
    public WPSearchClient withUserAgent(String userAgent) {
        this.userAgent = userAgent;
        return this;
    }

    /**
     * Define a specific endpoint. Defaults to "/api/ws/v1"
     * @param endpoint  If we need to change the default endpoint
     * @return the current instance
     */
    public WPSearchClient withEndpoint(String endpoint) {
        this.endpoint = endpoint;
        this.urlForApi = host + endpoint;
        return this;
    }

    /**
     * Define a specific host. Defaults to "http://localhost:3002"
     * @param host  If we need to change the default host
     * @return the current instance
     */
    public WPSearchClient withHost(String host) {
        this.host = host;
        this.urlForApi = host + endpoint;
        return this;
    }

    /**
     * Start the client
     */
    public void start() {
        logger.debug("Starting the WPSearchClient");
        ClientConfig config = new ClientConfig();
        // We need to suppress this so we can do DELETE with body
        config.property(ClientProperties.SUPPRESS_HTTP_COMPLIANCE_VALIDATION, true);
        client = ClientBuilder.newClient(config);

        // Create the BulkProcessor instance
        bulkProcessor = new FsCrawlerBulkProcessor.Builder<>(
                new WPSearchEngine(this),
                new FsCrawlerRetryBulkProcessorListener<>(),
                WPSearchBulkRequest::new)
                .setBulkActions(100)
                .setFlushInterval(TimeValue.timeValueSeconds(5))
        .build();
    }

    /**
     * Index one single document
     * @param document Document to index
     */
    public void indexDocument(Map<String, Object> document) {
        logger.debug("Adding document {}", document);
        bulkProcessor.add(new WPSearchOperation(document));
    }

    /**
     * Delete existing documents
     * @param ids List of document ids to delete
     */
    public void destroyDocuments(List<String> ids) {
        logger.debug("Removing documents {}", ids);
        try {
            String response = delete(urlForBulkDestroy, ids, String.class);
            logger.debug("Removing documents response: {}", response);
            // TODO parse the response to check for errors
        } catch (NotFoundException e) {
            logger.warn("We did not find the resources: {}", ids);
        }
    }

    /**
     * Delete one document
     * @param id Document id to delete
     */
    public void destroyDocument(String id) {
        destroyDocuments(Collections.singletonList(id));
    }

    public String search(String query) {
        logger.debug("Searching for {}", query);
        Map<String, Object> request = new HashMap<>();
        request.put("query", query);

        // TODO Fix this. It needs a OAuth access apparently and we can't just use the existing credentials
        // String response = post(urlForSearch, request, String.class);
        String response = "Needs to be implemented...";
        logger.warn("Searching response: {}", response);
        return response;
    }

    @Override
    public void close() {
        logger.debug("Closing the WPSearchClient");
        if (client != null) {
            client.close();
        }
    }

    <T> T post(String path, Object data, Class<T> clazz) {
        return prepareHttpCall(path).post(Entity.entity(data, MediaType.APPLICATION_JSON), clazz);
    }

    private <T> T delete(String path, Object data, Class<T> clazz) {
        // TODO This does not remove the entity. Something to fix in the future...
        return prepareHttpCall(path).method("DELETE", Entity.entity(data, MediaType.APPLICATION_JSON), clazz);
    }

    private Invocation.Builder prepareHttpCall(String path) {
        WebTarget target = client
                .target(urlForApi)
                .path(path);

        Invocation.Builder builder = target
                .request(MediaType.APPLICATION_JSON)
                .header("Content-Type", "application/json")
                .header("X-Swiftype-Client", CLIENT_NAME)
                .header("X-Swiftype-Client-Version", CLIENT_VERSION)
                .header("Authorization", "Bearer " + accessToken);

        if (userAgent != null) {
            builder.header("User-Agent", userAgent);
        }

        return builder;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("WPSearchClient{");
        sb.append("endpoint='").append(endpoint).append('\'');
        sb.append(", host='").append(host).append('\'');
        sb.append(", urlForBulkCreate='").append(urlForBulkCreate).append('\'');
        sb.append(", urlForBulkDestroy='").append(urlForBulkDestroy).append('\'');
        sb.append(", urlForApi='").append(urlForApi).append('\'');
        sb.append(", urlForSearch='").append(urlForSearch).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
