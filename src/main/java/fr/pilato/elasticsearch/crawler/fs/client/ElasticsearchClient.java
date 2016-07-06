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


import com.google.api.client.http.*;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.GenericJson;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Key;
import fr.pilato.elasticsearch.crawler.fs.meta.MetaParser;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.Elasticsearch.Node;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple Elasticsearch client over HTTP.
 * Only needed methods are exposed.
 */
public class ElasticsearchClient {

    static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    static final JsonFactory JSON_FACTORY = new JacksonFactory();
    private final HttpRequestFactory requestFactory;
    private final AtomicInteger counter;

    public static final int NODE_SKIP_BEFORE_RETRY = 10;

    private static final Logger logger = LogManager.getLogger(ElasticsearchClient.class);

    private final List<Node> nodes;

    private ElasticsearchClient() {
        nodes = new ArrayList<>();

        requestFactory =
                HTTP_TRANSPORT.createRequestFactory(new HttpRequestInitializer() {
                    @Override
                    public void initialize(HttpRequest request) {
                        request.setParser(new JsonObjectParser(JSON_FACTORY));
                    }
                });

        counter = new AtomicInteger();
    }

    public Node getNode(int node) {
        return nodes.get(node);
    }

    public Node findNextNode() throws IOException {
        Node node = null;

        // We have no node! WTF?
        if (nodes.size() == 0) {
            throw new IOException("no node has been declared. Check your code and call addNode(Node).");
        }

        // If we have only one node, just return it if active!
        if (nodes.size() == 1) {
            node = nodes.get(0);
        } else {
            AtomicInteger localTries = new AtomicInteger();
            // Get a random running node from the list
            int nextNode = nodes.size() > 1 ? new Random().nextInt(nodes.size()) : 0;
            while (node == null && localTries.incrementAndGet() < 10) {
                logger.trace("Trying node #{}", nextNode);
                node = nodes.get(nextNode);
                if (!node.active()) {
                    logger.trace("Nonactive node #{}", nextNode);
                    checkAndSetActive(node);

                    // If the node is still inactive, let's try the next one
                    if (!node.active()) {
                        node = null;
                        nextNode++;
                        if (nextNode >= nodes.size()) {
                            nextNode = 0;
                        }
                    }
                }
            }
        }

        if (node == null || !node.active()) {
            throw new IOException(buildErrorMessage());
        }

        return node;
    }

    private String buildErrorMessage() {
        StringBuilder sb = new StringBuilder("no active node found. Start an elasticsearch cluster first! ");
        sb.append("Expecting something running at ");
        for (Node node : nodes) {
            sb.append("[").append(node.getHost()).append(":").append(node.getPort()).append("]");
        }
        return sb.toString();
    }

    private ElasticsearchUrl buildUrl(Node node) throws IOException {
        if (node == null) {
            throw new IOException("no node seems to be available");
        }

        ElasticsearchUrl url = new ElasticsearchUrl();

        url.setScheme("http");
        url.setHost(node.getHost());
        url.setPort(node.getPort());
        return url;
    }

    public ElasticsearchClient addNode(Node node) {
        // We first check if the node is responding
        checkAndSetActive(node);
        nodes.add(node);
        return this;
    }

    public void createIndex(String index) throws IOException {
        createIndex(index, false);
    }

    public boolean isActive(Node node) {
        logger.debug("is node [{}] active?", node);
        boolean active = false;
        try {
            GenericUrl genericUrl = buildUrl(node);
            genericUrl.appendRawPath("/");
            HttpResponse httpResponse = requestFactory.buildGetRequest(genericUrl).execute();
            logger.trace("get / response: {}", httpResponse.parseAsString());
            active = true;
        } catch (IOException e) {
            logger.trace("error received", e);
        }
        logger.debug("is node active? -> [{}]", active);
        return active;
    }

    public boolean checkAndSetActive(Node node) {
        node.active(isActive(node));
        return node.active();
    }

    public void createIndex(Node node, String index, boolean ignoreErrors) throws IOException {
        logger.debug("create index [{}]", index);
        GenericUrl genericUrl = buildUrl(node);
        genericUrl.appendRawPath("/" + index);
        HttpRequest request = requestFactory.buildPutRequest(genericUrl, null);

        HttpResponse httpResponse;
        try {
            httpResponse = request.execute();
        } catch (HttpResponseException e) {
            if (e.getStatusCode() == 400 &&
                    (e.getContent().contains("index_already_exists_exception") || e.getContent().contains("IndexAlreadyExistsException") )) {
                if (!ignoreErrors) {
                    throw new RuntimeException("index already exists");
                }
                logger.trace("index already exists. Ignoring error...");
                return;
            }
            node.active(false);
            logger.debug("caught exception. disabling node [{}]", node);
            throw e;
        } catch (ConnectException e) {
            node.active(false);
            logger.debug("caught exception. disabling node [{}]", node);
            throw e;
        }
        logger.trace("create index response: {}", httpResponse.parseAsString());
    }

