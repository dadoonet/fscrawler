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

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.PathNotFoundException;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.junit.Test;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.INDEX_SUFFIX_DOCS;
import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.parseJsonAsDocumentContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test crawler with empty files.
 * Issue <a href="https://github.com/dadoonet/fscrawler/issues/1798">#1798</a>
 */
public class FsCrawlerTestEmptyFilesIT extends AbstractFsCrawlerITCase {

    @Test
    public void empty_files() throws Exception {
        crawler = startCrawler();

        // We expect to have 2 files
        ESSearchResponse response = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS).withSort("file.filename"), 2L, null);

        DocumentContext doc01 = parseJsonAsDocumentContext(response.getHits().get(0).getSource());
        assertThat((String) doc01.read("$.file.filename")).isEqualTo("01-not-empty.txt");
        assertThat((String) doc01.read("$.content")).contains("Hello World");

        DocumentContext doc02 = parseJsonAsDocumentContext(response.getHits().get(1).getSource());
        assertThat((String) doc02.read("$.file.filename")).isEqualTo("02-empty.txt");
        assertThatThrownBy(() -> doc02.read("$.content")).isInstanceOf(PathNotFoundException.class);
    }
}
