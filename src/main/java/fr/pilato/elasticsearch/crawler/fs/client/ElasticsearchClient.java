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


import com.google.common.base.Strings;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.Elasticsearch;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.Elasticsearch.Node;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple Elasticsearch client over HTTP or HTTPS.
 * Only needed methods are exposed.
 */
public class ElasticsearchClient {

    private static final Logger logger = LogManager.getLogger(ElasticsearchClient.class);

    private final Elasticsearch settings;
    private final RestClient client;
    private String FIELDS = null;
    private boolean INGEST_SUPPORT = true;
    private String VERSION = null;

    public ElasticsearchClient(Elasticsearch settings) {
        this.settings = settings;
        List<HttpHost> hosts = new ArrayList<>(settings.getNodes().size());
        settings.getNodes().forEach(node -> {
            Node.Scheme scheme = node.getScheme();
            if (scheme == null) {
                // Default to HTTP. In case we are reading an old configuration
                scheme = Node.Scheme.HTTP;
            }
            hosts.add(new HttpHost(node.getHost(), node.getPort(), scheme.toLowerCase()));
        });

        RestClientBuilder builder = RestClient.builder(hosts.toArray(new HttpHost[hosts.size()]));

        if (settings.getUsername() != null) {
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(settings.getUsername(), settings.getPassword()));
            builder.setHttpClientConfigCallback(httpClientBuilder ->
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
        }

        client = builder.build();
    }

    public RestClient getClient() {
        return client;
    }

    public Elasticsearch getSettings() {
        return settings;
    }

    public void createIndex(String index) throws IOException {
        createIndex(index, false, null);
    }

