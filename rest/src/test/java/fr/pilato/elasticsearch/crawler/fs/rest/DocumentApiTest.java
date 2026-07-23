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
package fr.pilato.elasticsearch.crawler.fs.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carrotsearch.randomizedtesting.jupiter.RandomizedTest;
import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentService;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.settings.Passwords;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import fr.pilato.elasticsearch.crawler.fs.tika.TikaDocParser;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerExtensionFsProvider;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerExtensionPasswordProvider;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPluginsManager;
import fr.pilato.elasticsearch.crawler.plugins.PasswordProviderLookup;
import fr.pilato.elasticsearch.crawler.plugins.PasswordSession;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DocumentApiTest extends AbstractFSCrawlerTestCase {

    private static final String PASSWORD_PROVIDER_TYPE = "recording-password-provider";
    private static final String FS_PROVIDER_TYPE = "recording-fs-provider";

    private FsSettings settings;
    private FsCrawlerPluginsManager pluginsManager;
    private RecordingTikaDocParser tikaDocParser;
    private DocumentApi documentApi;
    private RecordingPasswordProvider passwordProvider;

    @BeforeEach
    void setUp() {
        settings = FsSettingsLoader.load();
        settings.setName(jobName);
        settings.getFs().setTempDir(testTmpDir.toString());
        pluginsManager = mock(FsCrawlerPluginsManager.class);
        tikaDocParser = new RecordingTikaDocParser(settings);
        documentApi = new DocumentApi(settings, mock(FsCrawlerDocumentService.class), pluginsManager, tikaDocParser);
        passwordProvider = new RecordingPasswordProvider();
    }

    @Test
    void multipartUploadPrefersFormPasswordAndSkipsJobPasswordProvider() throws Exception {
        configurePasswordProvider();

        byte[] content = randomContent();
        String formPassword = randomPassword();
        String headerPassword = randomPassword();
        String queryPassword = randomPassword();
        String filename = randomFilename("pdf");

        UploadResponse response = documentApi.addDocument(
                null,
                "true",
                null,
                null,
                null,
                null,
                null,
                null,
                formPassword,
                headerPassword,
                queryPassword,
                null,
                new ByteArrayInputStream(content),
                formData(filename, content.length));

        assertThat(response.isOk()).isTrue();
        assertThat(tikaDocParser.reopenGenerateCalls.get()).isEqualTo(1);
        assertThat(tikaDocParser.lastExplicitPassword).isEqualTo(formPassword);
        assertThat(tikaDocParser.lastProvider).isNull();
        assertThat(tikaDocParser.reopenedPayloads).hasSize(2);
        assertThat(tikaDocParser.reopenedPayloads.get(0)).containsExactly(content);
        assertThat(tikaDocParser.reopenedPayloads.get(1)).containsExactly(content);
        verify(pluginsManager, never()).findPasswordProvider(anyString());
    }

    @Test
    void multipartUploadUsesJobPasswordProviderWhenRequestPasswordMissing() throws Exception {
        configurePasswordProvider();
        when(pluginsManager.findPasswordProvider(PASSWORD_PROVIDER_TYPE)).thenReturn(passwordProvider);

        byte[] content = randomContent();
        String filename = randomFilename("docx");

        UploadResponse response = documentApi.addDocument(
                null,
                "true",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new ByteArrayInputStream(content),
                formData(filename, content.length));

        assertThat(response.isOk()).isTrue();
        assertThat(tikaDocParser.reopenGenerateCalls.get()).isEqualTo(1);
        assertThat(tikaDocParser.lastExplicitPassword).isNull();
        assertThat(tikaDocParser.lastProvider).isSameAs(passwordProvider);
        assertThat(tikaDocParser.reopenedPayloads).hasSize(2);
        assertThat(tikaDocParser.reopenedPayloads.get(0)).containsExactly(content);
        assertThat(tikaDocParser.reopenedPayloads.get(1)).containsExactly(content);
        verify(pluginsManager).findPasswordProvider(PASSWORD_PROVIDER_TYPE);
    }

    @Test
    void multipartUploadDeletesSpoolFileWhenTransferFails() throws Exception {
        String filename = randomFilename("pdf");
        byte[] partialContent = RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 1024, 1024)
                .getBytes(StandardCharsets.UTF_8);
        long declaredSize = 65L * 1024;

        assertThatThrownBy(() -> documentApi.addDocument(
                        null,
                        "true",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new ThrowingMultipartInputStream(partialContent),
                        formData(filename, declaredSize)))
                .isInstanceOf(IOException.class)
                .hasMessage("multipart transfer failed");

        assertThat(spooledMultipartFiles()).isEmpty();
    }

    @Test
    void multipartUploadRequiresConfiguredTempDirWhenSpoolingToDisk() {
        settings.getFs().setTempDir(null);
        documentApi = new DocumentApi(settings, mock(FsCrawlerDocumentService.class), pluginsManager, tikaDocParser);

        String filename = randomFilename("pdf");
        byte[] content = RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 1024, 1024)
                .getBytes(StandardCharsets.UTF_8);
        long declaredSize = 65L * 1024;

        assertThatThrownBy(() -> documentApi.addDocument(
                        null,
                        "true",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new ByteArrayInputStream(content),
                        formData(filename, declaredSize)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("tempDir");
    }

    @Test
    void thirdPartyUploadPrefersHeaderPasswordAndSkipsJobPasswordProvider() {
        configurePasswordProvider();

        byte[] content = randomContent();
        String filename = randomFilename("pdf");
        String headerPassword = randomPassword();
        String queryPassword = randomPassword();
        RecordingFsProvider fsProvider = new RecordingFsProvider(filename, content);
        when(pluginsManager.findFsProvider(FS_PROVIDER_TYPE)).thenReturn(fsProvider);

        UploadResponse response = documentApi.addDocumentFrom3rdParty(
                null,
                "true",
                null,
                null,
                null,
                null,
                headerPassword,
                queryPassword,
                new ByteArrayInputStream(("{\"type\":\"" + FS_PROVIDER_TYPE + "\"}").getBytes(StandardCharsets.UTF_8)));

        assertThat(response.isOk()).isTrue();
        assertThat(tikaDocParser.reopenGenerateCalls.get()).isEqualTo(1);
        assertThat(tikaDocParser.lastExplicitPassword).isEqualTo(headerPassword);
        assertThat(tikaDocParser.lastProvider).isNull();
        assertThat(tikaDocParser.reopenedPayloads).hasSize(2);
        assertThat(tikaDocParser.reopenedPayloads.get(0)).containsExactly(content);
        assertThat(tikaDocParser.reopenedPayloads.get(1)).containsExactly(content);
        // Two reopen calls from RecordingTikaDocParser (no accessibility probe).
        assertThat(fsProvider.readFileCalls.get()).isEqualTo(2);
        verify(pluginsManager, never()).findPasswordProvider(anyString());
    }

    @Test
    void thirdPartyUploadUsesJobPasswordProviderAndReopensProviderStream() {
        configurePasswordProvider();
        when(pluginsManager.findPasswordProvider(PASSWORD_PROVIDER_TYPE)).thenReturn(passwordProvider);

        byte[] content = randomContent();
        String filename = randomFilename("xlsx");
        RecordingFsProvider fsProvider = new RecordingFsProvider(filename, content);
        when(pluginsManager.findFsProvider(FS_PROVIDER_TYPE)).thenReturn(fsProvider);

        UploadResponse response = documentApi.addDocumentFrom3rdParty(
                null,
                "true",
                null,
                null,
                null,
                null,
                null,
                null,
                new ByteArrayInputStream(("{\"type\":\"" + FS_PROVIDER_TYPE + "\"}").getBytes(StandardCharsets.UTF_8)));

        assertThat(response.isOk()).isTrue();
        assertThat(tikaDocParser.reopenGenerateCalls.get()).isEqualTo(1);
        assertThat(tikaDocParser.lastExplicitPassword).isNull();
        assertThat(tikaDocParser.lastProvider).isSameAs(passwordProvider);
        assertThat(tikaDocParser.reopenedPayloads).hasSize(2);
        assertThat(tikaDocParser.reopenedPayloads.get(0)).containsExactly(content);
        assertThat(tikaDocParser.reopenedPayloads.get(1)).containsExactly(content);
        // Two reopen calls from RecordingTikaDocParser (no accessibility probe).
        assertThat(fsProvider.readFileCalls.get()).isEqualTo(2);
        verify(pluginsManager).findPasswordProvider(PASSWORD_PROVIDER_TYPE);
    }

    private void configurePasswordProvider() {
        Passwords passwords = new Passwords();
        passwords.setProvider(PASSWORD_PROVIDER_TYPE);
        settings.setPasswords(passwords);
    }

    private String randomFilename(String extension) {
        return RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 6, 12)
                        .toLowerCase(Locale.ROOT)
                + "." + extension;
    }

    private String randomPassword() {
        return RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 8, 14);
    }

    private byte[] randomContent() {
        return RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 20, 40)
                .getBytes(StandardCharsets.UTF_8);
    }

    private FormDataContentDisposition formData(String filename, long size) {
        FormDataContentDisposition disposition = mock(FormDataContentDisposition.class);
        when(disposition.getFileName()).thenReturn(filename);
        when(disposition.getSize()).thenReturn(Long.valueOf(size));
        return disposition;
    }

    private List<Path> spooledMultipartFiles() throws IOException {
        try (var entries = Files.list(testTmpDir)) {
            return entries.filter(path -> path.getFileName().toString().startsWith("fscrawler-rest-"))
                    .toList();
        }
    }

    private static class RecordingTikaDocParser extends TikaDocParser {
        private final AtomicInteger reopenGenerateCalls = new AtomicInteger();
        private final List<byte[]> reopenedPayloads = new ArrayList<>();
        private FsCrawlerExtensionPasswordProvider lastProvider;
        private String lastExplicitPassword;

        private RecordingTikaDocParser(FsSettings fsSettings) {
            super(fsSettings);
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
            try (InputStream first = reopen.open();
                    InputStream second = reopen.open()) {
                reopenedPayloads.add(first.readAllBytes());
                reopenedPayloads.add(second.readAllBytes());
            }
            doc.setContent("parsed");
        }
    }

    private static class RecordingPasswordProvider implements FsCrawlerExtensionPasswordProvider {
        @Override
        public String getType() {
            return PASSWORD_PROVIDER_TYPE;
        }

        @Override
        public void start(FsSettings settings, PasswordProviderLookup lookup) {
            // No configuration needed for this test double.
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
                    // No resources to release.
                }
            };
        }

        @Override
        public void close() {
            // No resources to release.
        }
    }

    private static class ThrowingMultipartInputStream extends InputStream {
        private final byte[] content;
        private int index;

        private ThrowingMultipartInputStream(byte[] content) {
            this.content = content;
        }

        @Override
        public int read() throws IOException {
            if (index < content.length) {
                return content[index++] & 0xff;
            }

            throw new IOException("multipart transfer failed");
        }
    }

    private static class RecordingFsProvider implements FsCrawlerExtensionFsProvider {
        private final String filename;
        private final byte[] content;
        private final AtomicInteger readFileCalls = new AtomicInteger();

        private RecordingFsProvider(String filename, byte[] content) {
            this.filename = filename;
            this.content = content;
        }

        @Override
        public void start(FsSettings fsSettings, String restSettings) {
            // No configuration needed for this test double.
        }

        @Override
        public void stop() {
            // No resources to release.
        }

        @Override
        public String getType() {
            return FS_PROVIDER_TYPE;
        }

        @Override
        public InputStream readFile() {
            readFileCalls.incrementAndGet();
            return new ByteArrayInputStream(content);
        }

        @Override
        public Doc createDocument() {
            Doc doc = new Doc();
            doc.getFile().setFilename(filename);
            doc.getFile().setFilesize((long) content.length);
            doc.getPath().setVirtual(filename);
            doc.getPath().setReal(filename);
            return doc;
        }

        @Override
        public void close() {
            // No resources to release.
        }
    }
}
