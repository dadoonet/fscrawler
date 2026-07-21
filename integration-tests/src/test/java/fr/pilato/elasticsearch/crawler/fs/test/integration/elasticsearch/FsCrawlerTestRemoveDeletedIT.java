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

import com.carrotsearch.randomizedtesting.jupiter.RandomizedTest;
import com.jayway.jsonpath.DocumentContext;
import fr.pilato.elasticsearch.crawler.fs.beans.FsCrawlerCheckpoint;
import fr.pilato.elasticsearch.crawler.fs.beans.FsCrawlerCheckpointFileHandler;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.client.ESTermQuery;
import fr.pilato.elasticsearch.crawler.fs.framework.ExponentialBackoffPollInterval;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil;
import fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil;
import fr.pilato.elasticsearch.crawler.fs.framework.OsValidator;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** Test moving/removing/adding files */
class FsCrawlerTestRemoveDeletedIT extends AbstractFsCrawlerITCase {
    private static final Logger logger = LogManager.getLogger();

    @Test
    void remove_deleted_enabled() throws Exception {
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setRemoveDeleted(true);
        crawler = startCrawler(fsSettings);

        // We should have two docs first
        countTestHelper(
                new ESSearchRequest().withIndex(getCrawlerName() + FsCrawlerUtil.INDEX_SUFFIX_DOCS),
                2L,
                currentTestResourceDir);

        // We remove a file
        logger.info("  ---> Removing file deleted_roottxtfile.txt");
        Files.delete(currentTestResourceDir.resolve("deleted_roottxtfile.txt"));

        // We expect to have one file
        countTestHelper(
                new ESSearchRequest().withIndex(getCrawlerName() + FsCrawlerUtil.INDEX_SUFFIX_DOCS),
                1L,
                currentTestResourceDir);
    }

    @Test
    void remove_deleted_disabled() throws Exception {
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setRemoveDeleted(false);
        crawler = startCrawler(fsSettings);

        // We should have two docs first
        countTestHelper(
                new ESSearchRequest().withIndex(getCrawlerName() + FsCrawlerUtil.INDEX_SUFFIX_DOCS),
                2L,
                currentTestResourceDir);

        // We remove a file
        logger.info(" ---> Removing file deleted_roottxtfile.txt");
        Files.delete(currentTestResourceDir.resolve("deleted_roottxtfile.txt"));

        // We expect to have two files
        countTestHelper(
                new ESSearchRequest().withIndex(getCrawlerName() + FsCrawlerUtil.INDEX_SUFFIX_DOCS),
                2L,
                currentTestResourceDir);
    }

    /**
     * Test case for #95: <a
     * href="https://github.com/dadoonet/fscrawler/issues/95">https://github.com/dadoonet/fscrawler/issues/95</a> :
     * Folder index is not getting delete on delete of folder
     */
    @Test
    void remove_folder_deleted_enabled() throws Exception {
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setRemoveDeleted(true);
        crawler = startCrawler(fsSettings);

        // We should have 7 docs first
        countTestHelper(
                new ESSearchRequest().withIndex(getCrawlerName() + FsCrawlerUtil.INDEX_SUFFIX_DOCS),
                7L,
                currentTestResourceDir);

        logContentOfDir(currentTestResourceDir, Level.DEBUG);

        // We remove a directory
        logger.info("  ---> Removing dir subdir1");
        deleteRecursively(currentTestResourceDir.resolve("subdir1"));

        logContentOfDir(currentTestResourceDir, Level.DEBUG);

        countTestHelper(
                new ESSearchRequest().withIndex(getCrawlerName() + FsCrawlerUtil.INDEX_SUFFIX_DOCS),
                4L,
                currentTestResourceDir);
    }

    /**
     * Test case for <a
     * href="https://github.com/dadoonet/fscrawler/issues/110">https://github.com/dadoonet/fscrawler/issues/110</a>
     *
     * @throws Exception In case something is wrong
     */
    @Test
    void rename_file() throws Exception {
        crawler = startCrawler();

        // We should have one doc first
        countTestHelper(
                new ESSearchRequest().withIndex(getCrawlerName() + FsCrawlerUtil.INDEX_SUFFIX_DOCS),
                1L,
                currentTestResourceDir);

        // We rename the file
        logger.info(" ---> Renaming file roottxtfile.txt to renamed_roottxtfile.txt");
        // We create a copy of a file
        Files.move(
                currentTestResourceDir.resolve("roottxtfile.txt"),
                currentTestResourceDir.resolve("renamed_roottxtfile.txt"));
        // We need to "touch" the file we just moved otherwise it won't be seen as a new file
        // This might depend on the OS where the code is running though
        // Or there's a timing issue...
        Files.setLastModifiedTime(
                currentTestResourceDir.resolve("renamed_roottxtfile.txt"), FileTime.from(Instant.now()));

        // We expect to have one file only with a new name
        countTestHelper(
                new ESSearchRequest()
                        .withIndex(getCrawlerName() + FsCrawlerUtil.INDEX_SUFFIX_DOCS)
                        .withESQuery(new ESTermQuery("file.filename", "renamed_roottxtfile.txt")),
                1L,
                currentTestResourceDir);
    }

