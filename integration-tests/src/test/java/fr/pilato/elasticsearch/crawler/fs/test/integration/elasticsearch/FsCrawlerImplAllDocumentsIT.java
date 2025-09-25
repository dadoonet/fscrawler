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

import com.carrotsearch.randomizedtesting.annotations.Timeout;
import com.carrotsearch.randomizedtesting.annotations.TimeoutSuite;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl;
import fr.pilato.elasticsearch.crawler.fs.client.*;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.settings.Elasticsearch;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tika.parser.external.ExternalParser;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import static fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl.LOOP_INFINITE;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.INDEX_SUFFIX_FOLDER;
import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.parseJsonAsDocumentContext;
import static fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase.TIMEOUT_MINUTE_AS_MS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Test all type of documents we have
 */
@TimeoutSuite(millis = 5 * TIMEOUT_MINUTE_AS_MS)
@Timeout(millis = 5 * TIMEOUT_MINUTE_AS_MS)
public class FsCrawlerImplAllDocumentsIT extends AbstractFsCrawlerITCase {
    private static final Logger logger = LogManager.getLogger();
    private static FsCrawlerImpl crawler = null;
    private static final String INDEX_NAME = getCrawlerName(FsCrawlerImplAllDocumentsIT.class, "all_documents");

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
                    .forEach(path -> logger.debug("    - [{}]", path));
            numFiles = Files.list(testResourceTarget).count();
        } catch (NoSuchFileException e) {
            logger.error("directory [{}] should exist before we can start tests.", testResourceTarget);
            throw new RuntimeException(testResourceTarget + " doesn't seem to exist. Check your JUnit tests.");
        }

        logger.debug(" -> Removing existing index [{}*]", INDEX_NAME);
        client.deleteIndex(INDEX_NAME);
        client.deleteIndex(INDEX_NAME + INDEX_SUFFIX_FOLDER);

        // Remove existing templates if any
        String templateName = INDEX_NAME + "_*";
        logger.debug(" -> Removing existing index and component templates [{}]", templateName);
        removeIndexTemplates(templateName);
        removeComponentTemplates(templateName);

        logger.info("🎬 Starting test [{}]", INDEX_NAME);
        logger.debug("  --> starting crawler in [{}] which contains [{}] files", testResourceTarget, numFiles);

        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.setName(INDEX_NAME);
        fsSettings.getFs().setUrl(testResourceTarget.toString());
        fsSettings.getFs().setLangDetect(true);
        // Clone the elasticsearchConfiguration to avoid modifying the default one
        // We start with a clean configuration
        Elasticsearch elasticsearch = clone(elasticsearchConfiguration);
        fsSettings.setElasticsearch(elasticsearch);
        fsSettings.getElasticsearch().setIndex(INDEX_NAME);
        fsSettings.getElasticsearch().setIndexFolder(INDEX_NAME + INDEX_SUFFIX_FOLDER);
        fsSettings.getElasticsearch().setBulkSize(5);
        fsSettings.getElasticsearch().setFlushInterval(TimeValue.timeValueSeconds(1));
        fsSettings.getElasticsearch().setSemanticSearch(false);

        crawler = new FsCrawlerImpl(metadataDir, fsSettings, LOOP_INFINITE, false);
        crawler.start();

        // We wait until we have all docs up to 5 minutes
        countTestHelper(new ESSearchRequest().withIndex(INDEX_NAME), numFiles, null, TimeValue.timeValueMinutes(5));
    }

    @AfterClass
    public static void stopCrawling() throws Exception {
        if (crawler != null) {
            logger.info("  --> Stopping crawler");
            crawler.close();
            crawler = null;
        }
        if (!TEST_KEEP_DATA) {
            logger.debug(" -> Removing existing index [{}*]", INDEX_NAME);
            client.deleteIndex(INDEX_NAME);
            client.deleteIndex(INDEX_NAME + INDEX_SUFFIX_FOLDER);
            // Remove existing templates if any
            String templateName = INDEX_NAME + "_*";
            logger.debug(" -> Removing existing index and component templates [{}]", templateName);
            removeIndexTemplates(templateName);
            removeComponentTemplates(templateName);
        }

        logger.info("✅ End of test [{}]", INDEX_NAME);
    }

    @Override
    public void cleanExistingIndex() {
        // We need to override this method to avoid removing the index
    }

    @Override
    public void cleanUp() {
        // We need to override this method to avoid removing the index
    }

    /**
     * Test case for <a href="https://github.com/dadoonet/fscrawler/issues/163">https://github.com/dadoonet/fscrawler/issues/163</a>
     */
    @Test
    public void xmlIssue163() throws ElasticsearchClientException {
        runSearch("issue-163.xml");
    }

    @Test
    public void json() throws ElasticsearchClientException {
        runSearch("test.json", "json");
    }

    @Test
    public void extractFromDoc() throws ElasticsearchClientException {
        runSearch("test.doc", "sample");
    }

    @Test
    public void extractFromDocx() throws ElasticsearchClientException {
        ESSearchResponse response = runSearch("test.docx", "sample");
        for (ESSearchHit hit : response.getHits()) {
            DocumentContext document = parseJsonAsDocumentContext(hit.getSource());
            assertThat((String) document.read("$.file.filename")).isNotEmpty();
            assertThat((String) document.read("$.file.content_type")).isNotEmpty();
            assertThat((String) document.read("$.file.url")).isNotEmpty();
            assertThat((Integer) document.read("$.file.filesize")).isGreaterThan(0);
            assertThat((String) document.read("$.file.indexing_date")).isNotEmpty();
            assertThat((String) document.read("$.file.created")).isNotEmpty();
            assertThat((String) document.read("$.file.last_modified")).isNotEmpty();
            assertThat((String) document.read("$.file.last_accessed")).isNotEmpty();
            assertThat((String) document.read("$.meta.title")).isNotEmpty();
            assertThat((Object) document.read("$.meta.keywords"))
                    .asInstanceOf(InstanceOfAssertFactories.list(String.class))
                    .containsExactlyInAnyOrder("keyword1", " keyword2");
        }
    }

    @Test
    public void extractFromHtml() throws ElasticsearchClientException {
        runSearch("test.html", "sample");
    }

    @Test
    public void extractFromMp3() throws ElasticsearchClientException {
        runSearch("test.mp3", "tika");
    }

    @Test
    public void extractFromOdt() throws ElasticsearchClientException {
        runSearch("test.odt", "sample");
    }

    @Test
    public void extractFromPdf() throws ElasticsearchClientException {
        runSearch("test.pdf", "sample");
    }

    @Test
    public void extractFromRtf() throws ElasticsearchClientException {
        runSearch("test.rtf", "sample");
    }

    @Test
    public void extractFromTxt() throws ElasticsearchClientException {
        runSearch("test.txt", "contains");
    }

    @Test
    public void extractFromWav() throws ElasticsearchClientException {
        runSearch("test.wav");
    }

    /**
     * Test case for <a href="https://github.com/dadoonet/fscrawler/issues/229">https://github.com/dadoonet/fscrawler/issues/229</a>
     */
    @Test
    public void protectedDocument229() throws ElasticsearchClientException {
        runSearch("test-protected.docx");
    }

    /**
     * Test case for <a href="https://github.com/dadoonet/fscrawler/issues/221">https://github.com/dadoonet/fscrawler/issues/221</a>
     */
    @Test
    public void protectedDocument221() throws ElasticsearchClientException {
        runSearch("issue-221-doc1.pdf", "coucou");
        runSearch("issue-221-doc2.pdf", "FORMATIONS");
    }

    @Test
    public void languageDetection() throws ElasticsearchClientException {
        ESSearchResponse response = runSearch("test-fr.txt", "fichier");
        for (ESSearchHit hit : response.getHits()) {
            assertThat((String) JsonPath.read(hit.getSource(), "$.meta.language")).isEqualTo("fr");
        }
        response = runSearch("test-de.txt", "Datei");
        for (ESSearchHit hit : response.getHits()) {
            assertThat((String) JsonPath.read(hit.getSource(), "$.meta.language")).isEqualTo("de");
        }
        response = runSearch("test.txt", "contains");
        for (ESSearchHit hit : response.getHits()) {
            assertThat((String) JsonPath.read(hit.getSource(), "$.meta.language")).isEqualTo("en");
        }
    }

    @Test
    public void chineseContent369() throws ElasticsearchClientException {
        runSearch("issue-369.txt", "今天天气晴好");
    }

    @Test
    public void ocr() throws ElasticsearchClientException {
        assumeThat(ExternalParser.check("tesseract"))
                .as("Tesseract is not installed so we are skipping this test")
                .isTrue();
        runSearch("test-ocr.png", "words");
        runSearch("test-ocr.pdf", "words");
    }

    @Test
    public void shiftJisEncoding() throws ElasticsearchClientException {
        runSearch("issue-400-shiftjis.txt", "elasticsearch");
    }

    @Test
    public void nonUtf8Filename418() throws ElasticsearchClientException {
        runSearch("issue-418-中文名称.txt");
    }

    private ESSearchResponse runSearch(String filename) throws ElasticsearchClientException {
        return runSearch(filename, null);
    }

    private ESSearchResponse runSearch(String filename, String content) throws ElasticsearchClientException {
        logger.info(" -> Testing if file [{}] has been indexed correctly{}.", filename,
                content == null ? "" : " and contains [" + content + "]");
        ESBoolQuery query = new ESBoolQuery().addMust(new ESTermQuery("file.filename", filename));
        if (content != null) {
            query.addMust(new ESMatchQuery("content", content));
        }
        ESSearchResponse response = client.search(new ESSearchRequest()
                        .withIndex(INDEX_NAME)
                        .withESQuery(query));
        assertThat(response.getTotalHits()).isEqualTo(1L);
        return response;
    }
}
