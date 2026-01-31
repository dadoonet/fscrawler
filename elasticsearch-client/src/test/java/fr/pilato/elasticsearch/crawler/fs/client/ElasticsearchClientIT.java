package fr.pilato.elasticsearch.crawler.fs.client;

import com.carrotsearch.randomizedtesting.RandomizedTest;
import com.carrotsearch.randomizedtesting.annotations.Nightly;
import com.jayway.jsonpath.DocumentContext;
import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.framework.ExponentialBackoffPollInterval;
import fr.pilato.elasticsearch.crawler.fs.framework.bulk.FsCrawlerBulkResponse;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import fr.pilato.elasticsearch.crawler.fs.test.framework.TestContainerHelper;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.*;
import org.testcontainers.DockerClientFactory;
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
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import java.time.Duration;

import static com.carrotsearch.randomizedtesting.RandomizedTest.randomAsciiAlphanumOfLength;
import static fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClient.CHECK_NODES_EVERY;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.*;
import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.parseJsonAsDocumentContext;
import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.awaitility.Awaitility.await;
import org.awaitility.core.ConditionTimeoutException;
import static org.junit.Assume.assumeTrue;

public class ElasticsearchClientIT extends AbstractFSCrawlerTestCase {
    private static final Logger logger = LogManager.getLogger();
    private static final String DEFAULT_TEST_CLUSTER_URL = "https://127.0.0.1:9200";
    private static final String DEFAULT_USERNAME = "elastic";
    private static final String DOC_INDEX_NAME = "fscrawler_elasticsearch_client_i_t";
    private static final String FOLDER_INDEX_NAME = DOC_INDEX_NAME + "_folder";
    private static final TestContainerHelper TEST_CONTAINER_HELPER = new TestContainerHelper();

    private static String testClusterUrl = getSystemProperty("tests.cluster.url", DEFAULT_TEST_CLUSTER_URL);
    private static final boolean TEST_CHECK_CERTIFICATE = getSystemProperty("tests.cluster.check_ssl", true);
    private static final boolean TEST_KEEP_DATA = getSystemProperty("tests.leaveTemporary", true);
    protected static String testApiKey = getSystemProperty("tests.cluster.apiKey", null);
    private static String testCaCertificate;
    private static ElasticsearchClient esClient;

    private static Duration maxWaitForSearch;

    @BeforeClass
    public static void startServices() throws IOException, ElasticsearchClientException {
        logger.debug("Generate settings against [{}] with ssl check [{}]", testClusterUrl, TEST_CHECK_CERTIFICATE);

        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.setName(DOC_INDEX_NAME);
        fsSettings.getElasticsearch().setIndex(DOC_INDEX_NAME);
        fsSettings.getElasticsearch().setIndexFolder(FOLDER_INDEX_NAME);
        fsSettings.getElasticsearch().setUrls(List.of(testClusterUrl));
        fsSettings.getElasticsearch().setSslVerification(TEST_CHECK_CERTIFICATE);
        fsSettings.getElasticsearch().setCaCertificate(testCaCertificate);
        if (testApiKey != null) {
            fsSettings.getElasticsearch().setApiKey(testApiKey);
        } else {
            fsSettings.getElasticsearch().setUsername(DEFAULT_USERNAME);
            fsSettings.getElasticsearch().setPassword(TestContainerHelper.DEFAULT_PASSWORD);
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
                        fsSettings.getElasticsearch().getUrls().get(0));
                throw e;
            }

            if (!DEFAULT_TEST_CLUSTER_URL.equals(testClusterUrl)) {
                logger.fatal("❌ Can not connect to Elasticsearch on [{}] with ssl checks [{}]. You can " +
                                "disable it using -Dtests.cluster.check_ssl=false",
                        testClusterUrl, TEST_CHECK_CERTIFICATE);
                throw e;
            }
            if (TEST_CONTAINER_HELPER.isStarted()) {
                logger.fatal("❌ Elasticsearch TestContainer was previously started but we can not connect to it " +
                                "on [{}] with ssl checks [{}].",
                        testClusterUrl, TEST_CHECK_CERTIFICATE);
                logger.fatal("Full error:", e);
                throw e;
            }

