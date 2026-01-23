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
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.junit.Test;

import java.util.List;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.INDEX_SUFFIX_DOCS;

/**
 * Test filters crawler settings
 */
public class FsCrawlerTestFiltersIT extends AbstractFsCrawlerITCase {
    @Test
    public void filter_one_term() throws Exception {
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setFilters(List.of(".*foo.*"));
        crawler = startCrawler(fsSettings);
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS), 2L, null);
    }

    @Test
    public void filter_visa_pattern() throws Exception {
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setFilters(List.of("^4\\d{3}([\\ \\-]?)\\d{4}\\1\\d{4}\\1\\d{4}"));
        crawler = startCrawler(fsSettings);
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS), 2L, null);
    }

    @Test
    public void filter_visa_pattern_plus_foo() throws Exception {
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setFilters(List.of("^4\\d{3}([\\ \\-]?)\\d{4}\\1\\d{4}\\1\\d{4}", ".*foo.*"));
        crawler = startCrawler(fsSettings);
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS), 1L, null);
    }
}
