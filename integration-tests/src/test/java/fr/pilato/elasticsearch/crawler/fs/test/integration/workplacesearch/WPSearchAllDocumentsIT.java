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

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentService;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentServiceWorkplaceSearchImpl;
import fr.pilato.elasticsearch.crawler.fs.settings.Fs;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.ServerUrl;
import fr.pilato.elasticsearch.crawler.fs.settings.WorkplaceSearch;
import fr.pilato.elasticsearch.crawler.fs.thirdparty.wpsearch.WPSearchClient;
import org.junit.Assume;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl.LOOP_INFINITE;
import static fr.pilato.elasticsearch.crawler.fs.client.WorkplaceSearchClientUtil.generateDefaultCustomSourceName;
import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.parseJsonAsDocumentContext;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Test all type of documents we have with workplace search
 */
public class WPSearchAllDocumentsIT extends AbstractWorkplaceSearchITCase {

    @Test
    public void testAllDocuments() throws Exception {
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
                        .setBulkSize(5)
                        .setFlushInterval(TimeValue.timeValueSeconds(1))
                        .build())
                .build();

        sourceName = generateDefaultCustomSourceName(fsSettings.getName());

        try (FsCrawlerDocumentService documentService = new FsCrawlerDocumentServiceWorkplaceSearchImpl(metadataDir, fsSettings)) {
            documentService.start();

            sourceId = getSourceIdFromSourceName(sourceName);
            assertThat("Custom source id should be found for source " + sourceName, sourceId, notNullValue());

            logger.info("  --> starting crawler in [{}] which contains [{}] files", testResourceTarget, numFiles);

            // Create the crawler and wait until we have all docs
            try (FsCrawlerImpl crawler = new FsCrawlerImpl(metadataDir, fsSettings, LOOP_INFINITE, false);
                 WPSearchClient wpClient = createClient()) {
                crawler.start();
                countTestHelper(wpClient, sourceId, numFiles, TimeValue.timeValueMinutes(5));
            }

            logger.info("  --> checking that files have expected content");

            runSearch("issue-163.xml", null);
            runSearch("test.json", "json");
            runSearch("test.doc", "sample");

            {
                DocumentContext document = runSearch("test.docx", "sample");
                assertThat(document.read("$.results[*].name.raw"), notNullValue());
                assertThat(document.read("$.results[*].mime_type.raw"), notNullValue());
                assertThat(document.read("$.results[*].url.raw"), notNullValue());
                assertThat(document.read("$.results[*].size.raw"), notNullValue());
                assertThat(document.read("$.results[*].last_modified.raw"), notNullValue());
                assertThat(document.read("$.results[*].path.raw"), notNullValue());
                assertThat(document.read("$.results[*].created_at.raw"), notNullValue());
                assertThat(document.read("$.results[*].title.raw"), notNullValue());
                assertThat(document.read("$.results[*].keywords.raw"), notNullValue());
                assertThat(document.read("$.results[*].body.raw"), notNullValue());
            }
            runSearch("test.html", "sample");
            runSearch("test.mp3", "tika");
            runSearch("test.odt", "sample");
            runSearch("test.pdf", "sample");
            runSearch("test.rtf", "sample");
            runSearch("test.txt", "contains");
            runSearch("test.wav", null);
            runSearch("test-protected.docx", null);
            runSearch("issue-221-doc1.pdf", "coucou");
            runSearch("issue-221-doc2.pdf", "FORMATIONS");

            DocumentContext response = runSearch("test-fr.txt", "fichier");
            assertThat(response.read("$.results[0].language.raw"), is("fr"));
            response = runSearch("test-de.txt", "Datei");
            assertThat(response.read("$.results[0].language.raw"), is("de"));
            response = runSearch("test.txt", "contains");
            assertThat(response.read("$.results[0].language.raw"), is("en"));

            runSearch("issue-369.txt", "今天天气晴好");
            runSearch("issue-400-shiftjis.txt", "elasticsearch");
            runSearch("issue-418-中文名称.txt", null);

            // If Tesseract is not installed, we are skipping this test
            if (isOcrAvailable) {
                runSearch("test-ocr.png", "words");
                runSearch("test-ocr.pdf", "words");
            }

        } catch (FsCrawlerIllegalConfigurationException e) {
            Assume.assumeNoException("We don't have a compatible client for this version of the stack.", e);
        }
    }

    /**
     * Run a search with optional parameters and returns a Json Document
     * that has been parsed by JSonPath behind the scene.
     * So it's easy then to extract values from the JSon document with:
     * document.read("$.field")
     * @param filename  optional filename (this will add a filter on the path field)
     * @param content   optional content (will run as a fulltext search query)
     * @return the DocumentContext object
     */
    private DocumentContext runSearch(String filename, String content) {
        logger.info(" -> Testing if file [{}] has been indexed correctly{}.", filename,
                content == null ? "" : " and contains [" + content + "]");

        try (WPSearchClient client = createClient()) {
            Map<String, Object> filters = new HashMap<>();
            if (filename != null) {
                filters.put("name", Collections.singletonList(filename));
            }
            String json = client.search(content, filters);
            DocumentContext document = parseJsonAsDocumentContext(json);

            List<String> ids = document.read("$.results[*].id.raw");
            assertThat(ids, hasSize(1));

            return document;
        }
    }
}
