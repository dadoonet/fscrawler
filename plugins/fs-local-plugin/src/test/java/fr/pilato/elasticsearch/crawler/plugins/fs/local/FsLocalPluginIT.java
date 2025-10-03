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

import static org.assertj.core.api.Assertions.assertThat;

public class FsLocalPluginIT extends AbstractFSCrawlerTestCase {
    private static final Logger logger = LogManager.getLogger();

    private static Path createFile(String objectName, String object) throws IOException {
        logger.info("Create fake content [{}]; [{}]", objectName, object);
        Path file = rootTmpDir.resolve(objectName);
        Files.writeString(file, object);
        return file;
    }

    @Test
    public void readFile() throws Exception {
        String text = "Hello Foo world!";
        Path bucket = createFile("foo.txt", text);
        createFile("bar.txt", "This one should be ignored.");

        logger.info("Starting Test with bucket [{}]", bucket);
        try (FsCrawlerExtensionFsProvider provider = new FsLocalPlugin.FsCrawlerExtensionFsProviderLocal()) {
            provider.settings("{\n" +
                    "  \"type\": \"local\",\n" +
                    "  \"local\": {\n" +
                    "    \"url\": \"" + bucket.toString().replace("\\", "\\\\") + "\"\n" +
                    "  }\n" +
                    "}");
            provider.start();
            InputStream inputStream = provider.readFile();
            String object = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            assertThat(object).isEqualTo(text);
            assertThat(provider.getFilename()).isEqualTo("foo.txt");
            assertThat(provider.getFilesize()).isEqualTo(16L);
        }
    }

    @Test
    public void readFileWithVirtualPath() throws Exception {
        String text = "Hello virtual path world!";
        // Create subdirectory structure: rootTmpDir/path/to/foo.txt
        Path subDir = rootTmpDir.resolve("path").resolve("to");
        Files.createDirectories(subDir);
        Path file = subDir.resolve("foo.txt");
        Files.writeString(file, text);

        logger.info("Starting Test with file [{}] and root [{}]", file, rootTmpDir);
        try (FsCrawlerExtensionFsProvider provider = new FsLocalPlugin.FsCrawlerExtensionFsProviderLocal()) {
            // Simulate DocumentApi injecting _fs_url
            provider.settings("{\n" +
                    "  \"type\": \"local\",\n" +
                    "  \"local\": {\n" +
                    "    \"url\": \"" + file.toString().replace("\\", "\\\\") + "\"\n" +
                    "  },\n" +
                    "  \"_fs_url\": \"" + rootTmpDir.toString().replace("\\", "\\\\") + "\"\n" +
                    "}");
            provider.start();
            InputStream inputStream = provider.readFile();
            String object = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            assertThat(object).isEqualTo(text);
            // The virtual path should be /path/to/foo.txt (relative to fs.url)
            String expectedVirtualPath = "/path/to/foo.txt".replace("/", java.io.File.separator);
            assertThat(provider.getFilename()).isEqualTo(expectedVirtualPath);
            assertThat(provider.getFilesize()).isEqualTo(text.length());
        }
    }
}
