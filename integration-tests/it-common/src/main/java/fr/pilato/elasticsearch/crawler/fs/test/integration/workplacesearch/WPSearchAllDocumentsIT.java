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
import org.apache.tika.parser.external.ExternalParser;
import org.junit.After;
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
import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.parseJson;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Test all type of documents we have with workplace search
 */
public class WPSearchAllDocumentsIT extends AbstractWorkplaceSearchITCase {
    private String sourceName;

    @After
    public void cleanUpCustomSource() {
        if (sourceName != null) {
            cleanExistingCustomSources(sourceName);
        }
    }

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

            String customSourceId = getSourceIdFromSourceName(sourceName);
            assertThat("Custom source id should be found for source " + sourceName, customSourceId, notNullValue());

            logger.info("  --> starting crawler in [{}] which contains [{}] files", testResourceTarget, numFiles);

            crawler = new FsCrawlerImpl(metadataDir, fsSettings, LOOP_INFINITE, false);
            crawler.start();

            // We wait until we have all docs
            // TODO Replace with the real search API
            try (WPSearchClient wpClient = createClient()) {
                countTestHelper(wpClient, numFiles, TimeValue.timeValueMinutes(5));
            }

            logger.info("  --> stopping crawler");
            crawler.close();
            crawler = null;

            logger.info("  --> checking that files have expected content");

            runSearch("issue-163.xml");
            runSearch("test.json", "json");
            runSearch("test.doc", "sample");

            {
                Object document = runSearch("test.docx", "sample");
                assertThat(JsonPath.read(document, "$.results[*].name.raw"), notNullValue());
                assertThat(JsonPath.read(document, "$.results[*].mime_type.raw"), notNullValue());
                assertThat(JsonPath.read(document, "$.results[*].url.raw"), notNullValue());
                assertThat(JsonPath.read(document, "$.results[*].size.raw"), notNullValue());
                assertThat(JsonPath.read(document, "$.results[*].last_modified.raw"), notNullValue());
                assertThat(JsonPath.read(document, "$.results[*].path.raw"), notNullValue());
                assertThat(JsonPath.read(document, "$.results[*].created_at.raw"), notNullValue());
                assertThat(JsonPath.read(document, "$.results[*].title.raw"), notNullValue());
                assertThat(JsonPath.read(document, "$.results[*].keywords.raw"), notNullValue());
                assertThat(JsonPath.read(document, "$.results[*].body.raw"), notNullValue());
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
/*
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
*/
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

    private Object runSearch(String filename) {
        return runSearch(filename, null);
    }

    /**
     * Run a search with optional parameters and returns a Json Document
     * that has been parsed by JSonPath.
     * So it's easy then to extract values from the JSon document with:
     * JsonPath.read(document, "$.field")
     * @param filename  optional filename (this will add a filter on the path field)
     * @param content   optional content (will run as a fulltext search query)
     * @return the JsonPath object
     */
    private Object runSearch(String filename, String content) {
        logger.info(" -> Testing if file [{}] has been indexed correctly{}.", filename,
                content == null ? "" : " and contains [" + content + "]");

        try (WPSearchClient client = createClient()) {
            Map<String, List<String>> filters = new HashMap<>();
            if (filename != null) {
                filters.put("name", Collections.singletonList(filename));
            }
            String json = client.search(content, filters);
            List<String> ids = JsonPath.read(json, "$.results[*].id.raw");
            assertThat(ids, hasSize(1));

            // We parse the json
            return parseJson(json);
        }
    }
}
