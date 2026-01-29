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

package fr.pilato.elasticsearch.crawler.fs.test.integration;

import com.carrotsearch.randomizedtesting.RandomizedTest;
import fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClient;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientException;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.settings.Elasticsearch;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import fr.pilato.elasticsearch.crawler.fs.test.framework.TestContainerHelper;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPluginsManager;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ProcessingException;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.junit.AfterClass;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Before;

import javax.net.ssl.SSLHandshakeException;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import java.time.Duration;

import static com.carrotsearch.randomizedtesting.RandomizedTest.randomAsciiAlphanumOfLength;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.*;
import static fr.pilato.elasticsearch.crawler.fs.framework.TimeValue.MAX_WAIT_FOR_SEARCH;
import static fr.pilato.elasticsearch.crawler.fs.test.framework.FsCrawlerUtilForTests.copyDirs;
import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests expect to have an elasticsearch instance running on <a href="https://127.0.0.1:9200">https://127.0.0.1:9200</a>.
 * Otherwise, a TestContainer instance will be started.
 * <br/>
 * Note that all existing data in this cluster might be removed
 * <br/>
 * If you want to run tests against a remote cluster, please launch tests using
 * tests.cluster.url property:
 * <pre><code>mvn verify -Dtests.cluster.url=https://127.0.0.1:9200</code></pre>
 * All integration tests might be skipped using:
 * <pre><code>mvn verify -DskipIntegTests</code></pre>
 */
public abstract class AbstractITCase extends AbstractFSCrawlerTestCase {
    private static final Logger logger = LogManager.getLogger();
    protected static final Path DEFAULT_RESOURCES =  Paths.get(getUrl("samples", "_common"));
    private static final String DEFAULT_TEST_CLUSTER_URL = "https://127.0.0.1:9200";
    private static final String DEFAULT_USERNAME = "elastic";
    protected static String testClusterUrl = getSystemProperty("tests.cluster.url", DEFAULT_TEST_CLUSTER_URL);
    protected static String testApiKey = getSystemProperty("tests.cluster.apiKey", null);
    protected static final boolean TEST_KEEP_DATA = getSystemProperty("tests.leaveTemporary", true);
    protected static final boolean testCheckCertificate = getSystemProperty("tests.cluster.check_ssl", true);
    private static final TestContainerHelper testContainerHelper = new TestContainerHelper();

    protected static Path metadataDir = null;
    protected FsCrawlerImpl crawler = null;
    protected Path currentTestResourceDir;
    protected Path currentTestTagDir;

    private static String testCaCertificate = null;

    protected static Elasticsearch elasticsearchConfiguration;
    protected static FsCrawlerPluginsManager pluginsManager;
    protected static ElasticsearchClient client;

    /**
     * We suppose that each test has its own set of files. Even if we duplicate them, that will make the code
     * more readable.
     * The temp folder which is used as a root is automatically cleaned after the test, so we don't have to worry
     * about it.
     */
    @Before
    public void copyTestResources() throws IOException {
        Path testResourceTarget = rootTmpDir.resolve("resources");
        if (Files.notExists(testResourceTarget)) {
            Files.createDirectory(testResourceTarget);
        }

        String currentTestName = getCurrentTestName();
        // We copy files from the src dir to the temp dir
        logger.info("  --> Launching test [{}]", currentTestName);
        currentTestResourceDir = testResourceTarget.resolve(currentTestName);
        String url = getUrl("samples", currentTestName);
        Path from = Paths.get(url);

        if (Files.exists(from)) {
            logger.debug("  --> Copying test resources from [{}]", from);
        } else {
            logger.debug("  --> Copying test resources from [{}]", DEFAULT_RESOURCES);
            from = DEFAULT_RESOURCES;
        }

        copyDirs(from, currentTestResourceDir);

        logger.debug("  --> Test resources ready in [{}]", currentTestResourceDir);
    }

