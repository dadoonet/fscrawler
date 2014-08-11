/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

package fr.pilato.elasticsearch.river.fs.integration;

import fr.pilato.elasticsearch.river.fs.river.FsRiver;
import fr.pilato.elasticsearch.river.fs.util.FsRiverUtil;
import fr.pilato.elasticsearch.river.fs.util.FsUtils;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsResponse;
import org.elasticsearch.action.count.CountResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.base.Predicate;
import org.elasticsearch.common.io.FileSystemUtils;
import org.elasticsearch.common.joda.time.DateTime;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.test.ElasticsearchIntegrationTest;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.hamcrest.Matchers.equalTo;

/**
 * Test all river settings
 */
@ElasticsearchIntegrationTest.ClusterScope(scope = ElasticsearchIntegrationTest.Scope.SUITE)
public class FsRiverAllParametersTest extends ElasticsearchIntegrationTest {

    // Test infrastructure creates random number of shards and replicas.
    // But we expect to have only one shard for rivers
    @Override
    protected boolean randomizeNumberOfShardsAndReplicas() {
        return false;
    }

    private static final String testRiverPrefix = "fsriver_test_";

    private String getRiverName() {
        String testName = testRiverPrefix.concat(Strings.toUnderscoreCase(getTestName()));
        return testName.indexOf(" ") >= 0? Strings.split(testName, " ")[0] : testName;
    }

    private XContentBuilder startRiverDefinition(String dir) throws IOException {
        return startRiverDefinition(dir, 500);
    }

    private XContentBuilder startRiverDefinition(String dir, long updateRate) throws IOException {
        return jsonBuilder().prettyPrint().startObject()
                .field("type", "fs")
                .startObject("fs")
                    .field("url", getUrl(dir))
                    .field("update_rate", updateRate);
    }

    private XContentBuilder endRiverDefinition(XContentBuilder xcb) throws IOException {
        xcb.endObject()
                .startObject("index")
                .field("bulk_size", 1)
                .endObject()
                .endObject();

        logger.info(" --> creating river: {}", xcb.string());
        return xcb;
    }

    private File URItoFile(URL url) {
        try {
            return new File(url.toURI());
        } catch(URISyntaxException e) {
            return new File(url.getPath());
        }
    }

    private String getUrl(String dir) throws IOException {
        URL resource = FsRiverAllParametersTest.class.getResource("/elasticsearch.yml");
        File dataDir = new File(URItoFile(resource).getParentFile(), dir);
        if (!dataDir.exists()) {
            logger.error("directory [src/test/resources/{}] should be copied to [{}]", dir, dataDir);
            throw new RuntimeException("src/test/resources/" + dir + " doesn't seem to exist. Check your JUnit tests.");
        }

        return dataDir.getAbsoluteFile().getAbsolutePath();
    }

    private void startRiver(final String riverName, XContentBuilder river) throws InterruptedException {
        logger.info("  --> starting river [{}]", riverName);
        createIndex(riverName);
        client().prepareIndex("_river", riverName, "_meta").setSource(river).get();

        // We wait up to 10 seconds before considering a failing test
        assertThat("Document should exists with [_lastupdated] id...", awaitBusy(new Predicate<Object>() {
            @Override
            public boolean apply(Object o) {
                GetResponse getResponse = client().prepareGet("_river", riverName, "_lastupdated").execute().actionGet();
                return getResponse.isExists();
            }
        }, 10, TimeUnit.SECONDS), equalTo(true));

        // Make sure we refresh indexed docs before launching tests
        refresh();

        // Print index settings
        GetSettingsResponse riverSettings = client().admin().indices().prepareGetSettings("_river").get();
        logger.info("  --> Index settings [{}]", riverSettings);
    }

    private void stopRiver(final String riverName) {
        if (riverName == null) {
            logger.info("  --> stopping all rivers");
            cluster().wipeIndices("_river");
        } else {
            logger.info("  --> stopping river [{}]", riverName);
            client().admin().indices().prepareDeleteMapping("_river").setType(riverName).get();
        }
    }

