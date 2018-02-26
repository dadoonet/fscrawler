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

import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.settings.Fs;
import org.elasticsearch.action.search.SearchRequest;
import org.junit.Test;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.INDEX_SUFFIX_FOLDER;

/**
 * Test various crawler settings
 */
public class FsCrawlerTestSettingsIT extends AbstractFsCrawlerITCase {

    /**
     * Test for #83: https://github.com/dadoonet/fscrawler/issues/83
     */
    @Test
    public void test_time_value() throws Exception {
        Fs fs = fsBuilder(currentTestResourceDir.toString(), TimeValue.timeValueHours(1)).build();
        startCrawler(getCrawlerName(), fs, elasticsearchBuilder());

        // We expect to have one file
        countTestHelper(new SearchRequest(getCrawlerName()), 1L, null);
    }

    @Test
    public void test_bulk_flush() throws Exception {
        Fs fs = fsBuilder().build();
        startCrawler(getCrawlerName(), fs,
                elasticsearchBuilder(getCrawlerName(), getCrawlerName() + INDEX_SUFFIX_FOLDER,
                        100, TimeValue.timeValueSeconds(2)));

        countTestHelper(new SearchRequest(getCrawlerName()), 1L, null);
    }
}
