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

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakFilters;
import com.carrotsearch.randomizedtesting.annotations.ThreadLeakLingering;
import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import fr.pilato.elasticsearch.containers.ElasticsearchContainer;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClient;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.rest.RestJsonProvider;
import fr.pilato.elasticsearch.crawler.fs.settings.Elasticsearch;
import fr.pilato.elasticsearch.crawler.fs.settings.Rest;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import fr.pilato.elasticsearch.crawler.fs.test.framework.TestContainerThreadFilter;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.logging.log4j.Level;
import org.elasticsearch.Version;
import org.elasticsearch.action.main.MainResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.hamcrest.Matcher;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.testcontainers.containers.wait.HttpWaitStrategy;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.copyDefaultResources;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.copyDirs;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.unzip;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeThat;

/**
 * Integration tests are using Docker behind the scene to start an elasticsearch instance
 * running by default locally on 127.0.0.1:9200
 *
 * Note that all existing data in this cluster might be removed
 *
 * If you want to run tests against a remote cluster, please launch tests using
 * tests.cluster.host and tests.cluster.port properties:
 *
 * mvn clean install -Dtests.cluster.host=127.0.0.1 -Dtests.cluster.port=9400
 *
 * When tests.cluster.host is set, the internal Docker container is not ran anymore.
 *
 * You can choose running against http or https with tests.cluster.scheme (defaults to HTTP):
 *
 * mvn clean install -Dtests.cluster.scheme=HTTPS
 *
 * If the cluster is running with x-pack and using the default username and passwords
 * of x-pack, tests can be run as well. You can overwrite default username and password
 * with tests.cluster.user and tests.cluster.password
 *
 * mvn clean install -Dtests.cluster.user=elastic -Dtests.cluster.pass=changeme
 *
 * All integration tests might be skipped if the cluster is not running
 */
@ThreadLeakFilters(filters = {TestContainerThreadFilter.class})
@ThreadLeakScope(ThreadLeakScope.Scope.SUITE)
@ThreadLeakLingering(linger = 5000) // 5 sec lingering
public abstract class AbstractITCase extends AbstractFSCrawlerTestCase {

    static Path metadataDir = null;

    private static ElasticsearchContainer container;
    private static RestClient esRestClient;

