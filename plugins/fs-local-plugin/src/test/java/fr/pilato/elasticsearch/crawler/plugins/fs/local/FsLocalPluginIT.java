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
package fr.pilato.elasticsearch.crawler.plugins.fs.local;

import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerExtensionFsProvider;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

public class FsLocalPluginIT extends AbstractFSCrawlerTestCase {
    private static final Logger logger = LogManager.getLogger();

    private static Path createFile(String objectName, String object) throws IOException {
        return createFile(rootTmpDir, objectName, object);
    }

    private static Path createFile(Path dir, String objectName, String object) throws IOException {
        logger.info("Create fake content [{}] in [{}]; [{}]", objectName, dir, object);
        Path file = dir.resolve(objectName);
        Files.writeString(file, object);
        return file;
    }

    @Test
    public void readFileWithFullPath() throws Exception {
        String text = "Hello Foo world!";
        Path fileName = createFile("foo.txt", text);
        createFile("bar.txt", "This one should be ignored.");

        logger.info("Starting Test with bucket [{}]", fileName);
        try (FsCrawlerExtensionFsProvider provider = new FsLocalPlugin.FsCrawlerExtensionFsProviderLocal()) {
            FsSettings fsSettings = FsSettingsLoader.load();
            fsSettings.getFs().setUrl(rootTmpDir.toString());
            provider.start(fsSettings, "{\n" +
                    "  \"type\": \"local\",\n" +
                    "  \"local\": {\n" +
                    "    \"url\": \"" + fileName.toString().replace("\\", "\\\\") + "\"\n" +
                    "  }\n" +
                    "}");
            InputStream inputStream = provider.readFile();
            String object = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            assertThat(object).isEqualTo(text);
            Doc doc = provider.createDocument();
            assertThat(doc.getFile().getFilename()).isEqualTo("foo.txt");
            assertThat(doc.getFile().getFilesize()).isEqualTo(16L);
            assertThat(doc.getPath().getVirtual()).isEqualTo("/");
            assertThat(doc.getPath().getReal()).isEqualTo(fileName.toAbsolutePath().toString());
        }
    }

    @Test
    public void readFileWithRelativePath() throws Exception {
        String text = "Hello Foo world!";
        Path fileName = createFile("foo.txt", text);

        logger.info("Starting Test with bucket [{}]", fileName);
        try (FsCrawlerExtensionFsProvider provider = new FsLocalPlugin.FsCrawlerExtensionFsProviderLocal()) {
            FsSettings fsSettings = FsSettingsLoader.load();
            fsSettings.getFs().setUrl(rootTmpDir.toString());
            provider.start(fsSettings, "{\n" +
                    "  \"type\": \"local\",\n" +
                    "  \"local\": {\n" +
                    "    \"url\": \"foo.txt\"\n" +
                    "  }\n" +
                    "}");
            InputStream inputStream = provider.readFile();
            String object = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            assertThat(object).isEqualTo(text);
            Doc doc = provider.createDocument();
            assertThat(doc.getFile().getFilename()).isEqualTo("foo.txt");
            assertThat(doc.getFile().getFilesize()).isEqualTo(16L);
            assertThat(doc.getPath().getVirtual()).isEqualTo("/");
            assertThat(doc.getPath().getReal()).isEqualTo(fileName.toAbsolutePath().toString());
        }
    }

    @Test
    public void readFileWithFullPathOutsideRootDir() throws Exception {
        Path rootDir = rootTmpDir.resolve("root-dir");
        Files.createDirectory(rootDir);
        Path outsideRootDir = rootTmpDir.resolve("outside-root-dir");
        Files.createDirectory(outsideRootDir);

        String text = "Hello Foo world!";
        Path fileName = createFile(outsideRootDir, "foo.txt", text);

        logger.info("Starting Test with bucket [{}]", fileName);
        try (FsCrawlerExtensionFsProvider provider = new FsLocalPlugin.FsCrawlerExtensionFsProviderLocal()) {
            FsSettings fsSettings = FsSettingsLoader.load();
            fsSettings.getFs().setUrl(rootDir.toString());
            assertThatThrownBy(() -> provider.start(fsSettings, "{\n" +
                    "  \"type\": \"local\",\n" +
                    "  \"local\": {\n" +
                    "    \"url\": \"" + fileName.toString().replace("\\", "\\\\") + "\"\n" +
                    "  }\n" +
                    "}"))
                    .isInstanceOf(FsCrawlerIllegalConfigurationException.class)
                    .hasMessageContaining("is not within");
        }
    }
}
