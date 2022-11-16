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

import com.jayway.jsonpath.DocumentContext;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.framework.Version;
import fr.pilato.elasticsearch.crawler.fs.framework.bulk.FsCrawlerBulkProcessor;
import fr.pilato.elasticsearch.crawler.fs.framework.bulk.FsCrawlerRetryBulkProcessorListener;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.glassfish.jersey.logging.LoggingFeature;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.*;
import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.parseJsonAsDocumentContext;

/**
 * Workplace Search Java client
 * We make it as dumb as possible as it should be replaced in the future by
 * an official implementation.
 */
public class WPSearchClient implements Closeable {

    private static final Logger logger = LogManager.getLogger(WPSearchClient.class);

    private static final String USER_AGENT = "FSCrawler-Rest-Client-" + Version.getVersion();

    final static String DEFAULT_WS_ENDPOINT = "/api/ws/v1/";
    final static String DEFAULT_ENT_ENDPOINT = "/api/ent/v1/";
    private final static String DEFAULT_HOST = "http://127.0.0.1:3002";

    private Client client;
    private String host = DEFAULT_HOST;
    private String username;
    private String password;
    private int bulkSize;
    private TimeValue flushInterval;

    private FsCrawlerBulkProcessor<WPSearchOperation, WPSearchBulkRequest, WPSearchBulkResponse> bulkProcessor;
    private boolean started = false;
    private String sourceId;
    private final Path rootDir;
    private final Path jobMappingDir;
    private String version;

    /**
     * Create a client
     */
    public WPSearchClient(Path rootDir, Path jobMappingDir) {
        this.rootDir = rootDir;
        this.jobMappingDir = jobMappingDir;
    }

    /**
     * If needed we can allow passing a specific username. Defaults to "elastic".
     * @param username Username
     * @param defaultValue default value to use for username. Defaults to Elasticsearch username.
     * @return the current instance
     */
    public WPSearchClient withUsername(String username, String defaultValue) {
        if (username != null) {
            this.username = username;
        } else {
            this.username = defaultValue;
        }
        return this;
    }

    /**
     * If needed we can allow passing a specific password. Defaults to "changeme".
     * @param password Password
     * @param defaultValue default value to use for password. Defaults to Elasticsearch password.
     * @return the current instance
     */
    public WPSearchClient withPassword(String password, String defaultValue) {
        if (password != null) {
            this.password = password;
        } else {
            this.password = defaultValue;
        }
        return this;
    }

    /**
     * Define a specific host. Defaults to "http://localhost:3002"
     * @param host  If we need to change the default host
     * @return the current instance
     */
    @SuppressWarnings("JavadocLinkAsPlainText")
    public WPSearchClient withHost(String host) {
        this.host = host;
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
        logger.debug("Starting the WPSearchClient: {}", this.toString());

        // Create the client
        ClientConfig config = new ClientConfig();
        // We need to suppress this, so we can do DELETE with body
        config.property(ClientProperties.SUPPRESS_HTTP_COMPLIANCE_VALIDATION, true);
        HttpAuthenticationFeature feature = HttpAuthenticationFeature.basic(username, password);
        client = ClientBuilder.newClient(config);
        if (logger.isTraceEnabled()) {
            client
//                    .property(LoggingFeature.LOGGING_FEATURE_LOGGER_NAME_CLIENT, WPSearchClient.class.getName())
                    .property(LoggingFeature.LOGGING_FEATURE_LOGGER_LEVEL_CLIENT, Level.FINEST.getName())
                    .property(LoggingFeature.LOGGING_FEATURE_VERBOSITY_CLIENT, LoggingFeature.Verbosity.PAYLOAD_ANY)
                    .property(LoggingFeature.LOGGING_FEATURE_MAX_ENTITY_SIZE_CLIENT, 8000);
        }
        client.register(feature);

        // Create the BulkProcessor instance
        bulkProcessor = new FsCrawlerBulkProcessor.Builder<>(
                new WPSearchEngine(this),
                new FsCrawlerRetryBulkProcessorListener<>(),
                WPSearchBulkRequest::new)
                .setBulkActions(bulkSize)
                .setFlushInterval(flushInterval)
        .build();

        // We check that the service is available
        try {
            version = getVersion();
            logger.info("Wokplace Search Client connected to a service running version {}", version);
        } catch (Exception e) {
            logger.warn("failed to create workplace search client on {}, disabling crawler...", host);
            throw e;
        }

        started = true;
    }