    /**
     * Test case for <a
     * href="https://github.com/dadoonet/fscrawler/issues/379">https://github.com/dadoonet/fscrawler/issues/379</a>
     *
     * @throws Exception In case something is wrong
     */
    @Test
    void move_file() throws Exception {
        crawler = startCrawler();

        // Create a directory outside of the crawled path to temporarily move files
        Path movedFilesDir = Files.createDirectories(rootTmpDir.resolve(jobName + "-moved"));

        // We should have one doc first
        countTestHelper(
                new ESSearchRequest().withIndex(getCrawlerName() + FsCrawlerUtil.INDEX_SUFFIX_DOCS),
                1L,
                currentTestResourceDir);

        // We move the file
        logger.info(" ---> Moving file roottxtfile.txt to a tmp dir");
        Files.move(
                currentTestResourceDir.resolve("roottxtfile.txt"),
                movedFilesDir.resolve("roottxtfile.txt"),
                StandardCopyOption.ATOMIC_MOVE);

        // We expect to have 0 file
        countTestHelper(
                new ESSearchRequest().withIndex(getCrawlerName() + FsCrawlerUtil.INDEX_SUFFIX_DOCS),
                0L,
                currentTestResourceDir);

        // We move the file back
        logger.info(" ---> Moving file roottxtfile.txt from the tmp dir");
        Files.move(
                movedFilesDir.resolve("roottxtfile.txt"),
                currentTestResourceDir.resolve("roottxtfile.txt"),
                StandardCopyOption.ATOMIC_MOVE);

        // We need to "touch" the file we just moved
        Files.setLastModifiedTime(currentTestResourceDir.resolve("roottxtfile.txt"), FileTime.from(Instant.now()));

        // We expect to have 1 file
        countTestHelper(
                new ESSearchRequest().withIndex(getCrawlerName() + FsCrawlerUtil.INDEX_SUFFIX_DOCS),
                1L,
                currentTestResourceDir);
    }

