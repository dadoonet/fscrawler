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
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientException;
import fr.pilato.elasticsearch.crawler.fs.settings.Fs;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.junit.Test;

import java.io.IOException;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.INDEX_SUFFIX_FOLDER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;

/**
 * Test index_folders crawler setting
 */
public class FsCrawlerTestIgnoreFoldersIT extends AbstractFsCrawlerITCase {

    /**
     * Test case for #155: <a href="https://github.com/dadoonet/fscrawler/issues/155">https://github.com/dadoonet/fscrawler/issues/155</a> : New option: do not index folders
     */
    @Test
    public void test_ignore_folders() throws Exception {
        Fs fs = startCrawlerDefinition()
                .setIndexFolders(false)
                .build();
        crawler = startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        // We expect to have two files
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 2L, null);

        // The folder index should not exist
        try {
            documentService.search(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_FOLDER));
            fail("We should not be able to read the folder index");
        } catch (ElasticsearchClientException e) {
            assertThat(e.getMessage(), is("index " + getCrawlerName() + INDEX_SUFFIX_FOLDER + " does not exist."));
        }
    }
}
