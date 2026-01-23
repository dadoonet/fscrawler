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
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.junit.Test;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.INDEX_SUFFIX_DOCS;

/**
 * Test various crawler settings
 */
public class FsCrawlerTestSettingsIT extends AbstractFsCrawlerITCase {

    /**
     * Test for #83: <a href="https://github.com/dadoonet/fscrawler/issues/83">https://github.com/dadoonet/fscrawler/issues/83</a>
     */
    @Test
    public void time_value() throws Exception {
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setUpdateRate(TimeValue.timeValueHours(1));
        crawler = startCrawler(fsSettings);

        // We expect to have one file
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS), 1L, null);
    }

    @Test
    public void bulk_flush() throws Exception {
        FsSettings fsSettings = createTestSettings();
        fsSettings.getElasticsearch().setBulkSize(100);
        fsSettings.getElasticsearch().setFlushInterval(TimeValue.timeValueSeconds(2));
        fsSettings.getElasticsearch().setByteSize(ByteSizeValue.parseBytesSizeValue("100b"));
        crawler = startCrawler(fsSettings);

        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS), 1L, null);
    }
}
