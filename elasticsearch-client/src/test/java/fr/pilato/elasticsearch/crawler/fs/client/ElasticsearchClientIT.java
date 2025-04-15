package fr.pilato.elasticsearch.crawler.fs.client;

import com.carrotsearch.randomizedtesting.RandomizedTest;
import com.carrotsearch.randomizedtesting.annotations.Nightly;
import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.framework.bulk.FsCrawlerBulkResponse;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.settings.ServerUrl;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ProcessingException;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.BeforeClass;
import org.testcontainers.containers.NginxContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.utility.MountableFile;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static com.carrotsearch.randomizedtesting.RandomizedTest.randomAsciiAlphanumOfLength;
import static fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClient.CHECK_NODES_EVERY;
import static fr.pilato.elasticsearch.crawler.fs.framework.Await.awaitBusy;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.INDEX_SUFFIX_FOLDER;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.readPropertiesFromClassLoader;
import static org.apache.commons.lang3.StringUtils.split;
import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assumptions.assumeThat;

public class ElasticsearchClientIT extends AbstractFSCrawlerTestCase {
    private static final Logger logger = LogManager.getLogger();
    private final static String DEFAULT_TEST_CLUSTER_URL = "https://127.0.0.1:9200";
    private final static String DEFAULT_USERNAME = "elastic";
    final static String DEFAULT_PASSWORD = "changeme";
    private static final String DOC_INDEX_NAME = "fscrawler_elasticsearch_client_i_t";
    private static final String FOLDER_INDEX_NAME = DOC_INDEX_NAME + "_folder";
    private static final TestContainerHelper testContainerHelper = new TestContainerHelper();

    private static String testClusterUrl = getSystemProperty("tests.cluster.url", DEFAULT_TEST_CLUSTER_URL);
    private final static boolean testCheckCertificate = getSystemProperty("tests.cluster.check_ssl", true);
    private static final boolean testKeepData = getSystemProperty("tests.leaveTemporary", true);
    protected static String testApiKey = getSystemProperty("tests.cluster.apiKey", null);
    private static String testCaCertificate;
    private static IElasticsearchClient esClient;

    private static final TimeValue MAX_WAIT_FOR_SEARCH = TimeValue.timeValueMinutes(1);
    private static final TimeValue MAX_WAIT_FOR_SEARCH_LONG_TESTS = TimeValue.timeValueMinutes(5);
    private static TimeValue maxWaitForSearch;

    @BeforeClass
    public static void startServices() throws IOException, ElasticsearchClientException {
        logger.debug("Generate settings against [{}] with ssl check [{}]", testClusterUrl, testCheckCertificate);

        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getElasticsearch().setNodes(Collections.singletonList(new ServerUrl(testClusterUrl)));
        fsSettings.getElasticsearch().setSslVerification(testCheckCertificate);
        fsSettings.getElasticsearch().setCaCertificate(testCaCertificate);
        if (testApiKey != null) {
            fsSettings.getElasticsearch().setApiKey(testApiKey);
        } else {
            fsSettings.getElasticsearch().setUsername(DEFAULT_USERNAME);
            fsSettings.getElasticsearch().setPassword(DEFAULT_PASSWORD);
        }

        try {
            esClient = startClient(fsSettings);
        } catch (ElasticsearchClientException e) {
            if (e.getCause() instanceof ProcessingException
                    && e.getCause().getCause() instanceof SSLHandshakeException
                    && fsSettings.getElasticsearch().isSslVerification()
            ) {
                logger.fatal("❌ SSL check is on but you are probably using a self-signed certificate on [{}]." +
                                " You can bypass this SSL check using -Dtests.cluster.check_ssl=false",
                        fsSettings.getElasticsearch().getNodes().get(0).getUrl());
                throw e;
            }

            if (!DEFAULT_TEST_CLUSTER_URL.equals(testClusterUrl)) {
                logger.fatal("❌ Can not connect to Elasticsearch on [{}] with ssl checks [{}]. You can " +
                                "disable it using -Dtests.cluster.check_ssl=false",
                        testClusterUrl, testCheckCertificate);
                throw e;
            }
            if (testContainerHelper.isStarted()) {
                logger.fatal("❌ Elasticsearch TestContainer was previously started but we can not connect to it " +
                                "on [{}] with ssl checks [{}].",
                        testClusterUrl, testCheckCertificate);
                logger.fatal("Full error:", e);
                throw e;
            }

            logger.debug("Elasticsearch is not running on [{}]. We switch to TestContainer.", testClusterUrl);
            testClusterUrl = testContainerHelper.startElasticsearch(testKeepData);
            // Write the Ca Certificate on disk if exists (with versions < 8, no self-signed certificate)
            if (testContainerHelper.getCertAsBytes() != null) {
                Path clusterCaCrtPath = rootTmpDir.resolve("cluster-ca.crt");
                Files.write(clusterCaCrtPath, testContainerHelper.getCertAsBytes());
                testCaCertificate = clusterCaCrtPath.toAbsolutePath().toString();
            } else {
                testCaCertificate = null;
            }
            fsSettings.getElasticsearch().setNodes(List.of(new ServerUrl(testClusterUrl)));
            fsSettings.getElasticsearch().setSslVerification(testCaCertificate != null);
            fsSettings.getElasticsearch().setCaCertificate(testCaCertificate);
            esClient = startClient(fsSettings);
        }

        assumeThat(esClient)
                .as("Integration tests are skipped because we have not been able to find an Elasticsearch cluster")
                .isNotNull();

        // If the Api Key is not provided, we want to generate it and use in all the tests
        if (testApiKey == null) {
            // Generate the Api-Key
            testApiKey = esClient.generateApiKey("fscrawler-" + randomAsciiAlphanumOfLength(10));

            fsSettings.getElasticsearch().setApiKey(testApiKey);
            fsSettings.getElasticsearch().setUsername(null);
            fsSettings.getElasticsearch().setPassword(null);

            // Close the previous client
            esClient.close();

            // Start a new client with the Api Key
            esClient = startClient(fsSettings);
        }

        String version = esClient.getVersion();
        logger.info("✅ Starting integration tests against an external cluster running elasticsearch [{}]", version);

        if (esClient.isSemanticSupported()) {
            logger.info("Semantic search is supported on this cluster. We will give {} to run the tests.", MAX_WAIT_FOR_SEARCH_LONG_TESTS);
            maxWaitForSearch = MAX_WAIT_FOR_SEARCH_LONG_TESTS;
        } else {
            logger.info("Semantic search is supported on this cluster. We will give {} to run the tests.", MAX_WAIT_FOR_SEARCH);
            maxWaitForSearch = MAX_WAIT_FOR_SEARCH;
        }
    }

