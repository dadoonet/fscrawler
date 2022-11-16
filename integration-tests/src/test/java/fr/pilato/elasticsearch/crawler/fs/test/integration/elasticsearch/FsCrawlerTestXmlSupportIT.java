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

import fr.pilato.elasticsearch.crawler.fs.client.ESMatchQuery;
import fr.pilato.elasticsearch.crawler.fs.client.ESRangeQuery;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchHit;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.settings.Fs;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.junit.Test;

/**
 * Test Xml support crawler setting
 */
public class FsCrawlerTestXmlSupportIT extends AbstractFsCrawlerITCase {

    /**
     * Test case for issue #185: <a href="https://github.com/dadoonet/fscrawler/issues/185">https://github.com/dadoonet/fscrawler/issues/185</a> : Add xml_support setting
     */
    @Test
    public void test_xml_enabled() throws Exception {
        Fs fs = startCrawlerDefinition()
                .setXmlSupport(true)
                .build();
        crawler = startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);
        ESSearchResponse response = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 3L, null);

        countTestHelper(new ESSearchRequest()
                .withIndex(getCrawlerName())
                .withESQuery(new ESMatchQuery("title", "maeve")),
                1L, null);
        countTestHelper(new ESSearchRequest()
                .withIndex(getCrawlerName())
                .withESQuery(new ESRangeQuery("price").withGte(5).withLt(6)), 2L, null);

        logger.info("XML documents converted to:");
        for (ESSearchHit hit : response.getHits()) {
            logger.info("{}", hit.getSource());
        }
    }
}
