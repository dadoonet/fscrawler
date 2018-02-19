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

import fr.pilato.elasticsearch.crawler.fs.settings.Fs;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Test;

/**
 * Test Xml support crawler setting
 */
public class FsCrawlerTestXmlSupportIT extends AbstractFsCrawlerITCase {

    /**
     * Test case for issue #185: https://github.com/dadoonet/fscrawler/issues/185 : Add xml_support setting
     */
    @Test
    public void test_xml_enabled() throws Exception {
        assumeVersion6AtLeast();
        Fs fs = startCrawlerDefinition()
                .setXmlSupport(true)
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);
        SearchResponse response = countTestHelper(new SearchRequest(getCrawlerName()), 3L, null);

        countTestHelper(new SearchRequest(getCrawlerName()).source(new SearchSourceBuilder()
                .query(QueryBuilders.matchQuery("title", "maeve"))), 1L, null);
        countTestHelper(new SearchRequest(getCrawlerName()).source(new SearchSourceBuilder()
                .query(QueryBuilders.rangeQuery("price").from(5).to(6))), 2L, null);

        logger.info("XML documents converted to:");
        for (SearchHit hit : response.getHits().getHits()) {
            logger.info("{}", hit.getSourceAsString());
        }
    }
}
