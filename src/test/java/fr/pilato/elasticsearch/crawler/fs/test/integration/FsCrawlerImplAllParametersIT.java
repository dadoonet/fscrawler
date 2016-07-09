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

import com.google.api.client.util.Data;
import fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClient;
import fr.pilato.elasticsearch.crawler.fs.client.SearchResponse;
import fr.pilato.elasticsearch.crawler.fs.meta.job.FsJobFileHandler;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.Elasticsearch;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.Fs;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.Percentage;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.Server;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.util.FsCrawlerUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assume.assumeNoException;

/**
 * Test all crawler settings
 */
public class FsCrawlerImplAllParametersIT extends AbstractITCase {

    protected FsCrawlerImpl crawler = null;
    protected Path currentTestResourceDir;

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

        staticLogger.debug("  --> Copying test resources from [{}]", from);
        if (Files.notExists(from)) {
            logger.error("directory [{}] should be copied to [{}]", from, currentTestResourceDir);
            throw new RuntimeException(from + " doesn't seem to exist. Check your JUnit tests.");
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
        return Elasticsearch.builder()
                .setIndex(indexName)
                .addNode(Elasticsearch.Node.builder().setHost("127.0.0.1").setPort(HTTP_TEST_PORT).build())
                .setBulkSize(1)
                .build();
    }

    private void startCrawler() throws Exception {
        startCrawler(getCrawlerName());
    }

    private void startCrawler(final String jobName) throws Exception {
        startCrawler(jobName, startCrawlerDefinition().build(), endCrawlerDefinition(jobName), null);
    }

    private FsCrawlerImpl startCrawler(final String jobName, Fs fs, Elasticsearch elasticsearch, Server server) throws Exception {
        logger.info("  --> starting crawler [{}]", jobName);

        // TODO do this rarely() createIndex(jobName);

        crawler = new FsCrawlerImpl(metadataDir, FsSettings.builder(jobName).setElasticsearch(elasticsearch).setFs(fs).setServer(server).build());
        crawler.start();

        // We wait up to 10 seconds before considering a failing test
        assertThat("Job meta file should exists in ~/.fscrawler...", awaitBusy(() -> {
            try {
                new FsJobFileHandler(metadataDir).read(jobName);
                return true;
            } catch (IOException e) {
                return false;
            }
        }), equalTo(true));

        countTestHelper(jobName, null, null);

        // Make sure we refresh indexed docs before launching tests
        refresh();

        return crawler;
    }

    private void stopCrawler() throws InterruptedException, IOException {
        logger.info("  --> stopping crawler");
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
        ElasticsearchClient client = ElasticsearchClient.builder().build();
        boolean active = client.isActive(Elasticsearch.DEFAULT.getNodes().get(0));
        if (active) {
            logger.warn("you have a local elasticsearch node running on 9200 port. We will skip the test.");
            return;
        }

        try {
            startCrawler(getCrawlerName(), null, null, null);
        } catch (IOException e) {
            // We expect it as we probably don't have an elasticsearch node running with default 9200 port
        }
    }

    @Test
    public void test_filesize() throws Exception {
        startCrawler();

        SearchResponse searchResponse = countTestHelper(getCrawlerName(), null, 1);
        for (SearchResponse.Hit hit : searchResponse.getHits().getHits()) {
            Map<String, Object> file = (Map<String, Object>) hit.getSource().get(FsCrawlerUtil.Doc.FILE);
            assertThat(file, notNullValue());
            assertThat(file.get(FsCrawlerUtil.Doc.File.FILESIZE), is(new BigDecimal(30)));
        }
    }