    public void createIndex(String index, boolean ignoreErrors) throws IOException {
        createIndex(findNextNode(), index, ignoreErrors);
    }

    public BulkResponse bulk(Node node, BulkRequest bulkRequest) throws Exception {
        StringBuffer sbf = new StringBuffer();
        for (SingleBulkRequest request : bulkRequest.getRequests()) {
            sbf.append("{");
            String header = MetaParser.mapper.writeValueAsString(request);
            if (request instanceof DeleteRequest) {
                sbf.append("\"delete\":").append(header).append("}\n");
            }
            if (request instanceof IndexRequest) {
                sbf.append("\"index\":").append(header).append("}\n");
                // Index Request: header line + body
                sbf.append(((IndexRequest) request).content().replaceAll("\n", "")).append("\n");
            }
        }

        logger.trace("going to send a bulk");
        logger.trace("{}", sbf);

        GenericUrl genericUrl = buildUrl(node);
        genericUrl.appendRawPath("/_bulk");
        HttpRequest request = requestFactory.buildPostRequest(genericUrl,
                ByteArrayContent.fromString("application/json", sbf.toString()));

        try {
            HttpResponse httpResponse = request.execute();
            BulkResponse response = httpResponse.parseAs(BulkResponse.class);
            logger.debug("bulk response: {}", response);
            return response;
        } catch (HttpResponseException|ConnectException e) {
            node.active(false);
            logger.debug("caught exception. disabling node [{}]", node);
            throw e;
        }
    }

    public BulkResponse bulk(BulkRequest bulkRequest) throws Exception {
        return bulk(findNextNode(), bulkRequest);
    }

    public void putMapping(Node node, String index, String type, String mapping) throws IOException {
        logger.debug("put mapping [{}/{}]", index, type);
        GenericUrl genericUrl = buildUrl(node);
        genericUrl.appendRawPath("/" + index);
        genericUrl.appendRawPath("/_mapping");
        genericUrl.appendRawPath("/" + type);
        HttpRequest request = requestFactory.buildPutRequest(genericUrl,
                ByteArrayContent.fromString("application/json", mapping));

        try {
            HttpResponse httpResponse = request.execute();
            logger.trace("put mapping response: {}", httpResponse.parseAsString());
        } catch (HttpResponseException|ConnectException e) {
            node.active(false);
            logger.debug("caught exception. disabling node [{}]", node);
            throw e;
        }
    }

    public void putMapping(String index, String type, String mapping) throws IOException {
        putMapping(findNextNode(), index, type, mapping);
    }

    public String findVersion() throws IOException {
        return findVersion(findNextNode());
    }

    public String findVersion(Node node) throws IOException {
        logger.debug("findVersion [{}]", node);
        String version;
        GenericUrl genericUrl = buildUrl(node);
        genericUrl.appendRawPath("/");
        HttpResponse httpResponse = requestFactory.buildGetRequest(genericUrl).execute();
        GenericJson json = httpResponse.parseAs(GenericJson.class);
        logger.trace("get server response: {}", json);
        Object oVersion = extractFromPath(json, "version").get("number");
        version = (String) oVersion;
        logger.debug("findVersion [{}] -> [{}]", node, version);
        return version;
    }

    public void refresh(String index) throws IOException {
        refresh(findNextNode(), index);
    }

    public void refresh(Node node, String index) throws IOException {
        logger.debug("refresh index [{}] with node [{}]", index, node);

        ElasticsearchUrl genericUrl = buildUrl(node);
        if (index != null) {
            genericUrl.appendRawPath("/" + index);
        }
        genericUrl.appendRawPath("/_refresh");
        HttpRequest request = requestFactory.buildPostRequest(genericUrl, null);
        try {
            HttpResponse httpResponse = request.execute();
            String response = httpResponse.parseAsString();
            logger.trace("refresh raw response: {}", response);
        } catch (HttpResponseException|ConnectException e) {
            node.active(false);
            logger.debug("caught exception. disabling node [{}]", node);
            throw e;
        }
    }

