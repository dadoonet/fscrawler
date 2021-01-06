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

package fr.pilato.elasticsearch.crawler.fs.framework;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Provides utility methods to read and write metadata and settings files
 */
public class MetaFileHandler {

    private static final Logger logger = LogManager.getLogger(MetaFileHandler.class);
    public final static Path DEFAULT_ROOT = Paths.get(System.getProperty("user.home"), ".fscrawler");

    private final Path root;

    protected MetaFileHandler(Path root) {
        this.root = root;
    }

    /**
     * Read a file in ~/.fscrawler/{subdir} dir
     * @param subdir subdir where we can read the file (null if we read in the root dir)
     * @param filename filename
     * @return The String UTF-8 content
     * @throws IOException in case of error while reading
     */
    protected String readFile(String subdir, String filename) throws IOException {
        Path dir = root;
        if (subdir != null) {
            dir = dir.resolve(subdir);
        }
        logger.trace("Reading file {} from {}", filename, dir);
        return Files.readString(dir.resolve(filename));
    }

    /**
     * Write a file in ~/.fscrawler/{subdir} dir
     * @param subdir subdir where we can read the file (null if we read in the root dir)
     * @param filename filename
     * @param content The String UTF-8 content to write
     * @throws IOException in case of error while reading
     */
    protected void writeFile(String subdir, String filename, String content) throws IOException {
        Path dir = root;
        if (subdir != null) {
            dir = dir.resolve(subdir);

            // If the dir does not exist, we need to create it
            if (Files.notExists(dir)) {
                Files.createDirectory(dir);
            }
        }
        logger.trace("Writing file {} to {}", filename, dir);
        Files.writeString(dir.resolve(filename), content);
    }

    /**
     * Remove a file from ~/.fscrawler/{subdir} dir
     * @param subdir subdir where we can read the file (null if we read in the root dir)
     * @param filename filename
     * @throws IOException in case of error while reading
     */
    protected void removeFile(String subdir, String filename) throws IOException {
        Path dir = root;
        if (subdir != null) {
            dir = dir.resolve(subdir);
        }
        logger.trace("Removing file {} from {} if exists", filename, dir);
        Files.deleteIfExists(dir.resolve(filename));
    }
}