    /**
     * Configure the custom source for this client
     * @param id        custom source id
     * @param name      custom source name
     * @throws IOException in case of communication error
     */
    public void configureCustomSource(final String id, final String name) throws IOException {
        checkStarted();
        // Let's check that the source exists
        if (id != null) {
            // Check that the custom source exists knowing the id
            String source = getCustomSourceById(id);
            if (source == null) {
                logger.debug("Can not find the custom source with the provided id [{}]", id);
                throw new RuntimeException("Can not find custom source with the provided id: " + id);
            }
            sourceId = id;
        } else {
            // Check that the custom source exists knowing the name
            List<String> customSourceIds = getCustomSourcesByName(name);
            if (customSourceIds.isEmpty()) {
                // Let's create a new source
                sourceId = createCustomSource(name);
                logger.debug("Custom source [{}] created with id [{}].", name, sourceId);
            } else {
                sourceId = customSourceIds.get(0);
            }
        }

        // At the end, the sourceId can not be null
        assert sourceId != null;
    }

    /**
     * Index one single document
     * @param document Document to index
     */
    public void indexDocument(Map<String, Object> document) {
        checkStarted();
        logger.debug("Adding document {} to custom source {}", document.get("id"), sourceId);
        logger.trace("Adding document {}", document);
        bulkProcessor.add(new WPSearchOperation(sourceId, document));
    }


    /**
     * Get one single document
     * @param id the document id
     * @return the Json document as an object readable with JsonPath or null if not found
     */
    public String getDocument(String id) {
        checkStarted();
        logger.debug("Getting document {} to custom source {}", id, sourceId);
        try {
            return get(DEFAULT_WS_ENDPOINT, "sources/" + sourceId + "/documents/" + id, String.class);
        } catch (NotFoundException e) {
            return null;
        }
    }

    /**
     * Delete existing documents
     * @param sourceId  The custom source Id
     * @param ids       List of document ids to delete
     * @return true if removed, false if not found or in case of error
     */
    public boolean destroyDocuments(String sourceId, List<String> ids) {
        checkStarted();
        logger.debug("Removing from source {} documents {}", sourceId, ids);
        try {
            String response = post(DEFAULT_WS_ENDPOINT, "sources/" + sourceId + "/documents/bulk_destroy", ids, String.class);
            logger.debug("Removing documents response: {}", response);
            // TODO parse the response to check for errors
            return true;
        } catch (NotFoundException e) {
            logger.warn("We did not find the resources: {} in source {}", ids, sourceId);
            return false;
        }
    }

    /**
     * Delete one document
     * @param id Document id to delete
     * @return true if removed, false if not found or in case of error
     */
    public boolean destroyDocument(String id) {
        return destroyDocuments(sourceId, Collections.singletonList(id));
    }

    /**
     * This method needs to have a trial or platinum license.
     * It's only used in integration tests.
     * @param query Text we are searching for
     * @return the json response
     */
    public String search(String query, Map<String, Object> filters) {
        checkStarted();
        logger.trace("Searching for {} with filters {}", query, filters);
        Map<String, Object> request = new HashMap<>();

        if (query != null) {
            request.put("query", query);
        }

        if (filters != null) {
            request.put("filters", filters);
        }

        String json = post(DEFAULT_WS_ENDPOINT, "search", request, String.class);

        logger.debug("Search response: {}", json);
        return json;
    }

    /**
     * Get a Custom Source knowing its id
     * @param id the source id
     * @return the source as a json document.
     */
    public String getCustomSourceById(String id) {
        return get(DEFAULT_WS_ENDPOINT, "sources/" + id, String.class);
    }

    /**
     * Get a list of Custom Sources knowing its name
     * @param name the source name or a string pattern
     * @return the list of the source ids.
     */
    public List<String> getCustomSourcesByName(String name) {
        int currentPage = 0;
        int totalPages = Integer.MAX_VALUE;
        List<String> ids = new ArrayList<>();

        while(currentPage < totalPages) {
            currentPage++;
            String json = listAllCustomSources(currentPage);

            // We parse the json
            DocumentContext document = parseJsonAsDocumentContext(json);
            totalPages = document.read("$.meta.page.total_pages");

            // We compare every source
            List<Map<String, Object>> sources = document.read("$.results[*]");

            for (Map<String, Object> source : sources) {
                if (FilenameUtils.wildcardMatch((String) source.get("name"), name)) {
                    logger.trace("Source [{}] matched [{}] pattern", source.get("name"), name);
                    ids.add((String) source.get("id"));
                }
            }
        }

        logger.debug("Sources found for name [{}]: {}", name, ids);

        if (ids.size() > 1) {
            logger.warn("We found [{}] custom sources with the same name [{}]: {}. " +
                    "We will pick only the first one [{}]", ids.size(), name, ids, ids.get(0));
        }

        return ids;
    }

