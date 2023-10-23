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
 * Test includes crawler settings
 */
public class FsCrawlerTestIncludesIT extends AbstractFsCrawlerITCase {
    @Test
    public void test_includes() throws Exception {
        Fs fs = startCrawlerDefinition()
                .addInclude("*/*_include\\.txt")
                .build();
        crawler = startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
    }

    @Test
    public void test_subdirs_with_patterns() throws Exception {
        Fs fs = startCrawlerDefinition()
                .addInclude("*/*\\.txt")
                .build();
        crawler = startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        // We expect to have seven files
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 7L, null);
    }

    @Test
    public void test_ignore_dir() throws Exception {
        Fs fs = startCrawlerDefinition()
                .addExclude("*/\\.ignore")
                .addExclude("/subdir/sub*")
                .build();
        crawler = startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        // We expect to have one file
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 2L, null);
    }

    @Test
    public void test_fscrawlerignore() throws Exception {
        crawler = startCrawler();

        // We expect to have 4 files as subdir1 should be ignored
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 4L, null);
    }
}