    @Before
    public void copyTags() throws IOException {
        Path testResourceTarget = rootTmpDir.resolve("resources");
        if (Files.notExists(testResourceTarget)) {
            Files.createDirectory(testResourceTarget);
        }

        String currentTestName = getCurrentTestName();
        // We copy files from the src dir to the temp dir
        String url = getUrl("tags", currentTestName);
        Path from = Paths.get(url);

        currentTestTagDir = testResourceTarget.resolve(currentTestName + ".tags");
        if (Files.exists(from)) {
            logger.debug("  --> Copying test resources from [{}]", from);
            copyDirs(from, currentTestTagDir);
            logger.debug("  --> Tags ready in [{}]", currentTestTagDir);
        }
    }

    @After
    public void cleanTestResources() {
        logger.info("  --> Test [{}] is now stopped", getCurrentTestName());
    }

    @BeforeClass
    public static void createFsCrawlerJobDir() throws IOException {
        metadataDir = rootTmpDir.resolve(".fscrawler");
        if (Files.notExists(metadataDir)) {
            Files.createDirectory(metadataDir);
        }
        logger.debug("  --> Test metadata dir ready in [{}]", metadataDir);
    }

    @AfterClass
    public static void printMetadataDirContent() throws IOException {
        // If something goes wrong while initializing, we might have no metadataDir at all.
        if (metadataDir != null) {
            logger.debug("ls -l {}", metadataDir);
            Files.list(metadataDir).forEach(path -> logger.debug("{}", path));
        }
    }

    @BeforeClass
    public static void copyResourcesToTestDir() throws IOException, URISyntaxException {
        Path testResourceTarget = rootTmpDir.resolve("resources");
        if (Files.notExists(testResourceTarget)) {
            logger.debug("  --> Creating test resources dir in [{}]", testResourceTarget);
            Files.createDirectory(testResourceTarget);
        }

        // We copy files from the src dir to the temp dir
        copyTestDocumentsToTargetDir(testResourceTarget, "documents", "/fscrawler-test-documents-marker.txt");

        logger.debug("  --> Test resources ready in [{}]:", testResourceTarget);
    }

    /**
     * Copy test documents to the test dir, so we will be able to run tests against them
     * @param target            target directory.
     * @param sourceDirName     source subdir name where files will be copied or extracted to.
     * @param marker            one of the filename which is available in the classpath which contains
     *                          the test documents or within a jar
     * @throws IOException In case of IO problem
     */
    private static void copyTestDocumentsToTargetDir(Path target, String sourceDirName, String marker) throws IOException, URISyntaxException {
        URL resource = AbstractFSCrawlerTestCase.class.getResource(marker);

        switch (resource.getProtocol()) {
            case "file" : {
                Path finalTarget = target.resolve(sourceDirName);
                if (Files.notExists(finalTarget)) {
                    logger.debug("  --> Creating test dir named [{}]", finalTarget);
                    Files.createDirectory(finalTarget);
                }
                // We are running our tests from the IDE most likely and documents are directly available in the classpath
                Path source = Paths.get(resource.toURI()).getParent().resolve(sourceDirName);
                if (Files.notExists(source)) {
                    logger.error("directory [{}] should be copied to [{}]", source, target);
                    throw new RuntimeException(source + " doesn't seem to exist. Check your JUnit tests.");
                }

                logger.debug("-> Copying test documents from [{}] to [{}]", source, finalTarget);
                copyDirs(source, finalTarget);
                break;
            }
            case "jar" : {
                if (Files.notExists(target)) {
                    logger.debug("  --> Creating test dir named [{}]", target);
                    Files.createDirectory(target);
                }
                // We are  running our tests from the CLI most likely and documents are provided within a JAR as a dependency
                String fileInJar = resource.getPath();
                int i = fileInJar.indexOf("!/");
                String jarFileWithProtocol = fileInJar.substring(0, i);
                URI jarFile = new URI(jarFileWithProtocol);
                unzip(Path.of(jarFile), target, Charset.defaultCharset());
                break;
            }
            default :
                fail("Unknown protocol for IT document sources: " + resource.getProtocol());
                break;
        }
    }

