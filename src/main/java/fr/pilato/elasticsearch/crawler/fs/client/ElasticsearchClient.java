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
import org.elasticsearch.Version;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.TermQueryBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static fr.pilato.elasticsearch.crawler.fs.client.JsonUtil.extractFromPath;

/**
 * Simple Elasticsearch client over HTTP or HTTPS.
 * Only needed methods are exposed.
 */
public class ElasticsearchClient extends RestHighLevelClient {

    private static final Logger logger = LogManager.getLogger(ElasticsearchClient.class);

    private boolean INGEST_SUPPORT = true;
    private Version VERSION = null;

    public ElasticsearchClient(RestClientBuilder client) throws IOException {
        super(client);
    }

    /**
     * Shutdown the internal REST Low Level client
     * @throws IOException In case of error
     */
    public void shutdown() throws IOException {
        logger.debug("Closing REST client");
        close();
    }

    /**
     * Create an index
     * @param index index name
     * @param ignoreErrors don't fail if the index already exists
     * @param indexSettings index settings if any
     * @throws IOException In case of error
     */
    public void createIndex(String index, boolean ignoreErrors, String indexSettings) throws IOException {
        logger.debug("create index [{}]", index);
        logger.trace("index settings: [{}]", indexSettings);
        try {
            StringEntity entity = null;
            if (!Strings.isNullOrEmpty(indexSettings)) {
                entity = new StringEntity(indexSettings, ContentType.APPLICATION_JSON);
            }
            Response response = getLowLevelClient().performRequest("PUT", "/" + index, Collections.emptyMap(), entity);
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

    /**
     * Delete an index (removes all data)
     * @param index index name
     * @throws IOException In case of error
     */
    public void deleteIndex(String index) throws IOException {
        logger.debug("delete index [{}]", index);

        try {
            Response response = getLowLevelClient().performRequest("DELETE", "/" + index);
            logger.trace("delete index response: {}", JsonUtil.asMap(response));
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == 404) {
                logger.debug("index [{}] does not exist", index);
                return;
            }
            throw e;
        }
    }

    /**
     * Check if an index exists
     * @param index index name
     * @return true if the index exists, false otherwise
     * @throws IOException In case of error
     */
    public boolean isExistingIndex(String index) throws IOException {
        logger.debug("is existing index [{}]", index);

        try {
            Response restResponse = getLowLevelClient().performRequest("GET", "/" + index);
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

    /**
     * Refresh an index
     * @param index index name
     * @throws IOException In case of error
     */
    public void refresh(String index) throws IOException {
        logger.debug("refresh index [{}]", index);

        String path = "/";

        if (index != null) {
            path += index + "/";
        }

        path += "_refresh";

        Response restResponse = getLowLevelClient().performRequest("POST", path);
        logger.trace("refresh raw response: {}", JsonUtil.asMap(restResponse));
    }

    /**
     * Wait for an index to become at least yellow (all primaries assigned)
     * @param index index name
     * @throws IOException In case of error
     */
    public void waitForHealthyIndex(String index) throws IOException {
        logger.debug("wait for yellow health on index [{}]", index);

        Response restResponse = getLowLevelClient().performRequest("GET", "/_cluster/health/" + index,
                Collections.singletonMap("wait_for_status", "yellow"));
        logger.trace("health response: {}", JsonUtil.asMap(restResponse));
    }

    /**
     * Reindex data from one index/type to another index
     * @param sourceIndex source index name
     * @param sourceType source type name
     * @param targetIndex target index name
     * @return The number of documents that have been reindexed
     * @throws IOException In case of error
     */
    public int reindex(String sourceIndex, String sourceType, String targetIndex) throws IOException {
        logger.debug("reindex [{}]/[{}] -> [{}]/[doc]", sourceIndex, sourceType, targetIndex);

        if (VERSION.major < 2 || (VERSION.major == 2 && VERSION.minor < 4)) {
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

        logger.trace("{}", reindexQuery);

        StringEntity entity = new StringEntity(reindexQuery, ContentType.APPLICATION_JSON);
        Response restResponse = getLowLevelClient().performRequest("POST", "/_reindex", Collections.emptyMap(), entity);
        Map<String, Object> response = JsonUtil.asMap(restResponse);
        logger.debug("reindex response: {}", response);

        return (int) response.get("total");
    }

    /**
     * Fully removes a type from an index (removes data)
     * @param index index name
     * @param type type
     * @throws IOException In case of error
     */
    public void deleteByQuery(String index, String type) throws IOException {
        logger.debug("deleteByQuery [{}]/[{}]", index, type);

        if (VERSION.onOrBefore(Version.V_5_0_0_alpha1)) {
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
        Response restResponse = getLowLevelClient().performRequest("POST", "/" + index + "/" + type + "/_delete_by_query", Collections.emptyMap(), entity);
        Map<String, Object> response = JsonUtil.asMap(restResponse);
        logger.debug("reindex response: {}", response);
    }

    // Utility methods

    public void setElasticsearchBehavior() throws IOException {
        if (VERSION == null) {
            VERSION = info().getVersion();

            // With elasticsearch 5.0.0, we have ingest node
            if (VERSION.onOrAfter(Version.V_5_0_0_alpha1)) {
                INGEST_SUPPORT = true;
                logger.debug("Using elasticsearch >= 5, so we can use ingest node feature");
            } else {
                INGEST_SUPPORT = false;
                logger.debug("Using elasticsearch < 5, so we can't use ingest node feature");
            }
        }
    }

    public boolean isIngestSupported() {
        return INGEST_SUPPORT;
    }

    public Version getVersion() {
        return VERSION;
    }

    public static RestClientBuilder buildRestClient(Elasticsearch settings) {
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

        return builder;
    }

    // Deprecated methods
    /**
     * Search for stored fields in elasticsearch: V2 implementation using fields vs source_fields.
     * TODO: Remove when we won't support anymore 2.0
     */
    @Deprecated
    public Collection<String> getFromStoredFieldsV2(String index, int size, String fieldFullPath, String objectName, String field,
                                                     String path,
                                                     TermQueryBuilder termQuery) throws IOException {
        Collection<String> files = new ArrayList<>();
        // We need to fallback on old implementation
        logger.debug("using low level client search [{}], request [{}]", index, termQuery);

        String url = "/";

        if (index != null) {
            url += index + "/";
        }

        url += "_search";

        Map<String, String> params = new HashMap<>();
        if (termQuery !=  null) {
            params.put("q", termQuery.fieldName() + ":" + termQuery.value());
        }
        params.put("fields", "_source," + field);
        params.put("size", Integer.toString(size));

        Response restResponse = getLowLevelClient().performRequest("GET", url, params);
        fr.pilato.elasticsearch.crawler.fs.client.SearchResponse response = JsonUtil.deserialize(restResponse, fr.pilato.elasticsearch.crawler.fs.client.SearchResponse.class);

        logger.trace("Response [{}]", response.toString());
        if (response.getHits() != null && response.getHits().getHits() != null) {
            for (fr.pilato.elasticsearch.crawler.fs.client.SearchResponse.Hit hit : response.getHits().getHits()) {
                String name;
                if (hit.getSource() != null
                        && extractFromPath(hit.getSource(), objectName).get(field) != null) {
                    name = (String) extractFromPath(hit.getSource(), objectName).get(field);
                } else if (hit.getFields() != null
                        && hit.getFields().get(fieldFullPath) != null) {
                    // In case someone disabled _source which is not recommended
                    name = getName(hit.getFields().get(fieldFullPath));
                } else {
                    // Houston, we have a problem ! We can't get the old files from ES
                    logger.warn("Can't find in _source nor fields the existing filenames in path [{}]. " +
                            "Please enable _source or store field [{}]", path, fieldFullPath);
                    throw new RuntimeException("Mapping is incorrect: please enable _source or store field [" +
                            fieldFullPath + "].");
                }
                files.add(name);
            }
        }

        return files;
    }

    @Deprecated
    private String getName(Object nameObject) {
        if (nameObject instanceof List) {
            return String.valueOf (((List) nameObject).get(0));
        }

        throw new RuntimeException("search result, " + nameObject +
                " not of type List<String> but " +
                nameObject.getClass().getName() + " with value " + nameObject);
    }

    /**
     * Search with a JSON Body
     * TODO: Remove when we won't support anymore 2.0
     * @param index Index. Might be null.
     * @param json  Json Source
     * @return The Response object
     * @throws IOException if something goes wrong
     */
    @Deprecated
    public fr.pilato.elasticsearch.crawler.fs.client.SearchResponse searchJson(String index, String json) throws IOException {
        logger.debug("search [{}], request [{}]", index, json);

        String path = "/";

        if (index != null) {
            path += index + "/";
        }

        path += "_search";

        Response restResponse = getLowLevelClient().performRequest("GET", path, Collections.emptyMap(),
                new StringEntity(json, ContentType.APPLICATION_JSON));
        fr.pilato.elasticsearch.crawler.fs.client.SearchResponse searchResponse = JsonUtil.deserialize(restResponse, fr.pilato.elasticsearch.crawler.fs.client.SearchResponse.class);

        logger.trace("search response: {}", searchResponse);
        return searchResponse;
    }
}
