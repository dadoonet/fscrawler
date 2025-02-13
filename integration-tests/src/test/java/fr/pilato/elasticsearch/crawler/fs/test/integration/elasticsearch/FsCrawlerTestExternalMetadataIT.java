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
import fr.pilato.elasticsearch.crawler.fs.client.*;
import fr.pilato.elasticsearch.crawler.fs.settings.Fs;
import fr.pilato.elasticsearch.crawler.fs.settings.Tags;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.junit.Test;

import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.parseJsonAsDocumentContext;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Test crawler with external metadata files
 */
public class FsCrawlerTestExternalMetadataIT extends AbstractFsCrawlerITCase {

    @Test
    public void test_external_metadata_default() throws Exception {
        crawler = startCrawler();

        // We expect to have 3 files
        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 3L, null);
        for (ESSearchHit hit : searchResponse.getHits()) {
            DocumentContext document = parseJsonAsDocumentContext(hit.getSource());
            String filename = document.read("$.file.filename");
            assertThat(filename, isOneOf("root_dir.txt", "with_meta.txt", "without_meta.txt"));

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
    public void test_external_metadata_yaml() throws Exception {
        crawler = startCrawler(getCrawlerName(),
                startCrawlerDefinition().build(),
                endCrawlerDefinition(getCrawlerName()),
                null,
                Tags.builder().setMetaFilename("meta-as-yaml.yaml").build());

        // We expect to have 3 files
        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 3L, null);
        for (ESSearchHit hit : searchResponse.getHits()) {
            DocumentContext document = parseJsonAsDocumentContext(hit.getSource());
            String filename = document.read("$.file.filename");
            assertThat(filename, isOneOf("root_dir.txt", "with_meta.txt", "without_meta.txt"));

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
    public void test_external_metadata_json() throws Exception {
        crawler = startCrawler(getCrawlerName(),
                startCrawlerDefinition().build(),
                endCrawlerDefinition(getCrawlerName()),
                null,
                Tags.builder().setMetaFilename("meta-as-json.json").build());

        // We expect to have 3 files
        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 3L, null);
        for (ESSearchHit hit : searchResponse.getHits()) {
            DocumentContext document = parseJsonAsDocumentContext(hit.getSource());
            String filename = document.read("$.file.filename");
            assertThat(filename, isOneOf("root_dir.txt", "with_meta.txt", "without_meta.txt"));

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
    public void test_external_metadata_overwrite() throws Exception {
        crawler = startCrawler(getCrawlerName(),
                startCrawlerDefinition().build(),
                endCrawlerDefinition(getCrawlerName()),
                null,
                Tags.DEFAULT);

        // We expect to have 3 files
        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 3L, null);
        for (ESSearchHit hit : searchResponse.getHits()) {
            DocumentContext document = parseJsonAsDocumentContext(hit.getSource());
            String filename = document.read("$.file.filename");
            assertThat(filename, isOneOf("root_dir.txt", "with_meta.txt", "without_meta.txt"));

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

    private void checkHit(DocumentContext document, String filename, boolean hasExternal, String expectedContent) {
        assertThat(document.read("$.content"), containsString(expectedContent));
        assertThat(document.read("$.file.filename"), is(filename));
        if (hasExternal) {
            assertThat(document.read("$.external.tenantId"), is(23));
        } else {
            expectThrows(PathNotFoundException.class, () -> document.read("$.external"));
        }
    }
}