    private static ElasticsearchClient startClient(FsSettings fsSettings) throws ElasticsearchClientException {
        logger.debug("Starting a client against [{}] with [{}] as a CA certificate and ssl check [{}]",
                fsSettings.getElasticsearch().getNodes().get(0).getUrl(),
                fsSettings.getElasticsearch().getCaCertificate(),
                fsSettings.getElasticsearch().isSslVerification());
        ElasticsearchClient client = new ElasticsearchClient(null, fsSettings);
        client.start();
        return client;
    }

    @AfterClass
    public static void stopServices() throws IOException {
        logger.debug("Stopping integration tests against an external cluster");
        if (esClient != null) {
            esClient.close();
            esClient = null;
            logger.debug("Elasticsearch client stopped");
        }
        testCaCertificate = null;
    }

    @Before
    public void cleanExistingIndex() throws ElasticsearchClientException {
        logger.info(" -> Removing existing index [{}*]", getCrawlerName());
        esClient.deleteIndex(getCrawlerName());
        esClient.deleteIndex(getCrawlerName() + INDEX_SUFFIX_FOLDER);
        // Remove existing templates if any
        logger.info(" -> Removing existing templates");
        removeIndexTemplates();
        removeComponentTemplates();
    }

    @Test
    public void deleteIndex() throws ElasticsearchClientException {
        esClient.deleteIndex("does-not-exist-index");
        esClient.createIndex(getCrawlerName(), false, null);
        assertThat(esClient.isExistingIndex(getCrawlerName())).isTrue();
        esClient.deleteIndex(getCrawlerName());
        assertThat(esClient.isExistingIndex(getCrawlerName())).isFalse();
    }

    @Test
    public void waitForHealthyIndex() throws ElasticsearchClientException {
        esClient.createIndex(getCrawlerName(), false, null);
        esClient.waitForHealthyIndex(getCrawlerName());
        try {
            esClient.waitForHealthyIndex("does-not-exist-index");
            fail("We should have raised a ClientErrorException");
        } catch (ClientErrorException e) {
            assertThat(e.getResponse().getStatus()).isEqualTo(404);
        }
    }

    /**
     * We don't need to create indices anymore with ES >= 7
     * @throws ElasticsearchClientException in case of error
     */
    @Test
    @Deprecated
    public void createIndex() throws ElasticsearchClientException {
        esClient.createIndex(getCrawlerName(), false, null);
        boolean exists = esClient.isExistingIndex(getCrawlerName());
        assertThat(exists).isTrue();
    }

    /**
     * We don't need to create indices anymore with ES >= 7
     * @throws ElasticsearchClientException in case of error
     */
    @Test
    @Deprecated
    public void createIndexWithSettings() throws ElasticsearchClientException {
        esClient.createIndex(getCrawlerName(), false, "{\n" +
                "  \"settings\": {\n" +
                "    \"refresh_interval\": \"5s\"\n" +
                "  }\n" +
                "}\n");
        boolean exists = esClient.isExistingIndex(getCrawlerName());
        assertThat(exists).isTrue();
    }

    @Test
    public void refresh() throws ElasticsearchClientException {
        esClient.createIndex(getCrawlerName(), false, null);
        assertThatNoException().isThrownBy(() -> esClient.refresh(getCrawlerName()));
    }

    /**
     * We don't need to create indices anymore with ES >= 7
     * @throws ElasticsearchClientException in case of error
     */
    @Test
    @Deprecated
    public void createIndexAlreadyExistsShouldFail() throws ElasticsearchClientException {
        esClient.createIndex(getCrawlerName(), false, null);
        esClient.waitForHealthyIndex(getCrawlerName());
        assertThatExceptionOfType(ElasticsearchClientException.class)
                .isThrownBy(() -> esClient.createIndex(getCrawlerName(), false, null))
                .withMessageContaining("already exists");
    }

