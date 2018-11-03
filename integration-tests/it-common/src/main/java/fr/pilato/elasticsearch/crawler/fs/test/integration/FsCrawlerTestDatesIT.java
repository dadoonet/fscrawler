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

import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.beans.File;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchHit;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.framework.OsValidator;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;

import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.extractFromPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

/**
 * Test different dates of files
 */
public class FsCrawlerTestDatesIT extends AbstractFsCrawlerITCase {

    /**
     * We want to make sure dates are correctly generated
     */
    @Test
    public void test_check_dates() throws Exception {
        startCrawler();

        logger.info(" ---> Creating a new file second.txt");
        Files.write(currentTestResourceDir.resolve("second.txt"), "This is a second file".getBytes());

        // We expect to have two files
        ESSearchResponse responseNotModified = countTestHelper(new ESSearchRequest()
                        .withIndex(getCrawlerName())
                        .withSort(Doc.FIELD_NAMES.FILE + "." + File.FIELD_NAMES.CREATED)
                , 2L, currentTestResourceDir);

        // We look at the dates.
        showHitDates(responseNotModified.getHits());

        // We record what the current date is
        Instant mockAccessDate = Instant.now(); //can be LocalDateTime
        FileTime fileTime = FileTime.from(mockAccessDate);

        logger.info(" ---> Changing date for file second.txt to [{}]", mockAccessDate);
        Files.setAttribute(currentTestResourceDir.resolve("second.txt"), "lastAccessTime", fileTime);
        Files.setAttribute(currentTestResourceDir.resolve("second.txt"), "lastModifiedTime", fileTime);
        logger.info(" ---> Creating a new file third.txt");
        Files.write(currentTestResourceDir.resolve("third.txt"), "This is a third file".getBytes());

        // We expect to have 3 files
        ESSearchResponse responseModified = countTestHelper(new ESSearchRequest()
                        .withIndex(getCrawlerName())
                        .withSort(Doc.FIELD_NAMES.FILE + "." + File.FIELD_NAMES.CREATED)
                , 3L, currentTestResourceDir);

        // We look at the dates.
        showHitDates(responseModified.getHits());

        // Let's compare dates from 1st run and 2nd run
        compareHits(responseNotModified.getHits().get(0), responseModified.getHits().get(0), true);
        compareHits(responseNotModified.getHits().get(1), responseModified.getHits().get(1), false);
    }

    private void compareHits(ESSearchHit hitBefore, ESSearchHit hitAfter, boolean shouldBeIdentical) {
        String hitBeforeCreated = (String) extractFromPath(hitBefore.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.CREATED);
        String hitBeforeIndexingDate = (String) extractFromPath(hitBefore.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.INDEXING_DATE);
        String hitBeforeLastModified = (String) extractFromPath(hitBefore.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.LAST_MODIFIED);
        String hitBeforeLastAccessed = (String) extractFromPath(hitBefore.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.LAST_ACCESSED);
        String hitAfterCreated = (String) extractFromPath(hitAfter.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.CREATED);
        String hitAfterIndexingDate = (String) extractFromPath(hitAfter.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.INDEXING_DATE);
        String hitAfterLastModified = (String) extractFromPath(hitAfter.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.LAST_MODIFIED);
        String hitAfterLastAccessed = (String) extractFromPath(hitAfter.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.LAST_ACCESSED);

        // Apparently on some FS, the creation date may be modified when changing the
        // modification date... So we can't really compare.
        // assertThat(hitBeforeCreated, equalTo(hitAfterCreated));
        if (!hitBeforeCreated.equals(hitAfterCreated)) {
            logger.warn("OS is [{}]. Creation date changed from [{}] to [{}].", OsValidator.OS, hitBeforeCreated, hitAfterCreated);
        }
        if (shouldBeIdentical) {
            assertThat(hitBeforeIndexingDate, equalTo(hitAfterIndexingDate));
            assertThat(hitBeforeLastModified, equalTo(hitAfterLastModified));
            assertThat(hitBeforeLastAccessed, equalTo(hitAfterLastAccessed));
        } else {
            assertThat(hitBeforeIndexingDate, not(equalTo(hitAfterIndexingDate)));
            assertThat(hitBeforeLastModified, not(equalTo(hitAfterLastModified)));
            assertThat(hitBeforeLastAccessed, not(equalTo(hitAfterLastAccessed)));
        }
    }

    private void showHitDates(List<ESSearchHit> hits) {
        logger.info("|        created date        |        indexing date       |     last modified date     |     last accessed date     |");
        logger.info("|----------------------------|----------------------------|----------------------------|----------------------------|");
        for (ESSearchHit hit : hits) {
            String created = (String) extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.CREATED);
            String indexingDate = (String) extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.INDEXING_DATE);
            String lastModified = (String) extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.LAST_MODIFIED);
            String lastAccessed = (String) extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.FILE).get(File.FIELD_NAMES.LAST_ACCESSED);
            logger.info("|{}|{}|{}|{}|", created, indexingDate, lastModified, lastAccessed);
        }
    }
}
