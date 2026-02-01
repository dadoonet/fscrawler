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


import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.PathNotFoundException;
import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.framework.ExponentialBackoffPollInterval;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil;
import fr.pilato.elasticsearch.crawler.fs.framework.Version;
import fr.pilato.elasticsearch.crawler.fs.framework.bulk.FsCrawlerBulkProcessor;
import fr.pilato.elasticsearch.crawler.fs.framework.bulk.FsCrawlerRetryBulkProcessorListener;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.awaitility.core.ConditionTimeoutException;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.glassfish.jersey.logging.LoggingFeature;

import javax.net.ssl.*;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import java.io.*;
import java.net.ConnectException;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.stream.StreamSupport;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.*;
import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.parseJsonAsDocumentContext;
import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.serialize;

/**
 * Elasticsearch Client
 */
public class ElasticsearchClient implements IElasticsearchClient {

    private static final Logger logger = LogManager.getLogger();
    private final FsSettings settings;
    private static final String USER_AGENT = "FSCrawler-Rest-Client-" + Version.getVersion();

    // TODO this should be configurable
    public static final int CHECK_NODES_EVERY = 10;

    // Retry configuration for GET/HEAD requests
    private static final Duration RETRY_MAX_DURATION = Duration.ofSeconds(10);
    private static final Duration RETRY_INITIAL_DELAY = Duration.ofMillis(500);
    private static final Duration RETRY_MAX_DELAY = Duration.ofSeconds(5);

    private Client client = null;
    private FsCrawlerBulkProcessor<ElasticsearchOperation, ElasticsearchBulkRequest, ElasticsearchBulkResponse> bulkProcessor = null;
    private final List<String> hosts;
    private final List<String> initialHosts;

    private String version = null;
    private String license = null;
    private int majorVersion;
    private int minorVersion;
    private int currentNode = -1;
    private int currentRun = -1;
    private String authorizationHeader = null;
    private boolean semanticSearch;
    private boolean vectorSearch = false;
    private boolean serverless;

    public ElasticsearchClient(FsSettings settings) {
        this.settings = settings;
        this.hosts = new ArrayList<>(settings.getElasticsearch().getUrls().size());
        this.initialHosts = new ArrayList<>(settings.getElasticsearch().getUrls().size());
        settings.getElasticsearch().getUrls().forEach(url -> {
            hosts.add(url);
            initialHosts.add(url);
        });
        if (hosts.size() == 1) {
            // We have only one node, so we won't have to select a specific one but the only one.
            currentNode = 0;
        }
        semanticSearch = settings.getElasticsearch().isSemanticSearch();
    }

    @Override
    public void start() throws ElasticsearchClientException {
        if (client != null) {
            // The client has already been initialized. Let's skip this again
            return;
        }

        // Create the client
        ClientConfig config = new ClientConfig();
        // We need to suppress this, so we can do DELETE with body
        config.property(ClientProperties.SUPPRESS_HTTP_COMPLIANCE_VALIDATION, true);

        ClientBuilder clientBuilder = ClientBuilder.newBuilder()
                .hostnameVerifier(new NullHostNameVerifier())
                .withConfig(config);

        SSLContext sslContext = null;
        if (settings.getElasticsearch().isSslVerification()) {
            String caCertificatePath = settings.getElasticsearch().getCaCertificate();
            if (caCertificatePath != null) {
                File certFile = new File(caCertificatePath);
                sslContext = sslContextFromHttpCaCrt(certFile);
                logger.debug("Using provided CA Certificate from [{}]", caCertificatePath);
                clientBuilder.sslContext(sslContext);
            }
        } else {
            // Trusting all certificates. For test purposes only.
            try {
                sslContext = SSLContext.getInstance("SSL");
                sslContext.init(null, trustAllCerts, new SecureRandom());
                clientBuilder.sslContext(sslContext);
            } catch (KeyManagementException | NoSuchAlgorithmException e) {
                logger.warn("Failed to get SSL Context", e);
                throw new RuntimeException(e);
            }
        }

        // If we have an Api Key let's use it. Otherwise, we will use basic auth
        if (!FsCrawlerUtil.isNullOrEmpty(settings.getElasticsearch().getApiKey())) {
            authorizationHeader = "ApiKey " + settings.getElasticsearch().getApiKey();
        } else {
            HttpAuthenticationFeature feature = HttpAuthenticationFeature.basic(
                    settings.getElasticsearch().getUsername(),
                    settings.getElasticsearch().getPassword());
            clientBuilder.register(feature);
        }
        if (sslContext != null) {
            clientBuilder.sslContext(sslContext);
        }
        client = clientBuilder.build();
        if (logger.isTraceEnabled()) {
            client
//                    .property(LoggingFeature.LOGGING_FEATURE_LOGGER_NAME_CLIENT, ElasticsearchClient.class.getName())
                    .property(LoggingFeature.LOGGING_FEATURE_LOGGER_LEVEL_CLIENT, Level.FINEST.getName())
                    .property(LoggingFeature.LOGGING_FEATURE_VERBOSITY_CLIENT, LoggingFeature.Verbosity.PAYLOAD_ANY)
                    .property(LoggingFeature.LOGGING_FEATURE_MAX_ENTITY_SIZE_CLIENT, 8000);
        }

        try {
            String esVersion = getVersion();
            logger.debug("Elasticsearch Client connected to a node running version {}", esVersion);
            if (!settings.getElasticsearch().isSslVerification()) {
                logger.warn("We are not doing SSL verification. It's not recommended for production.");
            }
        } catch (Exception e) {
            logger.warn("Failed to create elasticsearch client on {}. Message: {}.",
                    settings.getElasticsearch().toString(),
                    e.getMessage());
            throw e;
        }

        if (settings.getElasticsearch().getPipeline() != null) {
            // Check that the pipeline exists
            if (!isExistingPipeline(settings.getElasticsearch().getPipeline())) {
                throw new RuntimeException("You defined pipeline:" + settings.getElasticsearch().getPipeline() +
                        ", but it does not exist.");
            }
        }

        if (semanticSearch) {
            // Check the version we are running or if it's using serverless
            if ((majorVersion >= 8 && minorVersion >= 17) || serverless || (majorVersion >= 9)) {
                logger.debug("Semantic search is enabled and we are running on a version of Elasticsearch {} " +
                        "which is 8.17 or higher. We will try to use the semantic search features.", version);
                license = getLicense();
                if (!"enterprise".equals(license) && !"trial".equals(license)) {
                    logger.warn("Semantic search is enabled but we are running Elasticsearch with a {} " +
                            "license although we need either an enterprise or trial license." +
                            "We will not be able to use the semantic search features ATM. We might switch later to " +
                            "a vector embeddings generation.", license);
                    semanticSearch = false;
                    vectorSearch = true;
                } else {
                    logger.debug("Semantic search is enabled");
                }
            } else {
                logger.warn("Semantic search is enabled but we are running on a version of Elasticsearch {} " +
                        "which is lower than 8.17. We will not be able to use the semantic search features.", version);
                semanticSearch = false;
            }
        }

        // Create the BulkProcessor instance
        bulkProcessor = new FsCrawlerBulkProcessor.Builder<>(
                new ElasticsearchEngine(this),
                new FsCrawlerRetryBulkProcessorListener<>("es_rejected_execution_exception"),
                ElasticsearchBulkRequest::new)
                .setBulkActions(settings.getElasticsearch().getBulkSize())
                .setFlushInterval(settings.getElasticsearch().getFlushInterval())
                .setByteSize(settings.getElasticsearch().getByteSize())
                .build();
    }

