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
package fr.pilato.elasticsearch.crawler.fs.framework;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Provides utility methods to read and write metadata and settings files */
public class MetaFileHandler {

    private static final Logger logger = LogManager.getLogger();
    public static final Path DEFAULT_ROOT = Paths.get(System.getProperty("user.home"), ".fscrawler");

    protected final Path root;

    protected MetaFileHandler(Path root) {
        this.root = root;
    }

    /** Resolve a job inside the configuration root. The root must be trusted against concurrent local mutation. */
    public static Path resolveJobDirectory(Path root, String jobName) throws IOException {
        if (jobName == null
                || jobName.isBlank()
                || jobName.equals(".")
                || jobName.equals("..")
                || jobName.indexOf('/') >= 0
                || jobName.indexOf('\\') >= 0
                || jobName.indexOf(':') >= 0) {
            throw new IOException("Invalid job name: expected a single directory name");
        }
        try {
            Path base = root.toAbsolutePath().normalize();
            Path directory = base.resolve(jobName).normalize();
            if (!directory.startsWith(base)
                    || Files.isSymbolicLink(directory)
                    || (Files.exists(directory)
                            && !directory.toRealPath().equals(base.toRealPath().resolve(jobName)))) {
                throw new IOException("Job directory escapes configuration root");
            }
            return directory;
        } catch (InvalidPathException e) {
            throw new IOException("Invalid job directory", e);
        }
    }

    private Path metadataFile(String subdir, String filename) throws IOException {
        Path directory = subdir == null ? root.toAbsolutePath().normalize() : resolveJobDirectory(root, subdir);
        Path file = directory.resolve(filename);
        if (Files.isSymbolicLink(file)) {
            throw new IOException("Metadata file must not be a symbolic link");
        }
        return file;
    }

    /**
     * Read a file in ~/.fscrawler/{subdir} dir
     *
     * @param subdir subdir where we can read the file (null if we read in the root dir)
     * @param filename filename
     * @return The String UTF-8 content
     * @throws IOException in case of error while reading
     */
    protected String readFile(String subdir, String filename) throws IOException {
        Path file = metadataFile(subdir, filename);
        logger.trace("Reading metadata file {}", file);
        return Files.readString(file);
    }

    /**
     * Write a file in ~/.fscrawler/{subdir} dir
     *
     * @param subdir subdir where we can read the file (null if we read in the root dir)
     * @param filename filename
     * @param content The String UTF-8 content to write
     * @throws IOException in case of error while reading
     */
    protected void writeFile(String subdir, String filename, String content) throws IOException {
        Path file = metadataFile(subdir, filename);
        if (subdir != null) {
            Files.createDirectories(file.getParent());
        }
        logger.trace("Writing metadata file {}", file);
        Files.writeString(file, content);
    }

    /**
     * Remove a file from ~/.fscrawler/{subdir} dir
     *
     * @param subdir subdir where we can read the file (null if we read in the root dir)
     * @param filename filename
     * @throws IOException in case of error while reading
     */
    protected void removeFile(String subdir, String filename) throws IOException {
        Path file = metadataFile(subdir, filename);
        logger.trace("Removing metadata file {} if exists", file);
        Files.deleteIfExists(file);
    }
}
