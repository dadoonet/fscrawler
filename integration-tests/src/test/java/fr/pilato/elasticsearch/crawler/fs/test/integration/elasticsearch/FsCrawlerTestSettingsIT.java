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

import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.framework.ByteSizeValue;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.settings.Fs;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.junit.Test;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.INDEX_SUFFIX_FOLDER;

/**
 * Test various crawler settings
 */
public class FsCrawlerTestSettingsIT extends AbstractFsCrawlerITCase {

    /**
     * Test for #83: <a href="https://github.com/dadoonet/fscrawler/issues/83">https://github.com/dadoonet/fscrawler/issues/83</a>
     */
    @Test
    public void test_time_value() throws Exception {
        Fs fs = startCrawlerDefinition(TimeValue.timeValueHours(1)).build();
        crawler = startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null, null);

        // We expect to have one file
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
    }

    @Test
    public void test_bulk_flush() throws Exception {
        Fs fs = startCrawlerDefinition().build();
        crawler = startCrawler(getCrawlerName(), fs,
                generateElasticsearchConfig(getCrawlerName(), getCrawlerName() + INDEX_SUFFIX_FOLDER,
                        100, TimeValue.timeValueSeconds(2), ByteSizeValue.parseBytesSizeValue("100b"), false), null, null);

        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
    }
}