    // TODO add pagination
    public String listAllCustomSources(int page) {
        return get(DEFAULT_WS_ENDPOINT, "sources", String.class);
    }

    /**
     * Create a custom source by using the built-in template
     * @param sourceName the source name to build
     * @return the id of the source
     * @throws IOException in case something goes wrong
     */
    public String createCustomSource(String sourceName) throws IOException {
        checkStarted();

        // If needed, we create the new settings for this files index
        int worplaceSearchVersion = FsCrawlerUtil.extractMajorVersion(version);
        String json = readJsonFile(jobMappingDir, rootDir, worplaceSearchVersion, INDEX_WORKPLACE_SEARCH_SETTINGS_FILE);

        // We need to replace the placeholder values
        json = json.replaceAll("SOURCE_NAME", sourceName);

        String response = post(DEFAULT_WS_ENDPOINT, "sources/", json, String.class);
        logger.trace("Source [{}] created. Response: {}", sourceName, response);

        // We parse the json
        DocumentContext document = parseJsonAsDocumentContext(response);
        String id = document.read("$.id");

        logger.debug("Source [{}/{}] created.", id, sourceName);

        return id;
    }

    /**
     * Remove a custom source
     * @param id id of the custom source
     */
    public void removeCustomSource(String id) {
        checkStarted();

        // Delete the source
        String response = delete(DEFAULT_WS_ENDPOINT, "sources/" + id, null, String.class);
        logger.debug("removeCustomSource({}): {}", id, response);
    }

    /**
     * Get the version number of the server if running. Fail otherwise.
     * @return  the version number
     */
    public String getVersion() {
        logger.debug("get version");
        String home = get(DEFAULT_ENT_ENDPOINT, "internal/version", String.class);
        DocumentContext context = parseJsonAsDocumentContext(home);
        return context.read("$.number");
    }

    public void flush() {
        bulkProcessor.flush();
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

    <T> T get(String urlForApi, String path, Class<T> clazz) {
        logger.debug("Calling GET {}{}", urlForApi, path);
        try (Response response = prepareHttpCall(urlForApi, path).build("GET").invoke()) {
            logger.trace("Response headers: {}", response.getHeaders());
            T entity = response.readEntity(clazz);
            logger.trace("Response entity: {}", entity);
            logger.trace("Status: {}", response.getStatusInfo());
            if (response.getStatusInfo().getStatusCode() == Response.Status.NOT_FOUND.getStatusCode()) {
                throw new NotFoundException();
            }

            return entity;
        } catch (NotFoundException e) {
            logger.debug("Calling GET {}{} gives {}", urlForApi, path, e.getMessage());
            throw e;
        } catch (WebApplicationException e) {
            logger.warn("Error while running GET {}{}: {}", urlForApi, path, e.getResponse().readEntity(String.class));
            throw e;
        }
    }

    <T> T post(String urlForApi, String path, Object data, Class<T> clazz) {
        logger.trace("Calling POST {}{}", urlForApi, path);
        try {
            return prepareHttpCall(urlForApi, path).post(Entity.json(data), clazz);
        } catch (WebApplicationException e) {
            logger.warn("Error while running POST {}{}: {}", urlForApi, path, e.getResponse().readEntity(String.class));
            throw e;
        }
    }

    private <T> T delete(String urlForApi, String path, Object data, Class<T> clazz) {
        logger.trace("Calling DELETE {}{}", urlForApi, path);
        try {
            return prepareHttpCall(urlForApi, path).method("DELETE", Entity.json(data), clazz);
        } catch (WebApplicationException e) {
            logger.warn("Error while running DELETE {}{}: {}", urlForApi, path, e.getResponse().readEntity(String.class));
            throw e;
        }
    }

    private Invocation.Builder prepareHttpCall(String urlForApi, String path) {
        WebTarget target = client
                .target(host)
                .path(urlForApi)
                .path(path);

        Invocation.Builder builder = target
                .request(MediaType.APPLICATION_JSON)
                .header("Content-Type", "application/json");

        builder.header("User-Agent", USER_AGENT);

        return builder;
    }

    @Override
    public String toString() {
        return "WPSearchClient{" +
                ", host='" + host + '\'' +
                '}';
    }
}