    @Test
    public void test_filesize_limit() throws Exception {
        Fs fs = startCrawlerDefinition()
                .setIndexedChars(new Percentage(7))
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        SearchResponse searchResponse = countTestHelper(getCrawlerName(), null, 1, null, null, "*");
        for (SearchResponse.Hit hit : searchResponse.getHits().getHits()) {
            Object content = hit.getFields().get(FsCrawlerUtil.Doc.CONTENT);
            Object indexedChars = hit.getFields().get(FsCrawlerUtil.Doc.FILE + "." + FsCrawlerUtil.Doc.File.INDEXED_CHARS);
            assertThat(content, notNullValue());
            assertThat(indexedChars, notNullValue());

            assertThat(hit.getFields().get(FsCrawlerUtil.Doc.CONTENT), notNullValue());
            assertThat(hit.getFields().get(FsCrawlerUtil.Doc.FILE + "." + FsCrawlerUtil.Doc.File.INDEXED_CHARS), notNullValue());

            // Our original text: "Bonjour David..." should be truncated
            assertThat(((ArrayList<Object>) content).get(0), is("Bonjour"));
            assertThat(((ArrayList<Object>) indexedChars).get(0), is(new BigDecimal(7)));
        }
    }

    @Test
    public void test_filesize_limit_percentage() throws Exception {
        Fs fs = startCrawlerDefinition()
                .setIndexedChars(Percentage.parse("0.1%"))
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        SearchResponse searchResponse = countTestHelper(getCrawlerName(), null, 1, null, null, "*");
        for (SearchResponse.Hit hit : searchResponse.getHits().getHits()) {
            Object content = hit.getFields().get(FsCrawlerUtil.Doc.CONTENT);
            Object indexedChars = hit.getFields().get(FsCrawlerUtil.Doc.FILE + "." + FsCrawlerUtil.Doc.File.INDEXED_CHARS);
            assertThat(content, notNullValue());
            assertThat(indexedChars, notNullValue());

            // Our original text: "Bonjour David..." should be truncated
            assertThat(((ArrayList<Object>) content).get(0), is("Bonjour "));
            assertThat(((ArrayList<Object>) indexedChars).get(0), is(new BigDecimal(8)));
        }
    }

