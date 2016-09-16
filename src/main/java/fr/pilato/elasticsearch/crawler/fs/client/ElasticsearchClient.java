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


import fr.pilato.elasticsearch.crawler.fs.meta.settings.Elasticsearch.Node;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple Elasticsearch client over HTTP.
 * Only needed methods are exposed.
 */
public class ElasticsearchClient {

    private static final Logger logger = LogManager.getLogger(ElasticsearchClient.class);

    private final RestClient client;
    private String FIELDS = null;

    private ElasticsearchClient(List<Node> nodes, String username, String password) {
        List<HttpHost> hosts = new ArrayList<>(nodes.size());
        for (Node node : nodes) {
            hosts.add(new HttpHost(node.getHost(), node.getPort()));
        }
        RestClientBuilder builder = RestClient.builder(hosts.toArray(new HttpHost[hosts.size()]));

        if (username != null) {
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));
            builder.setHttpClientConfigCallback(httpClientBuilder ->
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
        }

        client = builder.build();
    }

    public RestClient getClient() {
        return client;
    }

    public void createIndex(String index) throws IOException {
        createIndex(index, false);
    }

    public void createIndex(String index, boolean ignoreErrors) throws IOException {
        logger.debug("create index [{}]", index);
        try {
            Response response = client.performRequest("PUT", "/" + index, Collections.emptyMap());
            logger.trace("create index response: {}", response.getEntity().getContent());
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == 400 &&
                    (e.getMessage().contains("index_already_exists_exception") || e.getMessage().contains("IndexAlreadyExistsException") )) {
                if (!ignoreErrors) {
                    throw new RuntimeException("index already exists");
                }
                logger.trace("index already exists. Ignoring error...");
                return;
            }
            throw e;
        }
    }

    public BulkResponse bulk(BulkRequest bulkRequest) throws Exception {
        StringBuffer sbf = new StringBuffer();
        for (SingleBulkRequest request : bulkRequest.getRequests()) {
            sbf.append("{");
            String header = JsonUtil.serialize(request);
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

        StringEntity entity = new StringEntity(sbf.toString(), Charset.defaultCharset());
        Response restResponse = client.performRequest("POST", "/_bulk", Collections.emptyMap(), entity);
        BulkResponse response = JsonUtil.deserialize(restResponse, BulkResponse.class);
        logger.debug("bulk response: {}", response);
        return response;
    }

    public void putMapping(String index, String type, String mapping) throws IOException {
        logger.debug("put mapping [{}/{}]", index, type);

        StringEntity entity = new StringEntity(mapping, Charset.defaultCharset());
        Response restResponse = client.performRequest("PUT", "/" + index + "/_mapping/" + type, Collections.emptyMap(), entity);
        Map<String, Object> responseAsMap = JsonUtil.asMap(restResponse);
        logger.trace("put mapping response: {}", responseAsMap);
    }

    public String findVersion() throws IOException {
        logger.debug("findVersion()");
        String version;
        Response restResponse = client.performRequest("GET", "/");
        Map<String, Object> responseAsMap = JsonUtil.asMap(restResponse);
        logger.trace("get server response: {}", responseAsMap);
        Object oVersion = extractFromPath(responseAsMap, "version").get("number");
        version = (String) oVersion;
        logger.debug("findVersion() -> [{}]", version);
        return version;
    }

    public void refresh(String index) throws IOException {
        logger.debug("refresh index [{}]", index);

        String path = "/";

        if (index != null) {
            path += index + "/";
        }

        path += "_refresh";

        Response restResponse = client.performRequest("POST", path);
        Map<String, Object> responseAsMap = JsonUtil.asMap(restResponse);
        logger.trace("refresh raw response: {}", responseAsMap);
    }

    public void waitForHealthyIndex(String index) throws IOException {
        logger.debug("wait for yellow health on index [{}]", index);

        Response restResponse = client.performRequest("GET", "/_cluster/health/" + index,
                Collections.singletonMap("wait_for_status", "yellow"));
        Map<String, Object> responseAsMap = JsonUtil.asMap(restResponse);

        logger.trace("health response: {}", responseAsMap);
    }

    public void index(String index, String type, String id, String json) throws IOException {
        logger.debug("put document [{}/{}/{}]", index, type, id);

        StringEntity entity = new StringEntity(json, Charset.defaultCharset());
        Response restResponse = client.performRequest("PUT", "/" + index + "/" + type+ "/" + id, Collections.emptyMap(), entity);
        Map<String, Object> responseAsMap = JsonUtil.asMap(restResponse);

        logger.trace("put document response: {}", responseAsMap);
    }

    public boolean isExistingDocument(String index, String type, String id) throws IOException {
        logger.debug("is existing doc [{}]/[{}]/[{}]", index, type, id);

        try {
            Response restResponse = client.performRequest("GET", "/" + index + "/" + type+ "/" + id);
            Map<String, Object> responseAsMap = JsonUtil.asMap(restResponse);

            logger.trace("get document response: {}", responseAsMap);
            return true;
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == 404) {
                logger.debug("doc [{}]/[{}]/[{}] does not exist", index, type, id);
                return false;
            }
            throw e;
        }
    }

    public void deleteIndex(String index) throws IOException {
        logger.debug("delete index [{}]", index);

        try {
            Response response = client.performRequest("DELETE", "/" + index);
            logger.trace("delete index response: {}", response.getEntity().getContent());
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == 404) {
                logger.debug("index [{}] does not exist", index);
                return;
            }
            throw e;
        }
    }

    public void shutdown() throws IOException {
        logger.debug("Closing REST client");
        if (client != null) {
            client.close();
            logger.debug("REST client closed");
        }
    }

    public SearchResponse search(String index, String type, String query) throws IOException {
        return search(index, type, query, null, null);
    }

    public SearchResponse search(String index, String type, String query, Integer size, String field) throws IOException {
        SearchRequest.Builder builder = SearchRequest.builder().setQuery(query).setSize(size);
        if (field != null) {
            builder.setFields(field);
        }
        SearchRequest request = builder.build();

        return search(index, type, request);
    }

    public SearchResponse search(String index, String type, SearchRequest searchRequest) throws IOException {
        logger.debug("search [{}]/[{}], request [{}]", index, type, searchRequest);

        String path = "/";

        if (index != null) {
            path += index + "/";
        }
        if (type != null) {
            path += type + "/";
        }

        path += "_search";

        Map<String, String> params = new HashMap<>();
        if (searchRequest.getQuery() !=  null) {
            params.put("q", searchRequest.getQuery());
        }
        if (searchRequest.getFields() !=  null) {
            // If we never set elasticsearch behavior, it's time to do so
            if (FIELDS == null) {
                setElasticsearchBehavior();
            }
            params.put(FIELDS, String.join(",", (CharSequence[]) searchRequest.getFields()));
        }

        Response restResponse = client.performRequest("GET", path, params);
        SearchResponse searchResponse = JsonUtil.deserialize(restResponse, SearchResponse.class);

        logger.trace("search response: {}", searchResponse);
        return searchResponse;
    }

    protected String setElasticsearchBehavior() throws IOException {
        String version = findVersion();

        // With elasticsearch 5.0.0, we need to use `stored_fields` instead of `fields`
        if (new VersionComparator().compare(version, "5") >= 0) {
            FIELDS = "stored_fields";
            logger.debug("Using elasticsearch >= 5, so we use [{}] as fields option", FIELDS);
        } else {
            FIELDS = "fields";
            logger.debug("Using elasticsearch < 5, so we use [{}] as fields option", FIELDS);
        }
        return FIELDS;
    }


    public boolean isExistingType(String index, String type) throws IOException {
        logger.debug("is existing type [{}]/[{}]", index, type);

        try {
            Response restResponse = client.performRequest("GET", "/" + index);
            Map<String, Object> responseAsMap = JsonUtil.asMap(restResponse);
            logger.trace("get index metadata response: {}", responseAsMap);

            Map<String, Object> mappings = extractFromPath(responseAsMap, index, "mappings");
            return mappings.containsKey(type);
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == 404) {
                logger.debug("type [{}]/[{}] does not exist", index, type);
                return false;
            }
            throw e;
        }
    }

    public boolean isExistingIndex(String index) throws IOException {
        logger.debug("is existing index [{}]", index);

        try {
            Response restResponse = client.performRequest("GET", "/" + index);
            Map<String, Object> responseAsMap = JsonUtil.asMap(restResponse);
            logger.trace("get index metadata response: {}", responseAsMap);
            return true;
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == 404) {
                logger.debug("index [{}] does not exist", index);
                return false;
            }
            throw e;
        }
    }

    public static class Builder {

        private List<Node> nodes = new ArrayList<>();
        private String username = null;
        private String password = null;

        public Builder setUsername(String username) {
            this.username = username;
            return this;
        }

        public Builder setPassword(String password) {
            this.password = password;
            return this;
        }

        public Builder addNode(Node node) {
            nodes.add(node);
            return this;
        }

        public ElasticsearchClient build() {
            return new ElasticsearchClient(nodes, username, password);
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
     * @param forceUpdate If true, it will try to update the mapping
     * @throws Exception in case of error
     */
    public static void pushMapping(ElasticsearchClient client, String index, String type, String mapping, boolean forceUpdate) throws Exception {
        boolean mappingExist = client.isExistingType(index, type);
        if (!mappingExist || forceUpdate) {
            if (forceUpdate) {
                logger.debug("Mapping [{}]/[{}] will be updated if existing.", index, type);
            } else {
                logger.debug("Mapping [{}]/[{}] doesn't exist. Creating it.", index, type);
            }
            logger.trace("Mapping for [{}]/[{}]: [{}]", index, type, mapping);
            client.putMapping(index, type, mapping);
        } else {
            logger.debug("Mapping [" + index + "]/[" + type + "] already exists.");
        }
    }
}