    /**
     * We don't need to create indices anymore with ES >= 7
     * @throws ElasticsearchClientException in case of error
     */
    @Test
    @Deprecated
    public void createIndexAlreadyExistsShouldBeIgnored() throws ElasticsearchClientException {
        esClient.createIndex(getCrawlerName(), false, null);
        esClient.waitForHealthyIndex(getCrawlerName());
        assertThatNoException()
                .isThrownBy(() -> esClient.createIndex(getCrawlerName(), true, null));
    }

    /**
     * We don't need to create indices anymore with ES >= 7
     */
    @Test
    @Deprecated
    public void createIndexWithErrors() {
        try {
            esClient.createIndex(getCrawlerName(), false, "{this is wrong}");
            fail("we should reject creation of an already existing index");
        } catch (ElasticsearchClientException e) {
            assertThat(e.getMessage()).contains("error while creating index");
        }
    }

    private void createSearchDataset() throws Exception {
        esClient.createIndex(getCrawlerName(), false, "{\n" +
                "  \"mappings\": {\n" +
                "    \"properties\": {\n" +
                "      \"foo\": {\n" +
                "        \"properties\": {\n" +
                "          \"bar\": {\n" +
                "            \"type\": \"text\",\n" +
                "            \"store\": true,\n" +
                "            \"fields\": {\n" +
                "              \"raw\": { \n" +
                "                \"type\":  \"keyword\"\n" +
                "              }\n" +
                "            }\n" +
                "          }\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}");

        esClient.indexSingle(getCrawlerName(), "1", "{ \"foo\": { \"bar\": \"bar\" } }", null);
        esClient.indexSingle(getCrawlerName(), "2", "{ \"foo\": { \"bar\": \"baz\" } }", null);
        esClient.indexSingle(getCrawlerName(), "3", "{ \"number\": 1 }", null);
        esClient.indexSingle(getCrawlerName(), "4", "{ \"number\": 2 }", null);

        // Wait until we have 4 documents indexed
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 4L);
    }

    @Test
    public void searchMatchAll() throws Exception {
        createSearchDataset();

        ESSearchResponse response = esClient.search(new ESSearchRequest().withIndex(getCrawlerName()));
        assertThat(response.getTotalHits()).isEqualTo(4L);

        for (ESSearchHit hit : response.getHits()) {
            assertThat(hit.getIndex()).isEqualTo(getCrawlerName());
            assertThat(hit.getId()).containsAnyOf("1", "2", "3", "4");
            assertThat(hit.getVersion()).isEqualTo(1L);
            assertThat(hit.getSource()).isNotEmpty();
            assertThat(hit.getHighlightFields()).isEmpty();
            assertThat(hit.getStoredFields()).isNull();
        }
    }

    @Test
    public void searchTerm() throws Exception {
        createSearchDataset();

        ESSearchResponse response = esClient.search(new ESSearchRequest().withIndex(getCrawlerName())
                .withESQuery(new ESTermQuery("foo.bar", "bar")));
        assertThat(response.getTotalHits()).isEqualTo(1L);
        assertThat(response.getHits().get(0).getIndex()).isEqualTo(getCrawlerName());
        assertThat(response.getHits().get(0).getId()).isEqualTo("1");
        assertThat(response.getHits().get(0).getVersion()).isEqualTo(1L);
        assertThat(response.getHits().get(0).getSource()).isNotEmpty();
        assertThat(response.getHits().get(0).getHighlightFields()).isEmpty();
        assertThat(response.getHits().get(0).getStoredFields()).isNull();
    }

    @Test
    public void searchMatch() throws Exception {
        createSearchDataset();

        ESSearchResponse response = esClient.search(new ESSearchRequest().withIndex(getCrawlerName())
                .withESQuery(new ESMatchQuery("foo.bar", "bar")));
        assertThat(response.getTotalHits()).isEqualTo(1L);
        assertThat(response.getHits().get(0).getIndex()).isEqualTo(getCrawlerName());
        assertThat(response.getHits().get(0).getId()).isEqualTo("1");
        assertThat(response.getHits().get(0).getVersion()).isEqualTo(1L);
        assertThat(response.getHits().get(0).getSource()).isNotEmpty();
        assertThat(response.getHits().get(0).getHighlightFields()).isEmpty();
        assertThat(response.getHits().get(0).getStoredFields()).isNull();
    }

    @Test
    public void searchPrefix() throws Exception {
        createSearchDataset();

        ESSearchResponse response = esClient.search(new ESSearchRequest().withIndex(getCrawlerName())
                .withESQuery(new ESPrefixQuery("foo.bar", "ba")));
        assertThat(response.getTotalHits()).isEqualTo(2L);

        for (ESSearchHit hit : response.getHits()) {
            assertThat(hit.getIndex()).isEqualTo(getCrawlerName());
            assertThat(hit.getId()).containsAnyOf("1", "2");
            assertThat(hit.getVersion()).isEqualTo(1L);
            assertThat(hit.getSource()).isNotEmpty();
            assertThat(hit.getHighlightFields()).isEmpty();
            assertThat(hit.getStoredFields()).isNull();
        }
    }

    @Test
    public void searchRangeLowerThan() throws Exception {
        createSearchDataset();

        ESSearchResponse response = esClient.search(new ESSearchRequest().withIndex(getCrawlerName())
                .withESQuery(new ESRangeQuery("number").withLt(2)));
        assertThat(response.getTotalHits()).isEqualTo(1L);
        assertThat(response.getHits().get(0).getIndex()).isEqualTo(getCrawlerName());
        assertThat(response.getHits().get(0).getId()).isEqualTo("3");
        assertThat(response.getHits().get(0).getVersion()).isEqualTo(1L);
        assertThat(response.getHits().get(0).getSource()).isNotEmpty();
        assertThat(response.getHits().get(0).getHighlightFields()).isEmpty();
        assertThat(response.getHits().get(0).getStoredFields()).isNull();
    }

    @Test
    public void searchRangeGreaterOrEqual() throws Exception {
        createSearchDataset();

        ESSearchResponse response = esClient.search(new ESSearchRequest().withIndex(getCrawlerName())
                .withESQuery(new ESRangeQuery("number").withGte(2)));
        assertThat(response.getTotalHits()).isEqualTo(1L);
        assertThat(response.getHits().get(0).getIndex()).isEqualTo(getCrawlerName());
        assertThat(response.getHits().get(0).getId()).isEqualTo("4");
        assertThat(response.getHits().get(0).getVersion()).isEqualTo(1L);
        assertThat(response.getHits().get(0).getSource()).isNotEmpty();
        assertThat(response.getHits().get(0).getHighlightFields()).isEmpty();
        assertThat(response.getHits().get(0).getStoredFields()).isNull();
    }

    @Test
    public void searchBoolWithPrefixAndMatch() throws Exception {
        createSearchDataset();

        ESSearchResponse response = esClient.search(new ESSearchRequest().withIndex(getCrawlerName())
                .withESQuery(new ESBoolQuery()
                        .addMust(new ESPrefixQuery("foo.bar", "ba"))
                        .addMust(new ESMatchQuery("foo.bar", "bar"))
                ));
        assertThat(response.getTotalHits()).isEqualTo(1L);
        assertThat(response.getHits().get(0).getIndex()).isEqualTo(getCrawlerName());
        assertThat(response.getHits().get(0).getId()).isEqualTo("1");
        assertThat(response.getHits().get(0).getVersion()).isEqualTo(1L);
        assertThat(response.getHits().get(0).getSource()).isNotEmpty();
        assertThat(response.getHits().get(0).getHighlightFields()).isEmpty();
        assertThat(response.getHits().get(0).getStoredFields()).isNull();
    }

    @Test
    public void searchHighlighting() throws Exception {
        createSearchDataset();

        ESSearchResponse response = esClient.search(new ESSearchRequest().withIndex(getCrawlerName())
                .withESQuery(new ESMatchQuery("foo.bar", "bar"))
                .addHighlighter("foo.bar")
        );
        assertThat(response.getTotalHits()).isEqualTo(1L);
        assertThat(response.getHits())
                .singleElement()
                .satisfies(hit -> {
                    assertThat(hit.getIndex()).isEqualTo(getCrawlerName());
                    assertThat(hit.getId()).isEqualTo("1");
                    assertThat(hit.getVersion()).isEqualTo(1L);
                    assertThat(hit.getSource()).isNotEmpty();
                    assertThat(hit.getHighlightFields())
                            .hasSize(1)
                            .satisfies(highlight -> {
                                assertThat(highlight)
                                        .containsKey("foo.bar")
                                        .extractingByKey("foo.bar")
                                        .satisfies(highlightField -> assertThat(highlightField)
                                                .singleElement()
                                                .isEqualTo("<em>bar</em>"));
                            });
                    assertThat(hit.getStoredFields()).isNull();
                });
    }

    @Test
    public void searchFields() throws Exception {
        createSearchDataset();

        ESSearchResponse response = esClient.search(new ESSearchRequest().withIndex(getCrawlerName())
                .withESQuery(new ESPrefixQuery("foo.bar", "ba"))
                .addStoredField("foo.bar")
        );
        assertThat(response.getTotalHits()).isEqualTo(2L);

        for (ESSearchHit hit : response.getHits()) {
            assertThat(hit.getIndex()).isEqualTo(getCrawlerName());
            assertThat(hit.getId()).containsAnyOf("1", "2");
            assertThat(hit.getVersion()).isEqualTo(1L);
            assertThat(hit.getSource()).isNullOrEmpty();
            assertThat(hit.getHighlightFields()).isEmpty();
            assertThat(hit.getStoredFields()).isNotNull();
            assertThat(hit.getStoredFields())
                    .extractingByKey("foo.bar")
                    .satisfies(storedField -> assertThat(storedField).containsAnyOf("bar", "baz"));
        }
    }

    @Test
    public void searchFieldsWithSource() throws Exception {
        createSearchDataset();

        ESSearchResponse response = esClient.search(new ESSearchRequest().withIndex(getCrawlerName())
                .withESQuery(new ESPrefixQuery("foo.bar", "ba"))
                .addStoredField("_source")
                .addStoredField("foo.bar")
        );
        assertThat(response.getTotalHits()).isEqualTo(2L);

        for (ESSearchHit hit : response.getHits()) {
            assertThat(hit.getIndex()).isEqualTo(getCrawlerName());
            assertThat(hit.getId()).containsAnyOf("1", "2");
            assertThat(hit.getVersion()).isEqualTo(1L);
            assertThat(hit.getSource()).isNotEmpty();
            assertThat(hit.getHighlightFields()).isEmpty();
            assertThat(hit.getStoredFields()).isNotNull();
            assertThat(hit.getStoredFields())
                    .extractingByKey("foo.bar")
                    .satisfies(storedField -> assertThat(storedField).containsAnyOf("bar", "baz"));
        }
    }

    @Test
    public void searchAggregations() throws Exception {
        createSearchDataset();

        ESSearchResponse response = esClient.search(new ESSearchRequest().withIndex(getCrawlerName())
                .withAggregation(new ESTermsAggregation("foobar1", "foo.bar.raw"))
                .withAggregation(new ESTermsAggregation("foobar2", "foo.bar.raw"))
                .withSize(0)
        );
        assertThat(response.getTotalHits()).isEqualTo(4L);
        assertThat(response.getAggregations()).isNotNull();
        assertThat(response.getAggregations()).hasSize(2);
        assertThat(response.getAggregations()).containsKey("foobar1");
        assertThat(response.getAggregations().get("foobar1").getName()).isEqualTo("foobar1");
        assertThat(response.getAggregations().get("foobar1").getBuckets()).hasSize(2);
        assertThat(response.getAggregations().get("foobar1").getBuckets()).contains(new ESTermsAggregation.ESTermsBucket("bar", 1), new ESTermsAggregation.ESTermsBucket("baz", 1));
        assertThat(response.getAggregations()).containsKey("foobar2");
        assertThat(response.getAggregations().get("foobar2").getName()).isEqualTo("foobar2");
        assertThat(response.getAggregations().get("foobar2").getBuckets()).hasSize(2);
        assertThat(response.getAggregations().get("foobar2").getBuckets()).contains(new ESTermsAggregation.ESTermsBucket("bar", 1), new ESTermsAggregation.ESTermsBucket("baz", 1));
    }

    @Test
    public void findVersion() throws ElasticsearchClientException {
        String version = esClient.getVersion();
        logger.info("Current elasticsearch version: [{}]", version);

        // If we did not use an external URL but the docker instance we can test for sure that the version is the expected one
        if (System.getProperty("tests.cluster.url") == null) {
            Properties properties = readPropertiesFromClassLoader("elasticsearch.version.properties");
            assertThat(version).isEqualTo(properties.getProperty("version"));
        }
    }

    @Test
    public void pipeline() throws ElasticsearchClientException {
        String crawlerName = getCrawlerName();

        // Create an empty ingest pipeline
        String pipeline = "{\n" +
                "  \"description\": \"Testing Grok on PDF upload\",\n" +
                "  \"processors\": [\n" +
                "    {\n" +
                "      \"gsub\": {\n" +
                "        \"field\": \"content\",\n" +
                "        \"pattern\": \"\\n\",\n" +
                "        \"replacement\": \"-\"\n" +
                "      }\n" +
                "    },\n" +
                "    {\n" +
                "      \"grok\": {\n" +
                "        \"field\": \"content\",\n" +
                "        \"patterns\": [\n" +
                "          \"%{DATA}%{IP:ip_addr} %{GREEDYDATA}\"\n" +
                "        ]\n" +
                "      }\n" +
                "    }\n" +
                "  ]\n" +
                "}";
        esClient.performLowLevelRequest("PUT", "/_ingest/pipeline/" + crawlerName, pipeline);

        assertThat(esClient.isExistingPipeline(crawlerName)).isTrue();
        assertThat(esClient.isExistingPipeline(crawlerName + "_foo")).isFalse();
    }

    @Test
    public void bulk() throws Exception {
        {
            long nbItems = RandomizedTest.randomLongBetween(5, 20);

            ElasticsearchBulkRequest bulkRequest = new ElasticsearchBulkRequest();

            // Add some index op
            for (int i = 0; i < nbItems; i++) {
                bulkRequest.add(new ElasticsearchIndexOperation(getCrawlerName(),
                        "" + i,
                        null,
                        "{\"foo\":{\"bar\":\"bar\"},\"number\": " + i + "}"));
            }

            ElasticsearchEngine engine = new ElasticsearchEngine(esClient);
            ElasticsearchBulkResponse bulkResponse = engine.bulk(bulkRequest);
            assertThat(bulkResponse.hasFailures()).isFalse();
            assertThat(bulkResponse.getItems()).isNotEmpty();

            // Wait until we have the expected number of documents indexed
            countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), nbItems);
        }
        {
            esClient.deleteIndex(getCrawlerName());
            long nbItems = RandomizedTest.randomLongBetween(5, 20);
            long nbItemsToDelete = RandomizedTest.randomLongBetween(1, nbItems);

            ElasticsearchBulkRequest bulkRequest = new ElasticsearchBulkRequest();

            // Add some index op
            for (int i = 0; i < nbItems; i++) {
                bulkRequest.add(new ElasticsearchIndexOperation(getCrawlerName(),
                        "" + i,
                        null,
                        "{\"foo\":{\"bar\":\"bar\"},\"number\": " + i + "}"));
            }
            // Add some delete op
            for (int i = 0; i < nbItemsToDelete; i++) {
                bulkRequest.add(new ElasticsearchDeleteOperation(getCrawlerName(), "" + i));
            }

            ElasticsearchEngine engine = new ElasticsearchEngine(esClient);
            ElasticsearchBulkResponse bulkResponse = engine.bulk(bulkRequest);
            assertThat(bulkResponse.hasFailures()).isFalse();
            assertThat(bulkResponse.getItems()).isNotEmpty();

            // Wait until we have the expected number of documents indexed
            countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), nbItems - nbItemsToDelete);
        }
        {
            esClient.deleteIndex(getCrawlerName());
            long nbItems = RandomizedTest.randomLongBetween(5, 20);

            ElasticsearchBulkRequest bulkRequest = new ElasticsearchBulkRequest();

            // Add some index op
            for (int i = 0; i < nbItems; i++) {
                bulkRequest.add(new ElasticsearchIndexOperation(getCrawlerName(),
                        "" + i,
                        null,
                        "{\n" +
                                "          \"foo\" : {\n" +
                                "            \"bar\" : \"baz\"\n" +
                                "          }\n" +
                                "        }"));
            }

            ElasticsearchEngine engine = new ElasticsearchEngine(esClient);
            ElasticsearchBulkResponse bulkResponse = engine.bulk(bulkRequest);
            assertThat(bulkResponse.hasFailures()).isFalse();
            assertThat(bulkResponse.getItems()).isNotEmpty();

            // Wait until we have the expected number of documents indexed
            countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), nbItems);
        }
        {
            esClient.deleteIndex(getCrawlerName());
            String indexSettings = "{\n" +
                    "  \"mappings\": {\n" +
                    "    \"properties\": {\n" +
                    "      \"foo\": {\n" +
                    "        \"properties\": {\n" +
                    "          \"number\": {\n" +
                    "            \"type\": \"long\"\n" +
                    "          }\n" +
                    "        }\n" +
                    "      }\n" +
                    "    }\n" +
                    "  }\n" +
                    "}";

            esClient.createIndex(getCrawlerName(), false, indexSettings);

            long nbItems = RandomizedTest.randomLongBetween(6, 20);

            ElasticsearchBulkRequest bulkRequest = new ElasticsearchBulkRequest();

            // Add some index op
            // Every 5 ops, we had a failing document
            long nbErrors = 0;
            for (int i = 0; i < nbItems; i++) {
                if (i > 0 && i % 5 == 0) {
                    bulkRequest.add(new ElasticsearchIndexOperation(getCrawlerName(),
                            "" + i,
                            null,
                            "{\"foo\":{\"bar\":\"bar\"},\"number\":\"bar\"}"));
                    nbErrors++;
                } else {
                    bulkRequest.add(new ElasticsearchIndexOperation(getCrawlerName(),
                            "" + i,
                            null,
                            "{\"foo\":{\"bar\":\"bar\"},\"number\": " + i + "}"));
                }
            }

            ElasticsearchEngine engine = new ElasticsearchEngine(esClient);
            ElasticsearchBulkResponse bulkResponse = engine.bulk(bulkRequest);
            assertThat(bulkResponse.hasFailures()).isTrue();
            assertThat(bulkResponse.getItems()).isNotEmpty();
            long errors = bulkResponse.getItems().stream().filter(FsCrawlerBulkResponse.BulkItemResponse::isFailed).count();
            assertThat(errors).isEqualTo(nbErrors);

            // Wait until we have the expected number of documents indexed
            countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), nbItems - nbErrors);
        }
    }

    @Test
    public void deleteSingle() throws Exception {
        esClient.indexSingle(getCrawlerName(), "1", "{ \"foo\": { \"bar\": \"bar\" } }", null);
        esClient.indexSingle(getCrawlerName(), "2", "{ \"foo\": { \"bar\": \"baz\" } }", null);
        esClient.indexSingle(getCrawlerName(), "3", "{ \"number\": 1 }", null);
        esClient.indexSingle(getCrawlerName(), "4", "{ \"number\": 2 }", null);

        ESSearchResponse response = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 4L);
        assertThat(response.getTotalHits()).isEqualTo(4L);

        esClient.deleteSingle(getCrawlerName(), "1");

        response = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 3L);
        assertThat(response.getTotalHits()).isEqualTo(3L);

        try {
            esClient.deleteSingle(getCrawlerName(), "99999");
            fail("We should have raised an " + ElasticsearchClientException.class.getSimpleName());
        } catch (ElasticsearchClientException e) {
            assertThat(e.getMessage()).isEqualTo("Document " + getCrawlerName() + "/99999 does not exist");
        }

        response = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 3L);
        assertThat(response.getTotalHits()).isEqualTo(3L);
    }

    @Test
    public void exists() throws ElasticsearchClientException {
        esClient.indexSingle(getCrawlerName(), "1", "{ \"foo\": { \"bar\": \"bar\" } }", null);
        esClient.refresh(getCrawlerName());
        assertThat(esClient.exists(getCrawlerName(), "1")).isTrue();
        assertThat(esClient.exists(getCrawlerName(), "999")).isFalse();
    }

    @Test
    public void withOnlyOneRunningNode() throws ElasticsearchClientException, IOException {
        // Build a client with a non-running node
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getElasticsearch().setNodes(List.of(
                new ServerUrl("http://127.0.0.1:9206"),
                new ServerUrl(testClusterUrl)));
        fsSettings.getElasticsearch().setApiKey(testApiKey);
        fsSettings.getElasticsearch().setSslVerification(false);
        try (IElasticsearchClient localClient = new ElasticsearchClient(null, fsSettings)) {
            assertThatNoException().isThrownBy(localClient::start);
            assertThat(localClient.isExistingIndex("foo")).isFalse();
            assertThat(localClient.isExistingIndex("bar")).isFalse();
            assertThat(localClient.isExistingIndex("baz")).isFalse();
        }
    }

    @Test
    public void withTwoRunningNodes() throws ElasticsearchClientException, IOException {
        // Build a client with 2 running nodes (well, the same one is used twice) and one non-running node
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getElasticsearch().setNodes(List.of(
                        new ServerUrl(testClusterUrl),
                        new ServerUrl(testClusterUrl),
                        new ServerUrl("http://127.0.0.1:9206"),
                        new ServerUrl(testClusterUrl)));
        fsSettings.getElasticsearch().setApiKey(testApiKey);
        fsSettings.getElasticsearch().setSslVerification(false);
        fsSettings.getElasticsearch().setIndex(DOC_INDEX_NAME);
        fsSettings.getElasticsearch().setIndexFolder(FOLDER_INDEX_NAME);
        try (IElasticsearchClient localClient = new ElasticsearchClient(null, fsSettings)) {
            localClient.start();
            assertThat(localClient.getAvailableNodes()).hasSize(4);
            localClient.isExistingIndex("foo");
            assertThat(localClient.getAvailableNodes()).hasSize(3);

            for (int i = 0; i < CHECK_NODES_EVERY - 4; i++) {
                localClient.isExistingIndex("foo");
                assertThat(localClient.getAvailableNodes()).as("Run " + i).hasSize(3);
            }

            for (int i = 0; i < 10; i++) {
                localClient.isExistingIndex("foo");
                assertThat(localClient.getAvailableNodes()).hasSize(4);
                localClient.isExistingIndex("foo");
                assertThat(localClient.getAvailableNodes()).as("Run " + i).hasSize(4);
                localClient.isExistingIndex("foo");
                assertThat(localClient.getAvailableNodes()).as("Run " + i).hasSize(3);
                for (int j = 0; j < CHECK_NODES_EVERY - 4; j++) {
                    localClient.isExistingIndex("foo");
                    assertThat(localClient.getAvailableNodes()).as("Run " + i + "-" + j).hasSize(3);
                }
            }
        }
    }

    @Test
    public void withNonRunningNodes() throws IOException {
        // Build a client with a non-running node
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getElasticsearch().setNodes(List.of(
                new ServerUrl("http://127.0.0.1:9206"),
                new ServerUrl("http://127.0.0.1:9207")));
        fsSettings.getElasticsearch().setApiKey(testApiKey);
        fsSettings.getElasticsearch().setSslVerification(false);

        try (IElasticsearchClient localClient = new ElasticsearchClient(null, fsSettings)) {
            assertThatExceptionOfType(ElasticsearchClientException.class)
                    .isThrownBy(localClient::start)
                    .withMessageContaining("All nodes are failing");
        }
    }

    @Test
    public void withNonRunningNode() throws IOException {
        // Build a client with a non-running node
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getElasticsearch().setNodes(List.of(new ServerUrl("http://127.0.0.1:9206")));
        fsSettings.getElasticsearch().setApiKey(testApiKey);
        fsSettings.getElasticsearch().setSslVerification(false);

        try (IElasticsearchClient localClient = new ElasticsearchClient(null, fsSettings)) {
            assertThatExceptionOfType(ElasticsearchClientException.class)
                    .isThrownBy(localClient::start)
                    .withMessageContaining("Can not execute GET")
                    .havingCause()
                    .withCauseInstanceOf(ConnectException.class)
                    .withMessageContaining("Connection refused");
        }
    }

    @Test
    public void securedClusterWithBadCredentials() throws IOException, ElasticsearchClientException {
        // Build a client with a null password
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getElasticsearch().setNodes(List.of(new ServerUrl(testClusterUrl)));
        fsSettings.getElasticsearch().setSslVerification(false);

        try (IElasticsearchClient localClient = new ElasticsearchClient(null, fsSettings)) {
            localClient.start();
            fail("We should have raised a " + ElasticsearchClientException.class.getSimpleName());
        } catch (NotAuthorizedException ex) {
            assertThat(ex.getMessage()).contains("HTTP 401 Unauthorized");
        }
    }

    @Test
    public void createApiKey() {
        // This is not a critical one as this code is only used in tests
        try {
            String key = esClient.generateApiKey("fscrawler-es-client-test");
            assertThat(key).isNotNull();
        } catch (Exception e) {
            // creating derived api keys requires an explicit role descriptor that is empty (has no privileges)
            logger.warn("Can not create an API Key. " +
                    "This is not a critical one as this code is only used in tests. So we skip it.", e);
        }
    }

    @Test
    public void withHttpService() throws IOException, ElasticsearchClientException {
        logger.debug("Starting Nginx from {}", rootTmpDir);

        // First we call Elasticsearch client
        assertThat(esClient.getVersion()).isNotEmpty();

        Path nginxRoot = rootTmpDir.resolve("nginx-root");
        Files.createDirectory(nginxRoot);
        Files.writeString(nginxRoot.resolve("index.html"), "<html><body>Hello World!</body></html>");

        try (NginxContainer<?> container = new NginxContainer<>("nginx")) {
            container.waitingFor(new HttpWaitStrategy());
            container.start();
            container.copyFileToContainer(MountableFile.forHostPath(nginxRoot), "/usr/share/nginx/html");
            URL url = container.getBaseUrl("http", 80);
            logger.debug("Nginx started on {}.", url);

            InputStream inputStream = url.openStream();
            String text = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            assertThat(text).contains("Hello World!");
        }

        // Then we call Elasticsearch client again
        assertThat(esClient.getVersion()).isNotEmpty();
    }

    @Test
    public void license() throws ElasticsearchClientException {
        String license = esClient.getLicense();
        assertThat(license).isNotEmpty();
    }

    @Nightly("This test is only run in nightly builds as semantic search could take a long time")
    @Test
    public void indexFsCrawlerDocuments() throws Exception {
        // We push the templates to the cluster
        esClient.createIndexAndComponentTemplates();

        // We remove the exising indices
        esClient.deleteIndex(DOC_INDEX_NAME);
        esClient.deleteIndex(FOLDER_INDEX_NAME);

        // We create a document
        esClient.index(DOC_INDEX_NAME, "BackToTheFuture", new Doc("Marty! Let's go back to the future!"), null);
        esClient.index(DOC_INDEX_NAME, "StarWars", new Doc("Luke. Obiwan never told you what happened to your father. I'm your father!"), null);
        esClient.index(DOC_INDEX_NAME, "TheLordOfTheRings", new Doc("You cannot pass! I am a servant of the Secret Fire, wielder of the Flame of Anor. The dark fire will not avail you, Flame of Udun! Go back to the shadow. You shall not pass!"), null);

        // We flush the bulk request
        esClient.flush();

        // Wait until we have the expected number of documents indexed
        countTestHelper(new ESSearchRequest().withIndex(DOC_INDEX_NAME), 3L);

        // We can run some queries to check that semantic search actually works as expected
        assertThat(esClient.search(new ESSearchRequest()
                .withIndex(DOC_INDEX_NAME)
                .withESQuery(new ESMatchQuery("content", "father"))
        ).getHits().get(0).getId()).isEqualTo("StarWars");
        assertThat(esClient.search(new ESSearchRequest()
                .withIndex(DOC_INDEX_NAME)
                .withESQuery(new ESMatchQuery("content", "future"))
        ).getHits().get(0).getId()).isEqualTo("BackToTheFuture");
        assertThat(esClient.search(new ESSearchRequest()
                .withIndex(DOC_INDEX_NAME)
                .withESQuery(new ESMatchQuery("content", "Flame"))
        ).getHits().get(0).getId()).isEqualTo("TheLordOfTheRings");

        // We can only execute this test when semantic search is available
        if (esClient.isSemanticSupported()) {
            // We can run some queries to check that semantic search actually works as expected
            assertThat(esClient.search(new ESSearchRequest()
                    .withIndex(DOC_INDEX_NAME)
                    .withESQuery(new ESSemanticQuery("content_semantic", "a movie from Georges Lucas"))
            ).getHits().get(0).getId()).isEqualTo("StarWars");
            assertThat(esClient.search(new ESSearchRequest()
                    .withIndex(DOC_INDEX_NAME)
                    .withESQuery(new ESSemanticQuery("content_semantic", "a movie with a delorean car"))
            ).getHits().get(0).getId()).isEqualTo("BackToTheFuture");
            assertThat(esClient.search(new ESSearchRequest()
                    .withIndex(DOC_INDEX_NAME)
                    .withESQuery(new ESSemanticQuery("content_semantic", "Frodo and Gollum"))
            ).getHits().get(0).getId()).isEqualTo("TheLordOfTheRings");
        }
    }

    private void removeComponentTemplates() {
        logger.debug("Removing component templates");
        try {
            esClient.performLowLevelRequest("DELETE", "/_component_template/fscrawler_*", null);
        } catch (ElasticsearchClientException | NotFoundException e) {
            // We ignore the error
        }
    }

    private void removeIndexTemplates() {
        logger.debug("Removing index templates");
        try {
            esClient.performLowLevelRequest("DELETE", "/_index_template/fscrawler_*", null);
        } catch (ElasticsearchClientException | NotFoundException e) {
            // We ignore the error
        }
    }

    private String getCrawlerName() {
        String testName = "fscrawler_".concat(getCurrentClassName()).concat("_").concat(getCurrentTestName());
        return testName.contains(" ") ? split(testName, " ")[0] : testName;
    }

    /**
     * Check that we have the expected number of docs or at least one if expected is null
     *
     * @param request   Elasticsearch request to run.
     * @param expected  expected number of docs. Null if at least 1.
     * @return the search response if further tests are needed
     * @throws Exception in case of error
     */
    private static ESSearchResponse countTestHelper(final ESSearchRequest request, final Long expected) throws Exception {
        final ESSearchResponse[] response = new ESSearchResponse[1];

        // We wait before considering a failing test
        logger.info("  ---> Waiting up to {} for {} documents in {}", maxWaitForSearch,
                expected == null ? "some" : expected, request.getIndex());
        long hits = awaitBusy(() -> {
            long totalHits;

            // Let's search for entries
            try {
                // Make sure we refresh indexed docs before counting
                esClient.refresh(request.getIndex());
                response[0] = esClient.search(request);
            } catch (RuntimeException e) {
                logger.warn("error caught", e);
                return -1;
            } catch (ElasticsearchClientException e) {
                // TODO create a NOT FOUND Exception instead
                logger.debug("error caught", e);
                return -1;
            }
            totalHits = response[0].getTotalHits();

            logger.debug("got so far [{}] hits on expected [{}]", totalHits, expected);

            return totalHits;
        }, expected, maxWaitForSearch);

        if (expected == null) {
            assertThat(hits)
                    .as("checking if any document in " + request.getIndex())
                    .isGreaterThan(0);
        } else {
            assertThat(hits)
                    .as("checking documents in " + request.getIndex())
                    .isEqualTo(expected);
        }

        return response[0];
    }
}