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

package fr.pilato.elasticsearch.crawler.fs.test.integration.workplacesearch;

import com.jayway.jsonpath.JsonPath;
import fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl;
import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.beans.File;
import fr.pilato.elasticsearch.crawler.fs.beans.FsJobFileHandler;
import fr.pilato.elasticsearch.crawler.fs.beans.Meta;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClient;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientUtil;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentService;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import fr.pilato.elasticsearch.crawler.fs.thirdparty.wpsearch.WPSearchClient;
import org.apache.logging.log4j.Level;
import org.hamcrest.Matcher;
import org.junit.Assume;
import org.junit.BeforeClass;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.carrotsearch.randomizedtesting.RandomizedTest.frequently;
import static fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl.LOOP_INFINITE;
import static fr.pilato.elasticsearch.crawler.fs.client.WorkplaceSearchClientUtil.docToJson;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public abstract class AbstractWorkplaceSearchITCase extends AbstractFsCrawlerITCase {

    private final static String DEFAULT_TEST_WPSEARCH_URL = "http://127.0.0.1:3002";
    protected final static String testWorkplaceUrl = getSystemProperty("tests.workplace.url", DEFAULT_TEST_WPSEARCH_URL);
    protected final static String testWorkplaceUser = getSystemProperty("tests.workplace.user", testClusterUser);
    protected final static String testWorkplacePass = getSystemProperty("tests.workplace.pass", testClusterPass);

    @BeforeClass
    public static void checkWorkplaceSearchCompatible() throws IOException {
        // We must check that we can run the tests.
        // For example, with a 6.x cluster, this is not possible as Workplace Search engine does not exist
        // and thus is not started.
        ElasticsearchClient client = ElasticsearchClientUtil.getInstance(null, FsSettings.builder("foo").build());
        String version = client.compatibleVersion();
        Assume.assumeThat("We can not run workplace search tests on a version different than 7",
                version, equalTo("7"));
        HttpURLConnection urlConnection = (HttpURLConnection) new URL(testWorkplaceUrl).openConnection();
        urlConnection.getResponseCode();
    }

    protected FsCrawlerImpl startCrawler(final String jobName, FsSettings fsSettings, TimeValue duration)
            throws Exception {
        logger.info("  --> starting crawler [{}]", jobName);

        crawler = new FsCrawlerImpl(
                metadataDir,
                fsSettings,
                LOOP_INFINITE,
                fsSettings.getRest() != null);
        crawler.start();

        // We wait up to X seconds before considering a failing test
        assertThat("Job meta file [" + jobName + "] should exists in ~/.fscrawler...", awaitBusy(() -> {
            try {
                new FsJobFileHandler(metadataDir).read(jobName);
                return true;
            } catch (IOException e) {
                return false;
            }
        }, duration.seconds(), TimeUnit.SECONDS), equalTo(true));

        refresh();

        try (WPSearchClient wpClient = createClient()) {
            countTestHelper(wpClient, null, duration);
        }

        return crawler;
    }

    /**
     * Check that we have the expected number of docs or at least one if expected is null
     *
     * @param documentService The Document Service to run searches on.
     * @param request   Elasticsearch request to run.
     * @param expected  expected number of docs. Null if at least 1.
     * @param path      Path we are supposed to scan. If we have not accurate results, we display its content
     * @param timeout   Time before we declare a failure
     * @return the search response if further tests are needed
     * @throws Exception in case of error
     */
    public static ESSearchResponse countTestHelper(final FsCrawlerDocumentService documentService,
                                                   final ESSearchRequest request, final Long expected, final Path path,
                                                   final TimeValue timeout) throws Exception {

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
                response[0] = documentService.getClient().search(request);
            } catch (RuntimeException| IOException e) {
                staticLogger.warn("error caught", e);
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

    /**
     * Check that we have the expected number of docs or at least one if expected is null
     *
     * @param wpClient  Elasticsearch request to run.
     * @param expected  expected number of docs. Null if at least 1.
     * @param timeout   Time before we declare a failure
     * @return the search response if further tests are needed
     * @throws Exception in case of error
     */
    public static String countTestHelper(final WPSearchClient wpClient, final Long expected, final TimeValue timeout) throws Exception {

        final String[] response = new String[1];

        // We wait before considering a failing test
        staticLogger.info("  ---> Waiting up to {} for {} documents in workplace search", timeout.toString(),
                expected == null ? "some" : expected);
        long hits = awaitBusy(() -> {
            long totalHits;

            // Let's search for entries
            try {
                refresh();
                response[0] = wpClient.search(null, null);
            } catch (RuntimeException | IOException e) {
                staticLogger.warn("error caught", e);
                return -1;
            }

            // We need to tell JsonPath to return an int
            totalHits = JsonPath.<Integer>read(response[0], "$.meta.page.total_results");
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
            staticLogger.debug("     ---> expecting [{}] and got [{}] documents", expected, hits);
        } else {
            staticLogger.warn("     ---> expecting [{}] but got [{}] documents", expected, hits);
        }
        assertThat(hits, matcher);

        return response[0];
    }

    protected static WPSearchClient createClient() {
        staticLogger.info("  --> creating the workplace search custom source client");
        Path jobMappingDir = rootTmpDir.resolve("wpsearch").resolve("_mappings");
        WPSearchClient client = new WPSearchClient(metadataDir, jobMappingDir)
                .withHost(testWorkplaceUrl)
                .withUsername(null, testWorkplaceUser)
                .withPassword(null, testWorkplacePass);
        client.start();
        return client;
    }

    protected static void cleanExistingCustomSources(String sourceName) {
        if (!testKeepData) {
            try (WPSearchClient client = createClient()) {
                List<String> sourceIds = client.getCustomSourcesByName(sourceName);
                for (String sourceId : sourceIds) {
                    client.removeCustomSource(sourceId);
                }
            }
        }
    }

    protected static String initSource(String sourceName) throws Exception {
        staticLogger.info("  --> creating the workplace search custom source {}", sourceName);
        try (WPSearchClient client = createClient()) {
            cleanExistingCustomSources(sourceName);
            // Let's create a new source
            String customSourceId = client.createCustomSource(sourceName);
            assertThat(customSourceId, not(isEmptyOrNullString()));

            staticLogger.debug("  --> we will be using custom source {}.", customSourceId);
            return customSourceId;
        }
    }

    protected static String getSourceIdFromSourceName(String sourceName) throws Exception {
        staticLogger.info("  --> getting the workplace search custom source id from name {}", sourceName);
        try (WPSearchClient client = createClient()) {
            List<String> sources = client.getCustomSourcesByName(sourceName);
            assertThat(sources, not(empty()));

            staticLogger.debug("  --> custom source name {} has id {}.", sourceName, sources.get(0));
            return sources.get(0);
        }
    }

    protected static Map<String, Object> fakeDocument(String id, String text, String lang, String filename, String... tags) {
        Doc doc = new Doc();

        // Index content
        doc.setContent("Content for " + text);

        // Index main metadata
        Meta meta = new Meta();

        // Sometimes we won't generate a title but will let the system create a title from the filename
        if (frequently()) {
            meta.setTitle("Title for " + text);
        }
        meta.setAuthor("Mister " + text);
        meta.setKeywords(Arrays.asList(tags));
        meta.setLanguage(lang);
        meta.setComments("Comments for " + text);
        doc.setMeta(meta);

        // Index main file attributes
        File file = new File();
        file.setFilename(filename + ".txt");
        file.setContentType("text/plain");
        file.setExtension("txt");
        file.setIndexedChars(text.length());
        file.setFilesize(text.length() + 10L);
        file.setCreated(FsCrawlerUtil.localDateTimeToDate(LocalDateTime.now()));
        file.setLastModified(FsCrawlerUtil.localDateTimeToDate(LocalDateTime.now()));
        doc.setFile(file);

        // Index main path attributes
        fr.pilato.elasticsearch.crawler.fs.beans.Path path = new fr.pilato.elasticsearch.crawler.fs.beans.Path();
        path.setVirtual("/" + filename + ".txt");
        path.setReal("/tmp/es/" + filename + ".txt");
        doc.setPath(path);

        return docToJson(id, doc, "http://127.0.0.1");
    }
}
