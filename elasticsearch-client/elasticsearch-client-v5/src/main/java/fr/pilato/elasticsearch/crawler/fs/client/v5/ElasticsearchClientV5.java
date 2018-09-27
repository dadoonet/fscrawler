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
import fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil;
import fr.pilato.elasticsearch.crawler.fs.settings.Elasticsearch;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.Version;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.get.GetIndexRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshResponse;
import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.ingest.GetPipelineRequest;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentType;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.INDEX_SETTINGS_FILE;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.INDEX_SETTINGS_FOLDER_FILE;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.readJsonFile;
import static fr.pilato.elasticsearch.crawler.fs.settings.Elasticsearch.Node;

/**
 * Elasticsearch Client for Clusters running v6.
 */
public class ElasticsearchClientV5 implements ElasticsearchClientBase {

    private static final Logger logger = LogManager.getLogger(ElasticsearchClientV6.class);
    private final Path config;
    private final FsSettings settings;

    private RestHighLevelClient client = null;
    private BulkProcessor bulkProcessorDoc = null;
    private BulkProcessor bulkProcessorFolder = null;

    /**
     * Type name for Elasticsearch versions >= 6.0
     * @deprecated Will be removed with Elasticsearch V8
     */
    @Deprecated
    private static final String INDEX_TYPE_DOC = "_doc";

    public ElasticsearchClientV5(Path config, FsSettings settings) {
        this.config = config;
        this.settings = settings;
    }

    @Override
    public void start() throws IOException {
        if (client != null) {
            // The client has already been initialized. Let's skip this again
            return;
        }

        try {
            // Create an elasticsearch client
            client = new RestHighLevelClient(buildRestClient(settings.getElasticsearch()));
            // We set what will be elasticsearch behavior as it depends on the cluster version
            displayVersion();
        } catch (Exception e) {
            logger.warn("failed to create elasticsearch client, disabling crawler...");
            throw e;
        }

        if (settings.getElasticsearch().getPipeline() != null) {
            // Check that the pipeline exists
            if (!isExistingPipeline(settings.getElasticsearch().getPipeline())) {
                throw new RuntimeException("You defined pipeline:" + settings.getElasticsearch().getPipeline() +
                        ", but it does not exist.");
            }
        }

        BiConsumer<BulkRequest, ActionListener<BulkResponse>> bulkConsumer =
                (request, bulkListener) -> client.bulkAsync(request, RequestOptions.DEFAULT, bulkListener);

        bulkProcessorDoc = BulkProcessor.builder(bulkConsumer, new DebugListener(logger))
                .setBulkActions(settings.getElasticsearch().getBulkSize())
                .setFlushInterval(TimeValue.timeValueMillis(settings.getElasticsearch().getFlushInterval().millis()))
                .setBulkSize(new ByteSizeValue(settings.getElasticsearch().getByteSize().getBytes()))
                // TODO fix when elasticsearch will support global pipelines
//                .setPipeline(settings.getElasticsearch().getPipeline())
                .build();
        bulkProcessorFolder = BulkProcessor.builder(bulkConsumer, new DebugListener(logger))
                .setBulkActions(settings.getElasticsearch().getBulkSize())
                .setBulkSize(new ByteSizeValue(settings.getElasticsearch().getByteSize().getBytes()))
                .setFlushInterval(TimeValue.timeValueMillis(settings.getElasticsearch().getFlushInterval().millis()))
                .build();
    }

    public void displayVersion() throws IOException {
        Version version = client.info(RequestOptions.DEFAULT).getVersion();
        logger.info("Elasticsearch Client V{} connected to a node running V{}", "6", version.toString());
    }

    class DebugListener implements BulkProcessor.Listener {
        private final Logger logger;

        DebugListener(Logger logger) {
            this.logger = logger;
        }

        @Override public void beforeBulk(long executionId, BulkRequest request) {
            logger.trace("Sending a bulk request of [{}] requests", request.numberOfActions());
        }

        @Override public void afterBulk(long executionId, BulkRequest request, BulkResponse response) {
            logger.trace("Executed bulk request with [{}] requests", request.numberOfActions());
            if (response.hasFailures()) {
                final int[] failures = {0};
                response.iterator().forEachRemaining(bir -> {
                    if (bir.isFailed()) {
                        failures[0]++;
                        logger.debug("Error caught for [{}]/[{}]/[{}]: {}", bir.getIndex(),
                                bir.getType(), bir.getId(), bir.getFailureMessage());
                    };
                });
                logger.warn("Got [{}] failures of [{}] requests", failures[0], request.numberOfActions());
            }
        }

        @Override public void afterBulk(long executionId, BulkRequest request, Throwable failure) {
            logger.warn("Got a hard failure when executing the bulk request", failure);
        }
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
        CreateIndexResponse indexResponse = client.indices().create(cir, RequestOptions.DEFAULT);
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
        return client.indices().exists(gir, RequestOptions.DEFAULT);
    }