    /**
     * Check that we have the expected number of docs or at least one if expected is null
     *
     * @param indexName Index we will search in.
     * @param term      Term you search for. MatchAll if null.
     * @param expected  expected number of docs. Null if at least 1.
     * @throws Exception
     */
    public void countTestHelper(final String indexName, String term, final Integer expected) throws Exception {
        countTestHelper(indexName, term, expected, null);
    }

    /**
     * Check that we have the expected number of docs or at least one if expected is null
     *
     * @param indexName Index we will search in.
     * @param term      Term you search for. MatchAll if null.
     * @param expected  expected number of docs. Null if at least 1.
     * @param path      Path we are supposed to scan. If we have not accurate results, we display its content
     * @throws Exception
     */
    public void countTestHelper(final String indexName, String term, final Integer expected, final String path) throws Exception {
        // Let's search for entries
        final QueryBuilder query;
        if (term == null) {
            query = QueryBuilders.matchAllQuery();
        } else {
            query = QueryBuilders.queryString(term);
        }

        // We wait up to 5 seconds before considering a failing test
        assertThat(awaitBusy(new Predicate<Object>() {
            @Override
            public boolean apply(Object o) {
                long totalHits;
                if (logger.isDebugEnabled()) {
                    // We want traces, so let's run a search query and trace results
                    // Let's search for entries
                    SearchResponse response = client().prepareSearch(indexName)
                            .setTypes(FsRiverUtil.INDEX_TYPE_DOC)
                            .setQuery(query).execute().actionGet();

                    logger.debug("result {}", response.toString());
                    totalHits = response.getHits().getTotalHits();
                } else {
                    CountResponse response = client().prepareCount(indexName)
                            .setTypes(FsRiverUtil.INDEX_TYPE_DOC)
                            .setQuery(query).execute().actionGet();
                    totalHits = response.getCount();
                }

                if (expected == null) {
                    return (totalHits >= 1);
                } else {
                    if (expected.intValue() == totalHits) {
                        return true;
                    } else {
                        logger.info("     ---> expecting [{}] but got [{}] documents in [{}]", expected, totalHits, indexName);
                        if (path != null) {
                            logger.info("     ---> content of [{}]:", path);
                            File[] files = new File(path).listFiles();
                            for (int i = 0; i < files.length; i++) {
                                File file = files[i];
                                logger.info("         - {} {}", file.getAbsolutePath(), new DateTime(file.lastModified()));
                            }
                        }
                        return false;
                    }
                }
            }
        }, 10, TimeUnit.SECONDS), equalTo(true));
    }

    @Test
    public void test_filesize() throws IOException, InterruptedException {
        XContentBuilder river = startRiverDefinition("testfs_metadata")
                .field("excludes", "*.json");
        startRiver(getRiverName(), endRiverDefinition(river));

        SearchResponse searchResponse = client().prepareSearch(getRiverName()).setTypes("doc")
                .setQuery(QueryBuilders.matchAllQuery())
                .execute().actionGet();
        logger.info(searchResponse.toString());

        for (SearchHit hit : searchResponse.getHits()) {
            Map<String, Object> file = (Map<String, Object>) hit.getSource().get(FsRiverUtil.Doc.FILE);
            assertNotNull(file);
            assertEquals(8355, file.get(FsRiverUtil.Doc.File.FILESIZE));
        }
    }

    @Test
    public void test_filesize_limit() throws IOException, InterruptedException {
        XContentBuilder river = startRiverDefinition("testfs_metadata")
                .field("excludes", "*.json")
                .field("indexed_chars", 0.001);
        startRiver(getRiverName(), endRiverDefinition(river));

        SearchResponse searchResponse = client().prepareSearch(getRiverName()).setTypes("doc")
                .setQuery(QueryBuilders.matchAllQuery())
                .addField("*")
                .execute().actionGet();
        logger.info(searchResponse.toString());

        for (SearchHit hit : searchResponse.getHits()) {
            assertNotNull(hit.getFields().get(FsRiverUtil.Doc.CONTENT));
            assertNotNull(hit.getFields().get(FsRiverUtil.Doc.FILE + "." + FsRiverUtil.Doc.File.INDEXED_CHARS));

            // Our original text: "Bonjour David..." should be truncated
            assertEquals("Bonjour ", hit.getFields().get(FsRiverUtil.Doc.CONTENT).getValue());
            assertEquals(8L, hit.getFields().get(FsRiverUtil.Doc.FILE + "." + FsRiverUtil.Doc.File.INDEXED_CHARS).getValue());
        }
    }