    public void createIndex(String index, boolean ignoreErrors, String indexSettings) throws IOException {
        logger.debug("create index [{}]", index);
        logger.trace("index settings: [{}]", indexSettings);
        try {
            StringEntity entity = null;
            if (!Strings.isNullOrEmpty(indexSettings)) {
                entity = new StringEntity(indexSettings, ContentType.APPLICATION_JSON);
            }
            Response response = client.performRequest("PUT", "/" + index, Collections.emptyMap(), entity);
            logger.trace("create index response: {}", JsonUtil.asMap(response));
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == 400 &&
                    (e.getMessage().contains("index_already_exists_exception") || // ES 5.x
                            e.getMessage().contains("resource_already_exists_exception") || // ES 6.x
                            e.getMessage().contains("IndexAlreadyExistsException") )) { // ES 1.x and 2.x
                if (!ignoreErrors) {
                    throw new RuntimeException("index already exists");
                }
                logger.trace("index already exists. Ignoring error...");
                return;
            }
            throw e;
        }
    }

    public BulkResponse bulk(BulkRequest bulkRequest, String pipeline) throws Exception {
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

        Map<String, String> params;

        if (pipeline != null) {
            params = new HashMap<>(1);
            params.put("pipeline", pipeline);
        } else {
            params = Collections.emptyMap();
        }

        StringEntity entity = new StringEntity(sbf.toString(), ContentType.APPLICATION_JSON);
        Response restResponse = client.performRequest("POST", "/_bulk", params, entity);
        BulkResponse response = JsonUtil.deserialize(restResponse, BulkResponse.class);
        logger.debug("bulk response: {}", response);
        return response;
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
        logger.trace("refresh raw response: {}", JsonUtil.asMap(restResponse));
    }

    public void waitForHealthyIndex(String index) throws IOException {
        logger.debug("wait for yellow health on index [{}]", index);

        Response restResponse = client.performRequest("GET", "/_cluster/health/" + index,
                Collections.singletonMap("wait_for_status", "yellow"));
        logger.trace("health response: {}", JsonUtil.asMap(restResponse));
    }

    public void index(String index, String id, String json) throws IOException {
        logger.debug("put document [{}/doc/{}]", index, id);

        StringEntity entity = new StringEntity(json, ContentType.APPLICATION_JSON);
        Response restResponse = client.performRequest("PUT", "/" + index + "/doc/" + id, Collections.emptyMap(), entity);
        logger.trace("put document response: {}", JsonUtil.asMap(restResponse));
    }

    public boolean isExistingDocument(String index, String id) throws IOException {
        logger.debug("is existing doc [{}]/[doc]/[{}]", index, id);

        try {
            Response restResponse = client.performRequest("GET", "/" + index + "/doc/" + id);
            logger.trace("get document response: {}", JsonUtil.asMap(restResponse));
            return true;
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == 404) {
                logger.debug("doc [{}]/[doc]/[{}] does not exist", index, id);
                return false;
            }
            throw e;
        }
    }

    public void deleteIndex(String index) throws IOException {
        logger.debug("delete index [{}]", index);

        try {
            Response response = client.performRequest("DELETE", "/" + index);
            logger.trace("delete index response: {}", JsonUtil.asMap(response));
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

    public SearchResponse search(String index, String query) throws IOException {
        return search(index, query, null);
    }

    public SearchResponse search(String index, String query, Integer size, String... fields) throws IOException {
        SearchRequest request = SearchRequest.builder().setQuery(query).setSize(size).setFields(fields).build();
        return search(index, request);
    }

    public SearchResponse search(String index, SearchRequest searchRequest) throws IOException {
        logger.debug("search [{}], request [{}]", index, searchRequest);

        String path = "/";

        if (index != null) {
            path += index + "/";
        }

        path += "_search";

        Map<String, String> params = new HashMap<>();
        if (searchRequest.getQuery() !=  null) {
            params.put("q", searchRequest.getQuery());
        }
        if (searchRequest.getFields() !=  null && searchRequest.getFields().length > 0) {
            params.put(FIELDS, String.join(",", (CharSequence[]) searchRequest.getFields()));
        }
        if (searchRequest.getSize() != null) {
            params.put("size", searchRequest.getSize().toString());
        }
        Response restResponse = client.performRequest("GET", path, params);
        SearchResponse searchResponse = JsonUtil.deserialize(restResponse, SearchResponse.class);

        logger.trace("search response: {}", searchResponse);
        return searchResponse;
    }

    /**
     * Search with a JSON Body
     * @param index Index. Might be null.
     * @param json  Json Source
     * @return The Response object
     * @throws IOException if something goes wrong
     */
    public SearchResponse searchJson(String index, String json) throws IOException {
        logger.debug("search [{}], request [{}]", index, json);

        String path = "/";

        if (index != null) {
            path += index + "/";
        }

        path += "_search";

        Response restResponse = client.performRequest("GET", path, Collections.emptyMap(),
                new StringEntity(json, ContentType.APPLICATION_JSON));
        SearchResponse searchResponse = JsonUtil.deserialize(restResponse, SearchResponse.class);

        logger.trace("search response: {}", searchResponse);
        return searchResponse;
    }

    public void setElasticsearchBehavior() throws IOException {
        if (VERSION == null) {
            VERSION = findVersion();

            // With elasticsearch 5.0.0, we need to use `stored_fields` instead of `fields`
            if (new VersionComparator().compare(VERSION, "5") >= 0) {
                FIELDS = "stored_fields";
                logger.debug("Using elasticsearch >= 5, so we use [{}] as fields option", FIELDS);
                INGEST_SUPPORT = true;
                logger.debug("Using elasticsearch >= 5, so we can use ingest node feature");
            } else {
                FIELDS = "fields";
                logger.debug("Using elasticsearch < 5, so we use [{}] as fields option", FIELDS);
                INGEST_SUPPORT = false;
                logger.debug("Using elasticsearch < 5, so we can't use ingest node feature");
            }
        }
    }

    public boolean isExistingIndex(String index) throws IOException {
        logger.debug("is existing index [{}]", index);

        try {
            Response restResponse = client.performRequest("GET", "/" + index);
            logger.trace("get index metadata response: {}", JsonUtil.asMap(restResponse));
            return true;
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == 404) {
                logger.debug("index [{}] does not exist", index);
                return false;
            }
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> extractFromPath(Map<String, Object> json, String... path) {
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

    public boolean isIngestSupported() {
        return INGEST_SUPPORT;
    }

    public SearchResponse.Hit get(String index, String id) throws IOException {
        logger.debug("get [{}]/[doc]/[{}]", index, id);

        String path = "/" + index + "/doc/" + id;
        Response restResponse = client.performRequest("GET", path);
        SearchResponse.Hit hit = JsonUtil.deserialize(restResponse, SearchResponse.Hit.class);

        logger.trace("Hit: {}", hit);
        return hit;
    }

    public int reindex(String sourceIndex, String sourceType, String targetIndex) throws IOException {
        logger.debug("reindex [{}]/[{}] -> [{}]/[doc]", sourceIndex, sourceType, targetIndex);

        if (new VersionComparator().compare(VERSION, "2.3") < 0) {
            logger.warn("Can not use reindex API with elasticsearch [{}]", VERSION);
            return 0;
        }

        String reindexQuery = "{  \"source\": {\n" +
                "    \"index\": \"" + sourceIndex + "\",\n" +
                "    \"type\": \"" + sourceType + "\"\n" +
                "  },\n" +
                "  \"dest\": {\n" +
                "    \"index\": \"" + targetIndex + "\",\n" +
                "    \"type\": \"doc\"\n" +
                "  }\n" +
                "}\n";

        StringEntity entity = new StringEntity(reindexQuery, ContentType.APPLICATION_JSON);
        Response restResponse = client.performRequest("POST", "/_reindex", Collections.emptyMap(), entity);
        Map<String, Object> response = JsonUtil.asMap(restResponse);
        logger.debug("reindex response: {}", response);

        return (int) response.get("total");
    }

    public void deleteByQuery(String index, String type) throws IOException {
        logger.debug("deleteByQuery [{}]/[{}]", index, type);

        if (new VersionComparator().compare(VERSION, "5") < 0) {
            logger.warn("Can not use _delete_by_query API with elasticsearch [{}]. You have to reindex probably to get rid of [{}]/[{}].",
                    VERSION, index, type);
            return;
        }

        String deleteByQuery = "{\n" +
                "  \"query\": {\n" +
                "    \"match_all\": {}\n" +
                "  }\n" +
                "}";

        StringEntity entity = new StringEntity(deleteByQuery, ContentType.APPLICATION_JSON);
        Response restResponse = client.performRequest("POST", "/" + index + "/" + type + "/_delete_by_query", Collections.emptyMap(), entity);
        Map<String, Object> response = JsonUtil.asMap(restResponse);
        logger.debug("reindex response: {}", response);
    }
}
