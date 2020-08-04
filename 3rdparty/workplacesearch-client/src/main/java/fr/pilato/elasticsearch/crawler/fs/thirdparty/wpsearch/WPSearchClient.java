/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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
 * TODO add a bulk mechanism
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
    private final String key;

    private String urlForBulkCreate;
    private String urlForBulkDestroy;
    private String urlForApi;
    private String urlForSearch;

    /**
     * Create a client
     * @param accessToken   Access Token to use (get it from the Custom API interface)
     * @param key           Key to use (get it from the Custom API interface)
     */
    public WPSearchClient(String accessToken, String key) {
        this.accessToken = accessToken;
        this.key = key;
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
        return this;
    }

    /**
     * Define a specific host. Defaults to "http://localhost:3002"
     * @param host  If we need to change the default host
     * @return the current instance
     */
    public WPSearchClient withHost(String host) {
        this.host = host;
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
        urlForBulkCreate = "sources/" + key + "/documents/bulk_create";
        urlForBulkDestroy = "sources/" + key + "/documents/bulk_destroy";
        urlForSearch = "search";
        urlForApi = host + endpoint;
    }

    /**
     * Index multiple documents
     * @param documents Documents to index
     */
    public void indexDocuments(List<Map<String, Object>> documents) {
        logger.debug("Adding documents {}", documents);
        String response = post(urlForBulkCreate, documents, String.class);
        logger.debug("Adding documents response: {}", response);
    }

    /**
     * Index one single document
     * @param document Document to index
     */
    public void indexDocument(Map<String, Object> document) {
        indexDocuments(Collections.singletonList(document));
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
            logger.warn("We did not find the ressources: {}", ids);
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

    private <T> T post(String path, Object data, Class<T> clazz) {
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
}
