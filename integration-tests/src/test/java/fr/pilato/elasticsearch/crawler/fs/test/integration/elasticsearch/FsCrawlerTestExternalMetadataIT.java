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
import com.jayway.jsonpath.PathNotFoundException;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchHit;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import java.nio.file.Path;

import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.parseJsonAsDocumentContext;
import static org.assertj.core.api.Assertions.*;

/**
 * Test crawler with external metadata files
 */
public class FsCrawlerTestExternalMetadataIT extends AbstractFsCrawlerITCase {
    private static final Logger logger = LogManager.getLogger();

    @Test
    public void metadata_external_default() throws Exception {
        crawler = startCrawler();

        // We expect to have 3 files
        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 3L, null);
        for (ESSearchHit hit : searchResponse.getHits()) {
            DocumentContext document = parseJsonAsDocumentContext(hit.getSource());
            String filename = document.read("$.file.filename");
            assertThat(filename).containsAnyOf("root_dir.txt", "with_meta.txt", "without_meta.txt");

            switch (filename) {
                case "root_dir.txt":
                    checkHit(document, filename, true, "This file contains some words.");
                    break;
                case "with_meta.txt":
                    checkHit(document, filename, true, "the content should be altered.");
                    break;
                case "without_meta.txt":
                    checkHit(document, filename, false, "it does not have a metadata file.");
                    break;
            }
        }
    }

    @Test
    public void metadata_external_yaml() throws Exception {
        FsSettings fsSettings = createTestSettings();
        fsSettings.getTags().setMetaFilename("meta-as-yaml.yaml");
        crawler = startCrawler(fsSettings);

        // We expect to have 3 files
        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 3L, null);
        for (ESSearchHit hit : searchResponse.getHits()) {
            DocumentContext document = parseJsonAsDocumentContext(hit.getSource());
            String filename = document.read("$.file.filename");
            assertThat(filename).containsAnyOf("root_dir.txt", "with_meta.txt", "without_meta.txt");

            switch (filename) {
                case "root_dir.txt":
                    checkHit(document, filename, true, "This file contains some words.");
                    break;
                case "with_meta.txt":
                    checkHit(document, filename, true, "the content should be altered.");
                    break;
                case "without_meta.txt":
                    checkHit(document, filename, false, "it does not have a metadata file.");
                    break;
            }
        }
    }

    @Test
    public void metadata_external_json() throws Exception {
        FsSettings fsSettings = createTestSettings();
        fsSettings.getTags().setMetaFilename("meta-as-json.json");
        crawler = startCrawler(fsSettings);

        // We expect to have 3 files
        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 3L, null);
        for (ESSearchHit hit : searchResponse.getHits()) {
            DocumentContext document = parseJsonAsDocumentContext(hit.getSource());
            String filename = document.read("$.file.filename");
            assertThat(filename).containsAnyOf("root_dir.txt", "with_meta.txt", "without_meta.txt");

            switch (filename) {
                case "root_dir.txt":
                    checkHit(document, filename, true, "This file contains some words.");
                    break;
                case "with_meta.txt":
                    checkHit(document, filename, true, "the content should be altered.");
                    break;
                case "without_meta.txt":
                    checkHit(document, filename, false, "it does not have a metadata file.");
                    break;
            }
        }
    }

    @Test
    public void metadata_external_overwrite() throws Exception {
        crawler = startCrawler();

        // We expect to have 3 files
        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 3L, null);
        for (ESSearchHit hit : searchResponse.getHits()) {
            DocumentContext document = parseJsonAsDocumentContext(hit.getSource());
            String filename = document.read("$.file.filename");
            assertThat(filename).containsAnyOf("root_dir.txt", "with_meta.txt", "without_meta.txt");

            switch (filename) {
                case "root_dir.txt":
                    checkHit(document, filename, false, "This file contains some words.");
                    break;
                case "with_meta.txt":
                    checkHit(document, filename, true, "This is a new content which is overwritten.");
                    break;
                case "without_meta.txt":
                    checkHit(document, filename, false, "it does not have a metadata file.");
                    break;
            }
        }
    }

    @Test
    public void metadata_static_basic_json() throws Exception {
        Path staticMetadataFile = currentTestTagDir.resolve("static-meta.json");
        FsSettings fsSettings = createTestSettings();
        fsSettings.getTags().setStaticMetaFilename(staticMetadataFile.toString());
        crawler = startCrawler(fsSettings);

        // We expect to have 1 files
        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
        ESSearchHit hit = searchResponse.getHits().get(0);
        DocumentContext document = parseJsonAsDocumentContext(hit.getSource());
        String filename = document.read("$.file.filename");
        assertThat(filename).isEqualTo("roottxtfile.txt");

        // We should have the static metadata
        checkHit(document, filename, true, "Novo denique perniciosoque exemplo idem");
    }

    @Test
    public void metadata_static_basic_yaml() throws Exception {
        Path staticMetadataFile = currentTestTagDir.resolve("static-meta.yaml");
        FsSettings fsSettings = createTestSettings();
        fsSettings.getTags().setStaticMetaFilename(staticMetadataFile.toString());
        crawler = startCrawler(fsSettings);

        // We expect to have 1 files
        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
        ESSearchHit hit = searchResponse.getHits().get(0);
        DocumentContext document = parseJsonAsDocumentContext(hit.getSource());
        String filename = document.read("$.file.filename");
        assertThat(filename).isEqualTo("roottxtfile.txt");

        // We should have the static metadata
        checkHit(document, filename, true, "Novo denique perniciosoque exemplo idem");
    }

    @Test
    public void metadata_static_empty() throws Exception {
        Path staticMetadataFile = currentTestTagDir.resolve("static-meta.yaml");
        FsSettings fsSettings = createTestSettings();
        fsSettings.getTags().setStaticMetaFilename(staticMetadataFile.toString());
        crawler = startCrawler(fsSettings);

        // We expect to have 1 files
        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
        ESSearchHit hit = searchResponse.getHits().get(0);
        DocumentContext document = parseJsonAsDocumentContext(hit.getSource());
        String filename = document.read("$.file.filename");
        assertThat(filename).isEqualTo("roottxtfile.txt");

        // We should not have the static metadata
        checkHit(document, filename, false, "Novo denique perniciosoque exemplo idem");
    }

    @Test
    public void metadata_static_no_file() {
        Path staticMetadataFile = currentTestTagDir.resolve("static-meta.yaml");
        FsSettings fsSettings = createTestSettings();
        fsSettings.getTags().setStaticMetaFilename(staticMetadataFile.toString());
        assertThatThrownBy(() -> startCrawler(fsSettings))
                .isInstanceOf(FsCrawlerIllegalConfigurationException.class)
                .hasMessageContaining("Static meta file")
                .hasMessageContaining("does not exist or is not a file.");
    }

    @Test
    public void metadata_static_and_external() throws Exception {
        Path staticMetadataFile = currentTestTagDir.resolve("static-meta.yaml");
        FsSettings fsSettings = createTestSettings();
        fsSettings.getTags().setStaticMetaFilename(staticMetadataFile.toString());
        crawler = startCrawler(fsSettings);

        // We expect to have 3 files
        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 3L, null);
        for (ESSearchHit hit : searchResponse.getHits()) {
            DocumentContext document = parseJsonAsDocumentContext(hit.getSource());
            String filename = document.read("$.file.filename");
            assertThat(filename).containsAnyOf("root_dir.txt", "with_meta.txt", "without_meta.txt");

            switch (filename) {
                case "root_dir.txt":
                    checkHit(document, filename, true, "This file contains some words.");
                    break;
                case "with_meta.txt":
                    checkHit(document, filename, true, "This is a new content which is overwritten.");
                    break;
                case "without_meta.txt":
                    checkHit(document, filename, true, "it does not have a metadata file.");
                    break;
            }
        }
    }

    // TODO add a test about precedence of external metadata over static ones

    private void checkHit(DocumentContext document, String filename, boolean hasExternal, String expectedContent) {
        logger.debug("--> Checking hit for [{}], expecting {}external data and \"{}\" as part of the content",
                filename, hasExternal ? "" : "no ", expectedContent);
        assertThat((String) document.read("$.content")).contains(expectedContent);
        assertThat((String) document.read("$.file.filename")).isEqualTo(filename);
        if (hasExternal) {
            assertThat((Integer) document.read("$.external.tenantId")).isEqualTo(23);
        } else {
            assertThatThrownBy(() -> document.read("$.external")).isInstanceOf(PathNotFoundException.class);
        }
    }
}
