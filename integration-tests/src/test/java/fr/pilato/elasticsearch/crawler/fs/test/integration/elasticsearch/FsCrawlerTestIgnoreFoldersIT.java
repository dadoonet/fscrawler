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
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientException;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.junit.Test;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.INDEX_SUFFIX_DOCS;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.INDEX_SUFFIX_FOLDER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test index_folders crawler setting
 */
public class FsCrawlerTestIgnoreFoldersIT extends AbstractFsCrawlerITCase {

    /**
     * Test case for #155: <a href="https://github.com/dadoonet/fscrawler/issues/155">https://github.com/dadoonet/fscrawler/issues/155</a> : New option: do not index folders
     */
    @Test
    public void ignore_folders() throws Exception {
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setIndexFolders(false);
        crawler = startCrawler(fsSettings);

        // We expect to have two files
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS), 2L, null);

        // The folder index should not exist
        assertThatThrownBy(() -> client.search(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_FOLDER)))
                .isInstanceOfSatisfying(ElasticsearchClientException.class, e ->
                        assertThat(e.getMessage()).isEqualTo("index " + getCrawlerName() + INDEX_SUFFIX_FOLDER + " does not exist."));
    }
}
