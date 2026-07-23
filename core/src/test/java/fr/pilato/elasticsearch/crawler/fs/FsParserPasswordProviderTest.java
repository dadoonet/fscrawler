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

import com.carrotsearch.randomizedtesting.jupiter.RandomizedTest;
import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.beans.FileAbstractModel;
import fr.pilato.elasticsearch.crawler.fs.beans.ScanStatistic;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import fr.pilato.elasticsearch.crawler.fs.tika.TikaDocParser;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerExtensionFsProvider;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerExtensionPasswordProvider;
import fr.pilato.elasticsearch.crawler.plugins.PasswordProviderLookup;
import fr.pilato.elasticsearch.crawler.plugins.PasswordSession;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FsParserPasswordProviderTest extends AbstractFSCrawlerTestCase {

    @Test
    void crawl_uses_reopenable_stream_supplier_and_password_provider() throws Exception {
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.setName(jobName);

        String baseName = RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 6, 12)
                .toLowerCase(Locale.ROOT);
        byte[] content = RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 10, 20)
                .getBytes(StandardCharsets.UTF_8);
        String dirname = testTmpDir.toString();
        String filename = baseName + ".pdf";
        FileAbstractModel child = new FileAbstractModel(
                filename,
                true,
                Instant.now(),
                Instant.now(),
                Instant.now(),
                "pdf",
                dirname,
                dirname + "/" + filename,
                content.length,
                null,
                null,
                0,
                null,
                null);

        RecordingFsProvider crawlerPlugin = new RecordingFsProvider(content);
        FsParser parser = new FsParser(fsSettings, testTmpDir, null, null, 1, false, crawlerPlugin);
        RecordingTikaDocParser tikaDocParser = new RecordingTikaDocParser(fsSettings);
        setField(parser, "tikaDocParser", tikaDocParser);

        RecordingPasswordProvider passwordProvider = new RecordingPasswordProvider();
        setField(parser, "passwordProvider", passwordProvider);

        Method indexFileWithStreams = FsParser.class.getDeclaredMethod(
                "indexFileWithStreams",
                FileAbstractModel.class,
                ScanStatistic.class,
                String.class,
                FileAbstractModel.class,
                int.class);
        indexFileWithStreams.setAccessible(true);

        Object result = indexFileWithStreams.invoke(parser, child, new ScanStatistic(dirname), dirname, null, 0);

        assertThat(result).isEqualTo(0);
        assertThat(tikaDocParser.reopenGenerateCalls.get()).isEqualTo(1);
        assertThat(tikaDocParser.directGenerateCalls.get()).isZero();
        assertThat(tikaDocParser.lastExplicitPassword).isNull();
        assertThat(tikaDocParser.lastProvider).isSameAs(passwordProvider);
        assertThat(tikaDocParser.reopenedStreamBytes).containsExactly(content);
        assertThat(crawlerPlugin.openCalls.get()).isEqualTo(1);
        assertThat(crawlerPlugin.closeCalls.get()).isEqualTo(1);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static class RecordingTikaDocParser extends TikaDocParser {
        private final AtomicInteger directGenerateCalls = new AtomicInteger();
        private final AtomicInteger reopenGenerateCalls = new AtomicInteger();
        private FsCrawlerExtensionPasswordProvider lastProvider;
        private String lastExplicitPassword;
        private byte[] reopenedStreamBytes;

        private RecordingTikaDocParser(FsSettings fsSettings) {
            super(fsSettings);
        }

        @Override
        public void generate(InputStream inputStream, Doc doc, long filesize) throws IOException {
            directGenerateCalls.incrementAndGet();
            reopenedStreamBytes = inputStream.readAllBytes();
            doc.setContent("direct");
        }

        @Override
        public void generate(
                InputStreamSupplier reopen,
                Doc doc,
                long filesize,
                String explicitPassword,
                FsCrawlerExtensionPasswordProvider provider)
                throws IOException {
            reopenGenerateCalls.incrementAndGet();
            lastExplicitPassword = explicitPassword;
            lastProvider = provider;
            try (InputStream inputStream = reopen.open()) {
                reopenedStreamBytes = inputStream.readAllBytes();
            }
            doc.setContent("reopen");
        }
    }

    private static class RecordingPasswordProvider implements FsCrawlerExtensionPasswordProvider {
        @Override
        public String getType() {
            return "recording";
        }

        @Override
        public void start(FsSettings settings, PasswordProviderLookup lookup) {
            // Recording stub does not use job settings.
        }

        @Override
        public PasswordSession open(String documentPath) {
            return new PasswordSession() {
                @Override
                public Optional<String> next() {
                    return Optional.empty();
                }

                @Override
                public void close() {
                    // Recording stub session has no resources.
                }
            };
        }

        @Override
        public void close() {
            // Recording stub provider has no resources.
        }
    }

    private static class RecordingFsProvider implements FsCrawlerExtensionFsProvider {
        private final byte[] content;
        private final AtomicInteger openCalls = new AtomicInteger();
        private final AtomicInteger closeCalls = new AtomicInteger();

        private RecordingFsProvider(byte[] content) {
            this.content = content;
        }

        @Override
        public void start(FsSettings fsSettings, String restSettings) {
            // Recording stub ignores REST settings.
        }

        @Override
        public void stop() {
            // Recording stub has no background work to stop.
        }

        @Override
        public String getType() {
            return "recording-fs";
        }

        @Override
        public InputStream readFile() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public Doc createDocument() {
            return new Doc();
        }

        @Override
        public boolean supportsCrawling() {
            return true;
        }

        @Override
        public void closeConnection() {
            // Recording stub keeps no remote connection.
        }

        @Override
        public boolean exists(String directory) {
            return true;
        }

        @Override
        public Collection<FileAbstractModel> getFiles(String directory) {
            return java.util.List.of();
        }

        @Override
        public InputStream getInputStream(FileAbstractModel file) {
            openCalls.incrementAndGet();
            return new FilterInputStream(new ByteArrayInputStream(content)) {};
        }

        @Override
        public void closeInputStream(InputStream inputStream) {
            closeCalls.incrementAndGet();
            try {
                inputStream.close();
            } catch (IOException e) {
                throw new IllegalStateException("Failed to close recording input stream", e);
            }
        }

        @Override
        public void close() {
            // Recording stub provider has no resources beyond closed streams.
        }
    }
}
