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

import fr.pilato.elasticsearch.crawler.fs.beans.FsJob;
import fr.pilato.elasticsearch.crawler.fs.beans.FsJobFileHandler;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchHit;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;

import static java.lang.Thread.sleep;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test moving/removing/adding files
 */
public class FsCrawlerTestAddNewFilesIT extends AbstractFsCrawlerITCase {
    private static final Logger logger = LogManager.getLogger();

    @Test
    public void add_new_files_and_force_rescan() throws Exception {
        FsSettings fsSettings = createTestSettings();
        // We want to wait the default wait time which is 15 minutes.
        fsSettings.getFs().setUpdateRate(TimeValue.timeValueMinutes(15));
        crawler = startCrawler(fsSettings);

        // We should have one doc first
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, currentTestResourceDir);

        // We add a file
        logger.info("  ---> Adding file new_roottxtfile.txt");
        Files.write(currentTestResourceDir.resolve("new_roottxtfile.txt"), "This is a second file".getBytes(StandardCharsets.UTF_8));

        // Forcing a rescan by modifying the next scan date
        logger.info("  ---> changing next check date to now manually");
        FsJobFileHandler fsJobFileHandler = new FsJobFileHandler(metadataDir);
        FsJob fsJob = fsJobFileHandler.read(fsSettings.getName());
        // We set the next check to now so that the crawler will rescan the directory
        fsJob.setNextCheck(LocalDateTime.now());
        fsJobFileHandler.write(getCrawlerName(), fsJob);

        // We expect to have two files
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 2L, currentTestResourceDir);
    }

    @Test
    public void add_new_files_and_force_rescan_with_null() throws Exception {
        FsSettings fsSettings = createTestSettings();
        // We want to wait the default wait time which is 15 minutes.
        fsSettings.getFs().setUpdateRate(TimeValue.timeValueMinutes(15));
        crawler = startCrawler(fsSettings);

        // We should have one doc first
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, currentTestResourceDir);

        // We add a file
        logger.info("  ---> Adding file new_roottxtfile.txt");
        Files.write(currentTestResourceDir.resolve("new_roottxtfile.txt"), "This is a second file".getBytes(StandardCharsets.UTF_8));

        // Forcing a rescan by modifying the next scan date
        logger.info("  ---> removing next check date to force a manual rescan");
        FsJobFileHandler fsJobFileHandler = new FsJobFileHandler(metadataDir);
        FsJob fsJob = fsJobFileHandler.read(fsSettings.getName());
        // We set the next check to null so that the crawler will rescan the directory
        fsJob.setNextCheck(null);
        fsJobFileHandler.write(getCrawlerName(), fsJob);

        // We expect to have two files
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 2L, currentTestResourceDir);
    }

    /**
     * Test case for issue #60: <a href="https://github.com/dadoonet/fscrawler/issues/60">https://github.com/dadoonet/fscrawler/issues/60</a> : new files are not added
     */
    @Test
    public void add_new_file() throws Exception {
        // We need to wait for 2 seconds before starting the test as the file might have just been created
        // It's due to https://github.com/dadoonet/fscrawler/issues/82 which removes 2 seconds from the last scan date
        sleep(2000L);

        FsSettings fsSettings = createTestSettings();
        // We change the update rate to 5 seconds because the FsParser last scan date is set to 2 seconds less than the current time
        fsSettings.getFs().setUpdateRate(TimeValue.timeValueSeconds(5));
        crawler = startCrawler(fsSettings);

        // We should have one doc first
        ESSearchResponse response = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, currentTestResourceDir);
        checkDocVersions(response, 1L);

        logger.info(" ---> Creating a new file new_roottxtfile.txt");
        Files.write(currentTestResourceDir.resolve("new_roottxtfile.txt"), "This is a second file".getBytes());

        // We expect to have two files
        response = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 2L, currentTestResourceDir);

        // It should be only version <= 2 for both docs
        checkDocVersions(response, 2L);

        logger.info(" ---> Creating a new file new_new_roottxtfile.txt");
        Files.write(currentTestResourceDir.resolve("new_new_roottxtfile.txt"), "This is a third file".getBytes());

        // We expect to have three files
        response = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 3L, currentTestResourceDir);

        // It should be only version <= 2 for all docs
        checkDocVersions(response, 2L);
    }

    /**
     * Iterate other response hits and check that _version is at most a given version
     * @param response The search response object
     * @param maxVersion Maximum version number we can have
     */
    private void checkDocVersions(ESSearchResponse response, long maxVersion) {
        // It should be only version <= maxVersion for all docs
       assertThat(response.getHits())
               .isNotEmpty()
               .allSatisfy(hit -> {
           ESSearchHit getHit = client.get(hit.getIndex(), hit.getId());
           assertThat(getHit.getVersion()).isLessThanOrEqualTo(maxVersion);
       });
    }
}
