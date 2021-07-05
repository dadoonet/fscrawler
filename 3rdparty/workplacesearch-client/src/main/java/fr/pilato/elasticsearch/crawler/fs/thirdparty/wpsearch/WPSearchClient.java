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

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.framework.bulk.FsCrawlerBulkProcessor;
import fr.pilato.elasticsearch.crawler.fs.framework.bulk.FsCrawlerRetryBulkProcessorListener;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.readJsonFile;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.readPropertiesFromClassLoader;

/**
 * Workplace Search Java client
 * We make it as dumb as possible as it should be replaced in the future by
 * an official implementation.
 */
public class WPSearchClient implements Closeable {

    private static final Logger logger = LogManager.getLogger(WPSearchClient.class);

    private final static String WORKPLACESEARCH_PROPERTIES = "workplacesearch.properties";
    private static final Properties properties;
    private final static String CLIENT_VERSION;

    static {
        properties = readPropertiesFromClassLoader(WORKPLACESEARCH_PROPERTIES);
        CLIENT_VERSION = properties.getProperty("workplacesearch.version");
    }

    private final static String DEFAULT_ENDPOINT = "/api/ws/v1/";
    private final static String DEFAULT_HOST = "http://127.0.0.1:3002";
    private static final String DEFAULT_USERNAME = "elastic";
    private static final String DEFAULT_PASSWORD = "changeme";


    private Client client;
    private String userAgent;
    private String endpoint = DEFAULT_ENDPOINT;
    private String host = DEFAULT_HOST;
    private String username = DEFAULT_USERNAME;
    private String password = DEFAULT_PASSWORD;
    String urlForBulkCreate;
    private String urlForBulkDestroy;
    private String urlForApi;
    private final String urlForSearch = "search";
    private int bulkSize;
    private TimeValue flushInterval;

    private FsCrawlerBulkProcessor<WPSearchOperation, WPSearchBulkRequest, WPSearchBulkResponse> bulkProcessor;
    private boolean started = false;
    private String sourceId;
    private String sourceName;
    private final Path rootDir;
    private final Path jobMappingDir;

    /**
     * Create a client
     */
    public WPSearchClient(Path rootDir, Path jobMappingDir) {
        this.rootDir = rootDir;
        this.jobMappingDir = jobMappingDir;
        this.urlForApi = host + endpoint;
    }

    /**
     * The source sourceName.
     * @param sourceName source sourceName to use if the source id is not provided
     * @return the current instance
     */
    public WPSearchClient withSourceName(String sourceName) {
        this.sourceName = sourceName;
        return this;
    }

    /**
     * Provide the sourceId to be used for every operation.
     * @param sourceId Key to use (get it from the Custom API interface or from {@link #createCustomSource(String)})
     * @return the current instance
     */
    public WPSearchClient withSourceId(String sourceId) {
        this.sourceId = sourceId;
        this.urlForBulkCreate = "sources/" + sourceId + "/documents/bulk_create";
        this.urlForBulkDestroy = "sources/" + sourceId + "/documents/bulk_destroy";
        return this;
    }

    /**
     * If needed we can allow passing a specific username. Defaults to "enterprise_search".
     * @param username Username
     * @return the current instance
     */
    public WPSearchClient withUsername(String username) {
        this.username = username;
        return this;
    }

