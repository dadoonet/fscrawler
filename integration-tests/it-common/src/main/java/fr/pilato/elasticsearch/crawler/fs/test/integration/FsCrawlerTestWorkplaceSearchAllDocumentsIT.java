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
import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.beans.File;
import fr.pilato.elasticsearch.crawler.fs.beans.Meta;
import fr.pilato.elasticsearch.crawler.fs.client.ESBoolQuery;
import fr.pilato.elasticsearch.crawler.fs.client.ESMatchQuery;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchHit;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.client.ESTermQuery;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.settings.Elasticsearch;
import fr.pilato.elasticsearch.crawler.fs.settings.Fs;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.WorkplaceSearch;
import org.apache.tika.parser.external.ExternalParser;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import static fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl.LOOP_INFINITE;
import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.extractFromPath;
import static fr.pilato.elasticsearch.crawler.fs.settings.Elasticsearch.NODE_DEFAULT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

/**
 * Test all type of documents we have with workplace search
 */
public class FsCrawlerTestWorkplaceSearchAllDocumentsIT extends AbstractITCase {

    private static FsCrawlerImpl crawler = null;

    @BeforeClass
    public static void startCrawling() throws Exception {
        assumeFalse("Workplace Search credentials not defined. Launch with -Dtests.workplace.access_token=XYZ -Dtests.workplace.key=XYZ",
                testWorkplaceAccessToken == null || testWorkplaceKey == null);

        Path testResourceTarget = rootTmpDir.resolve("resources").resolve("documents");
        if (Files.notExists(testResourceTarget)) {
            copyResourcesToTestDir();
        }

        Long numFiles = 0L;

        try {
            Files.walk(testResourceTarget)
                    .filter(path -> Files.isRegularFile(path))
                    .forEach(path -> staticLogger.debug("    - [{}]", path));
            numFiles = Files.list(testResourceTarget).count();
        } catch (NoSuchFileException e) {
            staticLogger.error("directory [{}] should exist before we can start tests.", testResourceTarget);
            throw new RuntimeException(testResourceTarget + " doesn't seem to exist. Check your JUnit tests.");
        }

        staticLogger.info(" -> Removing existing index [.ent-search-engine-*]");
        esClient.deleteIndex(".ent-search-engine-*");

        staticLogger.info("  --> starting crawler in [{}] which contains [{}] files", testResourceTarget, numFiles);

        crawler = new FsCrawlerImpl(metadataDir,
                FsSettings.builder("fscrawler_workplacesearch_test_all_documents")
                        .setElasticsearch(generateElasticsearchConfig("fscrawler_workplacesearch_test_all_documents", "fscrawler_workplacesearch_test_all_documents_folder",
                                5, TimeValue.timeValueSeconds(1), null))
                        .setFs(Fs.builder()
                                .setUrl(testResourceTarget.toString())
                                .setLangDetect(true)
                                .build())
                        .setWorkplaceSearch(WorkplaceSearch.builder()
                                .setAccessToken(testWorkplaceAccessToken)
                                .setContentSourceKey(testWorkplaceKey)
                                .build())
                        .build(), LOOP_INFINITE, false);

        crawler.start();

        // We wait until we have all docs
        countTestHelper(new ESSearchRequest().withIndex(".ent-search-engine-*"), numFiles, null, TimeValue.timeValueMinutes(5));
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
    public void testJson() throws IOException {
        runSearch("test.json", "json");
    }

    @Test
    public void testExtractFromDoc() throws IOException {
        runSearch("test.doc", "sample");
    }

    @Test
    public void testExtractFromDocx() throws IOException {
        ESSearchResponse response = runSearch("test.docx", "sample");
        for (ESSearchHit hit : response.getHits()) {
            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.FILENAME), notNullValue());
            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.CONTENT_TYPE), notNullValue());
            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.URL), notNullValue());
            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.FILESIZE), notNullValue());
            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.INDEXING_DATE), notNullValue());
            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.CREATED), notNullValue());
            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.LAST_MODIFIED), notNullValue());
            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.LAST_ACCESSED), notNullValue());

            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.META).get(Meta.FIELD_NAMES.TITLE), notNullValue());
            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.META).get(Meta.FIELD_NAMES.KEYWORDS), notNullValue());
        }
    }

    @Test
    public void testExtractFromEml() throws IOException {
        ESSearchResponse response = runSearch("test.eml", "test");
        for (ESSearchHit hit : response.getHits()) {
            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.META).get(Meta.FIELD_NAMES.TITLE), is("Test"));
            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.META).get(Meta.FIELD_NAMES.AUTHOR), is("鲨掉 <2428617664@qq.com>"));
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

    /**
     * Test case for https://github.com/dadoonet/fscrawler/issues/221
     */
    @Test
    public void testProtectedDocument221() throws IOException {
        runSearch("issue-221-doc1.pdf", "Formations");
        runSearch("issue-221-doc2.pdf", "FORMATIONS");
    }

    @Test
    public void testLanguageDetection() throws IOException {
        ESSearchResponse response = runSearch("test-fr.txt", "fichier");
        for (ESSearchHit hit : response.getHits()) {
            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.META).get(Meta.FIELD_NAMES.LANGUAGE), is("fr"));
        }
        response = runSearch("test-de.txt", "Datei");
        for (ESSearchHit hit : response.getHits()) {
            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.META).get(Meta.FIELD_NAMES.LANGUAGE), is("de"));
        }
        response = runSearch("test.txt", "contains");
        for (ESSearchHit hit : response.getHits()) {
            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.META).get(Meta.FIELD_NAMES.LANGUAGE), is("en"));
        }
    }

    @Test
    public void testChineseContent369() throws IOException {
        runSearch("issue-369.txt", "今天天气晴好");
    }

    @Test
    public void testOcr() throws IOException {
        assumeTrue("Tesseract is not installed so we are skipping this test", ExternalParser.check("tesseract"));
        runSearch("test-ocr.png", "words");
        runSearch("test-ocr.pdf", "words");
    }

    @Test
    public void testShiftJisEncoding() throws IOException {
        runSearch("issue-400-shiftjis.txt", "elasticsearch");
    }

    @Test
    public void testNonUtf8Filename418() throws IOException {
        runSearch("issue-418-中文名称.txt");
    }

    private ESSearchResponse runSearch(String filename) throws IOException {
        return runSearch(filename, null);
    }

    private ESSearchResponse runSearch(String filename, String content) throws IOException {
        logger.info(" -> Testing if file [{}] has been indexed correctly{}.", filename,
                content == null ? "" : " and contains [" + content + "]");
        ESBoolQuery query = new ESBoolQuery().addMust(new ESTermQuery("file.filename", filename));
        if (content != null) {
            query.addMust(new ESMatchQuery("content", content));
        }
        ESSearchResponse response = esClient.search(new ESSearchRequest()
                        .withIndex(".ent-search-engine-*")
                        .withESQuery(query));
        assertThat(response.getTotalHits(), is(1L));
        return response;
    }
}
