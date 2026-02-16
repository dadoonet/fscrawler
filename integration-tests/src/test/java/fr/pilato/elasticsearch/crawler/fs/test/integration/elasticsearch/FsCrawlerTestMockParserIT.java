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

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import fr.pilato.elasticsearch.crawler.fs.client.ESMatchQuery;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.client.ESTermQuery;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.junit.Test;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.INDEX_SUFFIX_DOCS;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test crawler behavior with MockParser files that simulate erratic parser behaviors.
 * See <a href="https://cwiki.apache.org/confluence/display/tika/MockParser">Tika MockParser</a>
 * <p>
 * This test verifies that FSCrawler:
 * <ul>
 *     <li>Continues crawling after a parsing error</li>
 *     <li>Successfully indexes files that can be parsed</li>
 *     <li>Does not crash when encountering problematic files</li>
 * </ul>
 */
public class FsCrawlerTestMockParserIT extends AbstractFsCrawlerITCase {

    /**
     * Test that the crawler continues processing files even when one file causes
     * a parser exception via MockParser.
     * <p>
     * The test directory (mock_parser) contains:
     * <ul>
     *     <li>valid.txt - A valid text file</li>
     *     <li>mock-exception.xml - A file that triggers a RuntimeException via MockParser</li>
     *     <li>valid2.txt - Another valid text file</li>
     * </ul>
     * <p>
     * Expected: Both valid files should be indexed (2 documents),
     * the mock-exception.xml might also be indexed but with empty content.
     * This test also verifies that valid content remains searchable.
     */
    @Test
    public void mock_parser() throws Exception {
        crawler = startCrawler();

        // We expect 3 valid files to be indexed
        // The mock-exception.xml is also be indexed (with empty content)
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS), 3L, null);

        // Verify that the content from valid files is searchable
        countTestHelper(new ESSearchRequest()
                .withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS)
                .withESQuery(new ESMatchQuery("content", "valid")), 2L, null);

        // Verify content from valid2.txt specifically - it contains "continue"
        countTestHelper(new ESSearchRequest()
                .withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS)
                .withESQuery(new ESMatchQuery("content", "continue")), 1L, null);

        // Verify content from the xml file specifically. It should be empty
        ESSearchResponse response = countTestHelper(new ESSearchRequest()
                .withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS)
                .withESQuery(new ESTermQuery("file.extension", "xml")), 1L, null);
        assertThatThrownBy(() -> JsonPath.read(response.getHits().get(0).getSource(), "$.content")).isInstanceOf(PathNotFoundException.class);
    }
}
