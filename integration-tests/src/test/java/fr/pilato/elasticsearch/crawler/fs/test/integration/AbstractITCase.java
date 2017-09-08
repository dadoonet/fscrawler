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

import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClient;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.Elasticsearch;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.Rest;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.rest.RestJsonProvider;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import org.apache.logging.log4j.Level;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.main.MainResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.hamcrest.Matcher;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.net.ConnectException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.not;
import static org.junit.Assume.assumeThat;

/**
 * All integration tests assume that an elasticsearch cluster is already running on
 * the machine and one of the nodes is available at 127.0.0.1:9400.
 *
 * You can run one by launching:
 * bin/elasticsearch -Des.http.port=9400
 * bin/elasticsearch -Ehttp.port=9400
 *
 * The node can be run manually or when using maven, it's automatically started as
 * during the pre-integration phase and stopped after the tests.
 *
 * Note that all existing data in this cluster might be removed
 *
 * If you want to run tests against a remote cluster, please launch tests using
 * tests.cluster.host and tests.cluster.port properties:
 *
 * mvn clean install -Dtests.cluster.host=127.0.0.1 -Dtests.cluster.port=9400
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
public abstract class AbstractITCase extends AbstractFSCrawlerTestCase {

    private final static Integer DEFAULT_TEST_CLUSTER_PORT = 9400;
    private final static String DEFAULT_TEST_CLUSTER_HOST = "127.0.0.1";
    private final static String DEFAULT_USERNAME = "elastic";
    private final static String DEFAULT_PASSWORD = "changeme";
    private final static Integer DEFAULT_TEST_REST_PORT = 8080;

    static ElasticsearchClient elasticsearchClient;

    static boolean securityInstalled;

    private final static String testClusterHost = System.getProperty("tests.cluster.host", DEFAULT_TEST_CLUSTER_HOST);
    private final static int testClusterPort = Integer.parseInt(System.getProperty("tests.cluster.port", DEFAULT_TEST_CLUSTER_PORT.toString()));
    private final static String testClusterUser = System.getProperty("tests.cluster.user", DEFAULT_USERNAME);
    private final static String testClusterPass = System.getProperty("tests.cluster.pass", DEFAULT_PASSWORD);
    private final static Elasticsearch.Node.Scheme testClusterScheme = Elasticsearch.Node.Scheme.parse(System.getProperty("tests.cluster.scheme", Elasticsearch.Node.Scheme.HTTP.toString()));
    private final static int testRestPort =
            Integer.parseInt(System.getProperty("tests.rest.port", DEFAULT_TEST_REST_PORT.toString()));
    static Rest rest = Rest.builder().setPort(testRestPort).build();
    protected final static Elasticsearch elasticsearch = Elasticsearch.builder()
            .addNode(Elasticsearch.Node.builder().setHost(testClusterHost).setPort(testClusterPort).setScheme(testClusterScheme).build())
            .build();
    final static Elasticsearch elasticsearchWithSecurity = Elasticsearch.builder()
            .addNode(Elasticsearch.Node.builder().setHost(testClusterHost).setPort(testClusterPort).setScheme(testClusterScheme).build())
            .setUsername(testClusterUser)
            .setPassword(testClusterPass)
            .build();

    private static WebTarget target;
    private static Client client;

    @BeforeClass
    public static void startElasticsearchRestClient() throws IOException {
        elasticsearchClient = new ElasticsearchClient(ElasticsearchClient.buildRestClient(elasticsearch));

        securityInstalled = testClusterRunning(false);
        if (securityInstalled) {
            // We have a secured cluster. So we need to create a secured client
            // But first we need to close the previous client we built
            if (elasticsearchClient != null) {
                elasticsearchClient.shutdown();
            }

            elasticsearchClient = new ElasticsearchClient(ElasticsearchClient.buildRestClient(elasticsearchWithSecurity));
            securityInstalled = testClusterRunning(true);
        }

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

        target = client.target(rest.url());
    }

    @AfterClass
    public static void stopRestClient() {
        if (client != null) {
            client.close();
            client = null;
        }
    }

    private static boolean testClusterRunning(boolean withSecurity) throws IOException {
        try {
            MainResponse info = elasticsearchClient.info();
            staticLogger.info("Starting integration tests against an external cluster running elasticsearch [{}] with {}",
                    info.getVersion(), withSecurity ? "security" : "no security" );
            return withSecurity;
        } catch (ConnectException e) {
            // If we have an exception here, let's ignore the test
            staticLogger.warn("Integration tests are skipped: [{}]", e.getMessage());
            assumeThat("Integration tests are skipped", e.getMessage(), not(containsString("Connection refused")));
            return withSecurity;
        } catch (ElasticsearchStatusException e) {
            if (e.status().getStatus() == 401) {
                staticLogger.debug("The cluster is secured. So we need to build a client with security", e);
                return true;
            } else {
                staticLogger.error("Full error is", e);
                throw e;
            }
        } catch (IOException e) {
            staticLogger.error("Full error is", e);
            throw e;
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

    static Elasticsearch generateElasticsearchConfig(String indexName, String indexFolderName, boolean securityInstalled, int bulkSize,
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

        if (securityInstalled) {
            builder.setUsername(testClusterUser);
            builder.setPassword(testClusterPass);
        }

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
        staticLogger.info("  ---> Waiting up to {} seconds for {} documents in {}", timeout.toString(),
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
            staticLogger.debug("     ---> expecting [{}] but got [{}] documents in {}", expected, hits, request.indices());
            logContentOfDir(path, Level.DEBUG);
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
}
