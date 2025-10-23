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
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchHit;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.Tags;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.parseJsonAsDocumentContext;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test crawler with static metadata in settings
 */
public class FsCrawlerTestStaticMetadataIT extends AbstractFsCrawlerITCase {

    @Test
    public void static_metadata_basic() throws Exception {
        FsSettings fsSettings = createTestSettings();
        Tags tags = new Tags();
        Map<String, Object> staticMetadata = new HashMap<>();
        Map<String, Object> external = new HashMap<>();
        external.put("hostname", "server001");
        external.put("environment", "production");
        staticMetadata.put("external", external);
        tags.setStaticMetadata(staticMetadata);
        fsSettings.setTags(tags);
        
        crawler = startCrawler(fsSettings);

        // We expect to have 2 files
        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 2L, null);
        for (ESSearchHit hit : searchResponse.getHits()) {
            DocumentContext document = parseJsonAsDocumentContext(hit.getSource());
            String filename = document.read("$.file.filename");
            assertThat(filename).isIn("test1.txt", "test2.txt");

            // All documents should have the static metadata
            assertThat((String) document.read("$.external.hostname")).isEqualTo("server001");
            assertThat((String) document.read("$.external.environment")).isEqualTo("production");
            
            // Check content is preserved
            if (filename.equals("test1.txt")) {
                assertThat((String) document.read("$.content")).contains("test file 1");
            } else {
                assertThat((String) document.read("$.content")).contains("test file 2");
            }
        }
    }

    @Test
    public void static_metadata_complex() throws Exception {
        FsSettings fsSettings = createTestSettings();
        Tags tags = new Tags();
        Map<String, Object> staticMetadata = new HashMap<>();
        
        // Create complex nested structure
        Map<String, Object> external = new HashMap<>();
        external.put("tenantId", 42);
        external.put("company", "test company");
        external.put("region", "us-west-2");
        
        Map<String, Object> customData = new HashMap<>();
        customData.put("projectId", 123);
        customData.put("department", "engineering");
        
        staticMetadata.put("external", external);
        staticMetadata.put("custom", customData);
        tags.setStaticMetadata(staticMetadata);
        fsSettings.setTags(tags);
        
        crawler = startCrawler(fsSettings);

        // We expect to have 2 files
        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 2L, null);
        for (ESSearchHit hit : searchResponse.getHits()) {
            DocumentContext document = parseJsonAsDocumentContext(hit.getSource());
            
            // Check all static metadata fields are present
            assertThat((Integer) document.read("$.external.tenantId")).isEqualTo(42);
            assertThat((String) document.read("$.external.company")).isEqualTo("test company");
            assertThat((String) document.read("$.external.region")).isEqualTo("us-west-2");
            assertThat((Integer) document.read("$.custom.projectId")).isEqualTo(123);
            assertThat((String) document.read("$.custom.department")).isEqualTo("engineering");
        }
    }

    @Test
    public void static_metadata_empty() throws Exception {
        FsSettings fsSettings = createTestSettings();
        Tags tags = new Tags();
        tags.setStaticMetadata(new HashMap<>());
        fsSettings.setTags(tags);
        
        crawler = startCrawler(fsSettings);

        // We expect to have 2 files, but no static metadata
        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 2L, null);
        for (ESSearchHit hit : searchResponse.getHits()) {
            DocumentContext document = parseJsonAsDocumentContext(hit.getSource());
            
            // Should still have file metadata
            assertThat((String) document.read("$.file.filename")).isNotNull();
            assertThat((String) document.read("$.path")).isNotNull();
        }
    }
}
