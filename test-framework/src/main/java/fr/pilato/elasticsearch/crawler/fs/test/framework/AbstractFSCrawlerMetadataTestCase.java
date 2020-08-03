/*
 * Licensed to David Pilato under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static fr.pilato.elasticsearch.crawler.fs.test.framework.FsCrawlerUtilForTests.copyDefaultResources;

public abstract class AbstractFSCrawlerMetadataTestCase extends AbstractFSCrawlerTestCase {

    protected static Path metadataDir;

    @BeforeClass
    public static void createFsCrawlerJobDir() throws IOException {
        // We also need to create default mapping files
        metadataDir = rootTmpDir.resolve(".fscrawler");
        if (!metadataDir.toFile().exists()) {
            Files.createDirectory(metadataDir);
        }
        copyDefaultResources(metadataDir);
        staticLogger.debug("  --> Test metadata dir ready in [{}]", metadataDir);
    }

    @AfterClass
    public static void printMetadataDirContent() throws IOException {
        staticLogger.debug("ls -l {}", metadataDir);
        try (Stream<Path> files = Files.list(metadataDir)) {
            files.forEach(path -> staticLogger.debug("{}", path));
        }
    }
}
