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

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl;
import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.beans.File;
import fr.pilato.elasticsearch.crawler.fs.beans.FsJobFileHandler;
import fr.pilato.elasticsearch.crawler.fs.beans.Meta;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientException;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import fr.pilato.elasticsearch.crawler.fs.thirdparty.wpsearch.WPSearchClient;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.ServiceUnavailableException;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.parser.ocr.TesseractOCRParser;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;

import java.io.IOException;
import java.net.ConnectException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.carrotsearch.randomizedtesting.RandomizedTest.frequently;
import static fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl.LOOP_INFINITE;
import static fr.pilato.elasticsearch.crawler.fs.client.WorkplaceSearchClientUtil.docToJson;
import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.parseJsonAsDocumentContext;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNoException;

public abstract class AbstractWorkplaceSearchITCase extends AbstractFsCrawlerITCase {

    private final static String DEFAULT_TEST_WPSEARCH_URL = "http://127.0.0.1:3002";
    protected final static String testWorkplaceUrl = getSystemProperty("tests.workplace.url", DEFAULT_TEST_WPSEARCH_URL);
    protected final static String testWorkplaceUser = getSystemProperty("tests.workplace.user", testClusterUser);
    protected final static String testWorkplacePass = getSystemProperty("tests.workplace.pass", testClusterPass);

    protected String sourceName;
    protected String sourceId;

    static boolean isOcrAvailable;

    @BeforeClass
    public static void setOcrAvailable() {
        try {
            isOcrAvailable = new TesseractOCRParser().hasTesseract();
        } catch (TikaConfigException e) {
            staticLogger.warn("Can not configure Tesseract for tests, so we are supposing it won't be available");
            isOcrAvailable = false;
        }
    }

    @BeforeClass
    public static void checkWorkplaceSearchIsRunning() {
        try (WPSearchClient wpClient = createClient()) {
            wpClient.getVersion();
        } catch (ServiceUnavailableException e) {
            assumeNoException("We can not run the Workplace Search tests against this cluster. " +
                    "Check that you have workplace search running at " + testWorkplaceUrl, e);
        } catch (ProcessingException e) {
            if (e.getCause() instanceof ConnectException) {
                assumeNoException("We can not run the Workplace Search tests against this cluster. " +
                        "Check that you have workplace search running at " + testWorkplaceUrl, e);
            } else {
                staticLogger.error("We got an unexpected exception when running the Workplace Search tests against this cluster. " +
                        "Check that you have workplace search running at {}", testWorkplaceUrl);
                staticLogger.error("Stacktrace:", e);
                fail("We got an unexpected exception when running the Workplace Search tests against this cluster. " +
                        "Check that you have workplace search running at " + testWorkplaceUrl);
            }
        }
    }

    @SuppressWarnings("EmptyMethod")
    @BeforeClass
    public static void cleanAllTestResources() {
        // Just for dev only. In case we need to remove tons of workplace search custom sources at once
        // cleanExistingCustomSources(testCrawlerPrefix + "*");
    }

    @Before
    public void generateJobName() {
        sourceName = getRandomCrawlerName();
        cleanExistingCustomSources(sourceName);
        sourceId = null;
    }

    @After
    public void cleanUpCustomSource() {
        if (sourceId != null) {
            cleanExistingCustomSource(sourceId);
        }
        if (sourceName != null) {
            cleanExistingCustomSources(sourceName);
        }
    }

