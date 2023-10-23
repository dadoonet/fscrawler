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
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientException;
import fr.pilato.elasticsearch.crawler.fs.framework.ByteSizeValue;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentService;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentServiceElasticsearchImpl;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerManagementServiceElasticsearchImpl;
import fr.pilato.elasticsearch.crawler.fs.settings.Elasticsearch;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.ServerUrl;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import jakarta.ws.rs.ProcessingException;
import org.apache.logging.log4j.Level;
import org.hamcrest.Matcher;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import javax.net.ssl.SSLException;
import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static com.carrotsearch.randomizedtesting.RandomizedTest.randomAsciiAlphanumOfLength;
import static com.carrotsearch.randomizedtesting.RandomizedTest.randomIntBetween;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.*;
import static fr.pilato.elasticsearch.crawler.fs.settings.ServerUrl.decodeCloudId;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeThat;

/**
 * Integration tests expect to have an elasticsearch instance running on http://127.0.0.1:9200.
 *
 * Note that all existing data in this cluster might be removed
 *
 * If you want to run tests against a remote cluster, please launch tests using
 * tests.cluster.url property:
 *
 * mvn clean install -Dtests.cluster.url=http://127.0.0.1:9200
 *
 * If the cluster is running with security you may want to overwrite the username and password
 * with tests.cluster.user and tests.cluster.password:
 *
 * mvn clean install -Dtests.cluster.user=elastic -Dtests.cluster.pass=changeme
 *
 * All integration tests might be skipped if the cluster is not running
 */
public abstract class AbstractITCase extends AbstractFSCrawlerTestCase {

    protected static Path metadataDir = null;

    protected FsCrawlerImpl crawler = null;
    protected Path currentTestResourceDir;

    private static final Path DEFAULT_RESOURCES =  Paths.get(getUrl("samples", "common"));
    private final static String DEFAULT_TEST_CLUSTER_URL = "https://127.0.0.1:9200";
    private final static String DEFAULT_TEST_CLUSTER_HTTP_URL = "http://127.0.0.1:9200";
    private final static String DEFAULT_USERNAME = "elastic";
    private final static String DEFAULT_PASSWORD = "changeme";
    private final static Integer DEFAULT_TEST_REST_PORT = 8080;

    protected static String testClusterUrl;
    protected final static String testClusterUser = getSystemProperty("tests.cluster.user", DEFAULT_USERNAME);
    protected final static String testClusterPass = getSystemProperty("tests.cluster.pass", DEFAULT_PASSWORD);
    protected final static int testRestPort = getSystemProperty("tests.rest.port", DEFAULT_TEST_REST_PORT);
    protected final static boolean testKeepData = getSystemProperty("tests.leaveTemporary", false);

    protected static Elasticsearch elasticsearchWithSecurity;
    protected static FsCrawlerManagementServiceElasticsearchImpl managementService;
    protected static FsCrawlerDocumentService documentService;

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
        staticLogger.info("  --> Launching test [{}]", currentTestName);
        currentTestResourceDir = testResourceTarget.resolve(currentTestName);
        String url = getUrl("samples", currentTestName);
        Path from = Paths.get(url);

        if (Files.exists(from)) {
            staticLogger.debug("  --> Copying test resources from [{}]", from);
        } else {
            staticLogger.debug("  --> Copying test resources from [{}]", DEFAULT_RESOURCES);
            from = DEFAULT_RESOURCES;
        }

        copyDirs(from, currentTestResourceDir);

