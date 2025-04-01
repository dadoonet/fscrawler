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

package fr.pilato.elasticsearch.crawler.fs.crawler.fs;

import fr.pilato.elasticsearch.crawler.fs.crawler.FileAbstractModel;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Iterator;

import static org.junit.Assert.assertEquals;

public class FileAbstractorFileTest {

    private FileAbstractorFile fileAbstractorFile;

    @Before
    public void setUp() {
        FsSettings fsSettings = FsSettingsLoader.load();
        fileAbstractorFile = new FileAbstractorFile(fsSettings);
    }

    @Test
    public void testGetFiles() throws Exception {
        Path tempDir = Files.createTempDirectory("testGetFiles");
        // File1 is created first
        Path tempFile1 = Files.createFile(tempDir.resolve("file1.txt"));

        // Wait for 100ms to make sure file2 is created first
        Thread.sleep(100);

        // Then file2
        Path tempFile2 = Files.createFile(tempDir.resolve("file2.txt"));

        Collection<FileAbstractModel> files = fileAbstractorFile.getFiles(tempDir.toString());

        assertEquals(2, files.size());
        Iterator<FileAbstractModel> iterator = files.iterator();
        assertEquals(tempFile2.toFile().getName(), iterator.next().getName());
        assertEquals(tempFile1.toFile().getName(), iterator.next().getName());

        // Clean up
        Files.delete(tempFile1);
        Files.delete(tempFile2);
        Files.delete(tempDir);
    }
}
