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

import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.settings.Elasticsearch;
import fr.pilato.elasticsearch.crawler.fs.settings.Fs;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.WorkplaceSearch;
import org.junit.Test;

import static fr.pilato.elasticsearch.crawler.fs.settings.Elasticsearch.NODE_DEFAULT;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeThat;

/**
 * Test workplace search
 */
public class FsCrawlerTestWorkplaceSearchIT extends AbstractFsCrawlerITCase {

    @Test
    public void testWorkplaceSearch() throws Exception {
        assumeFalse("Workplace Search credentials not defined. Launch with -Dtests.workplace.access_token=XYZ -Dtests.workplace.key=XYZ",
                testWorkplaceAccessToken == null || testWorkplaceKey == null);

        Fs fs = startCrawlerDefinition().build();
        FsSettings fsSettings = FsSettings.builder(getCrawlerName())
                .setFs(fs)
                .setElasticsearch(Elasticsearch.builder()
                        .addNode(NODE_DEFAULT)
                        .setUsername("elastic").setPassword("changeme").build())
                .setWorkplaceSearch(WorkplaceSearch.builder()
                        .setAccessToken(testWorkplaceAccessToken)
                        .setContentSourceKey(testWorkplaceKey)
                        .build())
                .build();

        startCrawler(getCrawlerName(), fsSettings, TimeValue.timeValueSeconds(10));
        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
    }
}
