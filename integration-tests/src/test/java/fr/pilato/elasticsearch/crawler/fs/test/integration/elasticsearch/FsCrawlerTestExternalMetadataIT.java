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
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.junit.Test;

import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.parseJsonAsDocumentContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test crawler with external metadata files
 */
public class FsCrawlerTestExternalMetadataIT extends AbstractFsCrawlerITCase {

    @Test
    public void external_metadata_default() throws Exception {
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
    public void external_metadata_yaml() throws Exception {
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
    public void external_metadata_json() throws Exception {
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
    public void external_metadata_overwrite() throws Exception {
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

    // TODO Rewrite this test to use AssertJ
    private void checkHit(DocumentContext document, String filename, boolean hasExternal, String expectedContent) {
        assertThat((String) document.read("$.content")).contains(expectedContent);
        assertThat((String) document.read("$.file.filename")).isEqualTo(filename);
        if (hasExternal) {
            assertThat((Integer) document.read("$.external.tenantId")).isEqualTo(23);
        } else {
            assertThatThrownBy(() -> document.read("$.external")).isInstanceOf(PathNotFoundException.class);
        }
    }
}