            logger.debug("Elasticsearch is not running on [{}]. We switch to TestContainer.", testClusterUrl);
            testClusterUrl = TEST_CONTAINER_HELPER.startElasticsearch(TEST_KEEP_DATA);
            // Write the Ca Certificate on disk if exists (with versions < 8, no self-signed certificate)
            if (TEST_CONTAINER_HELPER.getCertAsBytes() != null) {
                Path clusterCaCrtPath = rootTmpDir.resolve("cluster-ca.crt");
                Files.write(clusterCaCrtPath, TEST_CONTAINER_HELPER.getCertAsBytes());
                testCaCertificate = clusterCaCrtPath.toAbsolutePath().toString();
            } else {
                testCaCertificate = null;
            }
            fsSettings.getElasticsearch().setUrls(List.of(testClusterUrl));
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
            maxWaitForSearch = MAX_WAIT_FOR_SEARCH_LONG_TESTS;
            logger.info("Semantic search is supported on this cluster. We will give {} to run the tests.", maxWaitForSearch);
        } else {
            maxWaitForSearch = MAX_WAIT_FOR_SEARCH;
            logger.info("Semantic search is not supported on this cluster. We will give {} to run the tests.", maxWaitForSearch);
        }
    }

    private static ElasticsearchClient startClient(FsSettings fsSettings) throws ElasticsearchClientException {
        logger.debug("Starting a client against [{}] with [{}] as a CA certificate and ssl check [{}]",
                fsSettings.getElasticsearch().getUrls().get(0),
                fsSettings.getElasticsearch().getCaCertificate(),
                fsSettings.getElasticsearch().isSslVerification());
        ElasticsearchClient client = new ElasticsearchClient(fsSettings);
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
        logger.debug(" -> Removing existing index [{}*]", getCrawlerName());
        esClient.deleteIndex(getCrawlerName() + INDEX_SUFFIX_DOCS);
        esClient.deleteIndex(getCrawlerName() + INDEX_SUFFIX_FOLDER);
        // Remove existing templates if any
        logger.debug(" -> Removing existing templates");
        removeTemplatesForIndex(getCrawlerName());
        removeTemplatesForIndex(DOC_INDEX_NAME);

        logger.info("🎬 Starting test [{}]", getCurrentTestName());
    }

    @After
    public void cleanUp() throws ElasticsearchClientException {
        if (!TEST_KEEP_DATA) {
            logger.debug(" -> Removing index [{}*]", getCrawlerName());
            esClient.deleteIndex(getCrawlerName() + INDEX_SUFFIX_DOCS);
            esClient.deleteIndex(getCrawlerName() + INDEX_SUFFIX_FOLDER);
            // Remove existing templates if any
            logger.debug(" -> Removing existing templates");
            removeTemplatesForIndex(getCrawlerName());
            removeTemplatesForIndex(DOC_INDEX_NAME);
        }

        logger.info("✅ End of test [{}]", getCurrentTestName());
    }

    private static void removeTemplatesForIndex(String indexName) {
        String templateName = "fscrawler_" + indexName + "_*";
        logger.debug(" -> Removing existing index and component templates [{}]", templateName);
        removeIndexTemplates(templateName);
        removeComponentTemplates(templateName);
    }

    @Test
    public void deleteIndex() throws ElasticsearchClientException {
        esClient.deleteIndex("does-not-exist-index");
        createIndex();
        assertThat(esClient.isExistingIndex(getCrawlerName() + INDEX_SUFFIX_DOCS)).isTrue();
        esClient.deleteIndex(getCrawlerName() + INDEX_SUFFIX_DOCS);
        assertThat(esClient.isExistingIndex(getCrawlerName() + INDEX_SUFFIX_DOCS)).isFalse();
    }

    @Test
    public void waitForHealthyIndex() throws ElasticsearchClientException {
        createIndex();
        esClient.waitForHealthyIndex(getCrawlerName() + INDEX_SUFFIX_DOCS);

        // This one could take a long time as the index does not exist
        assertThatNoException().isThrownBy(() -> esClient.waitForHealthyIndex("does-not-exist-index"));
    }

    @Test
    public void refresh() throws ElasticsearchClientException {
        createIndex();
        assertThatNoException().isThrownBy(() -> esClient.refresh(getCrawlerName() + INDEX_SUFFIX_DOCS));
    }

    private void createSearchDataset() throws Exception {
        createIndex("{\n" +
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

        esClient.indexSingle(getCrawlerName() + INDEX_SUFFIX_DOCS, "1", "{ \"foo\": { \"bar\": \"bar\" } }", null);
        esClient.indexSingle(getCrawlerName() + INDEX_SUFFIX_DOCS, "2", "{ \"foo\": { \"bar\": \"baz\" } }", null);
        esClient.indexSingle(getCrawlerName() + INDEX_SUFFIX_DOCS, "3", "{ \"number\": 1 }", null);
        esClient.indexSingle(getCrawlerName() + INDEX_SUFFIX_DOCS, "4", "{ \"number\": 2 }", null);

        // Wait until we have 4 documents indexed
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS), 4L);
    }

    @Test
    public void searchMatchAll() throws Exception {
        createSearchDataset();

        ESSearchResponse response = esClient.search(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS));
        assertThat(response.getTotalHits()).isEqualTo(4L);

        for (ESSearchHit hit : response.getHits()) {
            assertThat(hit.getIndex()).isEqualTo(getCrawlerName() + INDEX_SUFFIX_DOCS);
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

        ESSearchResponse response = esClient.search(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS)
                .withESQuery(new ESTermQuery("foo.bar", "bar")));
        assertThat(response.getTotalHits()).isEqualTo(1L);
        assertThat(response.getHits().get(0).getIndex()).isEqualTo(getCrawlerName() + INDEX_SUFFIX_DOCS);
        assertThat(response.getHits().get(0).getId()).isEqualTo("1");
        assertThat(response.getHits().get(0).getVersion()).isEqualTo(1L);
        assertThat(response.getHits().get(0).getSource()).isNotEmpty();
        assertThat(response.getHits().get(0).getHighlightFields()).isEmpty();
        assertThat(response.getHits().get(0).getStoredFields()).isNull();
    }

    @Test
    public void searchMatch() throws Exception {
        createSearchDataset();

        ESSearchResponse response = esClient.search(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS)
                .withESQuery(new ESMatchQuery("foo.bar", "bar")));
        assertThat(response.getTotalHits()).isEqualTo(1L);
        assertThat(response.getHits().get(0).getIndex()).isEqualTo(getCrawlerName() + INDEX_SUFFIX_DOCS);
        assertThat(response.getHits().get(0).getId()).isEqualTo("1");
        assertThat(response.getHits().get(0).getVersion()).isEqualTo(1L);
        assertThat(response.getHits().get(0).getSource()).isNotEmpty();
        assertThat(response.getHits().get(0).getHighlightFields()).isEmpty();
        assertThat(response.getHits().get(0).getStoredFields()).isNull();
    }

    @Test
    public void searchPrefix() throws Exception {
        createSearchDataset();

        ESSearchResponse response = esClient.search(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS)
                .withESQuery(new ESPrefixQuery("foo.bar", "ba")));
        assertThat(response.getTotalHits()).isEqualTo(2L);

        for (ESSearchHit hit : response.getHits()) {
            assertThat(hit.getIndex()).isEqualTo(getCrawlerName() + INDEX_SUFFIX_DOCS);
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

        ESSearchResponse response = esClient.search(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS)
                .withESQuery(new ESRangeQuery("number").withLt(2)));
        assertThat(response.getTotalHits()).isEqualTo(1L);
        assertThat(response.getHits().get(0).getIndex()).isEqualTo(getCrawlerName() + INDEX_SUFFIX_DOCS);
        assertThat(response.getHits().get(0).getId()).isEqualTo("3");
        assertThat(response.getHits().get(0).getVersion()).isEqualTo(1L);
        assertThat(response.getHits().get(0).getSource()).isNotEmpty();
        assertThat(response.getHits().get(0).getHighlightFields()).isEmpty();
        assertThat(response.getHits().get(0).getStoredFields()).isNull();
    }

    @Test
    public void searchRangeGreaterOrEqual() throws Exception {
        createSearchDataset();

        ESSearchResponse response = esClient.search(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS)
                .withESQuery(new ESRangeQuery("number").withGte(2)));
        assertThat(response.getTotalHits()).isEqualTo(1L);
        assertThat(response.getHits().get(0).getIndex()).isEqualTo(getCrawlerName() + INDEX_SUFFIX_DOCS);
        assertThat(response.getHits().get(0).getId()).isEqualTo("4");
        assertThat(response.getHits().get(0).getVersion()).isEqualTo(1L);
        assertThat(response.getHits().get(0).getSource()).isNotEmpty();
        assertThat(response.getHits().get(0).getHighlightFields()).isEmpty();
        assertThat(response.getHits().get(0).getStoredFields()).isNull();
    }

    @Test
    public void searchBoolWithPrefixAndMatch() throws Exception {
        createSearchDataset();

        ESSearchResponse response = esClient.search(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS)
                .withESQuery(new ESBoolQuery()
                        .addMust(new ESPrefixQuery("foo.bar", "ba"))
                        .addMust(new ESMatchQuery("foo.bar", "bar"))
                ));
        assertThat(response.getTotalHits()).isEqualTo(1L);
        assertThat(response.getHits().get(0).getIndex()).isEqualTo(getCrawlerName() + INDEX_SUFFIX_DOCS);
        assertThat(response.getHits().get(0).getId()).isEqualTo("1");
        assertThat(response.getHits().get(0).getVersion()).isEqualTo(1L);
        assertThat(response.getHits().get(0).getSource()).isNotEmpty();
        assertThat(response.getHits().get(0).getHighlightFields()).isEmpty();
        assertThat(response.getHits().get(0).getStoredFields()).isNull();
    }

    @Test
    public void searchHighlighting() throws Exception {
        createSearchDataset();

        ESSearchResponse response = esClient.search(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS)
                .withESQuery(new ESMatchQuery("foo.bar", "bar"))
                .addHighlighter("foo.bar")
        );
        assertThat(response.getTotalHits()).isEqualTo(1L);
        assertThat(response.getHits())
                .singleElement()
                .satisfies(hit -> {
                    assertThat(hit.getIndex()).isEqualTo(getCrawlerName() + INDEX_SUFFIX_DOCS);
                    assertThat(hit.getId()).isEqualTo("1");
                    assertThat(hit.getVersion()).isEqualTo(1L);
                    assertThat(hit.getSource()).isNotEmpty();
                    assertThat(hit.getHighlightFields())
                            .hasSize(1)
                            .satisfies(highlight -> assertThat(highlight)
                                    .containsKey("foo.bar")
                                    .extractingByKey("foo.bar")
                                    .satisfies(highlightField -> assertThat(highlightField)
                                            .singleElement()
                                            .isEqualTo("<em>bar</em>")));
                    assertThat(hit.getStoredFields()).isNull();
                });
    }

    @Test
    public void searchFields() throws Exception {
        createSearchDataset();

        ESSearchResponse response = esClient.search(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS)
                .withESQuery(new ESPrefixQuery("foo.bar", "ba"))
                .addStoredField("foo.bar")
        );
        assertThat(response.getTotalHits()).isEqualTo(2L);

        for (ESSearchHit hit : response.getHits()) {
            assertThat(hit.getIndex()).isEqualTo(getCrawlerName() + INDEX_SUFFIX_DOCS);
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

        ESSearchResponse response = esClient.search(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS)
                .withESQuery(new ESPrefixQuery("foo.bar", "ba"))
                .addStoredField("_source")
                .addStoredField("foo.bar")
        );
        assertThat(response.getTotalHits()).isEqualTo(2L);

        for (ESSearchHit hit : response.getHits()) {
            assertThat(hit.getIndex()).isEqualTo(getCrawlerName() + INDEX_SUFFIX_DOCS);
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

        ESSearchResponse response = esClient.search(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS)
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
                bulkRequest.add(new ElasticsearchIndexOperation(getCrawlerName() + INDEX_SUFFIX_DOCS,
                        "" + i,
                        null,
                        "{\"foo\":{\"bar\":\"bar\"},\"number\": " + i + "}"));
            }

            ElasticsearchEngine engine = new ElasticsearchEngine(esClient);
            ElasticsearchBulkResponse bulkResponse = engine.bulk(bulkRequest);
            assertThat(bulkResponse.hasFailures()).isFalse();
            assertThat(bulkResponse.getItems()).isNotEmpty();

            // Wait until we have the expected number of documents indexed
            countTestHelper(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS), nbItems);
        }
        {
            esClient.deleteIndex(getCrawlerName() + INDEX_SUFFIX_DOCS);
            long nbItems = RandomizedTest.randomLongBetween(5, 20);
            long nbItemsToDelete = RandomizedTest.randomLongBetween(1, nbItems);

            ElasticsearchBulkRequest bulkRequest = new ElasticsearchBulkRequest();

            // Add some index op
            for (int i = 0; i < nbItems; i++) {
                bulkRequest.add(new ElasticsearchIndexOperation(getCrawlerName() + INDEX_SUFFIX_DOCS,
                        "" + i,
                        null,
                        "{\"foo\":{\"bar\":\"bar\"},\"number\": " + i + "}"));
            }
            // Add some delete op
            for (int i = 0; i < nbItemsToDelete; i++) {
                bulkRequest.add(new ElasticsearchDeleteOperation(getCrawlerName() + INDEX_SUFFIX_DOCS, "" + i));
            }

            ElasticsearchEngine engine = new ElasticsearchEngine(esClient);
            ElasticsearchBulkResponse bulkResponse = engine.bulk(bulkRequest);
            assertThat(bulkResponse.hasFailures()).isFalse();
            assertThat(bulkResponse.getItems()).isNotEmpty();

            // Wait until we have the expected number of documents indexed
            countTestHelper(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS), nbItems - nbItemsToDelete);
        }
        {
            esClient.deleteIndex(getCrawlerName() + INDEX_SUFFIX_DOCS);
            long nbItems = RandomizedTest.randomLongBetween(5, 20);

            ElasticsearchBulkRequest bulkRequest = new ElasticsearchBulkRequest();

            // Add some index op
            for (int i = 0; i < nbItems; i++) {
                bulkRequest.add(new ElasticsearchIndexOperation(getCrawlerName() + INDEX_SUFFIX_DOCS,
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
            countTestHelper(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS), nbItems);
        }
        {
            esClient.deleteIndex(getCrawlerName() + INDEX_SUFFIX_DOCS);
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

            createIndex(indexSettings);

            long nbItems = RandomizedTest.randomLongBetween(6, 20);

            ElasticsearchBulkRequest bulkRequest = new ElasticsearchBulkRequest();

            // Add some index op
            // Every 5 ops, we had a failing document
            long nbErrors = 0;
            for (int i = 0; i < nbItems; i++) {
                if (i > 0 && i % 5 == 0) {
                    bulkRequest.add(new ElasticsearchIndexOperation(getCrawlerName() + INDEX_SUFFIX_DOCS,
                            "" + i,
                            null,
                            "{\"foo\":{\"bar\":\"bar\"},\"number\":\"bar\"}"));
                    nbErrors++;
                } else {
                    bulkRequest.add(new ElasticsearchIndexOperation(getCrawlerName() + INDEX_SUFFIX_DOCS,
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
            countTestHelper(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS), nbItems - nbErrors);
        }
    }

    @Test
    public void deleteSingle() throws Exception {
        esClient.indexSingle(getCrawlerName() + INDEX_SUFFIX_DOCS, "1", "{ \"foo\": { \"bar\": \"bar\" } }", null);
        esClient.indexSingle(getCrawlerName() + INDEX_SUFFIX_DOCS, "2", "{ \"foo\": { \"bar\": \"baz\" } }", null);
        esClient.indexSingle(getCrawlerName() + INDEX_SUFFIX_DOCS, "3", "{ \"number\": 1 }", null);
        esClient.indexSingle(getCrawlerName() + INDEX_SUFFIX_DOCS, "4", "{ \"number\": 2 }", null);

        ESSearchResponse response = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS), 4L);
        assertThat(response.getTotalHits()).isEqualTo(4L);

        esClient.deleteSingle(getCrawlerName() + INDEX_SUFFIX_DOCS, "1");

        response = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS), 3L);
        assertThat(response.getTotalHits()).isEqualTo(3L);

        try {
            esClient.deleteSingle(getCrawlerName() + INDEX_SUFFIX_DOCS, "99999");
            fail("We should have raised an " + ElasticsearchClientException.class.getSimpleName());
        } catch (ElasticsearchClientException e) {
            assertThat(e.getMessage()).isEqualTo("Document " + getCrawlerName() + INDEX_SUFFIX_DOCS + "/99999 does not exist");
        }

        response = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS), 3L);
        assertThat(response.getTotalHits()).isEqualTo(3L);
    }

    @Test
    public void exists() throws ElasticsearchClientException {
        esClient.indexSingle(getCrawlerName() + INDEX_SUFFIX_DOCS, "1", "{ \"foo\": { \"bar\": \"bar\" } }", null);
        esClient.refresh(getCrawlerName() + INDEX_SUFFIX_DOCS);
        assertThat(esClient.exists(getCrawlerName() + INDEX_SUFFIX_DOCS, "1")).isTrue();
        assertThat(esClient.exists(getCrawlerName() + INDEX_SUFFIX_DOCS, "999")).isFalse();
    }

    @Test
    public void withOnlyOneRunningNode() throws ElasticsearchClientException, IOException {
        // Build a client with a non-running node
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getElasticsearch().setUrls(List.of("http://127.0.0.1:9206", testClusterUrl));
        fsSettings.getElasticsearch().setApiKey(testApiKey);
        fsSettings.getElasticsearch().setSslVerification(false);
        try (IElasticsearchClient localClient = new ElasticsearchClient(fsSettings)) {
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
        fsSettings.getElasticsearch().setUrls(List.of(testClusterUrl, testClusterUrl, "http://127.0.0.1:9206", testClusterUrl));
        fsSettings.getElasticsearch().setApiKey(testApiKey);
        fsSettings.getElasticsearch().setSslVerification(false);
        fsSettings.getElasticsearch().setIndex(DOC_INDEX_NAME);
        fsSettings.getElasticsearch().setIndexFolder(FOLDER_INDEX_NAME);
        try (IElasticsearchClient localClient = new ElasticsearchClient(fsSettings)) {
            localClient.start();

            if (localClient.getMajorVersion() < 8) {
                // We are missing one call when comparing with an ES 8.x cluster
                localClient.isExistingIndex("foo");
            }

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
        fsSettings.getElasticsearch().setUrls(List.of("http://127.0.0.1:9206", "http://127.0.0.1:9207"));
        fsSettings.getElasticsearch().setApiKey(testApiKey);
        fsSettings.getElasticsearch().setSslVerification(false);

        try (IElasticsearchClient localClient = new ElasticsearchClient(fsSettings)) {
            assertThatExceptionOfType(ElasticsearchClientException.class)
                    .isThrownBy(localClient::start)
                    .withMessageContaining("All nodes are failing");
        }
    }

    @Test
    public void withNonRunningNode() throws IOException {
        // Build a client with a non-running node
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getElasticsearch().setUrls(List.of("http://127.0.0.1:9206"));
        fsSettings.getElasticsearch().setApiKey(testApiKey);
        fsSettings.getElasticsearch().setSslVerification(false);

        try (IElasticsearchClient localClient = new ElasticsearchClient(fsSettings)) {
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
        fsSettings.getElasticsearch().setUrls(List.of(testClusterUrl));
        fsSettings.getElasticsearch().setSslVerification(false);

        try (IElasticsearchClient localClient = new ElasticsearchClient(fsSettings)) {
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
        // We can only run this test if Docker is available on this machine
        assumeTrue("We can only run this test if Docker is available on this machine", DockerClientFactory.instance().isDockerAvailable());

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

    @Test
    public void componentTemplate() throws ElasticsearchClientException {
        String crawlerName = getCrawlerName();

        // Check it does not exist
        assertThat(esClient.isExistingComponentTemplate(crawlerName)).isFalse();

        // Create a simple component template
        String componentTemplate = "{\n" +
                "  \"template\": {\n" +
                "    \"mappings\": {\n" +
                "      \"properties\": {\n" +
                "        \"foo\": { \"type\": \"keyword\" }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}";
        esClient.pushComponentTemplate(crawlerName, componentTemplate);

        assertThat(esClient.isExistingComponentTemplate(crawlerName)).isTrue();
        assertThat(esClient.isExistingComponentTemplate(crawlerName + "_foo")).isFalse();
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

    protected static void removeComponentTemplates(String componentTemplateName) {
        logger.trace("Removing component templates for [{}]", componentTemplateName);
        try {
            esClient.performLowLevelRequest("DELETE", "/_component_template/" + componentTemplateName, null);
        } catch (ElasticsearchClientException | NotFoundException e) {
            // We ignore the error
        } catch (BadRequestException e) {
            // We ignore the error
            logger.warn("Failed to remove component templates. Got a [{}] when calling [DELETE /_component_template/{}]",
                    e.getMessage(), componentTemplateName);
        }
    }

    protected static void removeIndexTemplates(String indexTemplateName) {
        logger.trace("Removing index templates for [{}]", indexTemplateName);
        try {
            esClient.performLowLevelRequest("DELETE", "/_index_template/" + indexTemplateName, null);
        } catch (ElasticsearchClientException | NotFoundException e) {
            // We ignore the error
        } catch (BadRequestException e) {
            // We ignore the error
            logger.warn("Failed to remove index templates. Got a [{}] when calling [DELETE /_index_template/{}]",
                    e.getMessage(), indexTemplateName);
        }
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

        // Wait for the index to be healthy as we might have a race condition
        esClient.waitForHealthyIndex(request.getIndex());

        // We wait before considering a failing test
        logger.info("  ---> Waiting up to {} for {} documents in {}", maxWaitForSearch,
                expected == null ? "some" : expected, request.getIndex());
        AtomicReference<Exception> errorWhileWaiting = new AtomicReference<>();

        try {
            await().atMost(maxWaitForSearch)
                    .pollInterval(ExponentialBackoffPollInterval.exponential(Duration.ofMillis(500), Duration.ofSeconds(5)))
                    .until(() -> {
                        long totalHits;

                        // Let's search for entries
                        try {
                            // Make sure we refresh indexed docs before counting
                            esClient.refresh(request.getIndex());
                            response[0] = esClient.search(request);
                            errorWhileWaiting.set(null);
                        } catch (RuntimeException e) {
                            logger.warn("error caught", e);
                            errorWhileWaiting.set(e);
                            return false;
                        } catch (ElasticsearchClientException e) {
                            // TODO create a NOT FOUND Exception instead
                            logger.debug("error caught", e);
                            errorWhileWaiting.set(e);
                            return false;
                        }
                        totalHits = response[0].getTotalHits();

                        logger.debug("got so far [{}] hits on expected [{}]", totalHits, expected);

                        if (expected == null) {
                            return totalHits >= 1;
                        }
                        return totalHits == expected;
                    });
        } catch (ConditionTimeoutException e) {
            // If we caught an exception during waiting, throw it instead of the timeout
            if (errorWhileWaiting.get() != null) {
                throw errorWhileWaiting.get();
            }
            throw e;
        }

        long hits = response[0].getTotalHits();
        if (expected == null) {
            assertThat(hits)
                    .as("checking if any document in %s", request.getIndex())
                    .isGreaterThan(0);
        } else {
            assertThat(hits)
                    .as("checking documents in %s", request.getIndex())
                    .isEqualTo(expected);
        }

        return response[0];
    }

    /**
     * Create an index (for tests only)
     */
    private void createIndex() throws ElasticsearchClientException {
        createIndex(null);
    }

    /**
     * Create an index (for tests only)
     *
     * @param indexSettings index settings if any
     */
    private void createIndex(String indexSettings) throws ElasticsearchClientException {
        String realIndexSettings = indexSettings;
        String index = getCrawlerName() + INDEX_SUFFIX_DOCS;
        logger.debug("create index [{}]", index);
        if (indexSettings == null) {
            // We need to pass an empty body because PUT requires a body
            realIndexSettings = "{}";
        }
        logger.trace("index settings: [{}]", realIndexSettings);
        try {
            esClient.httpPut(index, realIndexSettings);
            esClient.waitForHealthyIndex(index);
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatusInfo().getFamily() == Response.Status.Family.CLIENT_ERROR) {
                logger.debug("Response for create index [{}]: {}", index, e.getMessage());
                DocumentContext document = parseJsonAsDocumentContext(e.getResponse().readEntity(String.class));
                String errorType = document.read("$.error.type");
                if (!errorType.contains("resource_already_exists_exception")) {
                    throw new ElasticsearchClientException("error while creating index " + index + ": " +
                            document.read("$"));
                }
            } else {
                throw new ElasticsearchClientException("Error while creating index " + index, e);
            }
        }
    }
}