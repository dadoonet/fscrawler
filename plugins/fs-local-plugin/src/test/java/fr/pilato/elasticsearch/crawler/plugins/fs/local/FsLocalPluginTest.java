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

import fr.pilato.elasticsearch.crawler.fs.beans.FileAbstractModel;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerExtensionFsProvider;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Iterator;

import static org.assertj.core.api.Assertions.assertThat;

public class FsLocalPluginTest {

    private FsCrawlerExtensionFsProvider localPlugin;

    @Before
    public void setUp() {
        FsSettings fsSettings = FsSettingsLoader.load();
        FsLocalPlugin.FsCrawlerExtensionFsProviderLocal plugin = new FsLocalPlugin.FsCrawlerExtensionFsProviderLocal();
        plugin.start(fsSettings, "{}");
        localPlugin = plugin;
    }

    @Test
    public void getFiles() throws Exception {
        Path tempDir = Files.createTempDirectory("testGetFiles");
        // File1 is created first
        Path tempFile1 = Files.createFile(tempDir.resolve("file1.txt"));

        // Wait for 100ms to make sure file2 is created after
        Thread.sleep(100);

        // Then file2
        Path tempFile2 = Files.createFile(tempDir.resolve("file2.txt"));

        Collection<FileAbstractModel> files = localPlugin.getFiles(tempDir.toString());

        assertThat(files).hasSize(2);
        Iterator<FileAbstractModel> iterator = files.iterator();
        // Files should be sorted by modification time (most recent first)
        assertThat(iterator.next().getName()).isEqualTo(tempFile2.toFile().getName());
        assertThat(iterator.next().getName()).isEqualTo(tempFile1.toFile().getName());

        // Clean up
        Files.delete(tempFile1);
        Files.delete(tempFile2);
        Files.delete(tempDir);
    }

    @Test
    public void exists() throws Exception {
        Path tempDir = Files.createTempDirectory("testExists");
        assertThat(localPlugin.exists(tempDir.toString())).isTrue();
        assertThat(localPlugin.exists(tempDir.resolve("nonexistent").toString())).isFalse();

        // Clean up
        Files.delete(tempDir);
    }

    @Test
    public void getType() {
        assertThat(localPlugin.getType()).isEqualTo("local");
    }
}
