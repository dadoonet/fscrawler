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

import com.google.common.base.Charsets;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchHit;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.client.ESTermQuery;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientException;
import fr.pilato.elasticsearch.crawler.fs.settings.Fs;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.apache.logging.log4j.Level;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.Assert.fail;

/**
 * Test moving/removing/adding files
 */
public class FsCrawlerTestRemoveDeletedIT extends AbstractFsCrawlerITCase {

    @Test
    public void test_remove_deleted_enabled() throws Exception {
        Fs fs = startCrawlerDefinition()
                .setRemoveDeleted(true)
                .build();
        crawler = startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        // We should have two docs first
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 2L, currentTestResourceDir);

        // We remove a file
        logger.info("  ---> Removing file deleted_roottxtfile.txt");
        Files.delete(currentTestResourceDir.resolve("deleted_roottxtfile.txt"));

        // We expect to have one file
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, currentTestResourceDir);
    }

    @Test
    public void test_remove_deleted_disabled() throws Exception {
        Fs fs = startCrawlerDefinition()
                .setRemoveDeleted(false)
                .build();
        crawler = startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        // We should have two docs first
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 2L, currentTestResourceDir);

        // We remove a file
        logger.info(" ---> Removing file deleted_roottxtfile.txt");
        Files.delete(currentTestResourceDir.resolve("deleted_roottxtfile.txt"));

        // We expect to have two files
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 2L, currentTestResourceDir);
    }

    /**
     * Test case for #95: <a href="https://github.com/dadoonet/fscrawler/issues/95">https://github.com/dadoonet/fscrawler/issues/95</a> : Folder index is not getting delete on delete of folder
     */
    @Test
    public void test_remove_folder_deleted_enabled() throws Exception {
        Fs fs = startCrawlerDefinition()
                .setRemoveDeleted(true)
                .build();
        crawler = startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

        // We should have 7 docs first
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 7L, currentTestResourceDir);

        logContentOfDir(currentTestResourceDir, Level.DEBUG);

        // We remove a directory
        logger.info("  ---> Removing dir subdir1");
        deleteRecursively(currentTestResourceDir.resolve("subdir1"));

        logContentOfDir(currentTestResourceDir, Level.DEBUG);

        // We expect to have 4 docs now
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 4L, currentTestResourceDir);
    }

    /**
     * Test case for <a href="https://github.com/dadoonet/fscrawler/issues/110">https://github.com/dadoonet/fscrawler/issues/110</a>
     * @throws Exception In case something is wrong
     */
    @Test
    public void test_rename_file() throws Exception {
        crawler = startCrawler();

        // We should have one doc first
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, currentTestResourceDir);

        // We rename the file
        logger.info(" ---> Renaming file roottxtfile.txt to renamed_roottxtfile.txt");
        // We create a copy of a file
        Files.move(currentTestResourceDir.resolve("roottxtfile.txt"),
                currentTestResourceDir.resolve("renamed_roottxtfile.txt"));

        // We expect to have one file only with a new name
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()).withESQuery(new ESTermQuery("file.filename", "renamed_roottxtfile.txt")), 1L, currentTestResourceDir);
    }

    /**
     * Test case for <a href="https://github.com/dadoonet/fscrawler/issues/379">https://github.com/dadoonet/fscrawler/issues/379</a>
     * @throws Exception In case something is wrong
     */
    @Test
    public void test_move_file() throws Exception {
        crawler = startCrawler();

        // We should have one doc first
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, currentTestResourceDir);

        // We move the file
        logger.info(" ---> Moving file roottxtfile.txt to a tmp dir");
        Files.move(currentTestResourceDir.resolve("roottxtfile.txt"),
                rootTmpDir.resolve("roottxtfile.txt"), StandardCopyOption.ATOMIC_MOVE);

        // We expect to have 0 file
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 0L, currentTestResourceDir);

        // We move the file back
        logger.info(" ---> Moving file roottxtfile.txt from the tmp dir");
        Files.move(rootTmpDir.resolve("roottxtfile.txt"),
                currentTestResourceDir.resolve("roottxtfile.txt"), StandardCopyOption.ATOMIC_MOVE);

        // We need to "touch" the file we just moved
        Files.setLastModifiedTime(currentTestResourceDir.resolve("roottxtfile.txt"), FileTime.from(Instant.now()));

        // We expect to have 1 file
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, currentTestResourceDir);
    }

    /**
     * Test case for #136: <a href="https://github.com/dadoonet/fscrawler/issues/136">https://github.com/dadoonet/fscrawler/issues/136</a> : Moving existing files does not index new files
     */
    @Test
    public void test_moving_files() throws Exception {
        String filename = "oldfile.txt";

        crawler = startCrawler();

        // Let's first create some files
        logger.info(" ---> Creating a file [{}]", filename);

        Path tmpDir = rootTmpDir.resolve("resources").resolve(getCurrentTestName() + "-tmp");
        if (Files.notExists(tmpDir)) {
            Files.createDirectory(tmpDir);
        }

        Path file = Files.createFile(tmpDir.resolve(filename));
        Files.writeString(file, "Hello world", Charsets.UTF_8);

        // We should have 1 doc first
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);

        logContentOfDir(currentTestResourceDir, Level.DEBUG);

        // We remove a directory
        logger.info("  ---> Moving file [{}] to [{}]", file, currentTestResourceDir);
        Files.move(file, currentTestResourceDir.resolve(filename));

        logContentOfDir(currentTestResourceDir, Level.DEBUG);

        // We expect to have 2 docs now
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 2L, null);
    }

    /**
     * Test case for issue #60: <a href="https://github.com/dadoonet/fscrawler/issues/60">https://github.com/dadoonet/fscrawler/issues/60</a> : new files are not added
     */
    @Test
    public void test_add_new_file() throws Exception {
        crawler = startCrawler();

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
        for (ESSearchHit hit : response.getHits()) {
            // Read the document. This is needed since 5.0 as search does not return the _version field
            try {
                ESSearchHit getHit = documentService.get(hit.getIndex(), hit.getId());
                assertThat(getHit.getVersion(), lessThanOrEqualTo(maxVersion));
            } catch (IOException | ElasticsearchClientException e) {
                fail("We got an Exception: " + e.getMessage());
            }
        }
    }
}
