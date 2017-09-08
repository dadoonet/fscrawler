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
import fr.pilato.elasticsearch.crawler.fs.meta.doc.Doc;
import fr.pilato.elasticsearch.crawler.fs.meta.doc.File;
import fr.pilato.elasticsearch.crawler.fs.meta.doc.Meta;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.Fs;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.util.FsCrawlerUtil;
import org.apache.tika.parser.external.ExternalParser;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static fr.pilato.elasticsearch.crawler.fs.client.JsonUtil.extractFromPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assume.assumeTrue;

/**
 * Test all type of documents we have
 */
public class FsCrawlerImplAllDocumentsIT extends AbstractITCase {

    private static FsCrawlerImpl crawler = null;

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
                .forEach(path -> staticLogger.debug("    - [{}]", path));
        Long numFiles = Files.list(testResourceTarget).count();

        staticLogger.info(" -> Removing existing index [fscrawler_test_all_documents]");
        elasticsearchClient.deleteIndex("fscrawler_test_all_documents");
        elasticsearchClient.deleteIndex("fscrawler_test_all_documents_folder");

        staticLogger.info("  --> starting crawler in [{}] which contains [{}] files", testResourceTarget, numFiles);

        crawler = new FsCrawlerImpl(metadataDir,
                FsSettings.builder("fscrawler_test_all_documents")
                        .setElasticsearch(generateElasticsearchConfig("fscrawler_test_all_documents", "fscrawler_test_all_documents_folder",
                                securityInstalled, 5, TimeValue.timeValueSeconds(1)))
                        .setFs(Fs.builder()
                                .setUrl(testResourceTarget.toString())
                                .setLangDetect(true)
                                .build())
                        .build());

        crawler.start();

        // We wait until we have all docs
        countTestHelper(new SearchRequest("fscrawler_test_all_documents"), numFiles, null, TimeValue.timeValueMinutes(1));
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
        SearchResponse response = runSearch("test.docx", "sample");
        for (SearchHit hit : response.getHits().getHits()) {
            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.FILENAME), notNullValue());
            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.CONTENT_TYPE), notNullValue());
            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.URL), notNullValue());
            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.FILESIZE), notNullValue());
            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.INDEXING_DATE), notNullValue());
            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.LAST_MODIFIED), notNullValue());

            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.META).get(Meta.FIELD_NAMES.TITLE), notNullValue());
            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.META).get(Meta.FIELD_NAMES.KEYWORDS), notNullValue());
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
        SearchResponse response = runSearch("test-fr.txt", "fichier");
        for (SearchHit hit : response.getHits().getHits()) {
            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.META).get(Meta.FIELD_NAMES.LANGUAGE), is("fr"));
        }
        response = runSearch("test-de.txt", "Datei");
        for (SearchHit hit : response.getHits().getHits()) {
            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.META).get(Meta.FIELD_NAMES.LANGUAGE), is("de"));
        }
        response = runSearch("test.txt", "contains");
        for (SearchHit hit : response.getHits().getHits()) {
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

    private SearchResponse runSearch(String filename) throws IOException {
        return runSearch(filename, null);
    }

    private SearchResponse runSearch(String filename, String content) throws IOException {
        logger.info(" -> Testing if file [{}] has been indexed correctly{}.", filename,
                content == null ? "" : " and contains [" + content + "]");
        BoolQueryBuilder query = QueryBuilders.boolQuery().must(QueryBuilders.termQuery("file.filename", filename));
        if (content != null) {
            query.must(QueryBuilders.matchQuery("content", content));
        }
        SearchResponse response = elasticsearchClient.search(new SearchRequest("fscrawler_test_all_documents").source(new SearchSourceBuilder().query(query)));
        assertThat(response.getHits().getTotalHits(), is(1L));
        return response;
    }
}
