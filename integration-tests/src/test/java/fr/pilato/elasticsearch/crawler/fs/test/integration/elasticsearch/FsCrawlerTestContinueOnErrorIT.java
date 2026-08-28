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
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.client.ESTermQuery;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Test case for <a href="https://github.com/dadoonet/fscrawler/issues/331">#331</a>: crawling continues after a
 * permission denied error when {@code fs.continue_on_error} is true.
 */
class FsCrawlerTestContinueOnErrorIT extends AbstractFsCrawlerITCase {
    private static final Logger logger = LogManager.getLogger();

    @Test
    void continue_on_error() throws Exception {
        Assumptions.assumeThat(
                        FileSystems.getDefault().supportedFileAttributeViews().contains("posix"))
                .describedAs("This test can only run on Posix systems")
                .isTrue();

        String readableName = RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 6, 12)
                        .toLowerCase(Locale.ROOT)
                + ".txt";
        String unreadableName = "z_"
                + RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 6, 12)
                        .toLowerCase(Locale.ROOT)
                + ".txt";
        String readableContent = RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 20, 80);
        String unreadableContent = RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 20, 80);

        Files.deleteIfExists(currentTestResourceDir.resolve("roottxtfile.txt"));
        Path readableFile = currentTestResourceDir.resolve(readableName);
        Path unreadableFile = currentTestResourceDir.resolve(unreadableName);
        Files.writeString(readableFile, readableContent, StandardCharsets.UTF_8);
        Files.writeString(unreadableFile, unreadableContent, StandardCharsets.UTF_8);

        logger.info(" ---> Removing all permissions from [{}]", unreadableFile);
        Files.getFileAttributeView(unreadableFile, PosixFileAttributeView.class)
                .setPermissions(EnumSet.noneOf(PosixFilePermission.class));

        // Root (and some containers) can still open mode 000 files. Skip rather than assert a false positive.
        boolean readableAsCurrentUser;
        try (FileInputStream in = new FileInputStream(unreadableFile.toFile())) {
            in.read();
            readableAsCurrentUser = true;
        } catch (Exception e) {
            readableAsCurrentUser = false;
        }
        Assumptions.assumeThat(readableAsCurrentUser)
                .describedAs("Opening the unreadable file must fail (not running as root)")
                .isFalse();

        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setContinueOnError(true);
        crawler = startCrawler(fsSettings);

        String docsIndex = getCrawlerName() + FsCrawlerUtil.INDEX_SUFFIX_DOCS;
        countTestHelper(new ESSearchRequest().withIndex(docsIndex), 1L, currentTestResourceDir);

        ESSearchResponse readableHits = client.search(
                new ESSearchRequest().withIndex(docsIndex).withESQuery(new ESTermQuery("file.filename", readableName)));
        Assertions.assertThat(readableHits.getTotalHits()).isEqualTo(1L);

        ESSearchResponse unreadableHits = client.search(new ESSearchRequest()
                .withIndex(docsIndex)
                .withESQuery(new ESTermQuery("file.filename", unreadableName)));
        Assertions.assertThat(unreadableHits.getTotalHits()).isZero();
    }
}
