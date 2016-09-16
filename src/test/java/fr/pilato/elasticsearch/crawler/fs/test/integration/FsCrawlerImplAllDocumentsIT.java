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
import fr.pilato.elasticsearch.crawler.fs.client.SearchResponse;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.Fs;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.util.FsCrawlerUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Test all type of documents we have
 */
public class FsCrawlerImplAllDocumentsIT extends AbstractITCase {

    protected static FsCrawlerImpl crawler = null;

    @BeforeClass
    public static void startCrawling() throws Exception {
        Path testResourceTarget = rootTmpDir.resolve("resources");
        if (Files.notExists(testResourceTarget)) {
            Files.createDirectory(testResourceTarget);
        }

        // We copy files from the src dir to the temp dir
        String url = getUrl("documents");
        Path from = Paths.get(url);

        staticLogger.debug("  --> Copying test resources from [{}]", from);
        if (Files.notExists(from)) {
            staticLogger.error("directory [{}] should be copied to [{}]", from, testResourceTarget);
            throw new RuntimeException(from + " doesn't seem to exist. Check your JUnit tests.");
        }

        FsCrawlerUtil.copyDirs(from, testResourceTarget);

        staticLogger.debug("  --> Test resources ready in [{}]:", testResourceTarget);
        Files.walk(testResourceTarget)
                .filter(path -> Files.isRegularFile(path))
                .forEach(path -> {
            staticLogger.debug("    - [{}]", path);
        });
        Long numFiles = Files.list(testResourceTarget).count();

        staticLogger.info(" -> Removing existing index [fscrawler_test_all_documents]");
        elasticsearchClient.deleteIndex("fscrawler_test_all_documents");

        staticLogger.info("  --> starting crawler in [{}] which contains [{}] files", testResourceTarget, numFiles);

        crawler = new FsCrawlerImpl(metadataDir,
                FsSettings.builder("fscrawler_test_all_documents")
                        .setElasticsearch(generateElasticsearchConfig("fscrawler_test_all_documents", securityInstalled, 5,
                                TimeValue.timeValueSeconds(1)))
                        .setFs(Fs.builder()
                                .setUrl(testResourceTarget.toString())
                                .build())
                        .build());

        crawler.start();

        // We wait until we have all docs
        countTestHelper("fscrawler_test_all_documents", null, numFiles.intValue(), null);
    }

    @AfterClass
    public static void stopCrawling() throws Exception {
        if (crawler != null) {
            staticLogger.info("  --> Stopping crawler");
            crawler.close();
            crawler = null;
        }
    }

    /**
     * Test case for https://github.com/dadoonet/fscrawler/issues/163
     */
    @Test
    public void testXmlIssue163() throws IOException {
        runSearch("issue-163.xml");
    }

    @Test
    public void testExtractFromDoc() throws IOException {
        runSearch("test.doc", "sample");
    }

    @Test
    public void testExtractFromDocx() throws IOException {
        SearchResponse response = runSearch("test.docx", "sample");
        for (SearchResponse.Hit hit : response.getHits().getHits()) {
            assertThat(hit.getFields().get(FsCrawlerUtil.Doc.FILE + "." + FsCrawlerUtil.Doc.File.FILENAME), notNullValue());
            assertThat(hit.getFields().get(FsCrawlerUtil.Doc.FILE + "." + FsCrawlerUtil.Doc.File.CONTENT_TYPE), notNullValue());
            assertThat(hit.getFields().get(FsCrawlerUtil.Doc.FILE + "." + FsCrawlerUtil.Doc.File.URL), notNullValue());
            assertThat(hit.getFields().get(FsCrawlerUtil.Doc.FILE + "." + FsCrawlerUtil.Doc.File.FILESIZE), notNullValue());
            assertThat(hit.getFields().get(FsCrawlerUtil.Doc.FILE + "." + FsCrawlerUtil.Doc.File.INDEXING_DATE), notNullValue());
            assertThat(hit.getFields().get(FsCrawlerUtil.Doc.FILE + "." + FsCrawlerUtil.Doc.File.LAST_MODIFIED), notNullValue());

            assertThat(hit.getFields().get(FsCrawlerUtil.Doc.META + "." + FsCrawlerUtil.Doc.Meta.TITLE), notNullValue());
            assertThat(hit.getFields().get(FsCrawlerUtil.Doc.META + "." + FsCrawlerUtil.Doc.Meta.KEYWORDS), notNullValue());
        }
    }

    @Test
    public void testExtractFromHtml() throws IOException {
        runSearch("test.html", "sample");
    }

    @Test
    public void testExtractFromMp3() throws IOException {
        runSearch("test.mp3", "tika");
    }

    @Test
    public void testExtractFromOdt() throws IOException {
        runSearch("test.odt", "sample");
    }

    @Test
    public void testExtractFromPdf() throws IOException {
        runSearch("test.pdf", "sample");
    }

    @Test
    public void testExtractFromRtf() throws IOException {
        runSearch("test.rtf", "sample");
    }

    @Test
    public void testExtractFromTxt() throws IOException {
        runSearch("test.txt", "contains");
    }

    @Test
    public void testExtractFromWav() throws IOException {
        runSearch("test.wav");
    }

    /**
     * Test case for https://github.com/dadoonet/fscrawler/issues/229
     */
    @Test
    public void testProtectedDocument229() throws IOException {
        runSearch("test-protected.docx");
    }

    private SearchResponse runSearch(String filename) throws IOException {
        return runSearch(filename, null);
    }

    private SearchResponse runSearch(String filename, String content) throws IOException {
        logger.info(" -> Testing if file [{}] has been indexed correctly{}.", filename,
                content == null ? "" : " and contains [" + content + "]");
        String fullQuery = "+file.filename:\"" + filename + "\"";
        if (content != null) {
            fullQuery += " +content:" + content;
        }
        SearchResponse response = elasticsearchClient.search("fscrawler_test_all_documents", null, fullQuery, null, "*");
        assertThat(response.getHits().getTotal(), is(1L));
        return response;
    }
}
