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
import fr.pilato.elasticsearch.crawler.fs.settings.Fs;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.junit.Test;

/**
 * Test filters crawler settings
 */
public class FsCrawlerTestFiltersIT extends AbstractFsCrawlerITCase {
    @Test
    public void test_filter_one_term() throws Exception {
        Fs fs = startCrawlerDefinition()
                .addFilter(".*foo.*")
                .build();
        crawler = startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 2L, null);
    }

    @Test
    public void test_filter_visa_pattern() throws Exception {
        Fs fs = startCrawlerDefinition()
                .addFilter("^4\\d{3}([\\ \\-]?)\\d{4}\\1\\d{4}\\1\\d{4}$")
                .build();
        crawler = startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 2L, null);
    }

    @Test
    public void test_filter_visa_pattern_plus_foo() throws Exception {
        Fs fs = startCrawlerDefinition()
                .addFilter("^4\\d{3}([\\ \\-]?)\\d{4}\\1\\d{4}\\1\\d{4}$")
                .addFilter(".*foo.*")
                .build();
        crawler = startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
    }
}