    private static void unzip(Path zip, Path outputFolder, Charset charset) throws IOException {
        logger.debug("-> Unzipping test documents from [{}] to [{}]", zip, outputFolder);

        try (ZipFile zipFile = new ZipFile(zip.toFile(), ZipFile.OPEN_READ, charset)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Path entryPath = outputFolder.resolve(entry.getName());
                if (entryPath.normalize().startsWith(outputFolder.normalize())) {
                    if (entry.isDirectory()) {
                        Files.createDirectories(entryPath);
                    } else {
                        Files.createDirectories(entryPath.getParent());
                        try (InputStream in = zipFile.getInputStream(entry)) {
                            try (OutputStream out = new FileOutputStream(entryPath.toFile())) {
                                IOUtils.copy(in, out);
                            }
                        }
                    }
                }
            }
        }
    }

    @BeforeClass
    public static void startServices() throws IOException, ElasticsearchClientException {
        logger.debug("Generate settings against [{}] with ssl check [{}]", testClusterUrl, testCheckCertificate);

        FsSettings fsSettings = FsSettingsLoader.load();
        // If we already have the elasticsearch settings, there's no need to load them again
        if (elasticsearchConfiguration != null) {
            logger.debug("We already found the cluster settings. No need to set them again.");
            Elasticsearch elasticsearchLocalConfiguration = clone(elasticsearchConfiguration);

            // If we already launched testcontainers, we need to write the CA certificate file again
            // Write the Ca Certificate on disk if exists (with versions < 8, no self-signed certificate)
            if (testContainerHelper.isStarted() && testContainerHelper.getCertAsBytes() != null) {
                Path clusterCaCrtPath = rootTmpDir.resolve("cluster-ca.crt");
                Files.write(clusterCaCrtPath, testContainerHelper.getCertAsBytes());
                testCaCertificate = clusterCaCrtPath.toAbsolutePath().toString();
            } else {
                testCaCertificate = null;
            }
            elasticsearchLocalConfiguration.setSslVerification(testCaCertificate != null);
            elasticsearchLocalConfiguration.setCaCertificate(testCaCertificate);
            fsSettings.setElasticsearch(elasticsearchLocalConfiguration);
        } else {
            logger.debug("No elasticsearch configuration found, using default settings");
            // We build the elasticsearch Client based on the parameters
            fsSettings.getElasticsearch().setUrls(List.of(testClusterUrl));
            fsSettings.getElasticsearch().setSslVerification(testCheckCertificate);
            fsSettings.getElasticsearch().setCaCertificate(testCaCertificate);
            if (testApiKey != null) {
                fsSettings.getElasticsearch().setApiKey(testApiKey);
            } else {
                fsSettings.getElasticsearch().setUsername(DEFAULT_USERNAME);
                fsSettings.getElasticsearch().setPassword(TestContainerHelper.DEFAULT_PASSWORD);
            }
        }

        try {
            client = startClient(fsSettings);
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
            testClusterUrl = testContainerHelper.startElasticsearch(TEST_KEEP_DATA);
            // Write the Ca Certificate on disk if exists (with versions < 8, no self-signed certificate)
            if (testContainerHelper.getCertAsBytes() != null) {
                Path clusterCaCrtPath = rootTmpDir.resolve("cluster-ca.crt");
                Files.write(clusterCaCrtPath, testContainerHelper.getCertAsBytes());
                testCaCertificate = clusterCaCrtPath.toAbsolutePath().toString();
            } else {
                testCaCertificate = null;
            }
            fsSettings.getElasticsearch().setUrls(List.of(testClusterUrl));
            fsSettings.getElasticsearch().setSslVerification(testCaCertificate != null);
            fsSettings.getElasticsearch().setCaCertificate(testCaCertificate);
            client = startClient(fsSettings);
        }

        elasticsearchConfiguration = fsSettings.getElasticsearch();

        assumeThat(client)
                .as("Integration tests are skipped because we have not been able to find an Elasticsearch cluster")
                .isNotNull();

        // If the Api Key is not provided, we want to generate it and use in all the tests
        if (testApiKey == null) {
            // Generate the Api-Key
            testApiKey = client.generateApiKey("fscrawler-" + randomAsciiAlphanumOfLength(10));

            fsSettings.getElasticsearch().setApiKey(testApiKey);
            fsSettings.getElasticsearch().setUsername(null);
            fsSettings.getElasticsearch().setPassword(null);

            // Close the previous client
            client.close();

            // Start a new client with the Api Key
            client = startClient(fsSettings);
        }

        // Load all plugins
        pluginsManager = new FsCrawlerPluginsManager();
        pluginsManager.loadPlugins();
        pluginsManager.startPlugins();

        String version = client.getVersion();
        logger.info("✅ Starting integration tests against an external cluster running elasticsearch [{}]", version);
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
        if (client != null) {
            client.close();
            client = null;
            logger.debug("Elasticsearch client stopped");
        }
        if (pluginsManager != null) {
            pluginsManager.close();
            pluginsManager = null;
        }
    }

    @Before
    public void checkSkipIntegTests() {
        // In case we are running tests from the IDE with the skipIntegTests option, let make sure we are skipping
        // those tests
        RandomizedTest.assumeFalse("skipIntegTests is true. So we are skipping the integration tests.",
                getSystemProperty("skipIntegTests", false));
    }

    protected static void refresh(String indexName) throws ElasticsearchClientException {
        try {
            client.refresh(indexName);
        } catch (NotFoundException e) {
            // The index might not have been created yet. It could happen with cloud services, like serverless.
            // We can safely ignore it.
            logger.trace("Index [{}] does not exist yet so we can't refresh it.", indexName);
        }
    }

    /**
     * Check that we have the expected number of docs or at least one if expected is null
     *
     * @param request   Elasticsearch request to run.
     * @param expected  expected number of docs. Null if at least 1.
     * @param path      Path we are supposed to scan. If we have not accurate results, we display its content
     * @return the search response if further tests are needed
     * @throws Exception in case of error
     */
    public static ESSearchResponse countTestHelper(final ESSearchRequest request, final Long expected, final Path path) throws Exception {
        return countTestHelper(request, expected, path, MAX_WAIT_FOR_SEARCH);
    }

    /**
     * Check that we have the expected number of docs or at least one if expected is null
     *
     * @param request   Elasticsearch request to run.
     * @param expected  expected number of docs. Null if at least 1.
     * @param path      Path we are supposed to scan. If we have not accurate results, we display its content
     * @param timeout   Time before we declare a failure
     * @return the search response if further tests are needed
     * @throws Exception in case of error
     */
    public static ESSearchResponse countTestHelper(final ESSearchRequest request, final Long expected, final Path path, final TimeValue timeout) throws Exception {

        final ESSearchResponse[] response = new ESSearchResponse[1];

        // We wait before considering a failing test
        logger.info("  ---> Waiting up to {} for {} documents in {}", timeout.toString(),
                expected == null ? "some" : expected, request.getIndex());
        AtomicReference<Exception> errorWhileWaiting = new AtomicReference<>();

        await().atMost(Duration.ofMillis(timeout.millis()))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> {
                    long totalHits;

                    // Let's search for entries
                    try {
                        // Make sure we refresh indexed docs before counting
                        refresh(request.getIndex());
                        response[0] = client.search(request);
                        errorWhileWaiting.set(null);
                    } catch (RuntimeException e) {
                        logger.warn("error caught", e);
                        errorWhileWaiting.set(e);
                        return false;
                    } catch (ElasticsearchClientException e) {
                        // TODO create a NOT FOUND Exception instead
                        logger.debug("error caught: [{}] ", e.getMessage());
                        logger.trace("error caught", e);
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

        // We check that we did not catch an error while waiting
        assertThatNoException().isThrownBy(() -> {
            if (errorWhileWaiting.get() != null) {
                throw errorWhileWaiting.get();
            }
        });

        long hits = response[0].getTotalHits();
        if (expected == null) {
            assertThat(hits)
                    .as("checking if any document in %s", request.getIndex())
                    .withFailMessage(() -> {
                        logContentOfDir(path, Level.WARN);
                        return "got 0 documents in " + request.getIndex() + " while we expected at least one";
                    })
                    .isGreaterThan(0);

        } else {
            assertThat(hits)
                    .as("checking documents in %s", request.getIndex())
                    .withFailMessage(() -> {
                        logContentOfDir(path, Level.WARN);
                        return "got " + hits + " documents in " + request.getIndex() + " while we expected exactly " + expected;
                    })
                    .isEqualTo(expected);
        }

        return response[0];
    }

    protected static void logContentOfDir(Path path, Level level) {
        if (path != null) {
            try (Stream<Path> stream = Files.walk(path)) {
                stream.forEach(file -> {
                    try {
                        if (Files.isDirectory(file)) {
                            logger.log(level, " * in dir [{}] [{}]",
                                    path.relativize(file).toString(),
                                    Files.getLastModifiedTime(file));
                        } else {
                            logger.log(level, "   - [{}] [{}]",
                                    file.getFileName().toString(),
                                    Files.getLastModifiedTime(file));
                        }
                    } catch (IOException ignored) {
                    }
                });
            } catch (IOException ex) {
                logger.error("can not read content of [{}]:", path);
            }
        }
    }

    public static String getUrl(String... subdirs) {
        URL resource = AbstractITCase.class.getResource("/fscrawler-integration-tests-marker.txt");
        File dir = URLtoFile(resource).getParentFile();

        for (String subdir : subdirs) {
            dir = new File(dir, subdir);
        }

        return dir.getAbsoluteFile().getAbsolutePath();
    }

    /**
     * Split a String at the first occurrence of the delimiter.
     * Does not include the delimiter in the result.
     *
     * @param toSplit   the string to split
     * @param delimiter to split the string up with
     * @return a two element array with index 0 being before the delimiter, and
     *         index 1 being after the delimiter (neither element includes the delimiter);
     *         or <code>null</code> if the delimiter wasn't found in the given input String
     */
    public static String[] split(String toSplit, String delimiter) {
        int offset = toSplit.indexOf(delimiter);
        String beforeDelimiter = toSplit.substring(0, offset);
        String afterDelimiter = toSplit.substring(offset + delimiter.length());
        return new String[]{beforeDelimiter, afterDelimiter};
    }

    public static void deleteRecursively(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public @NotNull FileVisitResult visitFile(Path file, @NotNull BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public @NotNull FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    protected static Elasticsearch clone(Elasticsearch source) {
        Elasticsearch elasticsearch = FsSettingsLoader.load().getElasticsearch();
        elasticsearch.setUrls(List.of(source.getUrls().get(0)));
        elasticsearch.setSslVerification(source.isSslVerification());
        elasticsearch.setCaCertificate(source.getCaCertificate());
        elasticsearch.setApiKey(source.getApiKey());
        elasticsearch.setUsername(source.getUsername());
        elasticsearch.setPassword(source.getPassword());
        return elasticsearch;
    }

    protected FsSettings createTestSettings() {
        return createTestSettings(getCrawlerName());
    }

    protected FsSettings createTestSettings(String name) {
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.setName(name);
        fsSettings.getFs().setUpdateRate(TimeValue.timeValueSeconds(5));
        fsSettings.getFs().setUrl(currentTestResourceDir.toString());

        // Clone the elasticsearchConfiguration to avoid modifying the default one
        // We start with a clean configuration
        Elasticsearch elasticsearch = clone(elasticsearchConfiguration);

        fsSettings.setElasticsearch(elasticsearch);
        fsSettings.getElasticsearch().setIndex(name + INDEX_SUFFIX_DOCS);
        fsSettings.getElasticsearch().setIndexFolder(name + INDEX_SUFFIX_FOLDER);
        fsSettings.getElasticsearch().setFlushInterval(TimeValue.timeValueSeconds(1));
        // We explicitly set semantic search to false because IT takes too long time
        fsSettings.getElasticsearch().setSemanticSearch(false);
        return fsSettings;
    }
}