    /**
     * Check if a pipeline exists
     * @param pipelineName pipeline name
     * @return true if the pipeline exists, false otherwise
     * @throws IOException In case of error
     */
    public boolean isExistingPipeline(String pipelineName) throws IOException {
        logger.debug("is existing pipeline [{}]", pipelineName);
        return client.ingest().getPipeline(new GetPipelineRequest(pipelineName), RequestOptions.DEFAULT).isFound();
    }

    /**
     * Refresh an index
     * @param index index name
     * @throws IOException In case of error
     */
    public void refresh(String index) throws IOException {
        logger.debug("refresh index [{}]", index);
        RefreshResponse refresh = client.indices().refresh(new RefreshRequest(index), RequestOptions.DEFAULT);
        logger.trace("refresh response: {}", refresh);
    }

    /**
     * Wait for an index to become at least yellow (all primaries assigned)
     * @param index index name
     * @throws IOException In case of error
     */
    public void waitForHealthyIndex(String index) throws IOException {
        logger.debug("wait for yellow health on index [{}]", index);
        ClusterHealthResponse health = client.cluster().health(new ClusterHealthRequest(index).waitForYellowStatus(),
                RequestOptions.DEFAULT);
        logger.trace("health response: {}", health);
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

        Response restResponse = client.getLowLevelClient().performRequest(request);
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

        String deleteByQuery = "{\n" +
                "  \"query\": {\n" +
                "    \"match_all\": {}\n" +
                "  }\n" +
                "}";

        Request request = new Request("POST", "/" + index + "/" + type + "/_delete_by_query");
        request.setJsonEntity(deleteByQuery);
        Response restResponse = client.getLowLevelClient().performRequest(request);
        Map<String, Object> response = asMap(restResponse);
        logger.debug("reindex response: {}", response);
    }

    // Utility methods

    public boolean isIngestSupported() {
        return true;
    }

    public String getDefaultTypeName() {
        return INDEX_TYPE_DOC;
    }

    @Override
    public void index(String index, String type, String id, String json, String pipeline) {
        bulkProcessorDoc.add(new IndexRequest(index, type, id).setPipeline(pipeline).source(json, XContentType.JSON));
    }

    @Override
    public void delete(String index, String type, String id) {
        bulkProcessorDoc.add(new DeleteRequest(index, type, id));
    }

    @Override
    public void close() throws IOException {
        logger.debug("Closing Elasticsearch client manager");
        if (bulkProcessorDoc != null) {
            try {
                bulkProcessorDoc.awaitClose(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                logger.warn("Did not succeed in closing the bulk processor for documents", e);
                throw new IOException(e);
            }
        }
        if (bulkProcessorFolder != null) {
            try {
                bulkProcessorFolder.awaitClose(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                logger.warn("Did not succeed in closing the bulk processor for folders", e);
                throw new IOException(e);
            }
        }
        if (client != null) {
            client.close();
        }
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

    public void createIndices() throws Exception {
        String elasticsearchVersion;
        Path jobMappingDir = config.resolve(settings.getName()).resolve("_mappings");

        // Let's read the current version of elasticsearch cluster
        Version version = client.info(RequestOptions.DEFAULT).getVersion();
        logger.debug("FS crawler connected to an elasticsearch [{}] node.", version.toString());

        elasticsearchVersion = Byte.toString(version.major);

        // If needed, we create the new settings for this files index
        if (!settings.getFs().isAddAsInnerObject() || (!settings.getFs().isJsonSupport() && !settings.getFs().isXmlSupport())) {
            createIndex(jobMappingDir, elasticsearchVersion, INDEX_SETTINGS_FILE, settings.getElasticsearch().getIndex());
        } else {
            createIndex(settings.getElasticsearch().getIndex(), true, null);
        }

        // If needed, we create the new settings for this folder index
        if (settings.getFs().isIndexFolders()) {
            createIndex(jobMappingDir, elasticsearchVersion, INDEX_SETTINGS_FOLDER_FILE, settings.getElasticsearch().getIndexFolder());
        } else {
            createIndex(settings.getElasticsearch().getIndexFolder(), true, null);
        }
    }

    private void createIndex(Path jobMappingDir, String elasticsearchVersion, String indexSettingsFile, String indexName) throws Exception {
        try {
            // If needed, we create the new settings for this files index
            String indexSettings = readJsonFile(jobMappingDir, config, elasticsearchVersion, indexSettingsFile);

            createIndex(indexName, true, indexSettings);
        } catch (Exception e) {
            logger.warn("failed to create index [{}], disabling crawler...", indexName);
            throw e;
        }
    }

    static Map<String, Object> asMap(Response response) {
        try {
            if (response.getEntity() == null) {
                return null;
            }
            return JsonUtil.asMap(response.getEntity().getContent());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
