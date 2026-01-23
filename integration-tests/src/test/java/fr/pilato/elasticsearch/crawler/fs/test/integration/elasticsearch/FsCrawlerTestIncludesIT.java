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
 * Test includes crawler settings
 */
public class FsCrawlerTestIncludesIT extends AbstractFsCrawlerITCase {
    @Test
    public void includes() throws Exception {
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setIncludes(List.of("*/*_include\\.txt"));
        crawler = startCrawler(fsSettings);
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS), 1L, null);
    }

    @Test
    public void subdirs_with_patterns() throws Exception {
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setIncludes(List.of("*/*\\.txt"));
        crawler = startCrawler(fsSettings);

        // We expect to have seven files
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS), 7L, null);
    }

    @Test
    public void ignore_dir() throws Exception {
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setExcludes(List.of("*/\\.ignore/", "/subdir/sub*"));
        crawler = startCrawler(fsSettings);

        // We expect to have two files: subdir/notsub/roottxtfile.txt and subdir/roottxtfile.txt
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS), 2L, null);
    }

    @Test
    public void fscrawlerignore() throws Exception {
        crawler = startCrawler();

        // We expect to have 4 files as subdir1 should be ignored
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS), 4L, null);
    }
}
