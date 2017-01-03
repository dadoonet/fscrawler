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

import fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl;
import fr.pilato.elasticsearch.crawler.fs.client.SearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.SearchResponse;
import fr.pilato.elasticsearch.crawler.fs.meta.job.FsJobFileHandler;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.Elasticsearch;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.Fs;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.Percentage;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.Rest;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.Server;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.util.FsCrawlerUtil;
import org.apache.http.entity.StringEntity;
import org.elasticsearch.client.ResponseException;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl.LOOP_INFINITE;
import static fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClient.extractFromPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeThat;

/**
 * Test all crawler settings
 */
public class FsCrawlerImplAllParametersIT extends AbstractITCase {

    protected FsCrawlerImpl crawler = null;
    protected Path currentTestResourceDir;

    protected static final Path DEFAULT_RESOURCES =  Paths.get(getUrl("samples", "common"));

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

        FsCrawlerUtil.copyDirs(from, currentTestResourceDir);

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
        return generateElasticsearchConfig(indexName, securityInstalled, 1, null);
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
                false,
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

        countTestHelper(jobName, null, null);

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

    /**
     * Check that we have the expected number of docs or at least one if expected is null
     *
     * @param indexName Index we will search in.
     * @param term      Term you search for. MatchAll if null.
     * @param expected  expected number of docs. Null if at least 1.
     * @return the search response if further tests are needed
     * @throws Exception
     */
    public SearchResponse countTestHelper(final String indexName, String term, final Integer expected) throws Exception {
        return countTestHelper(indexName, term, expected, null);
    }

    @Test
    public void test_default_settings() throws Exception {
        startCrawler();
    }

    @Test
    public void test_filesize() throws Exception {
        startCrawler();

        SearchResponse searchResponse = countTestHelper(getCrawlerName(), null, 1);
        for (SearchResponse.Hit hit : searchResponse.getHits().getHits()) {
            Map<String, Object> file = (Map<String, Object>) hit.getSource().get(FsCrawlerUtil.Doc.FILE);
            assertThat(file, notNullValue());
            assertThat(file.get(FsCrawlerUtil.Doc.File.FILESIZE), is(12230));
        }
    }