        staticLogger.debug("  --> Test resources ready in [{}]", currentTestResourceDir);
    }

    @BeforeClass
    public static void createFsCrawlerJobDir() throws IOException {
        // We also need to create default mapping files
        metadataDir = rootTmpDir.resolve(".fscrawler");
        if (Files.notExists(metadataDir)) {
            Files.createDirectory(metadataDir);
        }
        copyDefaultResources(metadataDir);
        staticLogger.debug("  --> Test metadata dir ready in [{}]", metadataDir);
    }

    @AfterClass
    public static void printMetadataDirContent() throws IOException {
        // If something goes wrong while initializing, we might have no metadataDir at all.
        if (metadataDir != null) {
            staticLogger.debug("ls -l {}", metadataDir);
            Files.list(metadataDir).forEach(path -> staticLogger.debug("{}", path));
        }
    }

    @BeforeClass
    public static void copyResourcesToTestDir() throws IOException {
        Path testResourceTarget = rootTmpDir.resolve("resources");
        if (Files.notExists(testResourceTarget)) {
            staticLogger.debug("  --> Creating test resources dir in [{}]", testResourceTarget);
            Files.createDirectory(testResourceTarget);
        }

        // We copy files from the src dir to the temp dir
        copyTestDocumentsToTargetDir(testResourceTarget, "documents", "/fscrawler-test-documents-marker.txt");

        staticLogger.debug("  --> Test resources ready in [{}]:", testResourceTarget);
    }

    /**
     * Copy test documents to the test dir, so we will be able to run tests against them
     * @param target            target directory.
     * @param sourceDirName     source subdir name where files will be copied or extracted to.
     * @param marker            one of the filename which is available in the classpath which contains
     *                          the test documents or within a jar
     * @throws IOException In case of IO problem
     */
    private static void copyTestDocumentsToTargetDir(Path target, String sourceDirName, String marker) throws IOException {
        URL resource = AbstractFSCrawlerTestCase.class.getResource(marker);

        switch (resource.getProtocol()) {
            case "file" : {
                Path finalTarget = target.resolve(sourceDirName);
                if (Files.notExists(finalTarget)) {
                    staticLogger.debug("  --> Creating test dir named [{}]", finalTarget);
                    Files.createDirectory(finalTarget);
                }
                // We are running our tests from the IDE most likely and documents are directly available in the classpath
                Path source = Paths.get(resource.getPath()).getParent().resolve(sourceDirName);
                if (Files.notExists(source)) {
                    staticLogger.error("directory [{}] should be copied to [{}]", source, target);
                    throw new RuntimeException(source + " doesn't seem to exist. Check your JUnit tests.");
                }

                staticLogger.info("-> Copying test documents from [{}] to [{}]", source, finalTarget);
                copyDirs(source, finalTarget);
                break;
            }
            case "jar" : {
                if (Files.notExists(target)) {
                    staticLogger.debug("  --> Creating test dir named [{}]", target);
                    Files.createDirectory(target);
                }
                // We are running our tests from the CLI most likely and documents are provided within a JAR as a dependency
                String fileInJar = resource.getPath();
                int i = fileInJar.indexOf("!/");
                String jarFile = fileInJar.substring(0, i);

                staticLogger.info("-> Unzipping test documents from [{}] to [{}]", jarFile, target);
                unzip(jarFile, target);
                break;
            }
            default :
                fail("Unknown protocol for IT document sources: " + resource.getProtocol());
                break;
        }
    }

    @BeforeClass
    public static void startServices() throws IOException, ElasticsearchClientException {
        String testClusterCloudId = System.getProperty("tests.cluster.cloud_id");
        if (testClusterCloudId != null && !testClusterCloudId.isEmpty()) {
            testClusterUrl = decodeCloudId(testClusterCloudId);
            staticLogger.debug("Using cloud id [{}] meaning actually [{}]", testClusterCloudId, testClusterUrl);
        } else {
            testClusterUrl = getSystemProperty("tests.cluster.url", DEFAULT_TEST_CLUSTER_URL);
            if (testClusterUrl.isEmpty()) {
                // When running from Maven CLI, tests.cluster.url is empty and not null...
                testClusterUrl = DEFAULT_TEST_CLUSTER_URL;
            }
        }

        staticLogger.info("Starting a client against [{}]", testClusterUrl);
        // We build the elasticsearch Client based on the parameters
        elasticsearchWithSecurity = Elasticsearch.builder()
                .setNodes(Collections.singletonList(new ServerUrl(testClusterUrl)))
                .setUsername(testClusterUser)
                .setPassword(testClusterPass)
                .setSslVerification(false)
                .build();
        FsSettings fsSettings = FsSettings.builder("esClient").setElasticsearch(elasticsearchWithSecurity).build();

        documentService = new FsCrawlerDocumentServiceElasticsearchImpl(metadataDir, fsSettings);

        try {
            documentService.start();
        } catch (ElasticsearchClientException e) {
            if ((e.getCause() instanceof SocketException ||
                    (e.getCause() instanceof ProcessingException && e.getCause().getCause() instanceof SSLException))
                            && testClusterUrl.equals(DEFAULT_TEST_CLUSTER_URL)) {
                staticLogger.info("May be we are trying to run against a <8.x cluster. So let's fallback to http");
                testClusterUrl=DEFAULT_TEST_CLUSTER_HTTP_URL;
                staticLogger.info("Starting a client against [{}]", testClusterUrl);
                // We build the elasticsearch Client based on the parameters
                elasticsearchWithSecurity = Elasticsearch.builder()
                        .setNodes(Collections.singletonList(new ServerUrl(testClusterUrl)))
                        .setUsername(testClusterUser)
                        .setPassword(testClusterPass)
                        .setSslVerification(false)
                        .build();
                fsSettings = FsSettings.builder("esClient").setElasticsearch(elasticsearchWithSecurity).build();
                documentService = new FsCrawlerDocumentServiceElasticsearchImpl(metadataDir, fsSettings);
                documentService.start();
            }
        }

        // We make sure the cluster is running
        try {
            managementService = new FsCrawlerManagementServiceElasticsearchImpl(metadataDir, fsSettings);
            managementService.start();

            String version = managementService.getVersion();
            staticLogger.info("Starting integration tests against an external cluster running elasticsearch [{}]", version);
        } catch (ConnectException e) {
            // If we have an exception here, let's ignore the test
            staticLogger.warn("Integration tests are skipped: [{}]", e.getMessage());
            assumeThat("Integration tests are skipped", e.getMessage(), not(containsString("Connection refused")));
        }
    }

    @AfterClass
    public static void stopServices() throws IOException {
        staticLogger.info("Stopping integration tests against an external cluster");
        if (documentService != null) {
            documentService.close();
            documentService = null;
            staticLogger.info("Document service stopped");
        }
        if (managementService != null) {
            managementService.close();
            managementService = null;
            staticLogger.info("Management service stopped");
        }
    }

    @Before
    public void checkSkipIntegTests() {
        // In case we are running tests from the IDE with the skipIntegTests option, let make sure we are skipping
        // those tests
        RandomizedTest.assumeFalse("skipIntegTests is true. So we are skipping the integration tests.",
                getSystemProperty("skipIntegTests", false));
    }

    protected static final String testCrawlerPrefix = "fscrawler_";

    protected static Elasticsearch generateElasticsearchConfig(String indexName, String indexFolderName, int bulkSize,
                                                               TimeValue timeValue, ByteSizeValue byteSize) {
        Elasticsearch.Builder builder = Elasticsearch.builder()
                .setNodes(Collections.singletonList(new ServerUrl(testClusterUrl)))
                .setBulkSize(bulkSize);

        if (indexName != null) {
            builder.setIndex(indexName);
        }
        if (indexFolderName != null) {
            builder.setIndexFolder(indexFolderName);
        }

        if (timeValue != null) {
            builder.setFlushInterval(timeValue);
        }
        if (byteSize != null) {
            builder.setByteSize(byteSize);
        }

        builder.setUsername(testClusterUser);
        builder.setPassword(testClusterPass);
        builder.setSslVerification(false);

        return builder.build();
    }

    protected static void refresh() throws IOException, ElasticsearchClientException {
        documentService.refresh(null);
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
        return countTestHelper(request, expected, path, TimeValue.timeValueSeconds(20));
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
        staticLogger.info("  ---> Waiting up to {} for {} documents in {}", timeout.toString(),
                expected == null ? "some" : expected, request.getIndex());
        long hits = awaitBusy(() -> {
            long totalHits;

            // Let's search for entries
            try {
                // Make sure we refresh indexed docs before counting
                refresh();
                response[0] = documentService.search(request);
            } catch (RuntimeException | IOException e) {
                staticLogger.warn("error caught", e);
                return -1;
            } catch (ElasticsearchClientException e) {
                // TODO create a NOT FOUND Exception instead
                staticLogger.debug("error caught", e);
                return -1;
            }
            totalHits = response[0].getTotalHits();

            staticLogger.debug("got so far [{}] hits on expected [{}]", totalHits, expected);

            return totalHits;
        }, expected, timeout.millis(), TimeUnit.MILLISECONDS);

        Matcher<Long> matcher;
        if (expected == null) {
            matcher = greaterThan(0L);
        } else {
            matcher = equalTo(expected);
        }

        if (matcher.matches(hits)) {
            staticLogger.debug("     ---> expecting [{}] and got [{}] documents in {}", expected, hits, request.getIndex());
            logContentOfDir(path, Level.DEBUG);
        } else {
            staticLogger.warn("     ---> expecting [{}] but got [{}] documents in {}", expected, hits, request.getIndex());
            logContentOfDir(path, Level.WARN);
        }
        assertThat(hits, matcher);

        return response[0];
    }

    protected static void logContentOfDir(Path path, Level level) {
        if (path != null) {
            try (Stream<Path> stream = Files.walk(path)) {
                stream.forEach(file -> {
                    try {
                        if (Files.isDirectory(file)) {
                            staticLogger.log(level, " * in dir [{}] [{}]",
                                    path.relativize(file).toString(),
                                    Files.getLastModifiedTime(file));
                        } else {
                            staticLogger.log(level, "   - [{}] [{}]",
                                    file.getFileName().toString(),
                                    Files.getLastModifiedTime(file));
                        }
                    } catch (IOException ignored) {
                    }
                });
            } catch (IOException ex) {
                staticLogger.error("can not read content of [{}]:", path);
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

    protected String getCrawlerName() {
        String testName = testCrawlerPrefix.concat(getCurrentClassName()).concat("_").concat(getCurrentTestName());
        return testName.contains(" ") ? split(testName, " ")[0] : testName;
    }

    protected String getRandomCrawlerName() {
        return testCrawlerPrefix.concat(randomAsciiAlphanumOfLength(randomIntBetween(10, 15))).toLowerCase(Locale.ROOT);
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
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

}
