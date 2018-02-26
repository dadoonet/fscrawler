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

import fr.pilato.elasticsearch.crawler.fs.settings.Fs;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Test;

/**
 * Test all crawler settings
 */
public class FsCrawlerTestIndexContentIT extends AbstractFsCrawlerITCase {

    /**
     * Test for #103: https://github.com/dadoonet/fscrawler/issues/103
     */
    @Test
    public void test_index_content() throws Exception {
        Fs fs = fsBuilder()
                .setIndexContent(false)
                .build();
        startCrawler(getCrawlerName(), fs, elasticsearchBuilder());

        // We expect to have one file
        countTestHelper(new SearchRequest(getCrawlerName()), 1L, null);

        countTestHelper(new SearchRequest(getCrawlerName()).source(new SearchSourceBuilder()
                .query(QueryBuilders.prefixQuery("content", "file*"))), 0L, null);
        countTestHelper(new SearchRequest(getCrawlerName()).source(new SearchSourceBuilder()
                .query(QueryBuilders.prefixQuery("file.content_type", "text*"))), 0L, null);
        countTestHelper(new SearchRequest(getCrawlerName()).source(new SearchSourceBuilder()
                .query(QueryBuilders.termQuery("file.extension", "txt"))), 1L, null);
    }
}
