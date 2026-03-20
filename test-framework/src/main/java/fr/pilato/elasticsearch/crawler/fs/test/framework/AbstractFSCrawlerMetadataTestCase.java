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
package fr.pilato.elasticsearch.crawler.fs.test.framework;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;

public abstract class AbstractFSCrawlerMetadataTestCase extends AbstractFSCrawlerTestCase {
    private static final Logger logger = LogManager.getLogger();
    protected static Path metadataDir;

    @BeforeClass
    public static void createFsCrawlerJobDir() throws IOException {
        metadataDir = rootTmpDir.resolve(".fscrawler");
        if (!metadataDir.toFile().exists()) {
            Files.createDirectory(metadataDir);
        }
        logger.debug("  --> Test metadata dir ready in [{}]", metadataDir);
    }

    @AfterClass
    public static void printMetadataDirContent() throws IOException {
        logger.debug("ls -l {}", metadataDir);
        try (Stream<Path> files = Files.list(metadataDir)) {
            files.forEach(path -> logger.debug("{}", path));
        }
    }
}