    @BeforeClass
    public static void createFsCrawlerJobDir() throws IOException, URISyntaxException {
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

    private final static Integer DEFAULT_TEST_CLUSTER_PORT = 9200;
    private final static String DEFAULT_USERNAME = "elastic";
    private final static String DEFAULT_PASSWORD = "changeme";
    private final static Integer DEFAULT_TEST_REST_PORT = 8080;

    static ElasticsearchClient elasticsearchClient;

    private static String testClusterHost;
    private static int testClusterPort;
    private final static String testClusterUser = System.getProperty("tests.cluster.user", DEFAULT_USERNAME);
    private final static String testClusterPass = System.getProperty("tests.cluster.pass", DEFAULT_PASSWORD);
    private final static Elasticsearch.Node.Scheme testClusterScheme = Elasticsearch.Node.Scheme.parse(System.getProperty("tests.cluster.scheme", Elasticsearch.Node.Scheme.HTTP.toString()));
    final static int testRestPort = Integer.parseInt(System.getProperty("tests.rest.port", DEFAULT_TEST_REST_PORT.toString()));
    static Elasticsearch elasticsearchWithSecurity;

    private static WebTarget target;
    private static Client client;

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
     * Copy test documents to the test dir so we will be able to run tests against them
     * @param target            target directory.
     * @param sourceDirName     source subdir name where files will be copied or extracted to.
     * @param marker            one of the filename which is available in the classpath which contains
     *                          the test documents or within a jar
     * @throws IOException In case of IO problem
     */
    private static void copyTestDocumentsToTargetDir(Path target, String sourceDirName, String marker) throws IOException {
        URL resource = AbstractFSCrawlerTestCase.class.getResource(marker);

        switch (resource.getProtocol()) {
            case "file": {
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
            case "jar": {
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
            default:
                fail("Unknown protocol for IT document sources: " + resource.getProtocol());
                break;
        }
    }

    @BeforeClass
    public static void startElasticsearchRestClient() throws IOException {
        testClusterPort = Integer.parseInt(System.getProperty("tests.cluster.port", DEFAULT_TEST_CLUSTER_PORT.toString()));
        testClusterHost = System.getProperty("tests.cluster.host");

        if (testClusterHost == null) {
            // We start an elasticsearch Docker instance
            Properties props = new Properties();
            props.load(AbstractITCase.class.getResourceAsStream("/elasticsearch.version.properties"));
            container = new ElasticsearchContainer().withVersion(props.getProperty("version"));
            container.withEnv("ELASTIC_PASSWORD", testClusterPass);
            container.setWaitStrategy(
                    new HttpWaitStrategy()
                            .forStatusCode(200)
                            .withBasicCredentials(testClusterUser, testClusterPass)
                            .withStartupTimeout(Duration.ofSeconds(90)));
            container.start();

            testClusterHost = container.getHost().getHostName();
            testClusterPort = container.getFirstMappedPort();
        } else {
            testClusterPort = Integer.parseInt(System.getProperty("tests.cluster.port", DEFAULT_TEST_CLUSTER_PORT.toString()));
        }

        if (testClusterHost == null) {
            Thread.dumpStack();
        }

        // We build the elasticsearch High Level Client based on the parameters
        elasticsearchClient = new ElasticsearchClient(getClientBuilder(
                new HttpHost(testClusterHost, testClusterPort), testClusterUser, testClusterPass));
        elasticsearchWithSecurity = Elasticsearch.builder()
                .addNode(Elasticsearch.Node.builder()
                        .setHost(testClusterHost).setPort(testClusterPort).setScheme(testClusterScheme).build())
                .setUsername(testClusterUser)
                .setPassword(testClusterPass)
                .build();

        // We make sure the cluster is running
        testClusterRunning();

        // We set what will be elasticsearch behavior as it depends on the cluster version
        elasticsearchClient.setElasticsearchBehavior();
    }

    @BeforeClass
    public static void startRestClient() {
        // create the client
        client = ClientBuilder.newBuilder()
                .register(MultiPartFeature.class)
                .register(RestJsonProvider.class)
                .register(JacksonFeature.class)
                .build();

        target = client.target(Rest.builder().setPort(testRestPort).build().url());
    }

    @AfterClass
    public static void stopRestClient() throws IOException {
        if (client != null) {
            client.close();
            client = null;
        }

        if (esRestClient != null) {
            esRestClient.close();
            esRestClient = null;
        }

        if (container != null) {
            container.close();
            container = null;
        }
    }

    private static RestClientBuilder getClientBuilder(HttpHost host, String username, String password) {
        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials(username, password));

        return RestClient.builder(host)
                .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
    }

    private static void testClusterRunning() throws IOException {
        try {
            MainResponse info = elasticsearchClient.info();
            staticLogger.info("Starting integration tests against an external cluster running elasticsearch [{}]", info.getVersion());
        } catch (ConnectException e) {
            // If we have an exception here, let's ignore the test
            staticLogger.warn("Integration tests are skipped: [{}]", e.getMessage());
            assumeThat("Integration tests are skipped", e.getMessage(), not(containsString("Connection refused")));
        }
    }

    @AfterClass
    public static void stopElasticsearchClient() throws IOException {
        staticLogger.info("Stopping integration tests against an external cluster");
        if (elasticsearchClient != null) {
            elasticsearchClient.shutdown();
            elasticsearchClient = null;
            staticLogger.info("Elasticsearch client stopped");
        }
    }

    private static final String testCrawlerPrefix = "fscrawler_";

    static Elasticsearch generateElasticsearchConfig(String indexName, String indexFolderName, int bulkSize,
                                                     TimeValue timeValue) {
        Elasticsearch.Builder builder = Elasticsearch.builder()
                .addNode(Elasticsearch.Node.builder().setHost(testClusterHost).setPort(testClusterPort).setScheme(testClusterScheme).build())
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

        builder.setUsername(testClusterUser);
        builder.setPassword(testClusterPass);

        return builder.build();
    }

    protected static void refresh() throws IOException {
        elasticsearchClient.refresh(null);
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
    public static SearchResponse countTestHelper(final SearchRequest request, final Long expected, final Path path) throws Exception {
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
    public static SearchResponse countTestHelper(final SearchRequest request, final Long expected, final Path path, final TimeValue timeout) throws Exception {

        final SearchResponse[] response = new SearchResponse[1];

        // We wait before considering a failing test
        staticLogger.info("  ---> Waiting up to {} for {} documents in {}", timeout.toString(),
                expected == null ? "some" : expected, request.indices());
        long hits = awaitBusy(() -> {
            long totalHits;

            // Let's search for entries
            try {
                response[0] = elasticsearchClient.search(request);
            } catch (IOException e) {
                staticLogger.warn("error caught", e);
                return -1;
            }
            staticLogger.trace("result {}", response[0].toString());
            totalHits = response[0].getHits().getTotalHits();

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
            staticLogger.debug("     ---> expecting [{}] and got [{}] documents in {}", expected, hits, request.indices());
            logContentOfDir(path, Level.DEBUG);
        } else {
            staticLogger.warn("     ---> expecting [{}] but got [{}] documents in {}", expected, hits, request.indices());
            logContentOfDir(path, Level.WARN);
        }
        assertThat(hits, matcher);

        return response[0];
    }

    static void logContentOfDir(Path path, Level level) {
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

    String getCrawlerName() {
        String testName = testCrawlerPrefix.concat(getCurrentTestName());
        return testName.contains(" ") ? split(testName, " ")[0] : testName;
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

    public static <T> T restCall(String path, Class<T> clazz) {
        if (staticLogger.isDebugEnabled()) {
            String response = target.path(path).request().get(String.class);
            staticLogger.debug("Rest response: {}", response);
        }
        return target.path(path).request().get(clazz);
    }

    public static <T> T restCall(String path, FormDataMultiPart mp, Class<T> clazz, Map<String, Object> params) {
        WebTarget targetPath = target.path(path);
        params.forEach(targetPath::queryParam);

        return targetPath.request(MediaType.MULTIPART_FORM_DATA)
                .accept(MediaType.APPLICATION_JSON)
                .post(Entity.entity(mp, mp.getMediaType()), clazz);
    }

    public static void deleteRecursively(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
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

    protected void assumeVersion6AtLeast() throws IOException {
        assumeThat("Sadly the HL Rest Client 6.x can not always be used with 5.x versions", elasticsearchClient.info().getVersion().onOrAfter(Version.V_6_0_0), is(true));
    }
}