    @Test
    public void test_filesize_disabled() throws IOException, InterruptedException {
        XContentBuilder river = startRiverDefinition("testfs_metadata")
                .field("excludes", "*.json")
                .field("add_filesize", false);
        startRiver(getRiverName(), endRiverDefinition(river));

        SearchResponse searchResponse = client().prepareSearch(getRiverName()).setTypes("doc")
                .setQuery(QueryBuilders.matchAllQuery())
                .execute().actionGet();
        logger.info(searchResponse.toString());

        for (SearchHit hit : searchResponse.getHits()) {
            assertNull(hit.getSource().get("filesize"));
        }
    }

    @Test
    public void test_includes_array() throws Exception {
        XContentBuilder river = startRiverDefinition("testfs_includes")
                .startArray("includes")
                    .value("*_include.txt")
                .endArray();
        startRiver(getRiverName(), endRiverDefinition(river));
        countTestHelper(getRiverName(), null, null);
    }

    @Test
    public void test_includes() throws Exception {
        XContentBuilder river = startRiverDefinition("testfs_includes")
                .field("includes", "*.txt");
        startRiver(getRiverName(), endRiverDefinition(river));
        countTestHelper(getRiverName(), null, null);
    }

    @Test
    public void test_metadata() throws Exception {
        XContentBuilder river = startRiverDefinition("testfs_metadata")
                .field("excludes", "*.json")
                .field("indexed_chars", 1);
        startRiver(getRiverName(), endRiverDefinition(river));

        SearchResponse searchResponse = client().prepareSearch(getRiverName()).setTypes("doc")
                .setQuery(QueryBuilders.matchAllQuery())
                .addField("*")
                .execute().actionGet();

        for (SearchHit hit : searchResponse.getHits()) {
            assertNotNull(hit.getFields().get(FsRiverUtil.Doc.FILE + "." + FsRiverUtil.Doc.File.FILENAME));
            assertNotNull(hit.getFields().get(FsRiverUtil.Doc.FILE + "." + FsRiverUtil.Doc.File.CONTENT_TYPE));
            assertNotNull(hit.getFields().get(FsRiverUtil.Doc.FILE + "." + FsRiverUtil.Doc.File.URL));
            assertNotNull(hit.getFields().get(FsRiverUtil.Doc.FILE + "." + FsRiverUtil.Doc.File.FILESIZE));
            assertNotNull(hit.getFields().get(FsRiverUtil.Doc.FILE + "." + FsRiverUtil.Doc.File.INDEXING_DATE));
            assertNotNull(hit.getFields().get(FsRiverUtil.Doc.FILE + "." + FsRiverUtil.Doc.File.INDEXED_CHARS));
            assertNotNull(hit.getFields().get(FsRiverUtil.Doc.FILE + "." + FsRiverUtil.Doc.File.LAST_MODIFIED));

            assertNotNull(hit.getFields().get(FsRiverUtil.Doc.META + "." + FsRiverUtil.Doc.Meta.TITLE));
            assertNotNull(hit.getFields().get(FsRiverUtil.Doc.META + "." + FsRiverUtil.Doc.Meta.DATE));
        }
    }

