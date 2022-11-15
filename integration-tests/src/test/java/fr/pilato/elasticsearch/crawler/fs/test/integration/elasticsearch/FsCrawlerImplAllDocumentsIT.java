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

package fr.pilato.elasticsearch.crawler.fs.test.integration.elasticsearch;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl;
import fr.pilato.elasticsearch.crawler.fs.client.ESBoolQuery;
import fr.pilato.elasticsearch.crawler.fs.client.ESMatchQuery;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchHit;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.client.ESTermQuery;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientException;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.settings.Fs;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.apache.tika.parser.external.ExternalParser;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import static fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl.LOOP_INFINITE;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.INDEX_SUFFIX_FOLDER;
import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.parseJsonAsDocumentContext;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assume.assumeTrue;

/**
 * Test all type of documents we have
 */
public class FsCrawlerImplAllDocumentsIT extends AbstractFsCrawlerITCase {

    private static FsCrawlerImpl crawler = null;

    @BeforeClass
    public static void startCrawling() throws Exception {
        Path testResourceTarget = rootTmpDir.resolve("resources").resolve("documents");
        if (Files.notExists(testResourceTarget)) {
            copyResourcesToTestDir();
        }

        long numFiles;

        try {
            Files.walk(testResourceTarget)
                    .filter(Files::isRegularFile)
                    .forEach(path -> staticLogger.debug("    - [{}]", path));
            numFiles = Files.list(testResourceTarget).count();
        } catch (NoSuchFileException e) {
            staticLogger.error("directory [{}] should exist before we can start tests.", testResourceTarget);
            throw new RuntimeException(testResourceTarget + " doesn't seem to exist. Check your JUnit tests.");
        }

        staticLogger.info(" -> Removing existing index [fscrawler_test_all_documents*]");
        managementService.getClient().deleteIndex("fscrawler_test_all_documents");
        managementService.getClient().deleteIndex("fscrawler_test_all_documents" + INDEX_SUFFIX_FOLDER);

        staticLogger.info("  --> starting crawler in [{}] which contains [{}] files", testResourceTarget, numFiles);

        crawler = new FsCrawlerImpl(metadataDir,
                FsSettings.builder("fscrawler_test_all_documents")
                        .setElasticsearch(generateElasticsearchConfig("fscrawler_test_all_documents", "fscrawler_test_all_documents_folder",
                                5, TimeValue.timeValueSeconds(1), null))
                        .setFs(Fs.builder()
                                .setUrl(testResourceTarget.toString())
                                .setLangDetect(true)
                                .build())
                        .build(), LOOP_INFINITE, false);

        crawler.start();

        // We wait until we have all docs
        countTestHelper(new ESSearchRequest().withIndex("fscrawler_test_all_documents"), numFiles, null, TimeValue.timeValueMinutes(1));
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
     * Test case for <a href="https://github.com/dadoonet/fscrawler/issues/163">https://github.com/dadoonet/fscrawler/issues/163</a>
     */
    @Test
    public void testXmlIssue163() throws IOException, ElasticsearchClientException {
        runSearch("issue-163.xml");
    }

    @Test
    public void testJson() throws IOException, ElasticsearchClientException {
        runSearch("test.json", "json");
    }

    @Test
    public void testExtractFromDoc() throws IOException, ElasticsearchClientException {
        runSearch("test.doc", "sample");
    }

    @Test
    public void testExtractFromDocx() throws IOException, ElasticsearchClientException {
        ESSearchResponse response = runSearch("test.docx", "sample");
        for (ESSearchHit hit : response.getHits()) {
            DocumentContext document = parseJsonAsDocumentContext(hit.getSource());
            assertThat(document.read("$.file.filename"), notNullValue());
            assertThat(document.read("$.file.content_type"), notNullValue());
            assertThat(document.read("$.file.url"), notNullValue());
            assertThat(document.read("$.file.filesize"), notNullValue());
            assertThat(document.read("$.file.indexing_date"), notNullValue());
            assertThat(document.read("$.file.created"), notNullValue());
            assertThat(document.read("$.file.last_modified"), notNullValue());
            assertThat(document.read("$.file.last_accessed"), notNullValue());

            assertThat(document.read("$.meta.title"), notNullValue());
            assertThat(document.read("$.meta.keywords"), notNullValue());
        }
    }

    @Test
    public void testExtractFromHtml() throws IOException, ElasticsearchClientException {
        runSearch("test.html", "sample");
    }

    @Test
    public void testExtractFromMp3() throws IOException, ElasticsearchClientException {
        runSearch("test.mp3", "tika");
    }

    @Test
    public void testExtractFromOdt() throws IOException, ElasticsearchClientException {
        runSearch("test.odt", "sample");
    }

    @Test
    public void testExtractFromPdf() throws IOException, ElasticsearchClientException {
        runSearch("test.pdf", "sample");
    }

    @Test
    public void testExtractFromRtf() throws IOException, ElasticsearchClientException {
        runSearch("test.rtf", "sample");
    }

    @Test
    public void testExtractFromTxt() throws IOException, ElasticsearchClientException {
        runSearch("test.txt", "contains");
    }

    @Test
    public void testExtractFromWav() throws IOException, ElasticsearchClientException {
        runSearch("test.wav");
    }

    /**
     * Test case for <a href="https://github.com/dadoonet/fscrawler/issues/229">https://github.com/dadoonet/fscrawler/issues/229</a>
     */
    @Test
    public void testProtectedDocument229() throws IOException, ElasticsearchClientException {
        runSearch("test-protected.docx");
    }

    /**
     * Test case for <a href="https://github.com/dadoonet/fscrawler/issues/221">https://github.com/dadoonet/fscrawler/issues/221</a>
     */
    @Test
    public void testProtectedDocument221() throws IOException, ElasticsearchClientException {
        runSearch("issue-221-doc1.pdf", "coucou");
        runSearch("issue-221-doc2.pdf", "FORMATIONS");
    }

    @Test
    public void testLanguageDetection() throws IOException, ElasticsearchClientException {
        ESSearchResponse response = runSearch("test-fr.txt", "fichier");
        for (ESSearchHit hit : response.getHits()) {
            assertThat(JsonPath.read(hit.getSource(), "$.meta.language"), is("fr"));
        }
        response = runSearch("test-de.txt", "Datei");
        for (ESSearchHit hit : response.getHits()) {
            assertThat(JsonPath.read(hit.getSource(), "$.meta.language"), is("de"));
        }
        response = runSearch("test.txt", "contains");
        for (ESSearchHit hit : response.getHits()) {
            assertThat(JsonPath.read(hit.getSource(), "$.meta.language"), is("en"));
        }
    }

    @Test
    public void testChineseContent369() throws IOException, ElasticsearchClientException {
        runSearch("issue-369.txt", "今天天气晴好");
    }

    @Test
    public void testOcr() throws IOException, ElasticsearchClientException {
        assumeTrue("Tesseract is not installed so we are skipping this test", ExternalParser.check("tesseract"));
        runSearch("test-ocr.png", "words");
        runSearch("test-ocr.pdf", "words");
    }

    @Test
    public void testShiftJisEncoding() throws IOException, ElasticsearchClientException {
        runSearch("issue-400-shiftjis.txt", "elasticsearch");
    }

    @Test
    public void testNonUtf8Filename418() throws IOException, ElasticsearchClientException {
        runSearch("issue-418-中文名称.txt");
    }

    private ESSearchResponse runSearch(String filename) throws IOException, ElasticsearchClientException {
        return runSearch(filename, null);
    }

    private ESSearchResponse runSearch(String filename, String content) throws IOException, ElasticsearchClientException {
        logger.info(" -> Testing if file [{}] has been indexed correctly{}.", filename,
                content == null ? "" : " and contains [" + content + "]");
        ESBoolQuery query = new ESBoolQuery().addMust(new ESTermQuery("file.filename", filename));
        if (content != null) {
            query.addMust(new ESMatchQuery("content", content));
        }
        ESSearchResponse response = documentService.search(new ESSearchRequest()
                        .withIndex("fscrawler_test_all_documents")
                        .withESQuery(query));
        assertThat(response.getTotalHits(), is(1L));
        return response;
    }
}
