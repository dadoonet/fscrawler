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
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchHit;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.framework.ByteSizeValue;
import fr.pilato.elasticsearch.crawler.fs.framework.Percentage;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import java.nio.file.Files;

import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.parseJsonAsDocumentContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test filesize crawler setting
 */
public class FsCrawlerTestFilesizeIT extends AbstractFsCrawlerITCase {
    private static final Logger logger = LogManager.getLogger();

    @Test
    public void indexed_chars() throws Exception {
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setIndexedChars(new Percentage(7));
        crawler = startCrawler(fsSettings);

        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
        for (ESSearchHit hit : searchResponse.getHits()) {
            DocumentContext document = parseJsonAsDocumentContext(hit.getSource());

            // Our original text should be truncated
            assertThat((String) document.read("$.content")).isEqualTo("Novo de");
            assertThat((Integer) document.read("$.file.indexed_chars")).isEqualTo(7);
        }
    }

    @Test
    public void indexed_chars_percentage() throws Exception {
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setIndexedChars(Percentage.parse("0.1%"));
        crawler = startCrawler(fsSettings);

        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
        for (ESSearchHit hit : searchResponse.getHits()) {
            DocumentContext document = parseJsonAsDocumentContext(hit.getSource());

            // Our original text should be truncated
            assertThat((String) document.read("$.content")).isEqualTo("Novo denique");
            assertThat((Integer) document.read("$.file.indexed_chars")).isEqualTo(12);
        }
    }

    @Test
    public void indexed_chars_nolimit() throws Exception {
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setIndexedChars(new Percentage(-1));
        crawler = startCrawler(fsSettings);

        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
        for (ESSearchHit hit : searchResponse.getHits()) {
            DocumentContext document = parseJsonAsDocumentContext(hit.getSource());

            // Our original text should not be truncated, so we must have its end extracted
            assertThat((String) document.read("$.content")).contains("haecque non diu sunt perpetrata.");
            assertThatThrownBy(() -> document.read("$.file.indexed_chars")).isInstanceOf(PathNotFoundException.class);
        }
    }

    @Test
    public void filesize() throws Exception {
        crawler = startCrawler();

        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
        for (ESSearchHit hit : searchResponse.getHits()) {
            assertThat((Integer) JsonPath.read(hit.getSource(), "$.file.filesize")).isEqualTo(12230);
        }
    }

    @Test
    public void filesize_disabled() throws Exception {
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setAddFilesize(false);
        crawler = startCrawler(fsSettings);

        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
        for (ESSearchHit hit : searchResponse.getHits()) {
            assertThatThrownBy(() -> JsonPath.read(hit.getSource(), "$.file.filesize")).isInstanceOf(PathNotFoundException.class);
        }
    }

    @Test
    public void filesize_limit() throws Exception {
        logger.info(" ---> Creating a smaller file small.txt");
        Files.write(currentTestResourceDir.resolve("small.txt"), "This is a second file smaller than the previous one".getBytes());

        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setIgnoreAbove(ByteSizeValue.parseBytesSizeValue("10kb"));
        crawler = startCrawler(fsSettings);

        ESSearchResponse response = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
        assertThat(response.getTotalHits()).isEqualTo(1);
    }
}
