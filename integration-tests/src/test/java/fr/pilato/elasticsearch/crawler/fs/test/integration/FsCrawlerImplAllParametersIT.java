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

import com.google.common.base.Charsets;
import fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl;
import fr.pilato.elasticsearch.crawler.fs.meta.doc.Attributes;
import fr.pilato.elasticsearch.crawler.fs.meta.doc.Doc;
import fr.pilato.elasticsearch.crawler.fs.meta.doc.File;
import fr.pilato.elasticsearch.crawler.fs.meta.doc.Meta;
import fr.pilato.elasticsearch.crawler.fs.meta.job.FsJobFileHandler;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.Elasticsearch;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.Fs;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.Percentage;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.Rest;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.Server;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.rest.UploadResponse;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.logging.log4j.Level;
import org.elasticsearch.Version;
import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.node.Node;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.threadpool.ThreadPool;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.carrotsearch.randomizedtesting.RandomizedTest.randomAsciiOfLengthBetween;
import static com.carrotsearch.randomizedtesting.RandomizedTest.randomLongBetween;
import static fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl.LOOP_INFINITE;
import static fr.pilato.elasticsearch.crawler.fs.client.JsonUtil.extractFromPath;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.INDEX_SUFFIX_FOLDER;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.copyDirs;
import static fr.pilato.elasticsearch.crawler.fs.test.integration.FsCrawlerRestIT.uploadFile;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isOneOf;
import static org.hamcrest.Matchers.iterableWithSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeThat;
import static org.junit.Assume.assumeTrue;

/**
 * Test all crawler settings
 */
public class FsCrawlerImplAllParametersIT extends AbstractITCase {

    private FsCrawlerImpl crawler = null;
    private Path currentTestResourceDir;

    private static final Path DEFAULT_RESOURCES =  Paths.get(getUrl("samples", "common"));

    /**
     * We suppose that each test has its own set of files. Even if we duplicate them, that will make the code
     * more readable.
     * The temp folder which is used as a root is automatically cleaned after the test so we don't have to worry
     * about it.
     */
    @Before
    public void copyTestResources() throws IOException, URISyntaxException {
        Path testResourceTarget = rootTmpDir.resolve("resources");
        if (Files.notExists(testResourceTarget)) {
            Files.createDirectory(testResourceTarget);
        }

        String currentTestName = getCurrentTestName();
        // We copy files from the src dir to the temp dir
        staticLogger.info("  --> Launching test [{}]", currentTestName);
        String url = getUrl("samples", currentTestName);
        Path from = Paths.get(url);
        currentTestResourceDir = testResourceTarget.resolve(currentTestName);

        if (Files.exists(from)) {
            staticLogger.debug("  --> Copying test resources from [{}]", from);
        } else {
            staticLogger.debug("  --> Copying test resources from [{}]", DEFAULT_RESOURCES);
            from = DEFAULT_RESOURCES;
        }

        copyDirs(from, currentTestResourceDir);

        staticLogger.debug("  --> Test resources ready in [{}]", currentTestResourceDir);
    }

    @Before
    public void cleanExistingIndex() throws IOException {
        logger.info(" -> Removing existing index [{}*]", getCrawlerName());
        elasticsearchClient.deleteIndex(getCrawlerName() + "*");
    }

    @After
    public void shutdownCrawler() throws InterruptedException, IOException {
        stopCrawler();
    }

    private Fs.Builder startCrawlerDefinition() throws IOException {
        return startCrawlerDefinition(currentTestResourceDir.toString(), TimeValue.timeValueSeconds(5));
    }

    private Fs.Builder startCrawlerDefinition(TimeValue updateRate) throws IOException {
        return startCrawlerDefinition(currentTestResourceDir.toString(), updateRate);
    }

    private Fs.Builder startCrawlerDefinition(String dir) throws IOException {
        return startCrawlerDefinition(dir, TimeValue.timeValueSeconds(5));
    }

    private Fs.Builder startCrawlerDefinition(String dir, TimeValue updateRate) {
        logger.info("  --> creating crawler for dir [{}]", dir);
        return Fs
                .builder()
                .setUrl(dir)
                .setUpdateRate(updateRate);
    }

    private Elasticsearch endCrawlerDefinition(String indexName) {
        return endCrawlerDefinition(indexName, indexName + INDEX_SUFFIX_FOLDER);
    }

    private Elasticsearch endCrawlerDefinition(String indexDocName, String indexFolderName) {
        return generateElasticsearchConfig(indexDocName, indexFolderName, securityInstalled, 1, null);
    }

    private void startCrawler() throws Exception {
        startCrawler(getCrawlerName());
    }

    private void startCrawler(final String jobName) throws Exception {
        startCrawler(jobName, startCrawlerDefinition().build(), endCrawlerDefinition(jobName), null);
    }

    private FsCrawlerImpl startCrawler(final String jobName, Fs fs, Elasticsearch elasticsearch, Server server) throws Exception {
        return startCrawler(jobName, fs, elasticsearch, server, null, TimeValue.timeValueSeconds(10));
    }

    private FsCrawlerImpl startCrawler(final String jobName, Fs fs, Elasticsearch elasticsearch, Server server, Rest rest, TimeValue duration)
            throws Exception {
        logger.info("  --> starting crawler [{}]", jobName);

        // TODO do this rarely() createIndex(jobName);

        crawler = new FsCrawlerImpl(
                metadataDir,
                FsSettings.builder(jobName).setElasticsearch(elasticsearch).setFs(fs).setServer(server).setRest(rest).build(),
                LOOP_INFINITE,
                rest != null);
        crawler.start();

        // We wait up to X seconds before considering a failing test
        assertThat("Job meta file should exists in ~/.fscrawler...", awaitBusy(() -> {
            try {
                new FsJobFileHandler(metadataDir).read(jobName);
                return true;
            } catch (IOException e) {
                return false;
            }
        }, duration.seconds(), TimeUnit.SECONDS), equalTo(true));

        countTestHelper(new SearchRequest(jobName), null, null);

        // Make sure we refresh indexed docs before launching tests
        refresh();

        return crawler;
    }

    private void stopCrawler() throws InterruptedException, IOException {
        if (crawler != null) {
            staticLogger.info("  --> Stopping crawler");
            crawler.close();
            crawler = null;
        }
    }

    @Test
    public void test_default_settings() throws Exception {
        startCrawler();
    }

    @Test
    public void test_filesize() throws Exception {
        startCrawler();

        SearchResponse searchResponse = countTestHelper(new SearchRequest(getCrawlerName()), 1L, null);
        for (SearchHit hit : searchResponse.getHits().getHits()) {
            Map<String, Object> file = (Map<String, Object>) hit.getSourceAsMap().get(Doc.FIELD_NAMES.FILE);
            assertThat(file, notNullValue());
            assertThat(file.get(File.FIELD_NAMES.FILESIZE), is(12230));
        }
    }

