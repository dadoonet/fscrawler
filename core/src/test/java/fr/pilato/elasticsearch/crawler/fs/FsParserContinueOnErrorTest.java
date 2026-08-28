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
package fr.pilato.elasticsearch.crawler.fs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carrotsearch.randomizedtesting.jupiter.RandomizedTest;
import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.beans.FileAbstractModel;
import fr.pilato.elasticsearch.crawler.fs.beans.ScanStatistic;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerExtensionFsProvider;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPluginException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Test case for <a href="https://github.com/dadoonet/fscrawler/issues/331">#331</a>: {@code fs.continue_on_error} skips
 * a file that cannot be opened instead of aborting the crawl.
 */
class FsParserContinueOnErrorTest extends AbstractFSCrawlerTestCase {

    @Test
    void skips_unreadable_file_when_continue_on_error_enabled() throws Exception {
        Object result = indexUnreadableFile(true);

        assertThat(result).isNull();
    }

    @Test
    void rethrows_when_continue_on_error_disabled() {
        assertThatThrownBy(() -> indexUnreadableFile(false))
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(FsCrawlerPluginException.class);
    }

    private Object indexUnreadableFile(boolean continueOnError) throws Exception {
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.setName(jobName);
        fsSettings.getFs().setContinueOnError(continueOnError);

        String filename = RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 6, 12)
                        .toLowerCase(Locale.ROOT)
                + ".txt";
        String dirname = testTmpDir.toString();
        byte[] content = RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 10, 20)
                .getBytes(StandardCharsets.UTF_8);
        FileAbstractModel child = new FileAbstractModel(
                filename,
                true,
                Instant.now(),
                Instant.now(),
                Instant.now(),
                "txt",
                dirname,
                dirname + "/" + filename,
                content.length,
                null,
                null,
                0,
                null,
                null);

        FsParser parser = new FsParser(fsSettings, testTmpDir, null, null, 1, false, new UnreadableFileFsProvider());
        Method indexFileWithStreams = FsParser.class.getDeclaredMethod(
                "indexFileWithStreams",
                FileAbstractModel.class,
                ScanStatistic.class,
                String.class,
                FileAbstractModel.class,
                int.class);
        indexFileWithStreams.setAccessible(true);
        return indexFileWithStreams.invoke(parser, child, new ScanStatistic(dirname), dirname, null, 0);
    }

    /**
     * Mimics a local-FS permission denied: {@code FileInputStream} fails with {@link java.io.FileNotFoundException},
     * which {@code FsLocalPlugin} wraps in {@link FsCrawlerPluginException}.
     */
    private static class UnreadableFileFsProvider implements FsCrawlerExtensionFsProvider {
        @Override
        public void start(FsSettings fsSettings, Map<String, Object> overlay) {
            // Stub: no overlay to apply.
        }

        @Override
        public void stop() {
            // Stub: no background work.
        }

        @Override
        public String getType() {
            return "unreadable-fs";
        }

        @Override
        public InputStream readFile() {
            throw new FsCrawlerPluginException("not used");
        }

        @Override
        public Doc createDocument() {
            throw new FsCrawlerPluginException("not used");
        }

        @Override
        public boolean supportsCrawling() {
            return true;
        }

        @Override
        public void closeConnection() {
            // Stub: no remote connection.
        }

        @Override
        public boolean exists(String directory) {
            return true;
        }

        @Override
        public Collection<FileAbstractModel> getFiles(String directory) {
            return List.of();
        }

        @Override
        public InputStream getInputStream(FileAbstractModel file) {
            throw new FsCrawlerPluginException("Can not get input stream for " + file.getFullpath());
        }

        @Override
        public void closeInputStream(InputStream inputStream) {
            // Stub: no stream was opened.
        }

        @Override
        public void close() {
            // Stub: no resources.
        }
    }
}
