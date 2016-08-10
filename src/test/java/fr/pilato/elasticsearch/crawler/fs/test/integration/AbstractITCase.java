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
import fr.pilato.elasticsearch.crawler.fs.client.JsonUtil;
import fr.pilato.elasticsearch.crawler.fs.client.SearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.SearchResponse;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.Elasticsearch;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.test.AbstractFSCrawlerTestCase;
import fr.pilato.elasticsearch.crawler.fs.util.FsCrawlerUtil;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.io.IOException;
import java.net.ConnectException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
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
 * If the cluster is running with x-pack and using the default username and passwords
 * of x-pack, tests can be run as well.
 */
public abstract class AbstractITCase extends AbstractFSCrawlerTestCase {

    protected final static int HTTP_TEST_PORT = 9400;

    protected static ElasticsearchClient elasticsearchClient;

    protected static boolean securityInstalled;

    protected final static String DEFAULT_USERNAME = "elastic";
    protected final static String DEFAULT_PASSWORD = "changeme";

    @BeforeClass
    public static void startRestClient() throws IOException {
        elasticsearchClient = ElasticsearchClient.builder()
                .addNode(Elasticsearch.Node.builder().setHost("127.0.0.1").setPort(HTTP_TEST_PORT).build())
                .build();

        securityInstalled = testClusterRunning(false);

        if (securityInstalled) {
            // We have a secured cluster. So we need to create a secured client
            // But first we need to close the previous client we built
            if (elasticsearchClient != null) {
                elasticsearchClient.shutdown();
            }

            elasticsearchClient = ElasticsearchClient.builder()
                    .addNode(Elasticsearch.Node.builder().setHost("127.0.0.1").setPort(HTTP_TEST_PORT).build())
                    .setUsername(DEFAULT_USERNAME)
                    .setPassword(DEFAULT_PASSWORD)
                    .build();
            securityInstalled = testClusterRunning(true);
        }
    }

    private static boolean testClusterRunning(boolean withSecurity) throws IOException {
        try {
            Response response = elasticsearchClient.getClient().performRequest("GET", "/", Collections.emptyMap());
            Map<String, Object> asMap = (Map<String, Object>) JsonUtil.asMap(response).get("version");

            staticLogger.info("Starting integration tests against an external cluster running elasticsearch [{}] with {}",
                    asMap.get("number"), withSecurity ? "security" : "no security" );
            return withSecurity;
        } catch (ConnectException e) {
            // If we have an exception here, let's ignore the test
            staticLogger.warn("Integration tests are skipped: [{}]", e.getMessage());
            assumeThat("Integration tests are skipped", e.getMessage(), not(containsString("Connection refused")));
            return withSecurity;
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == 401) {
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
    public static void stopRestClient() throws IOException {
        staticLogger.info("Stopping integration tests against an external cluster");
        if (elasticsearchClient != null) {
            elasticsearchClient.shutdown();
            elasticsearchClient = null;
            staticLogger.info("Elasticsearch client stopped");
        }
    }

    private static final String testCrawlerPrefix = "fscrawler_";

    protected static Elasticsearch generateElasticsearchConfig(String indexName, boolean securityInstalled, int bulkSize,
                                                               TimeValue timeValue) {
        Elasticsearch.Builder builder = Elasticsearch.builder()
                .addNode(Elasticsearch.Node.builder().setHost("127.0.0.1").setPort(HTTP_TEST_PORT).build())
                .setBulkSize(bulkSize)
                .setFlushInterval(timeValue);

        if (indexName != null) {
            builder.setIndex(indexName);
        }

        if (timeValue != null) {
            builder.setFlushInterval(timeValue);
        }

        if (securityInstalled) {
            builder.setUsername(DEFAULT_USERNAME);
            builder.setPassword(DEFAULT_PASSWORD);
        }

        return builder.build();
    }

    protected static void refresh() throws IOException {
        elasticsearchClient.refresh(null);
    }

    /**
     * Check that we have the expected number of docs or at least one if expected is null
     *
     * @param indexName Index we will search in.
     * @param query     QueryString query, like foo:bar. MatchAll if null.
     * @param expected  expected number of docs. Null if at least 1.
     * @param path      Path we are supposed to scan. If we have not accurate results, we display its content
     * @param fields    If we want to add some fields within the response
     * @return the search response if further tests are needed
     * @throws Exception
     */
    public static SearchResponse countTestHelper(final String indexName, String query, final Integer expected, final Path path,
                                                 final String... fields) throws Exception {

        final SearchResponse[] response = new SearchResponse[1];

        // We wait up to 5 seconds before considering a failing test
        staticLogger.info("  ---> Waiting up to 20 seconds for {} documents in index {}", expected == null ? "some" : expected, indexName);
        assertThat("We waited for 20 seconds but no document has been added", awaitBusy(() -> {
            long totalHits;

            // Let's search for entries
            SearchRequest.Builder sr = SearchRequest.builder();

            if (query != null) {
                sr.setQuery(query);
            }

            if (fields.length > 0) {
                sr.setFields(fields);
            }

            try {
                response[0] = elasticsearchClient.search(indexName, FsCrawlerUtil.INDEX_TYPE_DOC, sr.build());
            } catch (IOException e) {
                staticLogger.warn("error caught", e);
                return false;
            }
            staticLogger.trace("result {}", response[0].toString());
            totalHits = response[0].getHits().getTotal();

            if (expected == null) {
                return (totalHits >= 1);
            } else {
                if (expected == totalHits) {
                    staticLogger.debug("     ---> expected [{}] and got [{}] documents in [{}]", expected, totalHits, indexName);
                    return true;
                } else {
                    staticLogger.debug("     ---> expecting [{}] but got [{}] documents in [{}]", expected, totalHits, indexName);
                    if (path != null) {
                        staticLogger.debug("     ---> content of [{}]:", path);
                        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(path)) {
                            for (Path file : directoryStream) {
                                staticLogger.debug("         - {} {}",
                                        file.getFileName().toString(),
                                        Files.getLastModifiedTime(file));
                            }
                        } catch (IOException ex) {
                            staticLogger.error("can not read content of [{}]:", path);
                        }
                    }
                    return false;
                }
            }
        }, 20, TimeUnit.SECONDS), equalTo(true));

        return response[0];
    }

    protected String getCrawlerName() {
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
}
