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


import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Simple Elasticsearch client over HTTP.
 * Only needed methods are exposed.
 */
public class ElasticsearchClient {

    static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    static final JsonFactory JSON_FACTORY = new JacksonFactory();
    private final HttpRequestFactory requestFactory;

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
    }

    private ElasticsearchUrl buildUrl() {
        ElasticsearchUrl url = new ElasticsearchUrl();

        // Get the first node
        Node node = nodes.get(0);

        url.setScheme("http");
        url.setHost(node.getHost());
        url.setPort(node.getPort());
        return url;
    }

    public void addNode(Node node) {
        nodes.add(node);
    }

    public void createIndex(String index) throws IOException {
        createIndex(index, false);
    }

    public void createIndex(String index, boolean ignoreErrors) throws IOException {
        logger.debug("create index [{}]", index);
        GenericUrl genericUrl = buildUrl();
        genericUrl.appendRawPath("/" + index);
        HttpRequest request = requestFactory.buildPutRequest(genericUrl, null);

        HttpResponse httpResponse;
        try {
            httpResponse = request.execute();
        } catch (HttpResponseException e) {
            if (e.getStatusCode() == 400 && e.getContent().contains("index_already_exists_exception")) {
                if (!ignoreErrors) {
                    throw new RuntimeException("index already exists");
                }
                logger.trace("index already exists. Ignoring error...");
                return;
            }
            throw e;
        }
        logger.trace("create index response: {}", httpResponse.parseAsString());
    }

    public BulkResponse bulk(BulkRequest bulkRequest) throws Exception {
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

        ElasticsearchUrl genericUrl = buildUrl();
        genericUrl.appendRawPath("/_bulk");
        HttpRequest request = requestFactory.buildPostRequest(genericUrl,
                ByteArrayContent.fromString("application/json", sbf.toString()));
        HttpResponse httpResponse = request.execute();
        BulkResponse response = httpResponse.parseAs(BulkResponse.class);
        logger.debug("bulk response: {}", response);


        return new BulkResponse();
    }

    public void putMapping(String index, String type, ObjectNode mapping) throws IOException {
        logger.debug("put mapping [{}/{}]", index, type);
        GenericUrl genericUrl = buildUrl();
        genericUrl.appendRawPath("/" + index);
        genericUrl.appendRawPath("/_mapping");
        genericUrl.appendRawPath("/" + type);
        HttpRequest request = requestFactory.buildPutRequest(genericUrl,
                ByteArrayContent.fromString("application/json", mapping.toString()));

        HttpResponse httpResponse = request.execute();
        logger.trace("put mapping response: {}", httpResponse.parseAsString());
    }

    public static class ElasticsearchUrl extends GenericUrl {
        @Key
        public String q;
    }

    public SearchResponse search(String index, String type, String query) throws IOException {
        return search(index, type, query, null, null);
    }

    public SearchResponse search(String index, String type, String query, Integer size, String field) throws IOException {
        logger.debug("search [{}]/[{}], query [{}], size [{}], field [{}]", index, type, query, size, field);
        return search(index, type, SearchRequest.builder().setQuery(query).setSize(size).setFields(field).build());
    }

    public SearchResponse search(String index, String type, SearchRequest searchRequest) throws IOException {
        logger.debug("search [{}]/[{}], request [{}]", index, type, searchRequest);

        ElasticsearchUrl genericUrl = buildUrl();
        genericUrl.appendRawPath("/" + index);
        genericUrl.appendRawPath("/" + type);
        genericUrl.appendRawPath("/_search");
        if (searchRequest.getQuery() !=  null) {
            genericUrl.q = searchRequest.getQuery();
        }
        HttpRequest request = requestFactory.buildPostRequest(genericUrl, new JsonHttpContent(JSON_FACTORY, searchRequest));
        HttpResponse httpResponse = request.execute();
        SearchResponse response = httpResponse.parseAs(SearchResponse.class);
        logger.debug("search response: {}", response);

        return response;
    }

    public boolean isExistingType(String index, String type) throws IOException {
        logger.debug("is existing type [{}]/[{}]", index, type);

        GenericUrl genericUrl = buildUrl();
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
            throw e;
        }
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
    public static void pushMapping(ElasticsearchClient client, String index, String type, ObjectNode mapping) throws Exception {
        boolean mappingExist = client.isExistingType(index, type);
        if (!mappingExist) {
            logger.debug("Mapping [{}]/[{}] doesn't exist. Creating it.", index, type);
            logger.trace("Mapping for [{}]/[{}]: [{}]", index, type, mapping.toString());
            client.putMapping(index, type, mapping);
        } else {
            logger.debug("Mapping [" + index + "]/[" + type + "] already exists.");
        }
    }
}