    protected FsCrawlerImpl startCrawler(final String jobName, final String customSourceId, FsSettings fsSettings, TimeValue duration)
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
            countTestHelper(wpClient, customSourceId, null, duration);
        }

        return crawler;
    }

    /**
     * Check that we have the expected number of docs or at least one if expected is null
     *
     * @param wpClient  Elasticsearch request to run.
     * @param sourceId  The custom source id if any
     * @param expected  expected number of docs. Null if at least 1.
     * @param timeout   Time before we declare a failure
     * @return the search response if further tests are needed
     * @throws Exception in case of error
     */
    public static String countTestHelper(final WPSearchClient wpClient, final String sourceId, final Long expected, final TimeValue timeout) throws Exception {

        final String[] response = new String[1];

        // We wait before considering a failing test
        staticLogger.info("  ---> Waiting up to {} for {} documents in workplace search", timeout.toString(),
                expected == null ? "some" : expected);
        long hits = awaitBusy(() -> {
            long totalHits;

            // Let's search for entries
            try {
                refresh();
                response[0] = wpClient.search(null, sourceId == null ? null : Collections.singletonMap("content_source_id", List.of(sourceId)));
            } catch (RuntimeException | IOException | ElasticsearchClientException e) {
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
        return createClient(testWorkplaceUrl);
    }

    protected static WPSearchClient createClient(String host) {
        staticLogger.info("  --> creating the workplace search custom source client");
        Path jobMappingDir = rootTmpDir.resolve("wpsearch").resolve("_mappings");
        WPSearchClient client = new WPSearchClient(metadataDir, jobMappingDir)
                .withHost(host)
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

    protected static void cleanExistingCustomSource(String sourceId) {
        if (!testKeepData) {
            try (WPSearchClient client = createClient()) {
                client.removeCustomSource(sourceId);
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

    protected static String getSourceIdFromSourceName(String sourceName) {
        staticLogger.info("  --> getting the workplace search custom source id from name {}", sourceName);
        try (WPSearchClient client = createClient()) {
            List<String> sources = client.getCustomSourcesByName(sourceName);
            assertThat(sources, not(empty()));

            staticLogger.debug("  --> custom source name {} has id {}.", sourceName, sources.get(0));
            return sources.get(0);
        }
    }

    protected static Map<String, Object> fakeDocumentAsMap(String id, String text, String lang, String filename, String... tags) {
        return docToJson(id, fakeDocument(text, lang, filename, tags), "http://127.0.0.1");
    }

    protected static Doc fakeDocument(String text, String lang, String filename, String... tags) {
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

        return doc;
    }

    protected static void checker(String json, int results, List<String> filenames, List<String> texts) {
        staticLogger.fatal("{}", json);

        DocumentContext document = parseJsonAsDocumentContext(json);
        assertThat(document.read("$.meta.page.total_results"), is(results));

        for (int i = 0; i < results; i++) {
            documentChecker(document, "$.results[" + i + "]", filenames, texts);
        }
    }

    protected static void documentChecker(DocumentContext document, String prefix, List<String> filenames, List<String> texts) {
        List<String> urls = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        List<String> bodies = new ArrayList<>();
        List<String> paths = new ArrayList<>();

        filenames.forEach((filename) -> {
            urls.add("http://127.0.0.1/" + filename);
            titles.add(filename);
            paths.add("/tmp/es/" + filename);
        });

        texts.forEach((text) -> {
            titles.add("Title for " + text);
            bodies.add("Content for " + text);
        });

        propertyChecker(document, prefix + ".title", isOneOf(titles.toArray()));
        propertyChecker(document, prefix + ".body", isOneOf(bodies.toArray()));
        propertyChecker(document, prefix + ".size", notNullValue());
        propertyChecker(document, prefix + ".text_size", notNullValue());
        propertyChecker(document, prefix + ".mime_type", startsWith("text/plain"));
        propertyChecker(document, prefix + ".name", isOneOf(filenames.toArray()));
        propertyChecker(document, prefix + ".extension", is("txt"));
        propertyChecker(document, prefix + ".path", isOneOf(paths.toArray()));
        propertyChecker(document, prefix + ".url", isOneOf(urls.toArray()));
        propertyChecker(document, prefix + ".created_at", notNullValue());
        propertyChecker(document, prefix + ".last_modified", notNullValue());
    }

    private static void propertyChecker(DocumentContext document, String fieldName, Matcher<?> matcher) {
        try {
            // We try the .raw field if the document is coming from the search API
            assertThat(document.read(fieldName + ".raw"), matcher);
        } catch (PathNotFoundException e) {
            // We fall back to the field name if the document is coming from the get API
            assertThat(document.read(fieldName), matcher);
        }
    }
}