    @Test
    public void test_filesize_limit() throws Exception {
        Fs fs = startCrawlerDefinition()
                .setIndexedChars(new Percentage(7))
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        SearchResponse searchResponse = countTestHelper(new SearchRequest(getCrawlerName()), 1L, null);
        for (SearchHit hit : searchResponse.getHits().getHits()) {
            Object content = hit.getSourceAsMap().get(Doc.FIELD_NAMES.CONTENT);
            Object indexedChars = extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.INDEXED_CHARS);
            assertThat(content, notNullValue());
            assertThat(indexedChars, notNullValue());

            // Our original text should be truncated
            assertThat(content, is("Novo de"));
            assertThat(indexedChars, is(7));
        }
    }

    @Test
    public void test_filesize_limit_percentage() throws Exception {
        Fs fs = startCrawlerDefinition()
                .setIndexedChars(Percentage.parse("0.1%"))
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        SearchResponse searchResponse = countTestHelper(new SearchRequest(getCrawlerName()), 1L, null);
        for (SearchHit hit : searchResponse.getHits().getHits()) {
            Object content = hit.getSourceAsMap().get(Doc.FIELD_NAMES.CONTENT);
            Object indexedChars = extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.INDEXED_CHARS);
            assertThat(content, notNullValue());
            assertThat(indexedChars, notNullValue());

            // Our original text should be truncated
            assertThat(content, is("Novo denique"));
            assertThat(indexedChars, is(12));
        }
    }

    @Test
    public void test_filesize_nolimit() throws Exception {
        Fs fs = startCrawlerDefinition()
                .setIndexedChars(new Percentage(-1))
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        SearchResponse searchResponse = countTestHelper(new SearchRequest(getCrawlerName()), 1L, null);
        for (SearchHit hit : searchResponse.getHits().getHits()) {
            Object content = hit.getSourceAsMap().get(Doc.FIELD_NAMES.CONTENT);
            Object indexedChars = extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.INDEXED_CHARS);
            assertThat(content, notNullValue());
            assertThat(indexedChars, nullValue());

            // Our original text should not be truncated so we must have its end extracted
            assertThat((String) content, containsString("haecque non diu sunt perpetrata."));
        }
    }

    @Test
    public void test_filesize_disabled() throws Exception {
        Fs fs = startCrawlerDefinition()
                .setAddFilesize(false)
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        SearchResponse searchResponse = countTestHelper(new SearchRequest(getCrawlerName()), 1L, null);
        for (SearchHit hit : searchResponse.getHits().getHits()) {
            Map<String, Object> file = (Map<String, Object>) hit.getSourceAsMap().get(Doc.FIELD_NAMES.FILE);
            assertThat(file, notNullValue());
            assertThat(file.get(File.FIELD_NAMES.FILESIZE), nullValue());
        }
    }

    @Test
    public void test_includes() throws Exception {
        Fs fs = startCrawlerDefinition()
                .addInclude("*_include.txt")
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);
        countTestHelper(new SearchRequest(getCrawlerName()), 1L, null);
    }

    @Test
    public void test_default_metadata() throws Exception {
        startCrawler();

        SearchResponse searchResponse = countTestHelper(new SearchRequest(getCrawlerName()), 1L, null);
        for (SearchHit hit : searchResponse.getHits().getHits()) {
            assertThat(hit.getSourceAsMap().get(Doc.FIELD_NAMES.ATTACHMENT), nullValue());

            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.FILENAME), notNullValue());
            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.CONTENT_TYPE), notNullValue());
            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.URL), notNullValue());
            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.FILESIZE), notNullValue());
            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.INDEXING_DATE), notNullValue());
            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.INDEXED_CHARS), nullValue());
            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.LAST_MODIFIED), notNullValue());

            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.META).get(Meta.FIELD_NAMES.TITLE), notNullValue());
        }
    }

    @Test
    public void test_attributes() throws Exception {
        Fs fs = startCrawlerDefinition()
                .setAttributesSupport(true)
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);
        SearchResponse searchResponse = countTestHelper(new SearchRequest(getCrawlerName()), 1L, null);
        for (SearchHit hit : searchResponse.getHits().getHits()) {
            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.ATTRIBUTES).get(Attributes.FIELD_NAMES.OWNER), notNullValue());
        }
    }

    @Test
    public void test_remove_deleted_enabled() throws Exception {
        Fs fs = startCrawlerDefinition()
                .setRemoveDeleted(true)
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        // We should have two docs first
        countTestHelper(new SearchRequest(getCrawlerName()), 2L, currentTestResourceDir);

        // We remove a file
        logger.info("  ---> Removing file deleted_roottxtfile.txt");
        Files.delete(currentTestResourceDir.resolve("deleted_roottxtfile.txt"));

        // We expect to have one file
        countTestHelper(new SearchRequest(getCrawlerName()), 1L, currentTestResourceDir);
    }

    @Test
    public void test_remove_deleted_disabled() throws Exception {
        Fs fs = startCrawlerDefinition()
                .setRemoveDeleted(false)
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        // We should have two docs first
        countTestHelper(new SearchRequest(getCrawlerName()), 2L, currentTestResourceDir);

        // We remove a file
        logger.info(" ---> Removing file deleted_roottxtfile.txt");
        Files.delete(currentTestResourceDir.resolve("deleted_roottxtfile.txt"));

        // We expect to have two files
        countTestHelper(new SearchRequest(getCrawlerName()), 2L, currentTestResourceDir);
    }

    /**
     * Test case for https://github.com/dadoonet/fscrawler/issues/110
     * @throws Exception In case something is wrong
     */
    @Test
    public void test_rename_file() throws Exception {
        startCrawler();

        // We should have one doc first
        countTestHelper(new SearchRequest(getCrawlerName()), 1L, currentTestResourceDir);

        // We rename the file
        logger.info(" ---> Renaming file roottxtfile.txt to renamed_roottxtfile.txt");
        // We create a copy of a file
        Files.move(currentTestResourceDir.resolve("roottxtfile.txt"),
                currentTestResourceDir.resolve("renamed_roottxtfile.txt"));

        // We expect to have one file only with a new name
        countTestHelper(new SearchRequest(getCrawlerName()).source(new SearchSourceBuilder()
                .query(QueryBuilders.termQuery("file.filename", "renamed_roottxtfile.txt"))), 1L, currentTestResourceDir);
    }

    /**
     * Test case for issue #60: https://github.com/dadoonet/fscrawler/issues/60 : new files are not added
     */
    @Test
    public void test_add_new_file() throws Exception {
        startCrawler();

        // We should have one doc first
        SearchResponse response = countTestHelper(new SearchRequest(getCrawlerName()), 1L, currentTestResourceDir);
        checkDocVersions(response, 1L);

        logger.info(" ---> Creating a new file new_roottxtfile.txt");
        Files.write(currentTestResourceDir.resolve("new_roottxtfile.txt"), "This is a second file".getBytes());

        // We expect to have two files
        response = countTestHelper(new SearchRequest(getCrawlerName()), 2L, currentTestResourceDir);

        // It should be only version <= 2 for both docs
        checkDocVersions(response, 2L);

        logger.info(" ---> Creating a new file new_new_roottxtfile.txt");
        Files.write(currentTestResourceDir.resolve("new_new_roottxtfile.txt"), "This is a third file".getBytes());

        // We expect to have three files
        response = countTestHelper(new SearchRequest(getCrawlerName()), 3L, currentTestResourceDir);

        // It should be only version <= 2 for all docs
        checkDocVersions(response, 2L);
    }

    /**
     * Iterate other response hits and check that _version is at most a given version
     * @param response The search response object
     * @param maxVersion Maximum version number we can have
     */
    private void checkDocVersions(SearchResponse response, long maxVersion) {
        // It should be only version <= maxVersion for all docs
        for (SearchHit hit : response.getHits().getHits()) {
            // Read the document. This is needed since 5.0 as search does not return the _version field
            try {
                GetResponse getHit = elasticsearchClient.get(new GetRequest(hit.getIndex(), "doc", hit.getId()));
                assertThat(getHit.getVersion(), lessThanOrEqualTo(maxVersion));
            } catch (IOException e) {
                fail("We got an IOException: " + e.getMessage());
            }
        }
    }

    /**
     * Test case for issue #5: https://github.com/dadoonet/fscrawler/issues/5 : Support JSon documents
     */
    @Test
    public void test_json_support() throws Exception {
        Fs fs = startCrawlerDefinition()
                .setJsonSupport(true)
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        assertThat("We should have 2 doc for tweet in text field...", awaitBusy(() -> {
            try {
                SearchResponse response = elasticsearchClient.search(new SearchRequest(getCrawlerName()).source(
                        new SearchSourceBuilder().query(QueryBuilders.matchQuery("text", "tweet"))));
                return response.getHits().getTotalHits() == 2;
            } catch (IOException e) {
                logger.warn("Caught exception while running the test", e);
                return false;
            }
        }), equalTo(true));
    }

    /**
     * Test case for issue #5: https://github.com/dadoonet/fscrawler/issues/5 : Support JSon documents
     */
    @Test
    public void test_json_disabled() throws Exception {
        Fs fs = startCrawlerDefinition()
                .setJsonSupport(false)
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        assertThat("We should have 0 doc for tweet in text field...", awaitBusy(() -> {
            try {
                SearchResponse response = elasticsearchClient.search(new SearchRequest(getCrawlerName()).source(
                        new SearchSourceBuilder().query(QueryBuilders.matchQuery("text", "tweet"))));
                return response.getHits().getTotalHits() == 0;
            } catch (IOException e) {
                logger.warn("Caught exception while running the test", e);
                return false;
            }
        }), equalTo(true));

        assertThat("We should have 2 docs for tweet in content field...", awaitBusy(() -> {
            try {
                SearchResponse response = elasticsearchClient.search(new SearchRequest(getCrawlerName()).source(
                        new SearchSourceBuilder().query(QueryBuilders.matchQuery("content", "tweet"))));
                return response.getHits().getTotalHits() == 2;
            } catch (IOException e) {
                logger.warn("Caught exception while running the test", e);
                return false;
            }
        }), equalTo(true));
    }

    /**
     * Test case for issue #7: https://github.com/dadoonet/fscrawler/issues/7 : Use filename as ID
     */
    @Test
    public void test_filename_as_id() throws Exception {
        Fs fs = startCrawlerDefinition()
                .setFilenameAsId(true)
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        assertThat("Document should exists with [roottxtfile.txt] id...", awaitBusy(() -> {
            try {
                return elasticsearchClient.exists(new GetRequest(getCrawlerName(), "doc", "roottxtfile.txt"));
            } catch (IOException e) {
                return false;
            }
        }), equalTo(true));
    }

    /**
     * Test case for issue #237:  https://github.com/dadoonet/fscrawler/issues/237 Delete json documents
     */
    @Test
    public void test_add_as_inner_object() throws Exception {
        Fs fs = startCrawlerDefinition()
                .setJsonSupport(true)
                .setAddAsInnerObject(true)
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        assertThat("We should have 2 doc for tweet in object.text field...", awaitBusy(() -> {
            try {
                SearchResponse response = elasticsearchClient.search(new SearchRequest(getCrawlerName()).source(
                        new SearchSourceBuilder().query(QueryBuilders.matchQuery("object.text", "tweet"))));
                return response.getHits().getTotalHits() == 2;
            } catch (IOException e) {
                logger.warn("Caught exception while running the test", e);
                return false;
            }
        }), equalTo(true));
    }

    @Test
    public void test_store_source() throws Exception {
        Fs fs = startCrawlerDefinition()
                .setStoreSource(true)
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        SearchResponse searchResponse = countTestHelper(new SearchRequest(getCrawlerName()), 1L, null);
        for (SearchHit hit : searchResponse.getHits().getHits()) {
            // We check that the field is in _source
            assertThat(hit.getSourceAsMap().get(Doc.FIELD_NAMES.ATTACHMENT), notNullValue());
        }
    }

    @Test
    public void test_do_not_store_source() throws Exception {
        startCrawler();

        countTestHelper(new SearchRequest(getCrawlerName()), 1L, null);

        SearchResponse searchResponse = elasticsearchClient.search(new SearchRequest(getCrawlerName()));
        for (SearchHit hit : searchResponse.getHits().getHits()) {
            // We check that the field is not part of _source
            assertThat(hit.getSourceAsMap().get(Doc.FIELD_NAMES.ATTACHMENT), nullValue());
        }
    }

    @Test
    public void test_defaults() throws Exception {
        startCrawler();

        // We expect to have one file
        SearchResponse searchResponse = countTestHelper(new SearchRequest(getCrawlerName()), 1L, null);

        // The default configuration should not add file attributes
        for (SearchHit hit : searchResponse.getHits().getHits()) {
            assertThat(hit.getSourceAsMap().get(Doc.FIELD_NAMES.ATTRIBUTES), nullValue());
        }

    }

    @Test
    public void test_subdirs() throws Exception {
        startCrawler();

        // We expect to have two files
        SearchResponse searchResponse = countTestHelper(new SearchRequest(getCrawlerName()), 2L, null);

        // We check that the subdir document has his meta path data correctly set
        for (SearchHit hit : searchResponse.getHits().getHits()) {
            Object virtual = extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.PATH)
                    .get(fr.pilato.elasticsearch.crawler.fs.meta.doc.Path.FIELD_NAMES.VIRTUAL);
            assertThat(virtual, isOneOf("/subdir/roottxtfile_multi_feed.txt", "/roottxtfile.txt"));
        }
    }

    @Test
    public void test_subdirs_deep_tree() throws Exception {
        startCrawler();

        // We expect to have 7 files
        countTestHelper(new SearchRequest(getCrawlerName()), 7L, null);

        // Run aggs
        if (elasticsearchClient.getVersion().major >= 6) {
            // We can use the high level REST Client
            SearchResponse response = elasticsearchClient.search(new SearchRequest(getCrawlerName()).source(
                    new SearchSourceBuilder()
                            .size(0)
                            .aggregation(AggregationBuilders.terms("folders").field("path.virtual.tree"))));
            assertThat(response.getHits().getTotalHits(), is(7L));

            // aggregations
            assertThat(response.getAggregations().asMap(), hasKey("folders"));
            Terms aggregation = response.getAggregations().get("folders");
            List<? extends Terms.Bucket> buckets = aggregation.getBuckets();

            assertThat(buckets, iterableWithSize(10));

            // Check files
            response = elasticsearchClient.search(new SearchRequest(getCrawlerName()).source(new SearchSourceBuilder().sort("path.virtual")));
            assertThat(response.getHits().getTotalHits(), is(7L));

            int i = 0;
            pathHitTester(response, i++, hit -> (Map<String, Object>) hit.getSourceAsMap().get("path"), "/test_subdirs_deep_tree/roottxtfile.txt", is("/roottxtfile.txt"));
            pathHitTester(response, i++, hit -> (Map<String, Object>) hit.getSourceAsMap().get("path"), "/test_subdirs_deep_tree/subdir1/roottxtfile_multi_feed.txt", is("/subdir1/roottxtfile_multi_feed.txt"));
            pathHitTester(response, i++, hit -> (Map<String, Object>) hit.getSourceAsMap().get("path"), "/test_subdirs_deep_tree/subdir1/subdir11/roottxtfile.txt", is("/subdir1/subdir11/roottxtfile.txt"));
            pathHitTester(response, i++, hit -> (Map<String, Object>) hit.getSourceAsMap().get("path"), "/test_subdirs_deep_tree/subdir1/subdir12/roottxtfile.txt", is("/subdir1/subdir12/roottxtfile.txt"));
            pathHitTester(response, i++, hit -> (Map<String, Object>) hit.getSourceAsMap().get("path"), "/test_subdirs_deep_tree/subdir2/roottxtfile_multi_feed.txt", is("/subdir2/roottxtfile_multi_feed.txt"));
            pathHitTester(response, i++, hit -> (Map<String, Object>) hit.getSourceAsMap().get("path"), "/test_subdirs_deep_tree/subdir2/subdir21/roottxtfile.txt", is("/subdir2/subdir21/roottxtfile.txt"));
            pathHitTester(response, i, hit -> (Map<String, Object>) hit.getSourceAsMap().get("path"), "/test_subdirs_deep_tree/subdir2/subdir22/roottxtfile.txt", is("/subdir2/subdir22/roottxtfile.txt"));


            // Check folders
            response = elasticsearchClient.search(new SearchRequest(getCrawlerName() + INDEX_SUFFIX_FOLDER).source(new SearchSourceBuilder().sort("virtual")));
            assertThat(response.getHits().getTotalHits(), is(7L));

            i = 0;
            pathHitTester(response, i++, SearchHit::getSourceAsMap, "/test_subdirs_deep_tree", is("/"));
            pathHitTester(response, i++, SearchHit::getSourceAsMap, "/test_subdirs_deep_tree/subdir1", is("/subdir1"));
            pathHitTester(response, i++, SearchHit::getSourceAsMap, "/test_subdirs_deep_tree/subdir1/subdir11", is("/subdir1/subdir11"));
            pathHitTester(response, i++, SearchHit::getSourceAsMap, "/test_subdirs_deep_tree/subdir1/subdir12", is("/subdir1/subdir12"));
            pathHitTester(response, i++, SearchHit::getSourceAsMap, "/test_subdirs_deep_tree/subdir2", is("/subdir2"));
            pathHitTester(response, i++, SearchHit::getSourceAsMap, "/test_subdirs_deep_tree/subdir2/subdir21", is("/subdir2/subdir21"));
            pathHitTester(response, i, SearchHit::getSourceAsMap, "/test_subdirs_deep_tree/subdir2/subdir22", is("/subdir2/subdir22"));
        } else {
            // We need to use the old deprecated fashion for version < 5.0
            // We do minimal tests
            // Run aggs
            fr.pilato.elasticsearch.crawler.fs.client.SearchResponse response = elasticsearchClient.searchJson(getCrawlerName(),
                    "{\n" +
                            "  \"size\": 0, \n" +
                            "  \"aggs\": {\n" +
                            "    \"folders\": {\n" +
                            "      \"terms\": {\n" +
                            "        \"field\": \"path.virtual.tree\"\n" +
                            "      }\n" +
                            "    }\n" +
                            "  }\n" +
                            "}");
            assertThat(response.getHits().getTotal(), is(7L));

            // aggregations
            assertThat(response.getAggregations(), hasKey("folders"));
            List<Object> buckets = (List) extractFromPath(response.getAggregations(), "folders").get("buckets");
            assertThat(buckets, iterableWithSize(10));
        }
    }

    @Test
    public void test_subdirs_very_deep_tree() throws Exception {

        long subdirs = randomLongBetween(30, 100);

        staticLogger.debug("  --> Generating [{}] dirs [{}]", subdirs, currentTestResourceDir);

        Path sourceFile = currentTestResourceDir.resolve("roottxtfile.txt");
        Path mainDir = currentTestResourceDir.resolve("main_dir");
        Files.createDirectory(mainDir);
        Path newDir = mainDir;

        for (int i = 0; i < subdirs; i++) {
            newDir = newDir.resolve(i + "_" + randomAsciiOfLengthBetween(2, 5));
            Files.createDirectory(newDir);
            // Copy the original test file in the new dir
            Files.copy(sourceFile, newDir.resolve("sample.txt"));
        }

        startCrawler();

        // We expect to have x files (<- whoa that's funny Mulder!)
        countTestHelper(new SearchRequest(getCrawlerName()), subdirs+1, null);

        if (elasticsearchClient.getVersion().major >= 6) {
            // We can use the high level REST Client
            // Run aggs
            SearchResponse response = elasticsearchClient.search(new SearchRequest(getCrawlerName()).source(
                    new SearchSourceBuilder()
                            .size(0)
                            .aggregation(AggregationBuilders.terms("folders").field("path.virtual.tree"))));
            assertThat(response.getHits().getTotalHits(), is(subdirs+1));

            // aggregations
            assertThat(response.getAggregations().asMap(), hasKey("folders"));
            Terms aggregation = response.getAggregations().get("folders");
            List<? extends Terms.Bucket> buckets = aggregation.getBuckets();

            assertThat(buckets, iterableWithSize(10));
        } else {
            // We need to use the old deprecated fashion for version < 5.0
            // We do minimal tests
            // Run aggs
            fr.pilato.elasticsearch.crawler.fs.client.SearchResponse response = elasticsearchClient.searchJson(getCrawlerName(),
                    "{\n" +
                            "  \"size\": 0, \n" +
                            "  \"aggs\": {\n" +
                            "    \"folders\": {\n" +
                            "      \"terms\": {\n" +
                            "        \"field\": \"path.virtual.tree\"\n" +
                            "      }\n" +
                            "    }\n" +
                            "  }\n" +
                            "}");
            assertThat(response.getHits().getTotal(), is((long) subdirs+1));

            // aggregations
            assertThat(response.getAggregations(), hasKey("folders"));
            List<Object> buckets = (List) extractFromPath(response.getAggregations(), "folders").get("buckets");

            assertThat(buckets, iterableWithSize(10));
        }

        // Check files
        SearchResponse response = elasticsearchClient.search(new SearchRequest(getCrawlerName()).source(
                        new SearchSourceBuilder()
                                .size(1000)
                                .sort("path.virtual")));
        assertThat(response.getHits().getTotalHits(), is(subdirs+1));

        for (int i = 0; i < subdirs; i++) {
            pathHitTester(response, i, hit -> (Map<String, Object>) hit.getSourceAsMap().get("path"), "sample.txt", endsWith("/" + "sample.txt"));
        }

        // Check folders
        response = elasticsearchClient.search(new SearchRequest(getCrawlerName() + INDEX_SUFFIX_FOLDER).source(
                        new SearchSourceBuilder()
                                .size(1000)
                                .sort("virtual")));
        assertThat(response.getHits().getTotalHits(), is(subdirs+2));

        // Let's remove the main subdir and wait...
        staticLogger.debug("  --> Removing all dirs from [{}]", mainDir);
        deleteRecursively(mainDir);

        // We expect to have 1 doc now
        countTestHelper(new SearchRequest(getCrawlerName()), 1L, currentTestResourceDir);
    }

    private void pathHitTester(SearchResponse response, int position, Function<SearchHit, Map<String, Object>> extractPath,
                               String expectedReal, Matcher<String> expectedVirtual) {
        SearchHit hit = response.getHits().getHits()[position];
        Map<String, Object> path = extractPath.apply(hit);
        String real = (String) path.get("real");
        String virtual = (String) path.get("virtual");
        logger.debug(" - {}, {}", real, virtual);
        assertThat("path.real[" + position + "]", real, endsWith(expectedReal));
        assertThat("path.virtual[" + position + "]", virtual, expectedVirtual);
    }

    @Test
    public void test_subdirs_with_patterns() throws Exception {
        Fs fs = startCrawlerDefinition()
                .addInclude("*.txt")
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        // We expect to have seven files
        countTestHelper(new SearchRequest(getCrawlerName()), 7L, null);
    }

    @Test
    public void test_ignore_dir() throws Exception {
        Fs fs = startCrawlerDefinition()
                .addExclude(".ignore")
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        // We expect to have one file
        countTestHelper(new SearchRequest(getCrawlerName()), 1L, null);
    }

    @Test
    public void test_multiple_crawlers() throws Exception {
        Fs fs1 = startCrawlerDefinition(currentTestResourceDir.resolve("crawler1").toString()).build();
        Fs fs2 = startCrawlerDefinition(currentTestResourceDir.resolve("crawler2").toString()).build();
        FsCrawlerImpl crawler1 = startCrawler(getCrawlerName() + "_1", fs1, endCrawlerDefinition(getCrawlerName() + "_1"), null);
        FsCrawlerImpl crawler2 = startCrawler(getCrawlerName() + "_2", fs2, endCrawlerDefinition(getCrawlerName() + "_2"), null);
        // We should have one doc in index 1...
        countTestHelper(new SearchRequest(getCrawlerName() + "_1"), 1L, null);
        // We should have one doc in index 2...
        countTestHelper(new SearchRequest(getCrawlerName() + "_2"), 1L, null);

        crawler1.close();
        crawler2.close();
    }

    @Test
    public void test_filename_analyzer() throws Exception {
        startCrawler();

        // We should have one doc
        countTestHelper(new SearchRequest(getCrawlerName()).source(new SearchSourceBuilder()
                .query(QueryBuilders.termQuery("file.filename", "roottxtfile.txt"))), 1L, null);
    }

    /**
     * Test for #83: https://github.com/dadoonet/fscrawler/issues/83
     */
    @Test
    public void test_time_value() throws Exception {
        Fs fs = startCrawlerDefinition(TimeValue.timeValueHours(1)).build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        // We expect to have one file
        countTestHelper(new SearchRequest(getCrawlerName()), 1L, null);
    }


    /**
     * You have to adapt this test to your own system (login / password and SSH connexion)
     * So this test is disabled by default
     */
    @Test @Ignore
    public void test_ssh() throws Exception {
        String username = "USERNAME";
        String password = "PASSWORD";
        String hostname = "localhost";

        Fs fs = startCrawlerDefinition().build();
        Server server = Server.builder()
                .setHostname(hostname)
                .setUsername(username)
                .setPassword(password)
                .setProtocol(FsCrawlerImpl.PROTOCOL.SSH)
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), server);

        countTestHelper(new SearchRequest(getCrawlerName()), 2L, null);
    }

    /**
     * You have to adapt this test to your own system (login / pem file and SSH connexion)
     * So this test is disabled by default
     */
    @Test @Ignore
    public void test_ssh_with_key() throws Exception {
        String username = "USERNAME";
        String path_to_pem_file = "/path/to/private_key.pem";
        String hostname = "localhost";

        Fs fs = startCrawlerDefinition().build();
        Server server = Server.builder()
                .setHostname(hostname)
                .setUsername(username)
                .setPemPath(path_to_pem_file)
                .setProtocol(FsCrawlerImpl.PROTOCOL.SSH)
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), server);

        countTestHelper(new SearchRequest(getCrawlerName()), 1L, null);
    }

    /**
     * Test for #105: https://github.com/dadoonet/fscrawler/issues/105
     */
    @Test
    public void test_unparsable() throws Exception {
        startCrawler();

        // We expect to have two files
        countTestHelper(new SearchRequest(getCrawlerName()), 2L, null);
    }

    /**
     * Test for #103: https://github.com/dadoonet/fscrawler/issues/103
     */
    @Test
    public void test_index_content() throws Exception {
        Fs fs = startCrawlerDefinition()
                .setIndexContent(false)
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        // We expect to have one file
        countTestHelper(new SearchRequest(getCrawlerName()), 1L, null);

        countTestHelper(new SearchRequest(getCrawlerName()).source(new SearchSourceBuilder()
                .query(QueryBuilders.prefixQuery("content", "file*"))), 0L, null);
        countTestHelper(new SearchRequest(getCrawlerName()).source(new SearchSourceBuilder()
                .query(QueryBuilders.prefixQuery("file.content_type", "text*"))), 0L, null);
        countTestHelper(new SearchRequest(getCrawlerName()).source(new SearchSourceBuilder()
                .query(QueryBuilders.termQuery("file.extension", "txt"))), 1L, null);
    }

    @Test
    public void test_bulk_flush() throws Exception {
        Fs fs = startCrawlerDefinition().build();
        startCrawler(getCrawlerName(), fs,
                generateElasticsearchConfig(getCrawlerName(), getCrawlerName() + INDEX_SUFFIX_FOLDER,
                        securityInstalled, 100, TimeValue.timeValueSeconds(2)), null);

        countTestHelper(new SearchRequest(getCrawlerName()), 1L, null);
    }

    @Test
    public void test_checksum_md5() throws Exception {
        try {
            MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            assumeNoException(e);
        }

        Fs fs = startCrawlerDefinition()
                .setChecksum("MD5")
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);
        SearchResponse searchResponse = countTestHelper(new SearchRequest(getCrawlerName()), 1L, null);
        for (SearchHit hit : searchResponse.getHits().getHits()) {
            Object checksum = extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.CHECKSUM);
            assertThat(checksum, is("caa71e1914ecbcf5ae4f46cf85de8648"));
        }
    }

    @Test
    public void test_checksum_sha1() throws Exception {
        try {
            MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            assumeNoException(e);
        }

        Fs fs = startCrawlerDefinition()
                .setChecksum("SHA-1")
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);
        SearchResponse searchResponse = countTestHelper(new SearchRequest(getCrawlerName()), 1L, null);
        for (SearchHit hit : searchResponse.getHits().getHits()) {
            Object checksum = extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.CHECKSUM);
            assertThat(checksum, is("81bf7dba781a1efbea6d9f2ad638ffe772ba4eab"));
        }
    }

    @Test
    public void test_checksum_non_existing_algorithm() throws Exception {
        Fs fs = startCrawlerDefinition()
                .setChecksum("FSCRAWLER")
                .build();
        crawler = new FsCrawlerImpl(metadataDir,
                FsSettings.builder(getCrawlerName()).setElasticsearch(endCrawlerDefinition(getCrawlerName())).setFs(fs).build());
        crawler.start();
        assertThat(crawler.isClosed(), is(true));
    }

    /**
     * Test case for issue #204: https://github.com/dadoonet/fscrawler/issues/204 : JSON files are indexed twice
     */
    @Test
    public void test_json_support_and_other_files() throws Exception {
        Fs fs = startCrawlerDefinition()
                .setJsonSupport(true)
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        assertThat("We should have 2 docs only...", awaitBusy(() -> {
            try {
                SearchResponse response = elasticsearchClient.search(new SearchRequest(getCrawlerName()));
                return response.getHits().getTotalHits() == 2;
            } catch (IOException e) {
                logger.warn("Caught exception while running the test", e);
                return false;
            }
        }), equalTo(true));
    }

    /**
     * Test case for issue #185: https://github.com/dadoonet/fscrawler/issues/185 : Add xml_support setting
     */
    @Test
    public void test_xml_enabled() throws Exception {
        Fs fs = startCrawlerDefinition()
                .setXmlSupport(true)
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);
        SearchResponse response = countTestHelper(new SearchRequest(getCrawlerName()), 3L, null);

        countTestHelper(new SearchRequest(getCrawlerName()).source(new SearchSourceBuilder()
                .query(QueryBuilders.matchQuery("title", "maeve"))), 1L, null);
        countTestHelper(new SearchRequest(getCrawlerName()).source(new SearchSourceBuilder()
                .query(QueryBuilders.rangeQuery("price").from(5).to(6))), 2L, null);

        logger.info("XML documents converted to:");
        for (SearchHit hit : response.getHits().getHits()) {
            logger.info("{}", hit.toString());
        }
    }

    /**
     * Test case for issue #185: https://github.com/dadoonet/fscrawler/issues/185 : Add xml_support setting
     */
    @Test
    public void test_xml_and_json_enabled() throws Exception {
        Fs fs = startCrawlerDefinition()
                .setXmlSupport(true)
                .setJsonSupport(true)
                .build();

        logger.info("  --> starting crawler [{}]", getCrawlerName());

        crawler = new FsCrawlerImpl(metadataDir, FsSettings.builder(getCrawlerName())
                .setElasticsearch(endCrawlerDefinition(getCrawlerName())).setFs(fs).build());
        crawler.start();

        // We wait up to 10 seconds before considering a failing test
        assertThat("Job should not start", awaitBusy(() -> crawler.isClosed()), equalTo(true));
    }

    /**
     * Test case for #227: https://github.com/dadoonet/fscrawler/issues/227 : Add support for run only once
     */
    @Test
    public void test_single_loop() throws Exception {
        Fs fs = startCrawlerDefinition().build();

        logger.info("  --> starting crawler [{}]", getCrawlerName());

        crawler = new FsCrawlerImpl(metadataDir, FsSettings.builder(getCrawlerName())
                .setElasticsearch(endCrawlerDefinition(getCrawlerName())).setFs(fs).build(), 1, false);
        crawler.start();

        countTestHelper(new SearchRequest(getCrawlerName()), 1L, null);

        assertThat("Job should stop after one run", crawler.isClosed(), is(true));
        assertThat(crawler.getRunNumber(), is(1));
    }

    /**
     * Test case for #227: https://github.com/dadoonet/fscrawler/issues/227 : Add support for run only once
     */
    @Test
    public void test_two_loops() throws Exception {
        Fs fs = startCrawlerDefinition().build();

        logger.info("  --> starting crawler [{}]", getCrawlerName());

        crawler = new FsCrawlerImpl(metadataDir, FsSettings.builder(getCrawlerName())
                .setElasticsearch(endCrawlerDefinition(getCrawlerName())).setFs(fs).build(), 2, false);
        crawler.start();

        countTestHelper(new SearchRequest(getCrawlerName()), 1L, null);

        assertThat("Job should stop after two runs", awaitBusy(() -> crawler.isClosed()), is(true));
        assertThat(crawler.getRunNumber(), is(2));
    }

    /**
     * Test case for #95: https://github.com/dadoonet/fscrawler/issues/95 : Folder index is not getting delete on delete of folder
     */
    @Test
    public void test_remove_folder_deleted_enabled() throws Exception {
        Fs fs = startCrawlerDefinition()
                .setRemoveDeleted(true)
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        // We should have 7 docs first
        countTestHelper(new SearchRequest(getCrawlerName()), 7L, currentTestResourceDir);

        logContentOfDir(currentTestResourceDir, Level.DEBUG);

        // We remove a directory
        logger.info("  ---> Removing dir subdir1");
        deleteRecursively(currentTestResourceDir.resolve("subdir1"));

        logContentOfDir(currentTestResourceDir, Level.DEBUG);

        // We expect to have 4 docs now
        countTestHelper(new SearchRequest(getCrawlerName()), 4L, currentTestResourceDir);
    }

    /**
     * Test case for #155: https://github.com/dadoonet/fscrawler/issues/155 : New option: do not index folders
     */
    @Test
    public void test_ignore_folders() throws Exception {
        Fs fs = startCrawlerDefinition()
                .setIndexFolders(false)
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        // We expect to have two files
        countTestHelper(new SearchRequest(getCrawlerName()), 2L, null);

        // We expect having no folders
        SearchResponse response = elasticsearchClient.search(new SearchRequest(getCrawlerName() + INDEX_SUFFIX_FOLDER));
        staticLogger.trace("result {}", response.toString());
        assertThat(response.getHits().getTotalHits(), is(0L));
    }


    /**
     * Test case for #183: https://github.com/dadoonet/fscrawler/issues/183 : Optimize document and folder mappings
     * We want to make sure we can highlight documents even if we don't store fields
     */
    @Test
    public void test_highlight_documents() throws Exception {
        startCrawler();

        // We expect to have one file
        countTestHelper(new SearchRequest(getCrawlerName()), 1L, null);

        // Let's test highlighting
        SearchResponse response = elasticsearchClient.search(new SearchRequest(getCrawlerName()).source(
                new SearchSourceBuilder()
                        .query(QueryBuilders.matchQuery("content", "exemplo"))
                        .highlighter(new HighlightBuilder().field("content"))));
        staticLogger.trace("result {}", response.toString());
        assertThat(response.getHits().getTotalHits(), is(1L));

        SearchHit hit = response.getHits().getHits()[0];
        assertThat(hit.getHighlightFields(), hasKey("content"));
        assertThat(hit.getHighlightFields().get("content").getFragments(), arrayWithSize(1));
        assertThat(hit.getHighlightFields().get("content").getFragments()[0].string(), containsString("<em>exemplo</em>"));
    }

    /**
     * Test case for #230: https://github.com/dadoonet/fscrawler/issues/230 : Add support for compressed files
     * It's a long job, so we let it run up to 2 minutes
     */
    @Test
    public void test_zip() throws Exception {
        startCrawler(getCrawlerName(), startCrawlerDefinition().build(), endCrawlerDefinition(getCrawlerName()), null, null,
                TimeValue.timeValueMinutes(2));

        // We expect to have one file
        countTestHelper(new SearchRequest(getCrawlerName()), 1L, null);
    }

    /**
     * Test case for #234: https://github.com/dadoonet/fscrawler/issues/234 : Support ingest pipeline processing
     */
    @Test
    public void test_ingest_pipeline() throws Exception {
        String crawlerName = getCrawlerName();

        // We can only run this test against a 5.0 cluster or >
        assumeThat("We skip the test as we are not running it with a 5.0 cluster or >",
                elasticsearchClient.isIngestSupported(), is(true));

        // Create an empty ingest pipeline
        String pipeline = "{\n" +
                "  \"description\" : \"describe pipeline\",\n" +
                "  \"processors\" : [\n" +
                "    {\n" +
                "      \"rename\": {\n" +
                "        \"field\": \"content\",\n" +
                "        \"target_field\": \"my_content_field\"\n" +
                "      }\n" +
                "    }\n" +
                "  ]\n" +
                "}";
        StringEntity entity = new StringEntity(pipeline, ContentType.APPLICATION_JSON);

        elasticsearchClient.getLowLevelClient().performRequest("PUT", "_ingest/pipeline/" + crawlerName,
                Collections.emptyMap(), entity);

        Elasticsearch elasticsearch = endCrawlerDefinition(crawlerName);
        elasticsearch.setPipeline(crawlerName);

        startCrawler(crawlerName, startCrawlerDefinition().build(), elasticsearch, null);

        // We expect to have one file
        countTestHelper(new SearchRequest(getCrawlerName()).source(new SearchSourceBuilder()
                .query(QueryBuilders.matchQuery("my_content_field", "perniciosoque"))), 1L, currentTestResourceDir);

        // We expect to have one folder
        SearchResponse response = elasticsearchClient.search(new SearchRequest(getCrawlerName() + INDEX_SUFFIX_FOLDER));
        assertThat(response.getHits().getTotalHits(), is(1L));
    }

    /**
     * Test case for #392: https://github.com/dadoonet/fscrawler/issues/392 : Support ingest pipeline processing
     */
    @Test
    public void test_ingest_pipeline_392() throws Exception {
        String crawlerName = getCrawlerName();

        // We can only run this test against a 5.0 cluster or >
        assumeThat("We skip the test as we are not running it with a 5.0 cluster or >",
                elasticsearchClient.isIngestSupported(), is(true));

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
        StringEntity entity = new StringEntity(pipeline, ContentType.APPLICATION_JSON);

        elasticsearchClient.getLowLevelClient().performRequest("PUT", "_ingest/pipeline/" + crawlerName,
                Collections.emptyMap(), entity);

        Elasticsearch elasticsearch = endCrawlerDefinition(crawlerName);
        elasticsearch.setPipeline(crawlerName);

        startCrawler(crawlerName, startCrawlerDefinition().build(), elasticsearch, null);

        // We expect to have one file
        countTestHelper(new SearchRequest(getCrawlerName()).source(new SearchSourceBuilder()
                .query(QueryBuilders.termQuery("ip_addr", "10.21.23.123"))), 1L, currentTestResourceDir);
    }

    /**
     * Test case for #251: https://github.com/dadoonet/fscrawler/issues/251 : Add a REST Layer
     */
    @Test
    public void test_with_rest_only() throws Exception {
        logger.info("  --> starting crawler [{}]", getCrawlerName());

        // TODO do this rarely() createIndex(jobName);

        crawler = new FsCrawlerImpl(
                metadataDir,
                FsSettings.builder(getCrawlerName())
                        .setElasticsearch(endCrawlerDefinition(getCrawlerName()))
                        .setFs(startCrawlerDefinition().build())
                        .setServer(null)
                        .setRest(rest).build(),
                0,
                true);
        crawler.start();

        String url = getUrl("documents");
        Path from = Paths.get(url);
        Files.walk(from)
                .filter(path -> Files.isRegularFile(path))
                .forEach(path -> {
                    UploadResponse response = uploadFile(path);
                    assertThat(response.getFilename(), is(path.getFileName().toString()));
                });

        // We wait until we have all docs
        countTestHelper(new SearchRequest(getCrawlerName()), Files.list(from).count(), null, TimeValue.timeValueMinutes(1));
    }

    /**
     * Test case for #136: https://github.com/dadoonet/fscrawler/issues/136 : Moving existing files does not index new files
     */
    @Test
    public void test_moving_files() throws Exception {
        String filename = "oldfile.txt";

        startCrawler();

        // Let's first create some files
        logger.info(" ---> Creating a file [{}]", filename);

        Path tmpDir = rootTmpDir.resolve("resources").resolve(getCurrentTestName() + "-tmp");
        if (Files.notExists(tmpDir)) {
            Files.createDirectory(tmpDir);
        }

        Path file = Files.createFile(tmpDir.resolve(filename));
        Files.write(file, "Hello world".getBytes(Charsets.UTF_8));

        // We should have 1 doc first
        countTestHelper(new SearchRequest(getCrawlerName()), 1L, null);

        logContentOfDir(currentTestResourceDir, Level.DEBUG);

        // We remove a directory
        logger.info("  ---> Moving file [{}] to [{}]", file, currentTestResourceDir);
        Files.move(file, currentTestResourceDir.resolve(filename));

        logContentOfDir(currentTestResourceDir, Level.DEBUG);

        // We expect to have 2 docs now
        countTestHelper(new SearchRequest(getCrawlerName()), 2L, null);
    }

    /**
     * Test case for #336: https://github.com/dadoonet/fscrawler/issues/336
     */
    @Test
    public void test_remove_deleted_with_filename_as_id() throws Exception {
        Fs fs = startCrawlerDefinition()
                .setRemoveDeleted(true)
                .setFilenameAsId(true)
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        // We should have two docs first
        countTestHelper(new SearchRequest(getCrawlerName()), 2L, currentTestResourceDir);

        assertThat("Document should exists with [id1.txt] id...", awaitBusy(() -> {
            try {
                return elasticsearchClient.exists(new GetRequest(getCrawlerName(), "doc", "id1.txt"));
            } catch (IOException e) {
                return false;
            }
        }), equalTo(true));
        assertThat("Document should exists with [id2.txt] id...", awaitBusy(() -> {
            try {
                return elasticsearchClient.exists(new GetRequest(getCrawlerName(), "doc", "id2.txt"));
            } catch (IOException e) {
                return false;
            }
        }), equalTo(true));

        // We remove a file
        logger.info("  ---> Removing file id2.txt");
        Files.delete(currentTestResourceDir.resolve("id2.txt"));

        // We expect to have two files
        countTestHelper(new SearchRequest(getCrawlerName()), 1L, currentTestResourceDir);
    }

    /**
     * Test case for https://github.com/dadoonet/fscrawler/issues/362
     * @throws Exception In case something is wrong
     */
    @Test
    public void test_non_readable_file() throws Exception {
        // We change the attributes of the file
        logger.info(" ---> Changing attributes for file roottxtfile.txt");

        boolean isPosix =
                FileSystems.getDefault().supportedFileAttributeViews().contains("posix");

        assumeTrue("This test can only run on Posix systems", isPosix);

        Files.getFileAttributeView(currentTestResourceDir.resolve("roottxtfile.txt"), PosixFileAttributeView.class)
                .setPermissions(EnumSet.noneOf(PosixFilePermission.class));

        Fs fs = startCrawlerDefinition()
                .setIndexContent(false)
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        // We should have one doc first
        countTestHelper(new SearchRequest(getCrawlerName()), 1L, currentTestResourceDir);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void test_upgrade_version() throws Exception {
        // We can only run this test if elasticsearch version is >= 2.3 and < 6.0
        Version version = elasticsearchClient.getVersion();
        assumeFalse("We can only run the upgrade process on version between >= 2.3 and < 6.0",
                version.major < 2 || (version.major == 2 && version.minor < 4) || version.major >= 6);

        // Let's create some deprecated indices
        long nbDocs = randomLongBetween(10, 100);
        long nbFolders = randomLongBetween(1, 10);

        elasticsearchClient.createIndex(getCrawlerName(), false, null);

        ThreadPool threadPool = new ThreadPool(Settings.builder().put(Node.NODE_NAME_SETTING.getKey(), "high-level-client").build());
        BulkProcessor.Listener listener = new BulkProcessor.Listener() {
            @Override public void beforeBulk(long executionId, BulkRequest request) { }
            @Override public void afterBulk(long executionId, BulkRequest request, BulkResponse response) { }
            @Override public void afterBulk(long executionId, BulkRequest request, Throwable failure) { }
        };

        BulkProcessor bulkProcessor = new BulkProcessor.Builder(elasticsearchClient::bulkAsync, listener, threadPool)
                .setBulkActions(1000)
                .setFlushInterval(org.elasticsearch.common.unit.TimeValue.timeValueSeconds(2))
                .build();

        // Create fake data
        for (int i = 0; i < nbDocs; i++) {
            bulkProcessor.add(new IndexRequest(getCrawlerName(), "doc", "id" + i).source("{\"foo\":\"bar\"}", XContentType.JSON));
        }
        for (int i = 0; i < nbFolders; i++) {
            bulkProcessor.add(new IndexRequest(getCrawlerName(), "folder", "id" + i).source("{\"foo\":\"bar\"}", XContentType.JSON));
        }
        bulkProcessor.awaitClose(1, TimeUnit.SECONDS);
        threadPool.shutdownNow();

        elasticsearchClient.refresh(getCrawlerName());

        // Let's create a crawler instance
        FsSettings fsSettings = FsSettings.builder(getCrawlerName())
                .setElasticsearch(securityInstalled ? elasticsearchWithSecurity : elasticsearch).build();
        fsSettings.getElasticsearch().setIndex(getCrawlerName());
        crawler = new FsCrawlerImpl(metadataDir, fsSettings, 0, false);

        // Call the upgrade process
        crawler.upgrade();

        // Test that we have all needed docs in old index and new indices
        long expectedDocs = nbDocs;
        if (elasticsearchClient.getVersion().major < 5) {
            // If we ran our tests against a 2.x cluster, _delete_by_query is skipped (as it does not exist).
            // Which means that folders are still there
            expectedDocs += nbFolders;
        }
        countTestHelper(new SearchRequest(getCrawlerName()), expectedDocs, null);
        countTestHelper(new SearchRequest(getCrawlerName() + INDEX_SUFFIX_FOLDER), nbFolders, null);
    }
}
