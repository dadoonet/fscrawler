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

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class WPSearchClient implements Closeable {

    private static final Logger logger = LogManager.getLogger(WPSearchClient.class);

    private final static String CLIENT_NAME = "elastic-workplace-search-java";
    private final static String CLIENT_VERSION = "7.8.1";
    private final static String DEFAULT_ENDPOINT = "http://localhost:3002/api/ws/v1/";

    private Client client;
    private String userAgent;
    private String endpoint = DEFAULT_ENDPOINT;
    private final String accessToken;
    private final String key;

    /**
     * Create a client
     * @param accessToken   Access Token to use (get it from the Custom API interface)
     * @param key           Key to use (get it from the Custom API interface)
     */
    public WPSearchClient(String accessToken, String key) {
        this.accessToken = accessToken;
        this.key = key;
    }

    public WPSearchClient withUserAgent(String userAgent) {
        this.userAgent = userAgent;
        return this;
    }

    public WPSearchClient withEndpoint(String endpoint) {
        this.endpoint = endpoint;
        return this;
    }

    /**
     * Start the client
     */
    public void start() {
        logger.debug("Starting the WPSearchClient");
        client = ClientBuilder.newClient();
    }

    /**
     * Index multiple documents
     * @param sourceKey Source key (get it from the Custom API interface)
     * @param documents Documents to index
     */
    public void indexDocuments(String sourceKey, List<Map<String, Object>> documents) {
        logger.fatal("{}", documents);
        String response = post("sources/" + key + "/documents/bulk_create.json", documents, String.class);
        logger.fatal("{}", response);
    }

    /**
     * Index one single document
     * @param sourceKey Source key (get it from the Custom API interface)
     * @param document Document to index
     */
    public void indexDocument(String sourceKey, Map<String, Object> document) {

    }

    /**
     * Delete existing documents
     * @param sourceKey Source key (get it from the Custom API interface)
     * @param ids       List of document ids to delete
     */
    public void destroyDocuments(String sourceKey, List<String> ids) {

    }

    /**
     * Delete one document
     * @param sourceKey Source key (get it from the Custom API interface)
     * @param id        Document id to delete
     */
    public void destroyDocument(String sourceKey, String id) {

    }

    @Override
    public void close() throws IOException {
        logger.debug("Closing the WPSearchClient");
        if (client != null) {
            client.close();
        }
    }

    public <T> T post(String path, Object data, Class<T> clazz) {
        WebTarget target = client
                .target(endpoint)
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

        return builder.post(Entity.entity(data, MediaType.APPLICATION_JSON), clazz);
    }
}
