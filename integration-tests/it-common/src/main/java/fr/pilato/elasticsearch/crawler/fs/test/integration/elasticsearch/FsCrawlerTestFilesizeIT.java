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

import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.beans.File;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchHit;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.framework.ByteSizeValue;
import fr.pilato.elasticsearch.crawler.fs.framework.Percentage;
import fr.pilato.elasticsearch.crawler.fs.settings.Fs;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.junit.Test;

import java.nio.file.Files;
import java.util.Map;

import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.extractFromPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * Test filesize crawler setting
 */
public class FsCrawlerTestFilesizeIT extends AbstractFsCrawlerITCase {

    @Test
    public void test_indexed_chars() throws Exception {
        Fs fs = startCrawlerDefinition()
                .setIndexedChars(new Percentage(7))
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
        for (ESSearchHit hit : searchResponse.getHits()) {
            Object content = hit.getSourceAsMap().get(Doc.FIELD_NAMES.CONTENT);
            Object indexedChars = extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.INDEXED_CHARS);
            assertThat(content, notNullValue());
            assertThat(indexedChars, notNullValue());

            // Our original text should be truncated
            assertThat(content, is("Novo de"));
            assertThat(indexedChars, is(7));
        }
    }

    @Test
    public void test_indexed_chars_percentage() throws Exception {
        Fs fs = startCrawlerDefinition()
                .setIndexedChars(Percentage.parse("0.1%"))
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
        for (ESSearchHit hit : searchResponse.getHits()) {
            Object content = hit.getSourceAsMap().get(Doc.FIELD_NAMES.CONTENT);
            Object indexedChars = extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.INDEXED_CHARS);
            assertThat(content, notNullValue());
            assertThat(indexedChars, notNullValue());

            // Our original text should be truncated
            assertThat(content, is("Novo denique"));
            assertThat(indexedChars, is(12));
        }
    }

    @Test
    public void test_indexed_chars_nolimit() throws Exception {
        Fs fs = startCrawlerDefinition()
                .setIndexedChars(new Percentage(-1))
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
        for (ESSearchHit hit : searchResponse.getHits()) {
            Object content = hit.getSourceAsMap().get(Doc.FIELD_NAMES.CONTENT);
            Object indexedChars = extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.INDEXED_CHARS);
            assertThat(content, notNullValue());
            assertThat(indexedChars, nullValue());

            // Our original text should not be truncated so we must have its end extracted
            assertThat((String) content, containsString("haecque non diu sunt perpetrata."));
        }
    }

    @Test
    public void test_filesize() throws Exception {
        startCrawler();

        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
        for (ESSearchHit hit : searchResponse.getHits()) {
            @SuppressWarnings("unchecked") Map<String, Object> file = (Map<String, Object>) hit.getSourceAsMap().get(Doc.FIELD_NAMES.FILE);
            assertThat(file, notNullValue());
            assertThat(file.get(File.FIELD_NAMES.FILESIZE), is(12230));
        }
    }

    @Test
    public void test_filesize_disabled() throws Exception {
        Fs fs = startCrawlerDefinition()
                .setAddFilesize(false)
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
        for (ESSearchHit hit : searchResponse.getHits()) {
            @SuppressWarnings("unchecked") Map<String, Object> file = (Map<String, Object>) hit.getSourceAsMap().get(Doc.FIELD_NAMES.FILE);
            assertThat(file, notNullValue());
            assertThat(file.get(File.FIELD_NAMES.FILESIZE), nullValue());
        }
    }

    @Test
    public void test_filesize_limit() throws Exception {
        logger.info(" ---> Creating a smaller file small.txt");
        Files.write(currentTestResourceDir.resolve("small.txt"), "This is a second file smaller than the previous one".getBytes());

        Fs fs = startCrawlerDefinition()
                .setIgnoreAbove(ByteSizeValue.parseBytesSizeValue("10kb"))
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
    }
}