    @Test
    public void test_filesize_nolimit() throws Exception {
        Fs fs = startCrawlerDefinition()
                .setIndexedChars(new Percentage(-1))
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        SearchResponse searchResponse = countTestHelper(getCrawlerName(), null, 1, null, null, "*");
        for (SearchResponse.Hit hit : searchResponse.getHits().getHits()) {
            Object content = hit.getFields().get(FsCrawlerUtil.Doc.CONTENT);
            assertThat(content, notNullValue());
            assertThat(hit.getFields().get(FsCrawlerUtil.Doc.FILE + "." + FsCrawlerUtil.Doc.File.INDEXED_CHARS), nullValue());

            // Our original text: "Bonjour David\n\n\n" should not be truncated
            assertThat(((ArrayList<Object>) content).get(0), is("Bonjour David\n\n\n"));
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
            assertThat(Data.isNull(file.get(FsCrawlerUtil.Doc.File.FILESIZE)), is(true));
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

        SearchResponse searchResponse = countTestHelper(getCrawlerName(), null, 1, null, null, "*");
        for (SearchResponse.Hit hit : searchResponse.getHits().getHits()) {
            assertThat(hit.getFields().get(FsCrawlerUtil.Doc.ATTACHMENT), nullValue());

            assertThat(hit.getFields().get(FsCrawlerUtil.Doc.FILE + "." + FsCrawlerUtil.Doc.File.FILENAME), notNullValue());
            assertThat(hit.getFields().get(FsCrawlerUtil.Doc.FILE + "." + FsCrawlerUtil.Doc.File.CONTENT_TYPE), notNullValue());
            assertThat(hit.getFields().get(FsCrawlerUtil.Doc.FILE + "." + FsCrawlerUtil.Doc.File.URL), notNullValue());
            assertThat(hit.getFields().get(FsCrawlerUtil.Doc.FILE + "." + FsCrawlerUtil.Doc.File.FILESIZE), notNullValue());
            assertThat(hit.getFields().get(FsCrawlerUtil.Doc.FILE + "." + FsCrawlerUtil.Doc.File.INDEXING_DATE), notNullValue());
            assertThat(hit.getFields().get(FsCrawlerUtil.Doc.FILE + "." + FsCrawlerUtil.Doc.File.INDEXED_CHARS), nullValue());
            assertThat(hit.getFields().get(FsCrawlerUtil.Doc.FILE + "." + FsCrawlerUtil.Doc.File.LAST_MODIFIED), notNullValue());

            assertThat(hit.getFields().get(FsCrawlerUtil.Doc.META + "." + FsCrawlerUtil.Doc.Meta.TITLE), notNullValue());
        }
    }

    @Test
    public void test_attributes() throws Exception {
        Fs fs = startCrawlerDefinition()
                .setAttributesSupport(true)
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);
        SearchResponse searchResponse = countTestHelper(getCrawlerName(), null, 1, null, null, "attributes.owner");
        for (SearchResponse.Hit hit : searchResponse.getHits().getHits()) {
            assertThat(hit.getFields().get(FsCrawlerUtil.Doc.ATTRIBUTES + "." + FsCrawlerUtil.Doc.Attributes.OWNER), notNullValue());
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

        SearchResponse searchResponse = countTestHelper(getCrawlerName(), null, 1, null, null, "_source", "*");
        for (SearchResponse.Hit hit : searchResponse.getHits().getHits()) {
            // We check that the field has been stored
            assertThat(hit.getFields().get(FsCrawlerUtil.Doc.ATTACHMENT), notNullValue());

            // We check that the field is not part of _source
            assertThat(hit.getSource().get(FsCrawlerUtil.Doc.ATTACHMENT), nullValue());
        }
    }

    @Test
    public void test_do_not_store_source() throws Exception {
        startCrawler();

        SearchResponse searchResponse = countTestHelper(getCrawlerName(), null, 1, null, null, "_source", "*");
        for (SearchResponse.Hit hit : searchResponse.getHits().getHits()) {
            // We check that the field has not been stored
            assertThat(hit.getFields().get(FsCrawlerUtil.Doc.ATTACHMENT), nullValue());

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
            assertThat(Data.isNull(hit.getSource().get(FsCrawlerUtil.Doc.ATTRIBUTES)), is(true));
        }

    }

    @Test
    public void test_subdirs() throws Exception {
        startCrawler();

        // We expect to have two files
        countTestHelper(getCrawlerName(), null, 2);
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
        startCrawler(getCrawlerName(), fs, Elasticsearch.builder()
                .setIndex(getCrawlerName())
                .addNode(Elasticsearch.Node.builder().setHost("127.0.0.1").setPort(HTTP_TEST_PORT).build())
                .setBulkSize(100)
                .setFlushInterval(TimeValue.timeValueSeconds(2))
                .build(), null);

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
        SearchResponse searchResponse = countTestHelper(getCrawlerName(), null, 1, null, null,
                FsCrawlerUtil.Doc.FILE + "." + FsCrawlerUtil.Doc.File.CHECKSUM);
        for (SearchResponse.Hit hit : searchResponse.getHits().getHits()) {
            Object checksum = hit.getFields().get(FsCrawlerUtil.Doc.FILE + "." + FsCrawlerUtil.Doc.File.CHECKSUM);
            assertThat(checksum, notNullValue());
            assertThat(((ArrayList<Object>) checksum).get(0), is("c32eafae2587bef4b3b32f73743c3c61"));
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
        SearchResponse searchResponse = countTestHelper(getCrawlerName(), null, 1, null, null,
                FsCrawlerUtil.Doc.FILE + "." + FsCrawlerUtil.Doc.File.CHECKSUM);
        for (SearchResponse.Hit hit : searchResponse.getHits().getHits()) {
            Object checksum = hit.getFields().get(FsCrawlerUtil.Doc.FILE + "." + FsCrawlerUtil.Doc.File.CHECKSUM);
            assertThat(checksum, notNullValue());
            assertThat(((ArrayList<Object>) checksum).get(0), is("3e99cc50a64f53cf1cef011797400abb73b447a7"));
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
}
