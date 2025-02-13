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
import fr.pilato.elasticsearch.crawler.fs.client.*;
import fr.pilato.elasticsearch.crawler.fs.settings.Fs;
import fr.pilato.elasticsearch.crawler.fs.settings.Ocr;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assume.assumeTrue;

/**
 * Tests with OCR configuration
 * See <a href="https://github.com/dadoonet/fscrawler/issues/1988">#1988</a>
 */
public class FsCrawlerTestOcrIT extends AbstractFsCrawlerITCase {
    private static final Logger logger = LogManager.getLogger();

    @Test
    public void test_ocr() throws Exception {
        String exec = "tesseract";
        Optional<Path> tessPath = Stream.of(System.getenv("PATH").split(Pattern.quote(File.pathSeparator)))
                .map(Paths::get)
                .filter(path -> Files.exists(path.resolve(exec)))
                .findFirst();
        assumeTrue("We need to have tesseract installed and present in path to run this test", tessPath.isPresent());
        Path tessDirPath = tessPath.get();
        Path tesseract = tessDirPath.resolve(exec);
        logger.info("Tesseract is installed at [{}]", tesseract);

        // Default behaviour
        {
            crawler = startCrawler();

            // We expect to have one file
            ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 2L, null);

            // The default configuration should not add file attributes
            for (ESSearchHit hit : searchResponse.getHits()) {
                assertThat(JsonPath.read(hit.getSource(), "$.content"), containsString("words"));
            }

            crawler.close();
            crawler = null;
        }

        {
            Fs fs = startCrawlerDefinition()
                    .setOcr(Ocr.builder()
                            .setEnabled(true)
                            .setPath(tesseract.toString())
                            .setPdfStrategy("ocr_and_text")
                            .setLanguage("vie+eng")
                            .setOutputType("txt")
                            .build())
                    .build();

            crawler = startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null, null);

            // We expect to have one file
            ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 2L, null);

            // The default configuration should not add file attributes
            for (ESSearchHit hit : searchResponse.getHits()) {
                assertThat(JsonPath.read(hit.getSource(), "$.content"), containsString("words"));
            }

            crawler.close();
            crawler = null;
        }

        {
            Fs fs = startCrawlerDefinition()
                    .setOcr(Ocr.builder()
                            .setEnabled(true)
                            .setPath(tessDirPath.toString())
                            .setPdfStrategy("ocr_and_text")
                            .setLanguage("vie+eng")
                            .setOutputType("txt")
                            .build())
                    .build();

            crawler = startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null, null);

            // We expect to have one file
            ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 2L, null);

            // The default configuration should not add file attributes
            for (ESSearchHit hit : searchResponse.getHits()) {
                assertThat(JsonPath.read(hit.getSource(), "$.content"), containsString("words"));
            }
        }
    }
}
