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
package fr.pilato.elasticsearch.crawler.plugins.password.disk;

import com.carrotsearch.randomizedtesting.jupiter.RandomizedTest;
import fr.pilato.elasticsearch.crawler.fs.settings.Fs;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.Passwords;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class PasswordDiskPluginTest extends AbstractFSCrawlerTestCase {

    @Test
    void yieldsExactSidecarThenParentThenRoot() throws Exception {
        Path fsRoot = Files.createDirectories(rootTmpDir.resolve(randomName()));
        Path diskRoot = Files.createDirectories(rootTmpDir.resolve(randomName()));
        Path document = writeDocument(fsRoot.resolve("foo").resolve(randomName() + ".txt"));

        String exactPassword = randomPassword();
        String parentPassword = randomPassword();
        String rootPassword = randomPassword();

        writeUtf8(
                diskRoot.resolve("foo").resolve(document.getFileName() + ".password"), "\n  " + exactPassword + "  \n");
        writeUtf8(diskRoot.resolve("foo").resolve(".password"), parentPassword + "\n");
        writeUtf8(diskRoot.resolve(".password"), rootPassword + "\n");

        PasswordDiskPlugin.Provider provider = new PasswordDiskPlugin.Provider();
        provider.start(settings(fsRoot, diskRoot), type -> null);

        try (var session = provider.open(document.toString())) {
            Assertions.assertThat(session.next()).contains(exactPassword);
            Assertions.assertThat(session.next()).contains(parentPassword);
            Assertions.assertThat(session.next()).contains(rootPassword);
            Assertions.assertThat(session.next()).isEmpty();
        } finally {
            provider.close();
        }
    }

    @Test
    void defaultsDiskRootToFsUrl() throws Exception {
        Path fsRoot = Files.createDirectories(rootTmpDir.resolve(randomName()));
        Path document = writeDocument(fsRoot.resolve("nested").resolve(randomName() + ".pdf"));
        String password = randomPassword();
        writeUtf8(fsRoot.resolve("nested").resolve(document.getFileName() + ".password"), password + "\n");

        PasswordDiskPlugin.Provider provider = new PasswordDiskPlugin.Provider();
        provider.start(settings(fsRoot, null), type -> null);

        try (var session = provider.open(document.toString())) {
            Assertions.assertThat(session.next()).contains(password);
            Assertions.assertThat(session.next()).isEmpty();
        } finally {
            provider.close();
        }
    }

    @Test
    void fallsBackToFilenameOnlyWhenDocumentIsOutsideFsRoot() throws Exception {
        Path fsRoot = Files.createDirectories(rootTmpDir.resolve(randomName()));
        Path diskRoot = Files.createDirectories(rootTmpDir.resolve(randomName()));
        Path outsideRoot = Files.createDirectories(rootTmpDir.resolve(randomName()));
        Path document = writeDocument(outsideRoot.resolve("deep").resolve(randomName() + ".docx"));
        String password = randomPassword();
        writeUtf8(diskRoot.resolve(document.getFileName() + ".password"), password + "\n");

        PasswordDiskPlugin.Provider provider = new PasswordDiskPlugin.Provider();
        provider.start(settings(fsRoot, diskRoot), type -> null);

        try (var session = provider.open(document.toString())) {
            Assertions.assertThat(session.next()).contains(password);
            Assertions.assertThat(session.next()).isEmpty();
        } finally {
            provider.close();
        }
    }

    @Test
    void skipsMissingAndEmptyCandidates() throws Exception {
        Path fsRoot = Files.createDirectories(rootTmpDir.resolve(randomName()));
        Path diskRoot = Files.createDirectories(rootTmpDir.resolve(randomName()));
        Path document = writeDocument(fsRoot.resolve("branch").resolve(randomName() + ".xlsx"));
        String rootPassword = randomPassword();

        writeUtf8(diskRoot.resolve("branch").resolve(".password"), "\n \t \n");
        writeUtf8(diskRoot.resolve(".password"), "\n" + rootPassword + "\n");

        PasswordDiskPlugin.Provider provider = new PasswordDiskPlugin.Provider();
        provider.start(settings(fsRoot, diskRoot), type -> null);

        try (var session = provider.open(document.toString())) {
            Assertions.assertThat(session.next()).contains(rootPassword);
            Assertions.assertThat(session.next()).isEmpty();
        } finally {
            provider.close();
        }
    }

    private FsSettings settings(Path fsRoot, Path diskRoot) {
        Fs fs = new Fs();
        fs.setUrl(fsRoot.toString());

        Map<String, Object> disk = new LinkedHashMap<>();
        if (diskRoot != null) {
            disk.put("url", diskRoot.toString());
        }

        Passwords passwords = new Passwords();
        passwords.setProvider("disk");
        passwords.setProviders(Map.of("disk", disk));

        FsSettings settings = new FsSettings();
        settings.setFs(fs);
        settings.setPasswords(passwords);
        return settings;
    }

    private Path writeDocument(Path document) throws Exception {
        Files.createDirectories(document.getParent());
        return Files.writeString(document, randomPassword(), StandardCharsets.UTF_8);
    }

    private static void writeUtf8(Path path, String content) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private String randomName() {
        return RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 6, 12)
                .toLowerCase(Locale.ROOT);
    }

    private String randomPassword() {
        return RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 8, 16);
    }
}