    public void waitForHealthyIndex(String index) throws IOException {
        waitForHealthyIndex(findNextNode(), index);
    }

    public void waitForHealthyIndex(Node node, String index) throws IOException {
        logger.debug("wait for yellow health on index [{}] with node [{}]", index, node);

        ElasticsearchUrl genericUrl = buildUrl(node);
        genericUrl.appendRawPath("/_cluster/health/" + index);
        genericUrl.waitForStatus = "yellow";
        HttpRequest request = requestFactory.buildGetRequest(genericUrl);
        try {
            HttpResponse httpResponse = request.execute();
            String response = httpResponse.parseAsString();
            logger.trace("health response: {}", response);
            // TODO if health is incorrect
        } catch (HttpResponseException|ConnectException e) {
            node.active(false);
            logger.debug("caught exception. disabling node [{}]", node);
            throw e;
        }
    }

    public void index(String index, String type, String id, String json) throws IOException {
        index(findNextNode(), index, type, id, json);
    }

    public void index(Node node, String index, String type, String id, String json) throws IOException {
        logger.debug("put document [{}/{}/{}]", index, type, id);
        GenericUrl genericUrl = buildUrl(node);
        genericUrl.appendRawPath("/" + index);
        genericUrl.appendRawPath("/" + type);
        genericUrl.appendRawPath("/" + id);
        HttpRequest request = requestFactory.buildPutRequest(genericUrl,
                ByteArrayContent.fromString("application/json", json));

        try {
            HttpResponse httpResponse = request.execute();
            logger.trace("put document response: {}", httpResponse.parseAsString());
        } catch (HttpResponseException|ConnectException e) {
            node.active(false);
            logger.debug("caught exception. disabling node [{}]", node);
            throw e;
        }

    }

    public boolean isExistingDocument(String index, String type, String id) throws IOException {
        return isExistingDocument(findNextNode(), index, type, id);
    }

    public boolean isExistingDocument(Node node, String index, String type, String id) throws IOException {
        logger.debug("is existing doc [{}]/[{}]/[{}]", index, type, id);

        GenericUrl genericUrl = buildUrl(node);
        genericUrl.appendRawPath("/" + index);
        genericUrl.appendRawPath("/" + type);
        genericUrl.appendRawPath("/" + id);
        HttpRequest request = requestFactory.buildGetRequest(genericUrl);
        try {
            HttpResponse httpResponse = request.execute();
            String json = httpResponse.parseAsString();
            logger.debug("get document response: {}", json);
            return true;
        } catch (HttpResponseException e) {
            if (e.getStatusCode() == 404) {
                logger.debug("doc [{}]/[{}]/[{}] does not exist", index, type, id);
                return false;
            }
            node.active(false);
            throw e;
        } catch (ConnectException e) {
            node.active(false);
            logger.debug("caught exception. disabling node [{}]", node);
            throw e;
        }
    }

    public void deleteIndex(String index) throws IOException {
        deleteIndex(findNextNode(), index);
    }

    public void deleteIndex(Node node, String index) throws IOException {
        logger.debug("delete index [{}] from node [{}]", index, node);
        GenericUrl genericUrl = buildUrl(node);
        genericUrl.appendRawPath("/" + index);
        HttpRequest request = requestFactory.buildDeleteRequest(genericUrl);
        try {
            HttpResponse httpResponse = request.execute();
            String json = httpResponse.parseAsString();
            logger.debug("delete index response: {}", json);
        } catch (HttpResponseException e) {
            if (e.getStatusCode() == 404) {
                logger.debug("index [{}] does not exist", index);
                return;
            }
            node.active(false);
            throw e;
        } catch (ConnectException e) {
            node.active(false);
            logger.debug("caught exception. disabling node [{}]", node);
            throw e;
        }
    }

    public static class ElasticsearchUrl extends GenericUrl {
        @Key
        public String q;
        @Key
        public String waitForStatus;
    }

    public SearchResponse search(String index, String type, String query) throws IOException {
        return search(index, type, query, null, null);
    }

    public SearchResponse search(String index, String type, String query, Integer size, String field) throws IOException {
        return search(index, type, SearchRequest.builder().setQuery(query).setSize(size).setFields(field).build());
    }

