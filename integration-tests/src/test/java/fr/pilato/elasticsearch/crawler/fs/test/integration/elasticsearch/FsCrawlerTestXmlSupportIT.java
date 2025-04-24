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

import fr.pilato.elasticsearch.crawler.fs.client.*;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test Xml support crawler setting
 */
public class FsCrawlerTestXmlSupportIT extends AbstractFsCrawlerITCase {
    private static final Logger logger = LogManager.getLogger();

    /**
     * Test case for issue #185: <a href="https://github.com/dadoonet/fscrawler/issues/185">https://github.com/dadoonet/fscrawler/issues/185</a> : Add xml_support setting
     */
    @Test
    public void xmlSupport() throws Exception {
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setXmlSupport(true);
        crawler = startCrawler(fsSettings);
        ESSearchResponse response = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 3L, null);
        assertThat(response.getTotalHits()).isEqualTo(3L);

        countTestHelper(new ESSearchRequest()
                .withIndex(getCrawlerName())
                .withESQuery(new ESMatchQuery("title", "maeve")),
                1L, null);
        countTestHelper(new ESSearchRequest()
                .withIndex(getCrawlerName())
                .withESQuery(new ESRangeQuery("price").withGte(5).withLt(6)), 2L, null);

        logger.debug("XML documents converted to:");
        for (ESSearchHit hit : response.getHits()) {
            logger.debug("{}", hit.getSource());
        }
    }

    /**
     * Test case for issue #185: <a href="https://github.com/dadoonet/fscrawler/issues/185">https://github.com/dadoonet/fscrawler/issues/185</a> : Add xml_support setting
     */
    @Test
    public void xmlSupportAndOtherFiles() throws Exception {
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setXmlSupport(true);
        crawler = startCrawler(fsSettings);
        ESSearchResponse response = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 3L, null);
        assertThat(response.getTotalHits()).isEqualTo(3L);
    }

    /**
     * Test case for issue #1753: <a href="https://github.com/dadoonet/fscrawler/issues/1753">https://github.com/dadoonet/fscrawler/issues/1753</a> :
     * invalid json generated from XML
     */
    @Test
    public void xmlNotReadable() throws Exception {
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setXmlSupport(true);
        crawler = startCrawler(fsSettings);
        ESSearchResponse response = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
        assertThat(response.getTotalHits()).isEqualTo(1L);

        countTestHelper(new ESSearchRequest()
                .withIndex(getCrawlerName())
                .withESQuery(new ESMatchQuery("Tag.$", "Content")),
                1L, null);

        logger.debug("XML documents converted to:");
        for (ESSearchHit hit : response.getHits()) {
            logger.debug("{}", hit.getSource());
        }
    }
}
