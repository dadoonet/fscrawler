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
package fr.pilato.elasticsearch.crawler.fs.test.framework;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;

public class FsCrawlerUtilForTests {
    private static final Logger logger = LogManager.getLogger();

    /**
     * Copy files from a source to a target
     * under a _default subdirectory.
     * @param source The source dir
     * @param target The target dir
     * @param options Potential options
     * @throws IOException If copying does not work
     */
    public static void copyDirs(Path source, Path target, CopyOption... options) throws IOException {
        if (Files.notExists(target)) {
            Files.createDirectory(target);
        }

        logger.debug("  --> Copying resources from [{}]", source);
        if (Files.notExists(source)) {
            throw new RuntimeException(source + " doesn't seem to exist.");
        }

        Files.walkFileTree(source, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
                new InternalFileVisitor(source, target, options));

        logger.debug("  --> Resources ready in [{}]", target);
    }

    private static class InternalFileVisitor extends SimpleFileVisitor<Path> {

        private final Path fromPath;
        private final Path toPath;
        private final CopyOption[] copyOption;

        public InternalFileVisitor(Path fromPath, Path toPath, CopyOption... copyOption) {
            this.fromPath = fromPath;
            this.toPath = toPath;
            this.copyOption = copyOption;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {

            Path targetPath = toPath.resolve(fromPath.relativize(dir));
            if(!Files.exists(targetPath)){
                Files.createDirectory(targetPath);
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            try {
                Files.copy(file, toPath.resolve(fromPath.relativize(file)), copyOption);
            } catch (FileAlreadyExistsException ignored) {
                // The file already exists we just ignore it
            }
            return FileVisitResult.CONTINUE;
        }
    }
}