    /**
     * If needed we can allow passing a specific password. Defaults to "changeme".
     * @param password Password
     * @return the current instance
     */
    public WPSearchClient withPassword(String password) {
        this.password = password;
        return this;
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
     * Defines the bulk size, which is the number of expected operations added to the bulk
     * processor before actually sending the bulk request over the network.
     * @param bulkSize  Number of documents
     * @return the current instance
     * @see #withFlushInterval(TimeValue) for setting the flush interval
     */
    public WPSearchClient withBulkSize(int bulkSize) {
        this.bulkSize = bulkSize;
        return this;
    }

    /**
     * Interval to use to flush the existing operations within the bulk processor whatever
     * the number of documents. Which means that event you did not reach {@link #withBulkSize(int)}
     * number of elements, the content will be flushed after the flushInterval period.
     * @param flushInterval A duration.
     * @return the current instance
     * @see #withBulkSize(int) to set the maximum number of items in a bulk
     */
    public WPSearchClient withFlushInterval(TimeValue flushInterval) {
        this.flushInterval = flushInterval;
        return this;
    }

    /**
     * Start the client
     */
    public void start() {
        logger.debug("Starting the WPSearchClient");

        // Create the client
        ClientConfig config = new ClientConfig();
        // We need to suppress this so we can do DELETE with body
        config.property(ClientProperties.SUPPRESS_HTTP_COMPLIANCE_VALIDATION, true);
        HttpAuthenticationFeature feature = HttpAuthenticationFeature.basic(username, password);
        client = ClientBuilder.newClient(config);
        client.register(feature);

        // Create the BulkProcessor instance
        bulkProcessor = new FsCrawlerBulkProcessor.Builder<>(
                new WPSearchEngine(this),
                new FsCrawlerRetryBulkProcessorListener<>(),
                WPSearchBulkRequest::new)
                .setBulkActions(bulkSize)
                .setFlushInterval(flushInterval)
        .build();

        started = true;
    }

    /**
     * Index one single document
     * @param document Document to index
     */
    public void indexDocument(Map<String, Object> document) {
        checkStarted();
        logger.debug("Adding document {}", document);
        bulkProcessor.add(new WPSearchOperation(document));
    }

    /**
     * Delete existing documents
     * @param ids List of document ids to delete
     */
    public void destroyDocuments(List<String> ids) {
        checkStarted();
        logger.debug("Removing documents {}", ids);
        try {
            String response = post(urlForBulkDestroy, ids, String.class);
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

    /**
     * This method needs to have a trial or platinum license.
     * It's only used in integration tests.
     * @param query Text we are searching for
     * @return the json response
     */
    public String search(String query) {
        checkStarted();
        logger.debug("Searching for {}", query);
        Map<String, Object> request = new HashMap<>();
        request.put("query", query);

        String json = post(urlForSearch, request, String.class);

        logger.debug("Search response: {}", json);
        return json;
    }

    public String getCustomSource() throws Exception {
        logger.debug("get the custom source. We know [{}] as the id and [{}] as the name", sourceId, sourceName);

        String sourceId = this.sourceId;

        if (sourceId == null) {
            // We check if a source name already exists with the job name
            sourceId = findCustomSourceByName(this.sourceName);
            if (sourceId == null) {
                // We need to create the custom source
                String json = createCustomSource(sourceName);
                // We parse the json
                Object document = Configuration.defaultConfiguration().jsonProvider().parse(json);
                sourceId = JsonPath.read(document, "$.id");
            }
        }

        // We try to check if the custom source id exists
        String source = getContentSource(sourceId);
        if (source == null || source.isEmpty()) {
            throw new Exception("We can not read the source " + sourceId + " or create a source " +
                    "with the name " + sourceName +". Check your settings or the custom source list " +
                    "in Workplace Search admin UI.");
        }

        // Overwrite the urls
        withSourceId(sourceId);

        logger.debug("get the custom source. We got [{}] as the id for the source named [{}]", sourceId, sourceName);

        return sourceId;
    }

    private String getContentSource(String sourceId) {
        return get("sources/" + sourceId, String.class);
    }

    private String findCustomSourceByName(String sourceName) {
        int currentPage = 0;
        int totalPages = Integer.MAX_VALUE;
        String id = null;

        while(id == null && currentPage < totalPages) {
            currentPage++;
            String json = listAllContentSources(currentPage);

            // We parse the json
            Object document = Configuration.defaultConfiguration().jsonProvider().parse(json);
            totalPages = JsonPath.read(document, "$.meta.page.total_pages");

            // We compare every source
            List<Map<String, Object>> sources = JsonPath.read(document, "$.results[*]");

            for (Map<String, Object> source : sources) {
                if (sourceName.equals(source.get("name"))) {
                    id = (String) source.get("id");
                    break;
                }
            }
        }

        logger.debug("Source found for name [{}]: [{}]", sourceName, id);
        return id;
    }

    // TODO add pagination
    private String listAllContentSources(int page) {
        return get("sources", String.class);
    }

    private String createCustomSource(String sourceName) throws Exception {
        checkStarted();

        // If needed, we create the new settings for this files index
        String worplaceSearchVersion = FsCrawlerUtil.extractMajorVersion(CLIENT_VERSION);
        String json = readJsonFile(jobMappingDir, rootDir, worplaceSearchVersion, "_wpsearch_settings");

        // We need to replace the place holder values
        json = json.replaceAll("SOURCE_NAME", sourceName);

        String response = post("sources/", json, String.class);

        logger.debug("Source [{}] created.", sourceName);
        logger.trace("Source [{}] created. {}", sourceName, response);

        return response;
    }

    public void removeCustomSource(String id) {
        checkStarted();

        // Delete the source
        String response = delete("sources/" + id, null, String.class);
        logger.debug("removeCustomSource({}): {}", id, response);
    }


    @Override
    public void close() {
        logger.debug("Closing the WPSearchClient");
        if (bulkProcessor != null) {
            try {
                bulkProcessor.close();
            } catch (IOException e) {
                logger.warn("Error caught while trying to close the Bulk Processor for Workplace Search", e);
            }
        }
        if (client != null) {
            client.close();
        }
        started = false;
    }

    private void checkStarted() {
        if (!started) {
            throw new RuntimeException("Bug in code. You must call start() before calling any WPSearch API");
        }
    }

    <T> T get(String path, Class<T> clazz) {
        return prepareHttpCall(path).get(clazz);
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
                .header("Content-Type", "application/json");

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
