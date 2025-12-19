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
import fr.pilato.elasticsearch.crawler.fs.framework.OsValidator;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.junit.Test;

import java.util.List;

import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.parseJsonAsDocumentContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test attributes crawler settings
 */
public class FsCrawlerTestAttributesIT extends AbstractFsCrawlerITCase {
    @Test
    public void attributes() throws Exception {
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setAttributesSupport(true);
        crawler = startCrawler(fsSettings);
        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
        for (ESSearchHit hit : searchResponse.getHits()) {
            DocumentContext document = parseJsonAsDocumentContext(hit.getSource());
            assertThat((String) document.read("$.attributes.owner")).isNotEmpty();
            if (OsValidator.WINDOWS) {
                // We should not have values for group and permissions on Windows OS
                assertThatThrownBy(() -> document.read("$.attributes.group")).isInstanceOf(PathNotFoundException.class);
                assertThat((Integer) document.read("$.attributes.permissions")).isEqualTo(0);
            } else {
                // We test group and permissions only on non Windows OS
                assertThat((String) document.read("$.attributes.group")).isNotEmpty();
                assertThat((Integer) document.read("$.attributes.permissions")).isGreaterThanOrEqualTo(400);
            }
        }
    }

    @Test
    public void aclAttributes() throws Exception {
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setAttributesSupport(true);
        fsSettings.getFs().setAclSupport(true);
        crawler = startCrawler(fsSettings);

        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
        for (ESSearchHit hit : searchResponse.getHits()) {
            DocumentContext document = parseJsonAsDocumentContext(hit.getSource());

            List<?> aclEntries;
            try {
                aclEntries = document.read("$.attributes.acl");
            } catch (PathNotFoundException e) {
                aclEntries = null;
            }

            if (OsValidator.WINDOWS) {
                assertThat(aclEntries).as("ACL metadata should be collected on Windows").isNotNull().isNotEmpty();
                assertThat((String) document.read("$.attributes.acl[0].principal")).isNotBlank();
                assertThat((String) document.read("$.attributes.acl[0].type")).isNotBlank();
            } else {
                assertThat(aclEntries).as("ACL metadata should not be present when the platform does not expose ACLs").isNullOrEmpty();
            }
        }
    }
}
