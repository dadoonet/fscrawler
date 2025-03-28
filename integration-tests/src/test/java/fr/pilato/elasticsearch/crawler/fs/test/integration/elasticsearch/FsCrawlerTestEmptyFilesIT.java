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

import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.parseJsonAsDocumentContext;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

/**
 * Test crawler with empty files.
 * Issue <a href="https://github.com/dadoonet/fscrawler/issues/1798">#1798</a>
 */
public class FsCrawlerTestEmptyFilesIT extends AbstractFsCrawlerITCase {

    @Test
    public void test_empty_files() throws Exception {
        crawler = startCrawler();

        // We expect to have 2 files
        ESSearchResponse response = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()).withSort("file.filename"), 2L, null);

        DocumentContext doc01 = parseJsonAsDocumentContext(response.getHits().get(0).getSource());
        assertThat(doc01.read("$.file.filename"), is("01-not-empty.txt"));
        assertThat(doc01.read("$.content"), containsString("Hello World"));

        DocumentContext doc02 = parseJsonAsDocumentContext(response.getHits().get(1).getSource());
        assertThat(doc02.read("$.file.filename"), is("02-empty.txt"));
        expectThrows(PathNotFoundException.class, () -> doc02.read("$.content"));
    }
}
