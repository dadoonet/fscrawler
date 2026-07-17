/*
 * Licensed to David Pilato (the "Author") under one
 * or more contributor license agreements. See the NOTICE file
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
 *
 * Made from 🇫🇷🇪🇺 with ❤️ - 2011-2026
 */
package fr.pilato.elasticsearch.crawler.fs.test.integration.elasticsearch;

import com.jayway.jsonpath.DocumentContext;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchHit;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.framework.ExponentialBackoffPollInterval;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil;
import fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil;
import fr.pilato.elasticsearch.crawler.fs.framework.OsValidator;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/** Test different dates of files */
class FsCrawlerTestDatesIT extends AbstractFsCrawlerITCase {
    private static final Logger logger = LogManager.getLogger();

    /** We want to make sure dates are correctly generated */
    @Test
    void check_dates() throws Exception {
        crawler = startCrawler();

        logger.info(" ---> Creating a new file second.txt");
        // Let's wait for at least one second to make sure we have different dates
        // between the two files. Created date seems to be rounded to the second.
        FsCrawlerUtil.waitFor(Duration.ofMillis(1001));

        Files.write(currentTestResourceDir.resolve("second.txt"), "This is a second file".getBytes());

        ESSearchRequest searchRequest = new ESSearchRequest()
                .withIndex(getCrawlerName() + FsCrawlerUtil.INDEX_SUFFIX_DOCS)
                .withSort("file.filename");

        // We expect to have two files
        ESSearchResponse responseNotModified = countTestHelper(searchRequest, 2L, currentTestResourceDir);

        // We look at the dates.
        showHitDates(responseNotModified.getHits());

        // Snapshot second.txt dates before we mutate them. Hits are sorted by filename:
        // roottxtfile.txt (0), second.txt (1).
        DocumentContext secondBefore = JsonUtil.parseJsonAsDocumentContext(
                responseNotModified.getHits().get(1).getSource());
        String secondLastModifiedBefore = secondBefore.read("$.file.last_modified");
        String secondLastAccessedBefore = secondBefore.read("$.file.last_accessed");

        // We record what the current date is
        Instant mockAccessDate = Instant.now(); // can be LocalDateTime
        FileTime fileTime = FileTime.from(mockAccessDate);

        logger.info(" ---> Changing date for file second.txt to [{}]", mockAccessDate);
        Files.setAttribute(currentTestResourceDir.resolve("second.txt"), "lastAccessTime", fileTime);
        Files.setAttribute(currentTestResourceDir.resolve("second.txt"), "lastModifiedTime", fileTime);
        logger.info(" ---> Creating a new file third.txt");
        Files.write(currentTestResourceDir.resolve("third.txt"), "This is a third file".getBytes());

        // Wait until third.txt is indexed AND second.txt has been reindexed with new dates.
        // Waiting on hit count alone is flaky: third.txt (a new doc) can become searchable before
        // the async bulk update for second.txt is visible (especially on serverless under parallel load).
        AtomicReference<ESSearchResponse> responseModifiedRef = new AtomicReference<>();
        Awaitility.await()
                .atMost(MAX_WAIT_FOR_SEARCH)
                .alias("second.txt dates should be updated and third.txt indexed")
                .pollInterval(ExponentialBackoffPollInterval.exponential(Duration.ofMillis(500), Duration.ofSeconds(5)))
                .until(() -> {
                    try {
                        refresh(searchRequest.getIndex());
                        ESSearchResponse response = client.search(searchRequest);
                        if (response.getTotalHits() != 3L || response.getHits().size() < 2) {
                            return false;
                        }
                        DocumentContext secondAfter = JsonUtil.parseJsonAsDocumentContext(
                                response.getHits().get(1).getSource());
                        String lastModified = secondAfter.read("$.file.last_modified");
                        String lastAccessed = secondAfter.read("$.file.last_accessed");
                        if (Objects.equals(secondLastModifiedBefore, lastModified)
                                || Objects.equals(secondLastAccessedBefore, lastAccessed)) {
                            return false;
                        }
                        responseModifiedRef.set(response);
                        return true;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

        ESSearchResponse responseModified = responseModifiedRef.get();

        // We look at the dates.
        showHitDates(responseModified.getHits());

        // Let's compare dates from 1st run and 2nd run
        compareHits(
                responseNotModified.getHits().get(0), responseModified.getHits().get(0), true);
        compareHits(
                responseNotModified.getHits().get(1), responseModified.getHits().get(1), false);
    }

    private void compareHits(ESSearchHit hitBefore, ESSearchHit hitAfter, boolean shouldBeIdentical) {
        DocumentContext documentBefore = JsonUtil.parseJsonAsDocumentContext(hitBefore.getSource());
        String hitBeforeCreated = documentBefore.read("$.file.created");
        String hitBeforeLastModified = documentBefore.read("$.file.last_modified");
        String hitBeforeLastAccessed = documentBefore.read("$.file.last_accessed");
        DocumentContext documentAfter = JsonUtil.parseJsonAsDocumentContext(hitAfter.getSource());
        String hitAfterCreated = documentAfter.read("$.file.created");
        String hitAfterLastModified = documentAfter.read("$.file.last_modified");
        String hitAfterLastAccessed = documentAfter.read("$.file.last_accessed");

        // Apparently on some FS, the creation date may be modified when changing the
        // modification date... So we can't really compare.
        // assertThat(hitBeforeCreated, equalTo(hitAfterCreated));
        if (!hitBeforeCreated.equals(hitAfterCreated)) {
            logger.warn(
                    "OS is [{}]. Creation date changed from [{}] to [{}].",
                    OsValidator.OS,
                    hitBeforeCreated,
                    hitAfterCreated);
        }
        // We deliberately do NOT compare file.indexing_date here. Unlike created/last_modified/
        // last_accessed, indexing_date is not a file property: it records when FSCrawler indexed the
        // document. An unmodified file can be legitimately re-indexed on a later run because the scan
        // window is widened by 2 seconds to absorb filesystem timestamp truncation (see issue #82),
        // so indexing_date is not stable and comparing it makes this test flaky.
        if (shouldBeIdentical) {
            Assertions.assertThat(hitBeforeLastModified).isEqualTo(hitAfterLastModified);
            Assertions.assertThat(hitBeforeLastAccessed).isEqualTo(hitAfterLastAccessed);
        } else {
            Assertions.assertThat(hitBeforeLastModified).isNotEqualTo(hitAfterLastModified);
            Assertions.assertThat(hitBeforeLastAccessed).isNotEqualTo(hitAfterLastAccessed);
        }
    }

    private void showHitDates(List<ESSearchHit> hits) {
        logger.info(
                "|        created date         |         indexing date       |      last modified date     |      last accessed date     |");
        logger.info(
                "|-----------------------------|-----------------------------|-----------------------------|-----------------------------|");
        for (ESSearchHit hit : hits) {
            DocumentContext document = JsonUtil.parseJsonAsDocumentContext(hit.getSource());
            String created = document.read("$.file.created");
            String indexingDate = document.read("$.file.indexing_date");
            String lastModified = document.read("$.file.last_modified");
            String lastAccessed = document.read("$.file.last_accessed");
            logger.info("|{}|{}|{}|{}|", created, indexingDate, lastModified, lastAccessed);
        }
    }
}