    public SearchResponse search(Node node, String index, String type, SearchRequest searchRequest) throws IOException {
        logger.debug("search [{}]/[{}], request [{}] with node [{}]", index, type, searchRequest, node);

        ElasticsearchUrl genericUrl = buildUrl(node);
        if (index != null) {
            genericUrl.appendRawPath("/" + index);
        }
        if (type != null) {
            genericUrl.appendRawPath("/" + type);
        }
        genericUrl.appendRawPath("/_search");
        if (searchRequest.getQuery() !=  null) {
            genericUrl.q = searchRequest.getQuery();
        }
        HttpRequest request = requestFactory.buildPostRequest(genericUrl, new JsonHttpContent(JSON_FACTORY, searchRequest));
        try {
            HttpResponse httpResponse = request.execute();
            SearchResponse response = httpResponse.parseAs(SearchResponse.class);
            logger.debug("search response: {}", response);

            return response;
        } catch (HttpResponseException|ConnectException e) {
            node.active(false);
            logger.debug("caught exception. disabling node [{}]", e, node);
            throw e;
        }
    }

    public SearchResponse search(String index, String type, SearchRequest searchRequest) throws IOException {
        return search(findNextNode(), index, type, searchRequest);
    }

    public boolean isExistingType(Node node, String index, String type) throws IOException {
        logger.debug("is existing type [{}]/[{}]", index, type);

        GenericUrl genericUrl = buildUrl(node);
        genericUrl.appendRawPath("/" + index);
        HttpRequest request = requestFactory.buildGetRequest(genericUrl);
        try {
            HttpResponse httpResponse = request.execute();
            GenericJson json = httpResponse.parseAs(GenericJson.class);
            logger.debug("get index metadata response: {}", json);

            Map<String, Object> mappings = extractFromPath(json, index, "mappings");
            return mappings.containsKey(type);
        } catch (HttpResponseException e) {
            if (e.getStatusCode() == 404) {
                logger.debug("type [{}]/[{}] does not exist", index, type);
                return false;
            }
            node.active(false);
            throw e;
        } catch (ConnectException e) {
            node.active(false);
            logger.debug("caught exception. disabling node [{}]", node);
            throw e;
        }
    }

    public boolean isExistingType(String index, String type) throws IOException {
        return isExistingType(findNextNode(), index, type);
    }

    public boolean isExistingIndex(Node node, String index) throws IOException {
        logger.debug("is existing index [{}]", index);

        GenericUrl genericUrl = buildUrl(node);
        genericUrl.appendRawPath("/" + index);
        HttpRequest request = requestFactory.buildGetRequest(genericUrl);
        try {
            HttpResponse httpResponse = request.execute();
            String json = httpResponse.parseAsString();
            logger.debug("get index metadata response: {}", json);
            return true;
        } catch (HttpResponseException e) {
            if (e.getStatusCode() == 404) {
                logger.debug("index [{}] does not exist", index);
                return false;
            }
            node.active(false);
            throw e;
        } catch (ConnectException e) {
            node.active(false);
            logger.debug("caught exception. disabling node [{}]", node);
            throw e;
        }
    }

    public boolean isExistingIndex(String index) throws IOException {
        return isExistingIndex(findNextNode(), index);
    }

    public static class Builder {

        public ElasticsearchClient build() {
            return new ElasticsearchClient();
        }

    }

    public static Builder builder() {
        return new Builder();
    }

    private Map<String, Object> extractFromPath(Map<String, Object> json, String... path) {
        Map<String, Object> currentObject = json;
        for (String fieldName : path) {
            Object jObject = currentObject.get(fieldName);
            if (jObject == null) {
                throw new RuntimeException("incorrect Json. Was expecting field " + fieldName);
            }
            if (!(jObject instanceof Map)) {
                throw new RuntimeException("incorrect datatype in json. Expected Map and got " + jObject.getClass().getName());
            }
            currentObject = (Map<String, Object>) jObject;
        }
        return currentObject;
    }

    /**
     * Create a mapping if it does not exist already
     * @param client elasticsearch client
     * @param index index name
     * @param type type name
     * @param mapping Elasticsearch mapping
     * @throws Exception in case of error
     */
    public static void pushMapping(ElasticsearchClient client, String index, String type, String mapping) throws Exception {
        boolean mappingExist = client.isExistingType(index, type);
        if (!mappingExist) {
            logger.debug("Mapping [{}]/[{}] doesn't exist. Creating it.", index, type);
            logger.trace("Mapping for [{}]/[{}]: [{}]", index, type, mapping);
            client.putMapping(index, type, mapping);
        } else {
            logger.debug("Mapping [" + index + "]/[" + type + "] already exists.");
        }
    }
}
