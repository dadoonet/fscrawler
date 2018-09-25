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

package fr.pilato.elasticsearch.crawler.fs.client.v5;


import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientBase;
import fr.pilato.elasticsearch.crawler.fs.settings.Elasticsearch;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.Version;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.get.GetIndexRequest;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.common.xcontent.XContentType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static fr.pilato.elasticsearch.crawler.fs.client.v5.LowLevelClientJsonUtil.asMap;
import static fr.pilato.elasticsearch.crawler.fs.settings.Elasticsearch.Node;

/**
 * Simple Elasticsearch client over HTTP or HTTPS.
 * Only needed methods are exposed.
 */
public class ElasticsearchClientV5 implements ElasticsearchClientBase {

    private static final Logger logger = LogManager.getLogger(ElasticsearchClientV5.class);

    private boolean INGEST_SUPPORT = true;
    /**
     * Type name for Elasticsearch versions < 6.0
     * @deprecated Will be removed with Elasticsearch V8
     */
    @Deprecated
    private static final String INDEX_TYPE_DOC_V5 = "doc";
    /**
     * Type name for Elasticsearch versions >= 6.0
     * @deprecated Will be removed with Elasticsearch V8
     */
    @Deprecated
    private static final String INDEX_TYPE_DOC = "_doc";
    /**
     * Type name to use. It depends on elasticsearch version.
     * @deprecated Will be removed with Elasticsearch V8
     */
    @Deprecated
    private String defaultTypeName = INDEX_TYPE_DOC;
    private Version VERSION = null;

    public ElasticsearchClientV5(RestClientBuilder client) {
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
        CreateIndexRequest cir = new CreateIndexRequest(index);
        cir.settings(indexSettings, XContentType.JSON);
        CreateIndexResponse indexResponse = indices().create(cir, RequestOptions.DEFAULT);
        if (!indexResponse.isAcknowledged() && !ignoreErrors) {
            throw new RuntimeException("index already exists");
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
        GetIndexRequest gir = new GetIndexRequest();
        gir.indices(index);
        return indices().exists(gir, RequestOptions.DEFAULT);
    }

    /**
     * Check if a pipeline exists
     * @param pipeline pipeline name
     * @return true if the pipeline exists, false otherwise
     * @throws IOException In case of error
     */
    public boolean isExistingPipeline(String pipeline) throws IOException {
        logger.debug("is existing pipeline [{}]", pipeline);

        try {
            Response restResponse = getLowLevelClient().performRequest(new Request("GET", "/_ingest/pipeline/" + pipeline));
            logger.trace("get pipeline metadata response: {}", asMap(restResponse));
            return true;
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == 404) {
                logger.debug("pipeline [{}] does not exist", pipeline);
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

        Response restResponse = getLowLevelClient().performRequest(new Request("POST", path));
        logger.trace("refresh raw response: {}", asMap(restResponse));
    }

    /**
     * Wait for an index to become at least yellow (all primaries assigned)
     * @param index index name
     * @throws IOException In case of error
     */
    public void waitForHealthyIndex(String index) throws IOException {
        logger.debug("wait for yellow health on index [{}]", index);

        Request request = new Request("GET", "/_cluster/health/" + index);
        request.addParameter("wait_for_status", "yellow");
        Response restResponse = getLowLevelClient().performRequest(request);
        logger.trace("health response: {}", asMap(restResponse));
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

        Request request = new Request("POST", "/_reindex");
        request.setJsonEntity(reindexQuery);

        Response restResponse = getLowLevelClient().performRequest(request);
        Map<String, Object> response = asMap(restResponse);
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

        Request request = new Request("POST", "/" + index + "/" + type + "/_delete_by_query");
        request.setJsonEntity(deleteByQuery);
        Response restResponse = getLowLevelClient().performRequest(request);
        Map<String, Object> response = asMap(restResponse);
        logger.debug("reindex response: {}", response);
    }

    // Utility methods

    public void setElasticsearchBehavior() throws IOException {
        if (VERSION == null) {
            VERSION = info(RequestOptions.DEFAULT).getVersion();

            // With elasticsearch 5.0.0, we have ingest node
            if (VERSION.onOrAfter(Version.V_5_0_0_alpha1)) {
                INGEST_SUPPORT = true;
                logger.debug("Using elasticsearch >= 5, so we can use ingest node feature");
            } else {
                INGEST_SUPPORT = false;
                logger.debug("Using elasticsearch < 5, so we can't use ingest node feature");
            }

            // With elasticsearch 6.x, we can use _doc as the default type name
            if (VERSION.onOrAfter(Version.V_6_0_0)) {
                logger.debug("Using elasticsearch >= 6, so we can use {} as the default type name", defaultTypeName);
            } else {
                defaultTypeName = INDEX_TYPE_DOC_V5;
                logger.debug("Using elasticsearch < 6, so we use {} as the default type name", defaultTypeName);
            }
        }
    }

    public boolean isIngestSupported() {
        return INGEST_SUPPORT;
    }

    public Version getVersion() {
        return VERSION;
    }

    public String getDefaultTypeName() {
        return defaultTypeName;
    }

    @Override
    public void index(String index, String type, String id, String json, String pipeline) {

    }

    @Override
    public void delete(String index, String type, String id) {

    }

    public static Node decodeCloudId(String cloudId) {
     	// 1. Ignore anything before `:`.
        String id = cloudId.substring(cloudId.indexOf(':')+1);

     	// 2. base64 decode
        String decoded = new String(Base64.getDecoder().decode(id));

        // 3. separate based on `$`
        String[] words = decoded.split("\\$");

 	    // 4. form the URLs
        return Node.builder().setHost(words[1] + "." + words[0]).setPort(443).setScheme(Node.Scheme.HTTPS).build();
    }

    public static RestClientBuilder buildRestClient(Elasticsearch settings) {
        List<HttpHost> hosts = new ArrayList<>(settings.getNodes().size());
        settings.getNodes().forEach(node -> {
            if (node.getCloudId() != null) {
                // We have a cloud id which simplifies all
                node = decodeCloudId(node.getCloudId());
            }

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

    @Override
    public void close() {

    }
}
