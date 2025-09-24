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

package fr.pilato.elasticsearch.crawler.fs.settings;

import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.copyDefaultResources;
import static org.assertj.core.api.Assertions.assertThat;

public class FsCopyResourcesTest extends AbstractFSCrawlerTestCase {
    private static final Logger logger = LogManager.getLogger();

    @Test
    public void copyResources() throws IOException {
        Path target = Paths.get(folder.getRoot().toURI()).resolve(".fscrawler-test-copy-resources");

        Files.createDirectory(target);
        copyDefaultResources(target);

        AtomicInteger fileCounter = new AtomicInteger();
        AtomicInteger dirCounter = new AtomicInteger();

        Files.walkFileTree(target, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
                new FileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        logger.info(" --> found directory [{}]", dir);
                        dirCounter.incrementAndGet();
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        logger.info(" --> found file [{}]", file);
                        fileCounter.incrementAndGet();
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) {
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                        return FileVisitResult.CONTINUE;
                    }
                });

        // We have 2 dirs for now:
        // root test dir ".fscrawler-test-copy-resources"
        // "_default" dir
        assertThat(dirCounter.get()).isEqualTo(2);

        // We have 0 files now (version 6 files have been removed).
        assertThat(fileCounter.get()).isEqualTo(0);
    }
}
