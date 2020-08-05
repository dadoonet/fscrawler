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

import fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl;
import fr.pilato.elasticsearch.crawler.fs.client.ESBoolQuery;
import fr.pilato.elasticsearch.crawler.fs.client.ESMatchQuery;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchHit;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentService;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentServiceWorkplaceSearchImpl;
import fr.pilato.elasticsearch.crawler.fs.settings.Fs;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.ServerUrl;
import fr.pilato.elasticsearch.crawler.fs.settings.WorkplaceSearch;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.apache.tika.parser.external.ExternalParser;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Map;

import static fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl.LOOP_INFINITE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

/**
 * Test all type of documents we have with workplace search
 */
public class FsCrawlerTestWorkplaceSearchAllDocumentsIT extends AbstractFsCrawlerITCase {

    private static FsCrawlerImpl crawler = null;
    private static FsCrawlerDocumentService oldDocumentService;

    @BeforeClass
    public static void startCrawling() throws Exception {
        checkWorkplaceSettings();

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

        oldDocumentService = documentService;

        FsSettings fsSettings = FsSettings.builder("fscrawler_workplacesearch_test_all_documents")
                .setElasticsearch(generateElasticsearchConfig("fscrawler_workplacesearch_test_all_documents",
                        "fscrawler_workplacesearch_test_all_documents_folder",
                        5, TimeValue.timeValueSeconds(1), null))
                .setFs(Fs.builder()
                        .setUrl(testResourceTarget.toString())
                        .setLangDetect(true)
                        .build())
                .setWorkplaceSearch(WorkplaceSearch.builder()
                        .setServer(new ServerUrl(testWorkplaceUrl))
                        .setAccessToken(testWorkplaceAccessToken)
                        .setKey(testWorkplaceKey)
                        .build())
                .build();

        try {
            documentService = new FsCrawlerDocumentServiceWorkplaceSearchImpl(metadataDir, fsSettings);
            documentService.start();

            staticLogger.info(" -> Removing existing index [.ent-search-engine-*]");
            documentService.getClient().deleteIndex(".ent-search-engine-*");

            staticLogger.info("  --> starting crawler in [{}] which contains [{}] files", testResourceTarget, numFiles);

            crawler = new FsCrawlerImpl(metadataDir, fsSettings, LOOP_INFINITE, false);
            crawler.start();

            // We wait until we have all docs
            countTestHelper(new ESSearchRequest().withIndex(".ent-search-engine-*"), numFiles, null, TimeValue.timeValueMinutes(5));
        } catch (FsCrawlerIllegalConfigurationException e) {
            documentService = oldDocumentService;
            Assume.assumeNoException("We don't have a compatible client for this version of the stack.", e);
        }
    }

    @AfterClass
    public static void stopCrawling() throws Exception {
        if (crawler != null) {
            staticLogger.info("  --> Stopping crawler");
            crawler.close();
            crawler = null;
        }
        documentService.close();
        documentService = oldDocumentService;
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
            Map<String, Object> source = hit.getSourceAsMap();
            assertThat(source, hasEntry(is("name$string"), notNullValue()));
            assertThat(source, hasEntry(is("mime_type$string"), notNullValue()));
            assertThat(source, hasEntry(is("url$string"), notNullValue()));
            assertThat(source, hasEntry(is("size$float"), notNullValue()));
            assertThat(source, hasEntry(is("last_modified$string"), notNullValue()));
            assertThat(source, hasEntry(is("path$string"), notNullValue()));
            assertThat(source, hasEntry(is("created_at$string"), notNullValue()));
            assertThat(source, hasEntry(is("title$string"), notNullValue()));
            assertThat(source, hasEntry(is("keywords$string"), notNullValue()));
            assertThat(source, hasEntry(is("body$string"), notNullValue()));
        }
    }

    @Test
    public void testExtractFromEml() throws IOException {
        ESSearchResponse response = runSearch("test.eml", "test");
        for (ESSearchHit hit : response.getHits()) {
            Map<String, Object> source = hit.getSourceAsMap();
            assertThat(source, hasEntry(is("title$string"), is("Test")));
            assertThat(source, hasEntry(is("author$string"), is("鲨掉 <2428617664@qq.com>")));
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
        // TODO fix this hack. We can't make sure we are returning one single file
        ESSearchResponse response = runSearch("test-fr.txt", "fichier");
        for (ESSearchHit hit : response.getHits()) {
            if (hit.getSourceAsMap().get("name$string").equals("test-fr.txt")) {
                assertThat(hit.getSourceAsMap(), hasEntry("language$string", "fr"));
            }
        }
        response = runSearch("test-de.txt", "Datei");
        for (ESSearchHit hit : response.getHits()) {
            if (hit.getSourceAsMap().get("name$string").equals("test-de.txt")) {
                assertThat(hit.getSourceAsMap(), hasEntry("language$string", "de"));
            }
        }
        response = runSearch("test.txt", "contains");
        for (ESSearchHit hit : response.getHits()) {
            if (hit.getSourceAsMap().get("name$string").equals("test.txt")) {
                assertThat(hit.getSourceAsMap(), hasEntry("language$string", "en"));
            }
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
        ESBoolQuery query = new ESBoolQuery().addMust(new ESMatchQuery("name$string", filename));
        if (content != null) {
            query.addMust(new ESMatchQuery("body$string", content));
        }
        ESSearchResponse response = documentService.getClient().search(new ESSearchRequest()
                        .withIndex(".ent-search-engine-*")
                        .withESQuery(query));
        // assertThat(response.getTotalHits(), is(1L));
        if (response.getTotalHits() != 1) {
            logger.warn("With workplace search we can't search for exact filenames so we have {} hits instead " +
                    "of 1 when looking for [{}].", response.getTotalHits(), filename);
        }
        return response;
    }
}