    private static SSLContext sslContextFromHttpCaCrt(File file) {
        try(InputStream in = new FileInputStream(file)) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Certificate certificate = cf.generateCertificate(in);

            final KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            keyStore.setCertificateEntry("elasticsearch-ca", certificate);

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), null);
            return sslContext;
        } catch (CertificateException | NoSuchAlgorithmException | KeyManagementException | KeyStoreException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<String> getAvailableNodes() {
        return hosts;
    }

    @Override
    public String getVersion() throws ElasticsearchClientException {
        if (version != null) {
            return version;
        }
        logger.trace("get version");
        String response = httpGet(null);
        // We parse the response
        DocumentContext document = parseJsonAsDocumentContext(response);
        // Cache the version and the major version
        version = document.read("$.version.number");
        majorVersion = extractMajorVersion(version);
        minorVersion = extractMinorVersion(version);
        serverless = false;

        try {
            if ("serverless".equals(document.read("$.version.build_flavor"))) {
                logger.debug("We are running on Elastic serverless cloud so we can not consider version number.");
                serverless = true;
                version = "serverless";
                majorVersion = 99;
                minorVersion = 999;
            }
        } catch (PathNotFoundException e) {
            // We are not running an official Elasticsearch version
            logger.info("FSCrawler only supports Elasticsearch official distribution. " +
                    "You are running another custom distribution. We will not be able to use some features like " +
                    "semantic search.");
        }
        logger.debug("get version returns {} and {} as the major version number", version, majorVersion);
        return version;
    }

    @Override
    public String getLicense() throws ElasticsearchClientException {
        if (license != null) {
            return license;
        }

        // License endpoint might not be ready in IT so we retry with exponential wait time up to 1 minute
        try {
            return await()
                    .atMost(Duration.ofMinutes(1))
                    .pollInterval(ExponentialBackoffPollInterval.exponential(Duration.ofSeconds(1), Duration.ofSeconds(10)))
                    .until(() -> {
                        try {
                            return getLicenseInternal();
                        } catch (NotFoundException e) {
                            logger.warn("License endpoint is not ready yet. Retrying...");
                            return null;
                        }
                    }, Objects::nonNull);
        } catch (ConditionTimeoutException e) {
            throw new ElasticsearchClientException("License endpoint is not ready after 1 minute");
        }
    }

    private String getLicenseInternal() throws ElasticsearchClientException {
        logger.trace("get license");
        String response = httpGet("_license");

        // We parse the response
        DocumentContext document = parseJsonAsDocumentContext(response);
        // Cache the license level
        license = document.read("$.license.type");

        logger.debug("get license returns {}", license);
        return license;
    }

    @Override
    public int getMajorVersion() {
        return majorVersion;
    }

    @Override
    public void pushComponentTemplate(String name, String json) throws ElasticsearchClientException {
        logger.debug("push component template [{}]", name);
        String url = "_component_template/" + name;
        logger.trace("component template: [{}]", json);
        try {
            httpPut(url, json);
        } catch (WebApplicationException e) {
            throw new ElasticsearchClientException("Error while creating component template " + name, e);
        }
    }

    @Override
    public void pushIndexTemplate(String name, String json) throws ElasticsearchClientException {
        logger.debug("push index template [{}]", name);
        String url = "_index_template/" + name;
        logger.trace("index template: [{}]", json);
        try {
            httpPut(url, json);
        } catch (WebApplicationException e) {
            String errorMessage = e.getResponse().readEntity(String.class);
            logger.error("Error while creating index template [{}]: {}", name, errorMessage);
            logger.error("PUT {}\n{}", url, json);
            throw new ElasticsearchClientException("Error while creating index template " + name +
                    ". Response is: " + errorMessage, e);
        }
    }

    /**
     * Check if an index exists
     * @param index index name
     * @return true if the index exists, false otherwise
     */
    @Override
    public boolean isExistingIndex(String index) throws ElasticsearchClientException {
        logger.debug("is existing index [{}]", index);
        try {
            httpGet(index);
            logger.debug("Index [{}] was found", index);
            return true;
        } catch (NotFoundException e) {
            logger.debug("Index [{}] was not found", index);
            return false;
        }
    }

    /**
     * Check if a pipeline exists
     * @param pipelineName pipeline name
     * @return true if the pipeline exists, false otherwise
     */
    @Override
    public boolean isExistingPipeline(String pipelineName) throws ElasticsearchClientException {
        logger.debug("is existing pipeline [{}]", pipelineName);
        try {
            httpGet("_ingest/pipeline/" + pipelineName);
            logger.debug("Pipeline [{}] was found", pipelineName);
            return true;
        } catch (NotFoundException e) {
            logger.debug("Pipeline [{}] was not found", pipelineName);
            return false;
        }
    }

    /**
     * Check if a component template exists
     * @param templateName component template name
     * @return true if the component template exists, false otherwise
     */
    @Override
    public boolean isExistingComponentTemplate(String templateName) throws ElasticsearchClientException {
        logger.debug("is existing component template [{}]", templateName);
        try {
            httpGet("_component_template/" + templateName);
            logger.debug("Component template [{}] was found", templateName);
            return true;
        } catch (NotFoundException e) {
            logger.debug("Component template [{}] was not found", templateName);
            return false;
        }
    }

    /**
     * Refresh an index (only used in tests)
     * @param index index name
     * @throws ElasticsearchClientException In case of error
     */
    @Override
    public void refresh(String index) throws ElasticsearchClientException {
        if (serverless) {
            logger.debug("Skipping refresh on serverless");
            return;
        }

        logger.debug("refresh index [{}]", index);
        String url = index + "/_refresh";
        if (index == null) {
            url = "_refresh";
        }
        String response = httpPost(url, null);
        DocumentContext document = parseJsonAsDocumentContext(response);
        int shardsFailed = document.read("$._shards.failed");
        if (shardsFailed > 0) {
            throw new ElasticsearchClientException("Unable to refresh index " + index + " : " + response);
        }
    }

    /**
     * Wait for an index to become at least yellow (all primaries assigned), up to 10 seconds
     * @param index index name
     */
    @Override
    public void waitForHealthyIndex(String index) throws ElasticsearchClientException {
        AtomicReference<Exception> errorWhileWaiting = new AtomicReference<>();
        try {
            await()
                    .atMost(10, SECONDS)
                    .pollInterval(ExponentialBackoffPollInterval.exponential(Duration.ofMillis(500), Duration.ofSeconds(5)))
                    .until(() -> {
                try {
                    String health = catIndicesHealth(index);
                    errorWhileWaiting.set(null);
                    return "green".equals(health) || "yellow".equals(health);
                } catch (Exception e) {
                    errorWhileWaiting.set(e);
                    return false;
                }
            });
        } catch (ConditionTimeoutException e) {
            // If we caught an exception during waiting, throw it instead of the timeout
            if (errorWhileWaiting.get() != null) {
                Exception cause = errorWhileWaiting.get();
                throw new ElasticsearchClientException("Error while waiting for healthy index [" +
                        index + "]", cause);
            }
            logger.warn("Index [{}] did not become healthy within 10 seconds", index);
        }
    }

    private String catIndicesHealth(String index) {
        try {
            String response = httpGet("_cat/indices/" + index,
                    new AbstractMap.SimpleImmutableEntry<>("h", "health"));
            DocumentContext document = parseJsonAsDocumentContext(response);
            String health = document.read("$[0].health");
            logger.trace("index [{}] health: [{}]", index, health);
            return health;
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                logger.debug("Index [{}] not found yet", index);
                return null;
            }
            throw e;
        } catch (ElasticsearchClientException e) {
            throw new RuntimeException(e);
        }
    }

    private static final TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
        @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
        @Override public X509Certificate[] getAcceptedIssuers() { return null; }
    }};

    public static class NullHostNameVerifier implements HostnameVerifier {
        @Override public boolean verify(String urlHostName, SSLSession session) {
            if (!urlHostName.equalsIgnoreCase(session.getPeerHost())) {
                logger.warn("URL host [{}] is different to SSLSession host [{}].", urlHostName, session.getPeerHost());
            }
            return true;
        }
    }

    @Override
    public void index(String index, String id, Doc doc, String pipeline) {
        String json = serialize(doc);
        indexRawJson(index, id, json, pipeline);
    }

    @Override
    public void indexRawJson(String index, String id, String json, String pipeline) {
        logger.trace("JSon indexed : {}", json);
        bulkProcessor.add(new ElasticsearchIndexOperation(index, id, pipeline, json));
    }

    @Override
    public void indexSingle(String index, String id, String json, String pipeline) throws ElasticsearchClientException {
        logger.trace("JSon indexed : {}", json);
        String url = index + "/_doc/" + id;
        if (!isNullOrEmpty(pipeline)) {
            url += "?pipeline=" + pipeline;
        }
        httpPut(url, json);
    }

    @Override
    public void delete(String index, String id) {
        bulkProcessor.add(new ElasticsearchDeleteOperation(index, id));
    }

    @Override
    public void deleteSingle(String index, String id) throws ElasticsearchClientException {
        logger.debug("Removing document : {}/{}", index, id);
        try {
            String response = httpDelete(index + "/_doc/" + id, null);

            DocumentContext document = parseJsonAsDocumentContext(response);
            String result = document.read("$.result");
            if (!result.equals("deleted")) {
                throw new ElasticsearchClientException("Can not remove document " + index + "/" + id + " cause: " + response);
            }

            logger.debug("Document {}/{} has been removed", index, id);
        } catch (NotFoundException e) {
            logger.debug("Document {}/{} does not exist. It can't be removed.", index, id);
            throw new ElasticsearchClientException("Document " + index + "/" + id + " does not exist");
        }
    }

    @Override
    public void close() throws IOException {
        logger.debug("Closing Elasticsearch client manager");
        if (bulkProcessor != null) {
            bulkProcessor.close();
        }
        if (client != null) {
            client.close();
        }
    }

    @Override
    public void createIndexAndComponentTemplates() throws Exception {
        if (settings.getElasticsearch().isPushTemplates()) {
            List<String> componentTemplates = new ArrayList<>();

            logger.debug("Creating/updating component templates for [{}]", settings.getElasticsearch().getIndex());
            componentTemplates.add(loadAndPushComponentTemplate(majorVersion, "fscrawler_alias", settings.getElasticsearch().getIndex(), settings.getName()));
            componentTemplates.add(loadAndPushComponentTemplate(majorVersion, "fscrawler_settings_total_fields", settings.getElasticsearch().getIndex()));
            componentTemplates.add(loadAndPushComponentTemplate(majorVersion, "fscrawler_mapping_attributes", settings.getElasticsearch().getIndex()));
            componentTemplates.add(loadAndPushComponentTemplate(majorVersion, "fscrawler_mapping_file", settings.getElasticsearch().getIndex()));
            componentTemplates.add(loadAndPushComponentTemplate(majorVersion, "fscrawler_mapping_path", settings.getElasticsearch().getIndex()));
            componentTemplates.add(loadAndPushComponentTemplate(majorVersion, "fscrawler_mapping_attachment", settings.getElasticsearch().getIndex()));
            if (semanticSearch) {
                componentTemplates.add(loadAndPushComponentTemplate(majorVersion, "fscrawler_mapping_content_semantic", settings.getElasticsearch().getIndex()));
            } else {
                componentTemplates.add(loadAndPushComponentTemplate(majorVersion, "fscrawler_mapping_content", settings.getElasticsearch().getIndex()));
            }
            componentTemplates.add(loadAndPushComponentTemplate(majorVersion, "fscrawler_mapping_meta", settings.getElasticsearch().getIndex()));

            // Wait for all component templates to be available before creating index templates
            waitForComponentTemplates(componentTemplates);

            logger.debug("Creating/updating index templates for [{}]", settings.getElasticsearch().getIndex());
            // If needed, we create the new settings for this files index
            if (!settings.getFs().isAddAsInnerObject() || (!settings.getFs().isJsonSupport() && !settings.getFs().isXmlSupport())) {
                if (semanticSearch) {
                    loadAndPushIndexTemplate(majorVersion, "fscrawler_docs_semantic", settings.getElasticsearch().getIndex());
                } else {
                    loadAndPushIndexTemplate(majorVersion, "fscrawler_docs", settings.getElasticsearch().getIndex());
                }
            }

            // If needed, we create the component and index templates for the folder index
            if (settings.getFs().isIndexFolders()) {
                List<String> folderComponentTemplates = new ArrayList<>();

                logger.debug("Creating/updating component templates for [{}]", settings.getElasticsearch().getIndexFolder());
                folderComponentTemplates.add(loadAndPushComponentTemplate(majorVersion, "fscrawler_mapping_attributes", settings.getElasticsearch().getIndexFolder()));
                folderComponentTemplates.add(loadAndPushComponentTemplate(majorVersion, "fscrawler_mapping_file", settings.getElasticsearch().getIndexFolder()));
                folderComponentTemplates.add(loadAndPushComponentTemplate(majorVersion, "fscrawler_mapping_path", settings.getElasticsearch().getIndexFolder()));

                // Wait for all folder component templates to be available before creating index template
                waitForComponentTemplates(folderComponentTemplates);

                logger.debug("Creating/updating index templates for [{}]", settings.getElasticsearch().getIndexFolder());
                loadAndPushIndexTemplate(majorVersion, "fscrawler_folders", settings.getElasticsearch().getIndexFolder());
            }
        }
    }

    /**
     * Wait for all component templates to be available
     * @param templateNames list of component template names to wait for
     */
    private void waitForComponentTemplates(List<String> templateNames) {
        logger.debug("Waiting for component templates to be available: {}", templateNames);
        for (String templateName : templateNames) {
            try {
                await()
                        .atMost(30, SECONDS)
                        .pollInterval(ExponentialBackoffPollInterval.exponential(Duration.ofMillis(100), Duration.ofSeconds(5)))
                        .until(() -> {
                            try {
                                return isExistingComponentTemplate(templateName);
                            } catch (Exception e) {
                                logger.debug("Error while checking component template [{}]: {}", templateName, e.getMessage());
                                return false;
                            }
                        });
                logger.debug("Component template [{}] is now available", templateName);
            } catch (ConditionTimeoutException e) {
                logger.warn("Component template [{}] did not become available within 30 seconds", templateName);
            }
        }
    }

    private String loadAndPushComponentTemplate(int version, String name, String index) throws IOException, ElasticsearchClientException {
        logger.trace("Loading component template [{}]", name);
        String json = loadResourceFile(version + "/_component_templates/" + name + ".json");
        String componentTemplateName = name.replace("fscrawler_", "fscrawler_" + index + "_");
        pushComponentTemplate(componentTemplateName, json);
        return componentTemplateName;
    }

    private String loadAndPushComponentTemplate(int version, String name, String index, String alias) throws IOException, ElasticsearchClientException {
        logger.trace("Loading component template [{}]", name);
        String json = loadResourceFile(version + "/_component_templates/" + name + ".json");

        // We need to replace the placeholder values
        json = json.replace("ALIAS", alias != null ? alias : "fscrawler");

        String componentTemplateName = name.replace("fscrawler_", "fscrawler_" + index + "_");
        pushComponentTemplate(componentTemplateName, json);
        return componentTemplateName;
    }

    private void loadAndPushIndexTemplate(int version, String name, String index) throws IOException, ElasticsearchClientException {
        logger.trace("Loading index template [{}]", name);
        String json = loadResourceFile(version + "/_index_templates/" + name + ".json");

        // We need to replace the placeholder values
        json = json.replaceAll("INDEX_NAME", index);

        String indexTemplateName = name.replace("fscrawler_", "fscrawler_" + index + "_");
        pushIndexTemplate(indexTemplateName, json);
    }

    /**
     * Reads a resource file from the classpath or from a JAR.
     * @param source The target
     * @return The content of the file
     */
    private static String loadResourceFile(String source) throws IOException {
        try (BufferedReader buffer = new BufferedReader(new InputStreamReader(
                ElasticsearchClient.class.getResourceAsStream(source)))) {
            return StreamSupport.stream(buffer.lines().spliterator(), false)
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse(null);
        }
    }

    @Override
    public ESSearchResponse search(ESSearchRequest request) throws ElasticsearchClientException {

        String url = "";

        if (!isNullOrEmpty(request.getIndex())) {
            url += request.getIndex();
        }

        url += "/_search";

        logger.debug("searching index [{}]", request.getIndex());

        final AtomicReference<String> body = new AtomicReference<>("{");

        boolean bodyEmpty = true;

        int size = 10;
        if (request.getSize() != null) {
            size = request.getSize();
            body.getAndUpdate(s -> s + ("\"size\":" + request.getSize()));
            bodyEmpty = false;
        }
        if (!request.getStoredFields().isEmpty()) {
            if (!bodyEmpty) {
                body.getAndUpdate(s -> s + ",");
            }
            body.getAndUpdate(s -> s + "\"stored_fields\" : [");

            AtomicBoolean moreFields = new AtomicBoolean(false);
            request.getStoredFields().forEach(f -> {
                if (moreFields.getAndSet(true)) {
                    body.getAndUpdate(s -> s + ",");
                }
                body.getAndUpdate(s -> s + ("\"" + f + "\""));
            });
            body.getAndUpdate(s -> s + "]");
            bodyEmpty = false;
        }
        if (request.getESQuery() != null) {
            if (!bodyEmpty) {
                body.getAndUpdate(s -> s + ",");
            }
            body.getAndUpdate(s -> s + ("\"query\" : {" + toElasticsearchQuery(request.getESQuery()) + "}"));
            bodyEmpty = false;
        }
        if (!isNullOrEmpty(request.getSort())) {
            if (!bodyEmpty) {
                body.getAndUpdate(s -> s + ",");
            }
            body.getAndUpdate(s -> s + ("\"sort\" : [\"" + request.getSort() + "\"]"));
            bodyEmpty = false;
        }
        if (!request.getHighlighters().isEmpty()) {
            if (!bodyEmpty) {
                body.getAndUpdate(s -> s + ",");
            }
            body.getAndUpdate(s -> s + "\"highlight\": { \"fields\": {");

            AtomicBoolean moreFields = new AtomicBoolean(false);
            request.getHighlighters().forEach(f -> {
                if (moreFields.getAndSet(true)) {
                    body.getAndUpdate(s -> s + ",");
                }
                body.getAndUpdate(s -> s + ("\"" + f + "\":{}"));
            });
            body.getAndUpdate(s -> s + "}}");
            bodyEmpty = false;
        }
        if (!request.getAggregations().isEmpty()) {
            if (!bodyEmpty) {
                body.getAndUpdate(s -> s + ",");
            }
            body.getAndUpdate(s -> s + "\"aggs\": {");

            AtomicBoolean moreFields = new AtomicBoolean(false);
            request.getAggregations().forEach(a -> {
                if (moreFields.getAndSet(true)) {
                    body.getAndUpdate(s -> s + ",");
                }
                body.getAndUpdate(s -> s + ("\"" + a.getName() + "\":{\"terms\": {\"field\": \"" + a.getField() + "\"}}"));
            });
            body.getAndUpdate(s -> s + "}");
        }

        String query = body.updateAndGet(s -> s + "}");
        logger.trace("Elasticsearch query to run: {}", query);

        try {
            String response = httpPost(url, query, new AbstractMap.SimpleImmutableEntry<>("version", "true"));
            ESSearchResponse esSearchResponse = new ESSearchResponse(response);

            // Parse
            DocumentContext document = parseJsonAsDocumentContext(response);
            esSearchResponse.setTotalHits(document.read("$.hits.total.value"));

            int numHits = document.read("$.hits.hits.length()");
            if (numHits < size) {
                size = numHits;
            }
            for (int hitNum = 0; hitNum < size; hitNum++) {
                final ESSearchHit esSearchHit = new ESSearchHit();
                esSearchHit.setIndex(document.read("$.hits.hits[" + hitNum + "]._index"));
                esSearchHit.setId(document.read("$.hits.hits[" + hitNum + "]._id"));
                esSearchHit.setVersion(Integer.toUnsignedLong(document.read("$.hits.hits[" + hitNum + "]._version")));
                try {
                    esSearchHit.setSource(extractJsonFromPath(document, "$.hits.hits[" + hitNum + "]._source"));
                } catch (PathNotFoundException ignored) {
                    // When no _source, we just ignore
                }

                // Parse the highlights if any
                try {
                    Map<String, List<String>> highlights = document.read("$.hits.hits[" + hitNum + "].highlight");
                    highlights.forEach(esSearchHit::addHighlightField);
                } catch (PathNotFoundException ignored) {
                    // No highlights
                }

                // Parse the fields if any
                try {
                    Map<String, List<String>> fields = document.read("$.hits.hits[" + hitNum + "].fields");
                    esSearchHit.setStoredFields(fields);
                } catch (PathNotFoundException ignored) {
                    // No stored fields
                }
                // hits.hits[].fields":{"foo.bar":["bar"]}}
                esSearchResponse.addHit(esSearchHit);
            }

            // Aggregations
            try {
                Map<String, Object> aggs = document.read("$.aggregations");
                aggs.forEach((aggName, v) -> {
                    ESTermsAggregation aggregation = new ESTermsAggregation(aggName, null);
                    List<Map<String, Object>> buckets = document.read("$.aggregations." + aggName + ".buckets");
                    buckets.forEach((map) -> {
                        String key = (String) map.get("key");
                        long docCount = Integer.toUnsignedLong((Integer) map.get("doc_count"));
                        aggregation.addBucket(new ESTermsAggregation.ESTermsBucket(key, docCount));
                    });
                    esSearchResponse.addAggregation(aggName, aggregation);
                });
            } catch (PathNotFoundException ignored) {
                // No aggregation
            }

            return esSearchResponse;
        } catch (NotFoundException e) {
            logger.debug("index {} does not exist.", request.getIndex());
            throw new ElasticsearchClientException("index " + request.getIndex() + " does not exist.");
        } catch (ServiceUnavailableException e) {
            if (serverless) {
                logger.debug("on serverless this might happen if we just created the index as shards may not be " +
                        "fully allocated for index [{}].", request.getIndex());
                throw new ElasticsearchClientException("index " + request.getIndex() + " might not be fully allocated on serverless.");
            }
            logger.error("search on index [{}] thrown a [{}] error but we are not on serverless.",
                    request.getIndex(), e.getResponse().getStatus());
            logger.error("full stack trace", e);
            throw e;
        }
    }

    private String toElasticsearchQuery(ESQuery query) {
        if (query instanceof ESTermQuery esQuery) {
            return "\"term\": { \"" + esQuery.getField() +  "\": \"" + esQuery.getValue() + "\"}";
        }
        if (query instanceof ESMatchQuery esQuery) {
            return "\"match\": { \"" + esQuery.getField() +  "\": \"" + esQuery.getValue() + "\"}";
        }
        if (query instanceof ESSemanticQuery esQuery) {
            return "\"semantic\": { \"field\":\"" + esQuery.getField() +  "\", \"query\":\"" + esQuery.getValue() + "\"}";
        }
        if (query instanceof ESPrefixQuery esQuery) {
            return "\"prefix\": { \"" + esQuery.getField() +  "\": \"" + esQuery.getValue() + "\"}";
        }
        if (query instanceof ESRangeQuery esQuery) {
            String localQuery = "\"range\": { \"" + esQuery.getField() + "\": {";
            if (esQuery.getGte() != null) {
                localQuery += "\"gte\": " + esQuery.getGte();
                if (esQuery.getLt() != null) {
                    localQuery += ",";
                }
            }
            if (esQuery.getLt() != null) {
                localQuery += "\"lt\": " + esQuery.getLt();
            }
            localQuery += "}}";
            return localQuery;
        }
        if (query instanceof ESBoolQuery esQuery) {
            StringBuilder localQuery = new StringBuilder("\"bool\": { \"must\" : [");
            boolean hasClauses = false;
            for (ESQuery clause : esQuery.getMustClauses()) {
                if (hasClauses) {
                    localQuery.append(",");
                }
                localQuery.append("{");
                localQuery.append(toElasticsearchQuery(clause));
                localQuery.append("}");
                hasClauses = true;
            }
            localQuery.append("]}");
            return localQuery.toString();
        }
        throw new IllegalArgumentException("Query " + query.getClass().getSimpleName() + " not implemented yet");
    }

    @Override
    public void deleteIndex(String index) throws ElasticsearchClientException {
        logger.debug("delete index [{}]", index);
        try {
            String response = httpDelete(index, null);
            DocumentContext document = parseJsonAsDocumentContext(response);
            boolean acknowledged = document.read("$.acknowledged");
            if (!acknowledged) {
                throw new ElasticsearchClientException("Can not remove index " + index + " : " + document.read("$.error.reason"));
            }
        } catch (NotFoundException e) {
            logger.debug("Index [{}] was not found", index);
        }
    }

    @Override
    public void flush() {
        bulkProcessor.flush();
    }

    @Override
    public String performLowLevelRequest(String method, String endpoint, String jsonEntity) throws ElasticsearchClientException {
        return httpCall(method, endpoint, jsonEntity);
    }

    @Override
    public ESSearchHit get(String index, String id) throws ElasticsearchClientException {
        logger.debug("get document [{}/{}]", index, id);
        String response = httpGet(index + "/_doc/" + id);

        // Parse the response
        DocumentContext document = parseJsonAsDocumentContext(response);
        ESSearchHit hit = new ESSearchHit();
        hit.setIndex(document.read("$._index"));
        hit.setId(document.read("$._id"));
        hit.setVersion(Integer.toUnsignedLong(document.read("$._version")));
        hit.setSource(extractJsonFromPath(document, "$._source"));
        return hit;
    }

    @Override
    public boolean exists(String index, String id) throws ElasticsearchClientException {
        logger.debug("get document [{}/{}]", index, id);
        try {
            httpHead(index + "/_doc/" + id);
            logger.debug("document [{}/{}] exists", index, id);
            return true;
        } catch (NotFoundException e) {
            logger.debug("document [{}/{}] does not exist", index, id);
            return false;
        }
    }

    @Override
    public String bulk(String ndjson) throws ElasticsearchClientException {
        logger.debug("bulk a ndjson of {} characters", ndjson.length());
        return httpPost("_bulk", ndjson);
    }

    @Override
    public String generateApiKey(String keyName) throws ElasticsearchClientException {
        String request = "{\"name\":\"" + keyName + "\"}";
        logger.debug("delete any existing api key for [{}]", keyName);
        httpDelete("/_security/api_key", request);

        logger.debug("generate an api key for [{}]", keyName);
        String response = httpPost("/_security/api_key", request);

        // Parse the response
        DocumentContext document = parseJsonAsDocumentContext(response);
        String id = document.read("$.id");
        String encodedApiKey = document.read("$.encoded");
        logger.debug("generated key [{}] for [{}]: [{}]", id, keyName, encodedApiKey);
        return encodedApiKey;
    }

    @Override
    public boolean isSemanticSupported() {
        return semanticSearch;
    }

    /**
     * Read a field from a JSON document as a JSON String content
     * @param context   the document context
     * @param path      path to access the field. Like "$.field"
     * @return          the JSON as a String
     * @see fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil#parseJsonAsDocumentContext(String) to get a document context
     */
    private static String extractJsonFromPath(DocumentContext context, String path) {
        Map<String, Object> jsonMap = context.read(path);
        return serialize(jsonMap);
    }

    void httpHead(String path) throws ElasticsearchClientException {
        httpCallWithRetry("HEAD", path, null);
    }

    @SafeVarargs
    final String httpGet(String path, Map.Entry<String, Object>... params) throws ElasticsearchClientException {
        return httpCallWithRetry("GET", path, null, params);
    }

    @SafeVarargs
    final String httpPost(String path, Object data, Map.Entry<String, Object>... params) throws ElasticsearchClientException {
        return httpCall("POST", path, data, params);
    }

    @SuppressWarnings("UnusedReturnValue")
    @SafeVarargs
    final String httpPut(String path, Object data, Map.Entry<String, Object>... params) throws ElasticsearchClientException {
        return httpCall("PUT", path, data, params);
    }

    private String httpDelete(String path, Object data) throws ElasticsearchClientException {
        return httpCall("DELETE", path, data);
    }

    /**
     * Execute an HTTP call with retry logic for GET and HEAD methods.
     * This method will retry the call with exponential backoff when a 5xx server error is received.
     *
     * @param method HTTP method (should be GET or HEAD for retry to be applied)
     * @param path   the path to call
     * @param data   the data to send (should be null for GET/HEAD)
     * @param params optional query parameters
     * @return the response body as a String
     * @throws ElasticsearchClientException if all retries fail or a non-retryable error occurs
     */
    @SafeVarargs
    private String httpCallWithRetry(String method, String path, Object data, Map.Entry<String, Object>... params) throws ElasticsearchClientException {
        AtomicReference<WebApplicationException> lastServerError = new AtomicReference<>();

        try {
            return await()
                    .atMost(RETRY_MAX_DURATION)
                    .pollInterval(ExponentialBackoffPollInterval.exponential(RETRY_INITIAL_DELAY, RETRY_MAX_DELAY))
                    .until(() -> {
                        try {
                            return httpCall(method, path, data, params);
                        } catch (WebApplicationException e) {
                            // Only retry on server errors (5xx)
                            if (e.getResponse().getStatusInfo().getFamily() == Response.Status.Family.SERVER_ERROR) {
                                logger.warn("Server error {} on {} {}. Retrying...",
                                        e.getResponse().getStatus(), method, path == null ? "" : path);
                                lastServerError.set(e);
                                return null;
                            }
                            // Non-server errors (4xx, etc.) should not be retried, rethrow as RuntimeException
                            throw new RuntimeException(e);
                        }
                    }, Objects::nonNull);
        } catch (ConditionTimeoutException e) {
            logger.error("Retries exhausted for {} {} after {}. Last error: {}",
                    method, path == null ? "" : path, RETRY_MAX_DURATION,
                    lastServerError.get() != null ? lastServerError.get().getMessage() : "unknown");
            if (lastServerError.get() != null) {
                throw lastServerError.get();
            }
            throw new ElasticsearchClientException("Retries exhausted for " + method + " " + (path == null ? "" : path), e);
        } catch (RuntimeException e) {
            // Unwrap non-retryable WebApplicationException
            if (e.getCause() instanceof WebApplicationException) {
                throw (WebApplicationException) e.getCause();
            }
            throw e;
        }
    }

    @SafeVarargs
    private String httpCall(String method, String localPath, Object data, Map.Entry<String, Object>... params) throws ElasticsearchClientException {
        String node = getNode();
        String path = localPath;
        if (settings.getElasticsearch().getPathPrefix() != null) {
            path = settings.getElasticsearch().getPathPrefix() + "/" + localPath;
        }
        logger.trace("Calling {} {}/{} with params {}", method, node, path == null ? "" : path, params);
        try {
            Invocation.Builder callBuilder = prepareHttpCall(node, path, params);
            if (data == null) {
                String response = callBuilder.method(method, String.class);
                logger.trace("{} {}/{} gives {}", method, node, path == null ? "" : path, response);
                return response;
            }
            String response = callBuilder.method(method, Entity.json(data), String.class);
            logger.trace("{} {}/{} gives {}", method, node, path == null ? "" : path, response);
            return response;
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatusInfo().getFamily() == Response.Status.Family.SERVER_ERROR) {
                logger.error("Error on server side. {} -> {} / {}",
                        e.getResponse().getStatus(),
                        e.getResponse().getStatusInfo().getReasonPhrase(),
                        e.getResponse().readEntity(String.class));
            } else {
                logger.debug("Error while running {} {}/{}: {}", method, node, path == null ? "" : path, e.getResponse().readEntity(String.class));
            }
            throw e;
        } catch (ProcessingException e) {
            if (e.getCause() instanceof ConnectException && initialHosts.size() > 1) {
                // Test with non-existing nodes.
                logger.warn("We can not connect to {}. Let's try to find another one if available.", node);
                // Remove the node from the list and try again
                removeNode();
                return httpCall(method, path, data, params);
            } else {
                throw new ElasticsearchClientException("Can not execute " + method + " " +
                        node + "/" + (path == null ? "" : path) + " : " +
                        e.getCause().getMessage(), e);
            }
        }
    }

    /**
     * Choose a node
     * @return the selected node
     * @throws ElasticsearchClientException if no node is running
     */
    private synchronized String getNode() throws ElasticsearchClientException {
        reloadNodesIfNeeded();
        if (hosts.isEmpty()) {
            // We don't have any running node available
            // TODO Our last chance is to try to ping again the original list of nodes
            throw new ElasticsearchClientException("All nodes are failing. You need to check your configuration and " +
                    "your Elasticsearch cluster which should be running at " + initialHosts);
        }

        if (hosts.size() > 1) {
            ++currentNode;
            // Optimization. If we have only one node, there's no need to compute anything
            if (currentNode >= hosts.size()) {
                currentNode = 0;
            }

            String node = hosts.get(currentNode);
            logger.debug("More than one node is available so we pick node number {} from {}: {}.", currentNode, hosts, node);
            return node;
        }

        // We have only one node. We just return it.
        return hosts.get(0);
    }

    private void reloadNodesIfNeeded() {
        currentRun++;
        if (hosts.size() != initialHosts.size() && currentRun >= CHECK_NODES_EVERY) {
            // We don't have the initial list of nodes.
            // Let reintroduce all the nodes
            currentRun = 0;
            currentNode = -1;
            hosts.clear();

            // TODO be a bit smarter and just add nodes that are actually available
            hosts.addAll(initialHosts);

            logger.trace("We are adding back again all the nodes: {}", hosts);
        }
    }

    private synchronized void removeNode() {
        logger.debug("Removing node {}.", hosts.get(currentNode));
        hosts.remove(currentNode);
        logger.trace("List of remaining nodes {}.", hosts);
    }

    private Invocation.Builder prepareHttpCall(String node, String path, Map.Entry<String, Object>[] params) {
        WebTarget target = client.target(node);
        if (path != null) {
            target = target.path(path);
        }
        for (Map.Entry<String, Object> param : params) {
            target = target.queryParam(param.getKey(), param.getValue());
        }

        Invocation.Builder builder = target
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_TYPE, "application/json");
        builder.header(HttpHeaders.USER_AGENT, USER_AGENT);
        if (authorizationHeader != null) {
            builder.header(HttpHeaders.AUTHORIZATION, authorizationHeader);
        }

        return builder;
    }
}
