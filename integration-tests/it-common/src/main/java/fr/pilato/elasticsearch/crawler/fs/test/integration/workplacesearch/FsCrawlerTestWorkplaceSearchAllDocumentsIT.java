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
import fr.pilato.elasticsearch.crawler.fs.client.ESTermQuery;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentService;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentServiceWorkplaceSearchImpl;
import fr.pilato.elasticsearch.crawler.fs.settings.Fs;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.ServerUrl;
import fr.pilato.elasticsearch.crawler.fs.settings.WorkplaceSearch;
import org.apache.tika.parser.external.ExternalParser;
import org.junit.After;
import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Map;

import static fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl.LOOP_INFINITE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Test all type of documents we have with workplace search
 */
public class FsCrawlerTestWorkplaceSearchAllDocumentsIT extends AbstractWorkplaceSearchITCase {
    private static final String SOURCE_NAME = "fscrawler-all-documents";

    @After
    public void cleanUpCustomSource() {
        cleanExistingCustomSources(SOURCE_NAME);
    }

    @Test
    public void testAllDocuments() throws Exception {
        String customSourceId = initSource(SOURCE_NAME);

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
            logger.error("directory [{}] should exist before we can start tests.", testResourceTarget);
            throw new RuntimeException(testResourceTarget + " doesn't seem to exist. Check your JUnit tests.");
        }

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
                        .setId(customSourceId)
                        .setBulkSize(5)
                        .setFlushInterval(TimeValue.timeValueSeconds(1))
                        .build())
                .build();

        try (FsCrawlerDocumentService documentService = new FsCrawlerDocumentServiceWorkplaceSearchImpl(metadataDir, fsSettings)) {
            documentService.start();

            logger.info("  --> starting crawler in [{}] which contains [{}] files", testResourceTarget, numFiles);

            crawler = new FsCrawlerImpl(metadataDir, fsSettings, LOOP_INFINITE, false);
            crawler.start();

            // We wait until we have all docs
            // TODO Replace with the real search API
            countTestHelper(documentService, new ESSearchRequest().withIndex(".ent-search-engine-documents-source-" + customSourceId), numFiles, null, TimeValue.timeValueMinutes(5));

            logger.info("  --> stopping crawler");
            crawler.close();
            crawler = null;

            logger.info("  --> checking that files have expected content");

            runSearch("issue-163.xml");
            runSearch("test.json", "json");
            runSearch("test.doc", "sample");

            {
                ESSearchResponse response = runSearch("test.docx", "sample");
                for (ESSearchHit hit : response.getHits()) {
                    Map<String, Object> source = hit.getSourceAsMap();
                    assertThat(source, hasEntry(is("name"), notNullValue()));
                    assertThat(source, hasEntry(is("mime_type"), notNullValue()));
                    assertThat(source, hasEntry(is("url"), notNullValue()));
                    assertThat(source, hasEntry(is("size"), notNullValue()));
                    assertThat(source, hasEntry(is("last_modified"), notNullValue()));
                    assertThat(source, hasEntry(is("path"), notNullValue()));
                    assertThat(source, hasEntry(is("created_at"), notNullValue()));
                    assertThat(source, hasEntry(is("title"), notNullValue()));
                    assertThat(source, hasEntry(is("keywords"), notNullValue()));
                    assertThat(source, hasEntry(is("body"), notNullValue()));
                }
            }
            runSearch("test.html", "sample");
            runSearch("test.mp3", "tika");
            runSearch("test.odt", "sample");
            runSearch("test.pdf", "sample");
            runSearch("test.rtf", "sample");
            runSearch("test.txt", "contains");
            runSearch("test.wav");
            runSearch("test-protected.docx");
            runSearch("issue-221-doc1.pdf", "Formations");
            runSearch("issue-221-doc2.pdf", "FORMATIONS");
            {
                // TODO fix this hack. We can't make sure we are returning one single file
                ESSearchResponse response = runSearch("test-fr.txt", "fichier");
                for (ESSearchHit hit : response.getHits()) {
                    if (hit.getSourceAsMap().get("name").equals("test-fr.txt")) {
                        assertThat(hit.getSourceAsMap(), hasEntry("language", "fr"));
                    }
                }
                response = runSearch("test-de.txt", "Datei");
                for (ESSearchHit hit : response.getHits()) {
                    if (hit.getSourceAsMap().get("name").equals("test-de.txt")) {
                        assertThat(hit.getSourceAsMap(), hasEntry("language", "de"));
                    }
                }
                response = runSearch("test.txt", "contains");
                for (ESSearchHit hit : response.getHits()) {
                    if (hit.getSourceAsMap().get("name").equals("test.txt")) {
                        assertThat(hit.getSourceAsMap(), hasEntry("language", "en"));
                    }
                }
            }
            runSearch("issue-369.txt", "今天天气晴好");
            runSearch("issue-400-shiftjis.txt", "elasticsearch");
            runSearch("issue-418-中文名称.txt");

            // If Tesseract is not installed, we are skipping this test
            if (ExternalParser.check("tesseract")) {
                runSearch("test-ocr.png", "words");
                runSearch("test-ocr.pdf", "words");
            }

        } catch (FsCrawlerIllegalConfigurationException e) {
            Assume.assumeNoException("We don't have a compatible client for this version of the stack.", e);
        }
    }

    private ESSearchResponse runSearch(String filename) throws IOException {
        return runSearch(filename, null);
    }

    private ESSearchResponse runSearch(String filename, String content) throws IOException {
        logger.info(" -> Testing if file [{}] has been indexed correctly{}.", filename,
                content == null ? "" : " and contains [" + content + "]");

        // TODO We should use instead the WPSearch search API

        ESBoolQuery query = new ESBoolQuery().addMust(new ESTermQuery("name.enum", filename));
        if (content != null) {
            query.addMust(new ESMatchQuery("body", content));
        }
        ESSearchResponse response = documentService.getClient().search(new ESSearchRequest()
                        .withIndex(".ent-search-engine-documents-source-*")
                        .withESQuery(query));
        assertThat(response.getTotalHits(), is(1L));
        return response;
    }
}