    /**
     * Test case for #136: <a
     * href="https://github.com/dadoonet/fscrawler/issues/136">https://github.com/dadoonet/fscrawler/issues/136</a> :
     * Moving existing files does not index new files
     */
    @Test
    void moving_files() throws Exception {
        // Let's first create one old file: old = created before the crawler started
        String filename = "oldfile.txt";
        logger.info(" ---> Creating a file [{}]", filename);

        Path tmpDir = Files.createDirectories(rootTmpDir.resolve(jobName + "-temp"));
        Files.createDirectories(tmpDir);

        Path file = Files.createFile(tmpDir.resolve(filename));
        Files.writeString(file, "Hello world", StandardCharsets.UTF_8);

        // Start the crawler
        crawler = startCrawler();

        // We should have 1 doc first
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName() + FsCrawlerUtil.INDEX_SUFFIX_DOCS), 1L, null);

        logContentOfDir(currentTestResourceDir, Level.DEBUG);

        // We move the file from the tmp dir to the crawler dir
        logger.info("  ---> Moving file [{}] to [{}]", file, currentTestResourceDir);
        Files.move(file, currentTestResourceDir.resolve(filename));
        // We need to "touch" the file we just moved
        Files.setLastModifiedTime(currentTestResourceDir.resolve(filename), FileTime.from(Instant.now()));

        logContentOfDir(currentTestResourceDir, Level.DEBUG);

        // We expect to have 2 docs now
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName() + FsCrawlerUtil.INDEX_SUFFIX_DOCS), 2L, null);
    }

    /**
     * Test case for <a
     * href="https://github.com/dadoonet/fscrawler/issues/1300">https://github.com/dadoonet/fscrawler/issues/1300</a>:
     * moving a file between directories inside the watched tree must remove the old document and index the file at its
     * new location.
     *
     * <p>Unlike {@link #move_file()} and {@link #rename_file()}, this test deliberately does <strong>not</strong>
     * {@code touch} the file after the move, which matches real {@code mv} behaviour where mtime (and creation time)
     * are preserved. That is the scenario reported in #1300.
     *
     * <p>Disabled until #1300 is fixed: CI confirmed the failure on Linux and Windows.
     */
    @Test
    @Disabled("Known issue https://github.com/dadoonet/fscrawler/issues/1300 — move without touch is not detected")
    void move_file_between_directories_without_touch() throws Exception {
        String sourceDirName = "src_"
                + RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 4, 8)
                        .toLowerCase(Locale.ROOT);
        String destDirName = "dst_"
                + RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 4, 8)
                        .toLowerCase(Locale.ROOT);
        String filename = RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 6, 12)
                        .toLowerCase(Locale.ROOT)
                + ".txt";
        String content = RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 20, 80);

        Path sourceDir = Files.createDirectories(currentTestResourceDir.resolve(sourceDirName));
        Path destDir = Files.createDirectories(currentTestResourceDir.resolve(destDirName));
        Path sourceFile = sourceDir.resolve(filename);
        Files.writeString(sourceFile, content, StandardCharsets.UTF_8);
        // Ensure mtime is older than any later scan (creation time is handled below via checkpoint wait)
        Files.setLastModifiedTime(sourceFile, FileTime.from(Instant.now().minus(1, ChronoUnit.DAYS)));
        Instant fileCreation = Files.readAttributes(sourceFile, BasicFileAttributes.class)
                .creationTime()
                .toInstant();

        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setRemoveDeleted(true);
        crawler = startCrawler(fsSettings);

        // Default _common sample (roottxtfile.txt) + our randomized file
        countTestHelper(
                new ESSearchRequest().withIndex(getCrawlerName() + FsCrawlerUtil.INDEX_SUFFIX_DOCS),
                2L,
                currentTestResourceDir);

        // Scan dates are rounded to startDate-2s (#82). Wait until the checkpoint scanDate is strictly
        // after the file creation time, otherwise a move that preserves birthtime would still look "new".
        FsCrawlerCheckpointFileHandler checkpointHandler = new FsCrawlerCheckpointFileHandler(metadataDir);
        Awaitility.await()
                .alias("checkpoint scanDate after file creation")
                .atMost(Duration.ofMinutes(1))
                .pollInterval(ExponentialBackoffPollInterval.exponential(Duration.ofMillis(500), Duration.ofSeconds(5)))
                .until(() -> {
                    FsCrawlerCheckpoint checkpoint = checkpointHandler.read(fsSettings.getName());
                    Instant scanDate = checkpoint == null ? null : checkpoint.getScanDate();
                    logger.debug("Waiting for scanDate {} to be after file creation {}", scanDate, fileCreation);
                    return scanDate != null && scanDate.isAfter(fileCreation);
                });

        Path destFile = destDir.resolve(filename);
        logger.info(" ---> Moving [{}] to [{}] without touching timestamps", sourceFile, destFile);
        Files.move(sourceFile, destFile, StandardCopyOption.ATOMIC_MOVE);

        // remove_deleted should drop the document for the old path
        countTestHelper(
                new ESSearchRequest()
                        .withIndex(getCrawlerName() + FsCrawlerUtil.INDEX_SUFFIX_DOCS)
                        .withESQuery(new ESTermQuery("path.virtual.fulltext", sourceDirName)),
                0L,
                currentTestResourceDir);

        // Same crawl pass already evaluated both paths (processDirectory). When issue 1300 is fixed the
        // document is already under the new path; waiting longer does not help. Assert immediately for CI.
        String docsIndex = getCrawlerName() + FsCrawlerUtil.INDEX_SUFFIX_DOCS;
        refresh(docsIndex);
        ESSearchResponse response = client.search(
                new ESSearchRequest().withIndex(docsIndex).withESQuery(new ESTermQuery("file.filename", filename)));
        Assertions.assertThat(response.getTotalHits())
                .as("#1300: file moved between watched dirs without touch must stay indexed (OS=%s)", OsValidator.OS)
                .isEqualTo(1L);

        DocumentContext document =
                JsonUtil.parseJsonAsDocumentContext(response.getHits().get(0).getSource());
        String expectedVirtual =
                OsValidator.WINDOWS ? "\\" + destDirName + "\\" + filename : "/" + destDirName + "/" + filename;
        Assertions.assertThat((String) document.read("$.path.virtual")).isEqualTo(expectedVirtual);
    }
}
