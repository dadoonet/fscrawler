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

import fr.pilato.elasticsearch.crawler.fs.beans.Attributes;
import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.framework.OsValidator;
import fr.pilato.elasticsearch.crawler.fs.settings.Fs;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.junit.Test;

import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.extractFromPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Test attributes crawler settings
 */
public class FsCrawlerTestAttributesIT extends AbstractFsCrawlerITCase {
    @Test
    public void test_attributes() throws Exception {
        Fs fs = startCrawlerDefinition()
                .setAttributesSupport(true)
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);
        SearchResponse searchResponse = countTestHelper(new SearchRequest(getCrawlerName()), 1L, null);
        for (SearchHit hit : searchResponse.getHits().getHits()) {
            assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.ATTRIBUTES).get(Attributes.FIELD_NAMES.OWNER), notNullValue());
            if (OsValidator.WINDOWS) {
                // We should not have values for group and permissions on Windows OS
                assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.ATTRIBUTES), not(hasKey(Attributes.FIELD_NAMES.GROUP)));
                assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.ATTRIBUTES), not(hasKey(Attributes.FIELD_NAMES.PERMISSIONS)));
            } else {
                // We test group and permissions only on non Windows OS
                assertThat(extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.ATTRIBUTES).get(Attributes.FIELD_NAMES.GROUP), notNullValue());
                Object permissions = extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.ATTRIBUTES).get(Attributes.FIELD_NAMES.PERMISSIONS);
                assertThat(permissions, notNullValue());
                assertThat((int) permissions, greaterThanOrEqualTo(400));
            }
        }
    }
}
