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

package fr.pilato.elasticsearch.crawler.fs.meta;


import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Provides utility methods to read and write metadata and settings files
 */
public class MetaFileHandler {

    public final static Path DEFAULT_ROOT = Paths.get(System.getProperty("user.home"), ".fscrawler");

    private final Path root;

    public MetaFileHandler() {
        this(DEFAULT_ROOT);
    }

    public MetaFileHandler(Path root) {
        this.root = root;
        try {
            if (Files.notExists(root)) {
                Files.createDirectory(root);
            }
        } catch (IOException e) {

        }
    }

    /**
     * Read a file in ~/.fscrawler/{subdir} dir
     * @param subdir subdir where we can read the file (null if we read in the root dir)
     * @param filename filename
     * @return The String UTF-8 content
     * @throws IOException in case of error while reading
     */
    public String readFile(String subdir, String filename) throws IOException {
        Path dir = root;
        if (subdir != null) {
            dir = dir.resolve(subdir);
        }
        return new String(Files.readAllBytes(dir.resolve(filename)), "UTF-8");
    }

    /**
     * Write a file in ~/.fscrawler/{subdir} dir
     * @param subdir subdir where we can read the file (null if we read in the root dir)
     * @param filename filename
     * @param content The String UTF-8 content to write
     * @throws IOException in case of error while reading
     */
    public void writeFile(String subdir, String filename, String content) throws IOException {
        Path dir = root;
        if (subdir != null) {
            dir = dir.resolve(subdir);

            // If the dir does not exist, we need to create it
            if (Files.notExists(dir)) {
                Files.createDirectory(dir);
            }
        }
        Files.write(dir.resolve(filename), content.getBytes(Charset.forName("UTF-8")));
    }
}