    @Test
    public void test_default_metadata() throws Exception {
        XContentBuilder river = startRiverDefinition("testfs_metadata")
                .field("excludes", "*.json");
        startRiver(getRiverName(), endRiverDefinition(river));

        SearchResponse searchResponse = client().prepareSearch(getRiverName()).setTypes("doc")
                .setQuery(QueryBuilders.matchAllQuery())
                .addField("*")
                .execute().actionGet();

        for (SearchHit hit : searchResponse.getHits()) {
            assertNull(hit.getFields().get(FsRiverUtil.Doc.ATTACHMENT));

            assertNotNull(hit.getFields().get(FsRiverUtil.Doc.FILE + "." + FsRiverUtil.Doc.File.FILENAME));
            assertNotNull(hit.getFields().get(FsRiverUtil.Doc.FILE + "." + FsRiverUtil.Doc.File.CONTENT_TYPE));
            assertNotNull(hit.getFields().get(FsRiverUtil.Doc.FILE + "." + FsRiverUtil.Doc.File.URL));
            assertNotNull(hit.getFields().get(FsRiverUtil.Doc.FILE + "." + FsRiverUtil.Doc.File.FILESIZE));
            assertNotNull(hit.getFields().get(FsRiverUtil.Doc.FILE + "." + FsRiverUtil.Doc.File.INDEXING_DATE));
            assertNull(hit.getFields().get(FsRiverUtil.Doc.FILE + "." + FsRiverUtil.Doc.File.INDEXED_CHARS));
            assertNotNull(hit.getFields().get(FsRiverUtil.Doc.FILE + "." + FsRiverUtil.Doc.File.LAST_MODIFIED));

            assertNotNull(hit.getFields().get(FsRiverUtil.Doc.META + "." + FsRiverUtil.Doc.Meta.TITLE));
            assertNotNull(hit.getFields().get(FsRiverUtil.Doc.META + "." + FsRiverUtil.Doc.Meta.DATE));
        }
    }

    @Test
    public void test_remove_deleted_enabled() throws Exception {
        String dir = "testfs_delete_disabled";
        String fullpath = getUrl(dir);

        // We first create a copy of a file
        File file1 = new File(fullpath, "roottxtfile.txt");
        File file2 = new File(fullpath, "deleted_roottxtfile.txt");
        FsUtils.copyFile(file1, file2);

        XContentBuilder river = startRiverDefinition(dir);
        startRiver(getRiverName(), endRiverDefinition(river));

        // We should have two docs first
        countTestHelper(getRiverName(), null, 2);

        // We remove a file
        File file = new File(fullpath, "deleted_roottxtfile.txt");
        try {
            file.delete();
        } catch (Exception e) {
            // Ignoring
        }

        // We expect to have two files
        countTestHelper(getRiverName(), null, 1);
    }

    @Test
    public void test_remove_deleted_disabled() throws Exception {
        String dir = "testfs_delete_disabled";
        String fullpath = getUrl(dir);

        // We first create a copy of a file
        File file1 = new File(fullpath, "roottxtfile.txt");
        File file2 = new File(fullpath, "deleted_roottxtfile.txt");
        FsUtils.copyFile(file1, file2);

        XContentBuilder river = startRiverDefinition(dir)
                .field("remove_deleted", false);
        startRiver(getRiverName(), endRiverDefinition(river));

        // We should have two docs first
        countTestHelper(getRiverName(), null, 2);

        // We remove a file
        File file = new File(fullpath, "deleted_roottxtfile.txt");
        try {
            file.delete();
        } catch (Exception e) {
            // Ignoring
        }

        // We expect to have two files
        countTestHelper(getRiverName(), null, 2);
    }

    /**
     * Test case for issue #60: https://github.com/dadoonet/fsriver/issues/60 : new files are not added
     */
    @Test
    public void test_add_new_file() throws Exception {
        String dir = "test_add_new_file";
        String fullpath = getUrl(dir);

        // We need to make sure that the "new" file does not already exist
        // because we already ran the test
        // We remove the file
        File file = new File(fullpath, "new_roottxtfile.txt");
        try {
            file.delete();
        } catch (Exception e) {
            // Ignoring
        }

        XContentBuilder river = startRiverDefinition(dir);
        startRiver(getRiverName(), endRiverDefinition(river));

        // We should have one doc first
        countTestHelper(getRiverName(), null, 1, fullpath);

        logger.info(" ---> Adding a copy of roottxtfile.txt");
        // We create a copy of a file
        File file1 = new File(fullpath, "roottxtfile.txt");
        File file2 = new File(fullpath, "new_roottxtfile.txt");
        FsUtils.copyFile(file1, file2);

        // We expect to have two files
        countTestHelper(getRiverName(), null, 2, fullpath);
    }