    @Test
    public void test_filesize_limit() throws Exception {
        Fs fs = startCrawlerDefinition()
                .setIndexedChars(new Percentage(7))
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        SearchResponse searchResponse = countTestHelper(getCrawlerName(), null, 1, null);
        for (SearchResponse.Hit hit : searchResponse.getHits().getHits()) {
            Object content = hit.getSource().get(FsCrawlerUtil.Doc.CONTENT);
            Object indexedChars = extractFromPath(hit.getSource(), FsCrawlerUtil.Doc.FILE).get(FsCrawlerUtil.Doc.File.INDEXED_CHARS);
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

        SearchResponse searchResponse = countTestHelper(getCrawlerName(), null, 1, null);
        for (SearchResponse.Hit hit : searchResponse.getHits().getHits()) {
            Object content = hit.getSource().get(FsCrawlerUtil.Doc.CONTENT);
            Object indexedChars = extractFromPath(hit.getSource(), FsCrawlerUtil.Doc.FILE).get(FsCrawlerUtil.Doc.File.INDEXED_CHARS);
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

        SearchResponse searchResponse = countTestHelper(getCrawlerName(), null, 1, null);
        for (SearchResponse.Hit hit : searchResponse.getHits().getHits()) {
            Object content = hit.getSource().get(FsCrawlerUtil.Doc.CONTENT);
            Object indexedChars = extractFromPath(hit.getSource(), FsCrawlerUtil.Doc.FILE).get(FsCrawlerUtil.Doc.File.INDEXED_CHARS);
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

        SearchResponse searchResponse = countTestHelper(getCrawlerName(), null, 1);
        for (SearchResponse.Hit hit : searchResponse.getHits().getHits()) {
            Map<String, Object> file = (Map<String, Object>) hit.getSource().get(FsCrawlerUtil.Doc.FILE);
            assertThat(file, notNullValue());
            assertThat(file.get(FsCrawlerUtil.Doc.File.FILESIZE), nullValue());
        }
    }

    @Test
    public void test_includes() throws Exception {
        Fs fs = startCrawlerDefinition()
                .addInclude("*_include.txt")
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);
        countTestHelper(getCrawlerName(), null, 1);
    }

    @Test
    public void test_default_metadata() throws Exception {
        startCrawler();

        SearchResponse searchResponse = countTestHelper(getCrawlerName(), null, 1, null);
        for (SearchResponse.Hit hit : searchResponse.getHits().getHits()) {
            assertThat(hit.getSource().get(FsCrawlerUtil.Doc.ATTACHMENT), nullValue());

            assertThat(extractFromPath(hit.getSource(), FsCrawlerUtil.Doc.FILE).get(FsCrawlerUtil.Doc.File.FILENAME), notNullValue());
            assertThat(extractFromPath(hit.getSource(), FsCrawlerUtil.Doc.FILE).get(FsCrawlerUtil.Doc.File.CONTENT_TYPE), notNullValue());
            assertThat(extractFromPath(hit.getSource(), FsCrawlerUtil.Doc.FILE).get(FsCrawlerUtil.Doc.File.URL), notNullValue());
            assertThat(extractFromPath(hit.getSource(), FsCrawlerUtil.Doc.FILE).get(FsCrawlerUtil.Doc.File.FILESIZE), notNullValue());
            assertThat(extractFromPath(hit.getSource(), FsCrawlerUtil.Doc.FILE).get(FsCrawlerUtil.Doc.File.INDEXING_DATE), notNullValue());
            assertThat(extractFromPath(hit.getSource(), FsCrawlerUtil.Doc.FILE).get(FsCrawlerUtil.Doc.File.INDEXED_CHARS), nullValue());
            assertThat(extractFromPath(hit.getSource(), FsCrawlerUtil.Doc.FILE).get(FsCrawlerUtil.Doc.File.LAST_MODIFIED), notNullValue());

            assertThat(extractFromPath(hit.getSource(), FsCrawlerUtil.Doc.META).get(FsCrawlerUtil.Doc.Meta.TITLE), notNullValue());
        }
    }

    @Test
    public void test_attributes() throws Exception {
        Fs fs = startCrawlerDefinition()
                .setAttributesSupport(true)
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);
        SearchResponse searchResponse = countTestHelper(getCrawlerName(), null, 1, null);
        for (SearchResponse.Hit hit : searchResponse.getHits().getHits()) {
            assertThat(extractFromPath(hit.getSource(), FsCrawlerUtil.Doc.ATTRIBUTES).get(FsCrawlerUtil.Doc.Attributes.OWNER), notNullValue());
        }
    }

    @Test
    public void test_remove_deleted_enabled() throws Exception {
        Fs fs = startCrawlerDefinition()
                .setRemoveDeleted(true)
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        // We should have two docs first
        countTestHelper(getCrawlerName(), null, 2, currentTestResourceDir);

        // We remove a file
        logger.info("  ---> Removing file deleted_roottxtfile.txt");
        Files.delete(currentTestResourceDir.resolve("deleted_roottxtfile.txt"));

        // We expect to have two files
        countTestHelper(getCrawlerName(), null, 1, currentTestResourceDir);
    }

    @Test
    public void test_remove_deleted_disabled() throws Exception {
        Fs fs = startCrawlerDefinition()
                .setRemoveDeleted(false)
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        // We should have two docs first
        countTestHelper(getCrawlerName(), null, 2, currentTestResourceDir);

        // We remove a file
        logger.info(" ---> Removing file deleted_roottxtfile.txt");
        Files.delete(currentTestResourceDir.resolve("deleted_roottxtfile.txt"));

        // We expect to have two files
        countTestHelper(getCrawlerName(), null, 2, currentTestResourceDir);
    }

    /**
     * Test case for https://github.com/dadoonet/fscrawler/issues/110
     * @throws Exception In case something is wrong
     */
    @Test
    public void test_rename_file() throws Exception {
        startCrawler();

        // We should have two docs first
        countTestHelper(getCrawlerName(), null, 1, currentTestResourceDir);

        // We rename the file
        logger.info(" ---> Renaming file roottxtfile.txt to renamed_roottxtfile.txt");
        // We create a copy of a file
        Files.move(currentTestResourceDir.resolve("roottxtfile.txt"),
                currentTestResourceDir.resolve("renamed_roottxtfile.txt"));

        // We expect to have one file only with a new name
        countTestHelper(getCrawlerName(), "file.filename:renamed_roottxtfile.txt", 1, currentTestResourceDir);
    }

    /**
     * Test case for issue #60: https://github.com/dadoonet/fscrawler/issues/60 : new files are not added
     */
    @Test
    public void test_add_new_file() throws Exception {
        startCrawler();

        // We should have one doc first
        countTestHelper(getCrawlerName(), null, 1, currentTestResourceDir);

        logger.info(" ---> Adding a copy of roottxtfile.txt");
        // We create a copy of a file
        Files.copy(currentTestResourceDir.resolve("roottxtfile.txt"),
                currentTestResourceDir.resolve("new_roottxtfile.txt"));

        // We expect to have two files
        countTestHelper(getCrawlerName(), null, 2, currentTestResourceDir);
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

        assertThat("We should have 0 doc for tweet in text field...", awaitBusy(() -> {
            try {
                SearchResponse response = elasticsearchClient.search(getCrawlerName(), null, "text:tweet");
                return response.getHits().getTotal() == 2;
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
                SearchResponse response = elasticsearchClient.search(getCrawlerName(), null, "text:tweet");
                return response.getHits().getTotal() == 0;
            } catch (IOException e) {
                logger.warn("Caught exception while running the test", e);
                return false;
            }
        }), equalTo(true));

        assertThat("We should have 2 docs for tweet in _all...", awaitBusy(() -> {
            try {
                SearchResponse response = elasticsearchClient.search(getCrawlerName(), null, "_all:tweet");
                return response.getHits().getTotal() == 2;
            } catch (IOException e) {
                logger.warn("Caught exception while running the test", e);
                return false;
            }
        }), equalTo(true));
    }

    /**
     * Test case for issue #7: https://github.com/dadoonet/fscrawler/issues/7 : JSON support: use filename as ID
     */
    @Test
    public void test_filename_as_id() throws Exception {
        Fs fs = startCrawlerDefinition()
                .setJsonSupport(true)
                .setFilenameAsId(true)
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        assertThat("Document should exists with [tweet1] id...", awaitBusy(() -> {
            try {
                return elasticsearchClient.isExistingDocument(getCrawlerName(), FsCrawlerUtil.INDEX_TYPE_DOC, "tweet1");
            } catch (IOException e) {
                return false;
            }
        }), equalTo(true));

        assertThat("Document should exists with [tweet2] id...", awaitBusy(() -> {
            try {
                return elasticsearchClient.isExistingDocument(getCrawlerName(), FsCrawlerUtil.INDEX_TYPE_DOC, "tweet2");
            } catch (IOException e) {
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

        SearchResponse searchResponse = countTestHelper(getCrawlerName(), null, 1, null);
        for (SearchResponse.Hit hit : searchResponse.getHits().getHits()) {
            // We check that the field is in _source
            assertThat(hit.getSource().get(FsCrawlerUtil.Doc.ATTACHMENT), notNullValue());
        }
    }

    @Test
    public void test_do_not_store_source() throws Exception {
        startCrawler();

        SearchResponse searchResponse = countTestHelper(getCrawlerName(), null, 1, null, "_source", "*");
        for (SearchResponse.Hit hit : searchResponse.getHits().getHits()) {
            // We check that the field has not been stored
            assertThat(hit.getFields(), nullValue());

            // We check that the field is not part of _source
            assertThat(hit.getSource().get(FsCrawlerUtil.Doc.ATTACHMENT), nullValue());
        }
    }

    @Test
    public void test_defaults() throws Exception {
        startCrawler();

        // We expect to have one file
        SearchResponse searchResponse = countTestHelper(getCrawlerName(), null, 1);

        // The default configuration should not add file attributes
        for (SearchResponse.Hit hit : searchResponse.getHits().getHits()) {
            assertThat(hit.getSource().get(FsCrawlerUtil.Doc.ATTRIBUTES), nullValue());
        }

    }

    @Test
    public void test_subdirs() throws Exception {
        startCrawler();

        // We expect to have two files
        countTestHelper(getCrawlerName(), null, 2);
    }

    @Test
    public void test_subdirs_with_patterns() throws Exception {
        Fs fs = startCrawlerDefinition()
                .addInclude("*.txt")
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        // We expect to have seven files
        countTestHelper(getCrawlerName(), null, 7);
    }

    @Test
    public void test_ignore_dir() throws Exception {
        Fs fs = startCrawlerDefinition()
                .addExclude(".ignore")
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        // We expect to have one file
        countTestHelper(getCrawlerName(), null, 1);
    }

    @Test
    public void test_multiple_crawlers() throws Exception {
        Fs fs1 = startCrawlerDefinition(currentTestResourceDir.resolve("crawler1").toString()).build();
        Fs fs2 = startCrawlerDefinition(currentTestResourceDir.resolve("crawler2").toString()).build();
        FsCrawlerImpl crawler1 = startCrawler(getCrawlerName() + "_1", fs1, endCrawlerDefinition(getCrawlerName() + "_1"), null);
        FsCrawlerImpl crawler2 = startCrawler(getCrawlerName() + "_2", fs2, endCrawlerDefinition(getCrawlerName() + "_2"), null);
        // We should have one doc in index 1...
        countTestHelper(getCrawlerName() + "_1", null, 1);
        // We should have one doc in index 2...
        countTestHelper(getCrawlerName() + "_2", null, 1);

        crawler1.close();
        crawler2.close();
    }

    @Test
    public void test_filename_analyzer() throws Exception {
        startCrawler();

        // We should have one doc
        countTestHelper(getCrawlerName(), "file.filename:roottxtfile.txt", 1, null);
    }

    /**
     * Test for #83: https://github.com/dadoonet/fscrawler/issues/83
     */
    @Test
    public void test_time_value() throws Exception {
        Fs fs = startCrawlerDefinition(TimeValue.timeValueHours(1)).build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        // We expect to have one file
        countTestHelper(getCrawlerName(), null, 1);
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

        countTestHelper(getCrawlerName(), null, 2);
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

        countTestHelper(getCrawlerName(), null, 1);
    }

    /**
     * Test for #105: https://github.com/dadoonet/fscrawler/issues/105
     */
    @Test
    public void test_unparsable() throws Exception {
        startCrawler();

        // We expect to have two files
        countTestHelper(getCrawlerName(), null, 2);
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
        countTestHelper(getCrawlerName(), null, 1);

        countTestHelper(getCrawlerName(), "content:file*", 0, null);
        countTestHelper(getCrawlerName(), "file.content_type:text*", 0, null);
    }

    @Test
    public void test_bulk_flush() throws Exception {
        Fs fs = startCrawlerDefinition().build();
        startCrawler(getCrawlerName(), fs,
                generateElasticsearchConfig(getCrawlerName(), securityInstalled, 100, TimeValue.timeValueSeconds(2)), null);

        countTestHelper(getCrawlerName(), null, 1);
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
        SearchResponse searchResponse = countTestHelper(getCrawlerName(), null, 1, null);
        for (SearchResponse.Hit hit : searchResponse.getHits().getHits()) {
            Object checksum = extractFromPath(hit.getSource(), FsCrawlerUtil.Doc.FILE).get(FsCrawlerUtil.Doc.File.CHECKSUM);
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
        SearchResponse searchResponse = countTestHelper(getCrawlerName(), null, 1, null);
        for (SearchResponse.Hit hit : searchResponse.getHits().getHits()) {
            Object checksum = extractFromPath(hit.getSource(), FsCrawlerUtil.Doc.FILE).get(FsCrawlerUtil.Doc.File.CHECKSUM);
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
                SearchResponse response = elasticsearchClient.search(getCrawlerName(), "doc", (String) null);
                return response.getHits().getTotal() == 2;
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
        SearchResponse response = countTestHelper(getCrawlerName(), null, 3);

        countTestHelper(getCrawlerName(), "title:maeve", 1);
        countTestHelper(getCrawlerName(), "price:[5 TO 6]", 2);

        logger.info("XML documents converted to:");
        for (SearchResponse.Hit hit : response.getHits().getHits()) {
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
                .setElasticsearch(endCrawlerDefinition(getCrawlerName())).setFs(fs).build(), 1, false, false);
        crawler.start();

        countTestHelper(getCrawlerName(), null, 1);

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
                .setElasticsearch(endCrawlerDefinition(getCrawlerName())).setFs(fs).build(), 2, false, false);
        crawler.start();

        countTestHelper(getCrawlerName(), null, 1);

        assertThat("Job should stop after two runs", awaitBusy(() -> crawler.isClosed()), is(true));
        assertThat(crawler.getRunNumber(), is(2));
    }

    /**
     * Test case for #205: https://github.com/dadoonet/fscrawler/issues/205 : Add support for update mapping
     */
    @Test
    public void test_update_mapping() throws Exception {
        elasticsearchClient.createIndex(getCrawlerName());
        elasticsearchClient.putMapping(getCrawlerName(), FsCrawlerUtil.INDEX_TYPE_DOC,
                "{ \""+ FsCrawlerUtil.INDEX_TYPE_DOC + "\" : {   \"_source\" : {\n" +
                        "    \"excludes\" : [\n" +
                        "      \"attachment\"\n" +
                        "    ]\n" +
                        "  }\n} }");

        Fs fs = startCrawlerDefinition().build();

        logger.info("  --> starting crawler [{}]", getCrawlerName());

        crawler = new FsCrawlerImpl(metadataDir, FsSettings.builder(getCrawlerName())
                .setElasticsearch(endCrawlerDefinition(getCrawlerName())).setFs(fs).build(), -1, true, false);
        crawler.start();

        countTestHelper(getCrawlerName(), null, 1);
    }

    /**
     * Test case for #205: https://github.com/dadoonet/fscrawler/issues/205 : Add support for update mapping
     */
    @Test
    public void test_update_mapping_but_dont_launch() throws Exception {
        elasticsearchClient.createIndex(getCrawlerName());
        elasticsearchClient.putMapping(getCrawlerName(), FsCrawlerUtil.INDEX_TYPE_DOC,
                "{ \""+ FsCrawlerUtil.INDEX_TYPE_DOC + "\" : {   \"_source\" : {\n" +
                        "    \"excludes\" : [\n" +
                        "      \"attachment\"\n" +
                        "    ]\n" +
                        "  }\n} }");

        Fs fs = startCrawlerDefinition().build();

        logger.info("  --> starting crawler [{}]", getCrawlerName());

        crawler = new FsCrawlerImpl(metadataDir, FsSettings.builder(getCrawlerName())
                .setElasticsearch(endCrawlerDefinition(getCrawlerName())).setFs(fs).build(), 0, true, false);
        crawler.start();

        assertThat(crawler.isClosed(), is(true));
        assertThat(crawler.getRunNumber(), is(0));
    }

    /**
     * Test case for #205: https://github.com/dadoonet/fscrawler/issues/205 : Add support for update mapping
     */
    @Test(expected = ResponseException.class)
    public void test_fail_update_mapping() throws Exception {
        elasticsearchClient.createIndex(getCrawlerName());
        elasticsearchClient.putMapping(getCrawlerName(), FsCrawlerUtil.INDEX_TYPE_DOC,
                "{ \""+ FsCrawlerUtil.INDEX_TYPE_DOC + "\" : {\n" +
                        "  \"properties\": {\n" +
                        "    \"content\": {\n" +
                        "      \"type\": \"date\"\n" +
                        "    }\n" +
                        "  }\n" +
                        "}" +
                        " }");

        Fs fs = startCrawlerDefinition().build();

        logger.info("  --> starting crawler [{}]", getCrawlerName());

        crawler = new FsCrawlerImpl(metadataDir, FsSettings.builder(getCrawlerName())
                .setElasticsearch(endCrawlerDefinition(getCrawlerName())).setFs(fs).build(), -1, true, false);

        crawler.start();
    }

    /**
     * Test case for #95: https://github.com/dadoonet/fscrawler/issues/95 : Folder index is not getting delete on delete of folder
     * This test is marked as Ignored because it fails. Which proves that the issue reported is real!
     */
    @Test @Ignore
    public void test_remove_folder_deleted_enabled() throws Exception {
        Fs fs = startCrawlerDefinition()
                .setRemoveDeleted(true)
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        // We should have 7 docs first
        countTestHelper(getCrawlerName(), null, 7, currentTestResourceDir);

        // We remove a directory
        logger.info("  ---> Removing dir subdir1");
        Files.walkFileTree(currentTestResourceDir.resolve("subdir1"), new SimpleFileVisitor<Path>() {
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

        // We expect to have 4 docs now
        countTestHelper(getCrawlerName(), null, 4, currentTestResourceDir);
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
        countTestHelper(getCrawlerName(), null, 2);

        // We expect having no folders
        SearchRequest.Builder sr = SearchRequest.builder();
        SearchResponse response = elasticsearchClient.search(getCrawlerName(), FsCrawlerUtil.INDEX_TYPE_FOLDER, sr.build());
        staticLogger.trace("result {}", response.toString());
        assertThat(response.getHits().getTotal(), is(0L));
    }


    /**
     * Test case for #183: https://github.com/dadoonet/fscrawler/issues/183 : Optimize document and folder mappings
     * We want to make sure we can highlight documents even if we don't store fields
     */
    @Test
    public void test_highlight_documents() throws Exception {
        startCrawler();

        // We expect to have one file
        countTestHelper(getCrawlerName(), null, 1);

        // Let's test highlighting
        SearchResponse response = elasticsearchClient.searchJson(getCrawlerName(), FsCrawlerUtil.INDEX_TYPE_DOC,
                "{\n" +
                        "  \"query\": {\n" +
                        "    \"match\": {\n" +
                        "      \"content\": \"exemplo\"\n" +
                        "    }\n" +
                        "  },\n" +
                        "  \"highlight\": {\n" +
                        "    \"fields\": {\n" +
                        "      \"content\": {}\n" +
                        "    }\n" +
                        "  }\n" +
                        "}");
        staticLogger.trace("result {}", response.toString());
        assertThat(response.getHits().getTotal(), is(1L));

        SearchResponse.Hit hit = response.getHits().getHits().get(0);
        assertThat(hit.getHighlight(), hasKey("content"));
        assertThat(hit.getHighlight().get("content"), hasSize(1));
        assertThat(hit.getHighlight().get("content").get(0), containsString("<em>exemplo</em>"));
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
        countTestHelper(getCrawlerName(), null, 1);
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
        StringEntity entity = new StringEntity(pipeline, Charset.defaultCharset());

        elasticsearchClient.getClient().performRequest("PUT", "_ingest/pipeline/" + crawlerName,
                Collections.emptyMap(), entity);

        Elasticsearch elasticsearch = endCrawlerDefinition(crawlerName);
        elasticsearch.setPipeline(crawlerName);

        startCrawler(crawlerName, startCrawlerDefinition().build(), elasticsearch, null);

        // We expect to have one file
        countTestHelper(crawlerName, "my_content_field:perniciosoque", 1);
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
                false,
                true);
        crawler.start();

        // We expect to have one file
//        countTestHelper(getCrawlerName(), null, 1);
    }
}
