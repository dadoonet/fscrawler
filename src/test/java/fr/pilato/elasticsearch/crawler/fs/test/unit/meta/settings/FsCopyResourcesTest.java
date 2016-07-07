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

package fr.pilato.elasticsearch.crawler.fs.test.unit.meta.settings;

import fr.pilato.elasticsearch.crawler.fs.test.AbstractFSCrawlerTestCase;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;

import static fr.pilato.elasticsearch.crawler.fs.util.FsCrawlerUtil.copyDefaultResources;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class FsCopyResourcesTest extends AbstractFSCrawlerTestCase {

    @Test
    public void testCopyResources() throws IOException, URISyntaxException {
        Path target = Paths.get(folder.getRoot().toURI()).resolve(".fscrawler-test-copy-resources");

        Files.createDirectory(target);
        copyDefaultResources(target);

        AtomicInteger fileCounter = new AtomicInteger();
        AtomicInteger dirCounter = new AtomicInteger();

        Files.walkFileTree(target, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
                new FileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        logger.info(" --> found directory [{}]", dir);
                        dirCounter.incrementAndGet();
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        logger.info(" --> found file [{}]", file);
                        fileCounter.incrementAndGet();
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        return FileVisitResult.CONTINUE;
                    }
                });

        // We have 3 dirs for now:
        // root test dir ".fscrawler-test-copy-resources"
        // "_default" dir
        // "1" for elasticsearch version 1
        // "2" for elasticsearch version 2
        // "5" for elasticsearch version 5
        assertThat(dirCounter.get(), is(5));

        // We have 2 files that must be copied per version: folder.json and doc.json
        assertThat(fileCounter.get(), is(6));
    }
}