    /**
     * Test case for issue #5: https://github.com/dadoonet/fsriver/issues/5 : Support JSon documents
     */
    @Test
    public void test_json_support() throws Exception {
        XContentBuilder river = startRiverDefinition("testfsjson1")
                .field("json_support", true);
        startRiver(getRiverName(), endRiverDefinition(river));

        assertThat("We should have 0 doc for tweet in text field...", awaitBusy(new Predicate<Object>() {
            @Override
            public boolean apply(Object o) {
                SearchResponse searchResponse = client().prepareSearch(getRiverName())
                        .setQuery(QueryBuilders.termQuery("text", "tweet")).execute().actionGet();
                return searchResponse.getHits().getTotalHits() == 2;
            }
        }, 10, TimeUnit.SECONDS), equalTo(true));
    }

    /**
     * Test case for issue #5: https://github.com/dadoonet/fsriver/issues/5 : Support JSon documents
     */
    @Test
    public void test_json_disabled() throws Exception {
        XContentBuilder river = startRiverDefinition("testfsjson1")
                .field("json_support", false);
        startRiver(getRiverName(), endRiverDefinition(river));

        assertThat("We should have 0 doc for tweet in text field...", awaitBusy(new Predicate<Object>() {
            @Override
            public boolean apply(Object o) {
                SearchResponse searchResponse = client().prepareSearch(getRiverName())
                        .setQuery(QueryBuilders.termQuery("text", "tweet")).execute().actionGet();
                return searchResponse.getHits().getTotalHits() == 0;
            }
        }, 10, TimeUnit.SECONDS), equalTo(true));

        assertThat("We should have 2 docs for tweet in _all...", awaitBusy(new Predicate<Object>() {
            @Override
            public boolean apply(Object o) {
                SearchResponse searchResponse = client().prepareSearch(getRiverName())
                        .setQuery(QueryBuilders.queryString("tweet")).execute().actionGet();
                return searchResponse.getHits().getTotalHits() == 2;
            }
        }, 10, TimeUnit.SECONDS), equalTo(true));
    }

    /**
     * Test case for issue #7: https://github.com/dadoonet/fsriver/issues/7 : JSON support: use filename as ID
     */
    @Test
    public void test_filename_as_id() throws Exception {
        XContentBuilder river = startRiverDefinition("testfsjson1")
                .field("json_support", true)
                .field("filename_as_id", true);
        startRiver(getRiverName(), endRiverDefinition(river));

        assertThat("Document should exists with [tweet1] id...", awaitBusy(new Predicate<Object>() {
            @Override
            public boolean apply(Object o) {
                GetResponse getResponse = client().prepareGet(getRiverName(), FsRiverUtil.INDEX_TYPE_DOC, "tweet1").execute().actionGet();
                return getResponse.isExists();
            }
        }, 10, TimeUnit.SECONDS), equalTo(true));

        assertThat("Document should exists with [tweet2] id...", awaitBusy(new Predicate<Object>() {
            @Override
            public boolean apply(Object o) {
                GetResponse getResponse = client().prepareGet(getRiverName(), FsRiverUtil.INDEX_TYPE_DOC, "tweet2").execute().actionGet();
                return getResponse.isExists();
            }
        }, 10, TimeUnit.SECONDS), equalTo(true));
    }

    @Test
    public void test_store_source() throws Exception {
        XContentBuilder river = startRiverDefinition("testfs1")
                .field("json_support", true)
                .field("filename_as_id", true);
        startRiver(getRiverName(), endRiverDefinition(river));

        SearchResponse searchResponse = client().prepareSearch(getRiverName()).setTypes("doc")
                .setQuery(QueryBuilders.matchAllQuery())
                .addField("_source")
                .addField("*")
                .execute().actionGet();

        for (SearchHit hit : searchResponse.getHits()) {
            // We check that the field has been stored
            assertNotNull(hit.getFields().get(FsRiverUtil.Doc.ATTACHMENT));

            // We check that the field is not part of _source
            assertNull(hit.getSource().get(FsRiverUtil.Doc.ATTACHMENT));
        }
    }

    @Test
    public void test_defaults() throws Exception {
        startRiver(getRiverName(), endRiverDefinition(startRiverDefinition("testfs1")));

        // We expect to have one file
        countTestHelper(getRiverName(), null, 1);
    }

    @Test
    public void test_subdirs() throws Exception {
        startRiver(getRiverName(), endRiverDefinition(startRiverDefinition("testsubdir")));

        // We expect to have one file
        countTestHelper(getRiverName(), null, 2);
    }

    @Test
    public void test_multiple_rivers() throws Exception {
        startRiver(getRiverName() + "_1", endRiverDefinition(startRiverDefinition("testfs1")));
        startRiver(getRiverName() + "_2", endRiverDefinition(startRiverDefinition("testfs2")));
        CountResponse response = client().prepareCount(getRiverName() + "_1", getRiverName() + "_2")
                .setTypes(FsRiverUtil.INDEX_TYPE_DOC)
                .setQuery(QueryBuilders.matchAllQuery()).execute().actionGet();
        assertThat("We should have two docs...", response.getCount(), equalTo(2L));
    }

    @Test
    public void test_filename_analyzer() throws Exception {
        startRiver(getRiverName(), endRiverDefinition(startRiverDefinition("testfs1")));
        CountResponse response = client().prepareCount(getRiverName())
                .setTypes(FsRiverUtil.INDEX_TYPE_DOC)
                .setQuery(QueryBuilders.termQuery("file.filename", "roottxtfile.txt")).execute().actionGet();
        assertThat("We should have one doc...", response.getCount(), equalTo(1L));
    }

    /**
     * You have to adapt this test to your own system (login / password and SSH connexion)
     * So this test is disabled by default
     */
    @Test @Ignore
    public void test_ssh() throws Exception {
        String username = "USERNAME";
        String password = "PASSWORD";
        String server = "localhost";

        XContentBuilder river = startRiverDefinition("testsubdir")
                .field("username", username)
                .field("password", password)
                .field("protocol", FsRiver.PROTOCOL.SSH)
                .field("server", server);
        startRiver(getRiverName(), endRiverDefinition(river));

        countTestHelper(getRiverName(), null, 2);
    }

    /**
     * You have to adapt this test to your own system (login / pem file and SSH connexion)
     * So this test is disabled by default
     */
    @Test @Ignore
    public void test_ssh_with_key() throws Exception {
        String username = "USERNAME";
        String path_to_pem_file = "/path/to/private_key.pem";
        String server = "localhost";

        XContentBuilder river = startRiverDefinition("testsubdir")
                .field("username", username)
                .field("pem_path", path_to_pem_file)
                .field("protocol", FsRiver.PROTOCOL.SSH)
                .field("server", server);
        startRiver(getRiverName(), endRiverDefinition(river));

        countTestHelper(getRiverName(), null, 2);
    }

    @Test
    public void test_stop_river_while_adding_content() throws Exception {
        String sourcedir = "test_add_new_file";
        String riverdir = "test_stop_river";
        String fullpath_sourcedir = getUrl(sourcedir);
        String fullpath_riverdir = getUrl(riverdir);

        // We remove all "old files"
        FileSystemUtils.deleteRecursively(new File(fullpath_riverdir), false);

        XContentBuilder river = startRiverDefinition(riverdir, 5);
        startRiver(getRiverName(), endRiverDefinition(river));

        // While copying files, we stop the river
        int filenumber = 0;
        int filenumber_before_closing_river = between(5, 10);
        int filenumber_after_closing_river = between(30, 50);
        long sleepTime = 100;
        boolean closing_river = false;
        logger.info(" ---> Generating [{}] before closing the river and [{}] after", filenumber_before_closing_river, filenumber_after_closing_river);
        while (true) {
            File file1 = new File(fullpath_sourcedir, "roottxtfile.txt");
            File file2 = new File(fullpath_riverdir, "roottxtfile_" + filenumber +".txt");
            logger.info(" ---> Adding copy [{}] of roottxtfile.txt", filenumber);
            FsUtils.copyFile(file1, file2);
            filenumber++;

            if (filenumber == filenumber_before_closing_river) {
                // Close the river
                stopRiver(getRiverName());
                closing_river = true;
                // We accelerate copies if we want to have a failure
                sleepTime = 0;
            }

            if (closing_river) {
                if (filenumber_after_closing_river-- == 0) {
                    // This is the end of this test. We don't add file anymore
                    break;
                }
            }
            Thread.sleep(sleepTime);
        }
    }


}
