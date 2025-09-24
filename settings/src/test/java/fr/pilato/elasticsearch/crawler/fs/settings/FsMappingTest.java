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

import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerMetadataTestCase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class FsMappingTest extends AbstractFSCrawlerMetadataTestCase {
    private static final Logger logger = LogManager.getLogger();
    private static final String CLASSPATH_RESOURCES_ROOT = "/jobtest/";

    @BeforeClass
    public static void generateSpecificJobMappings() throws IOException {
        Path targetResourceDir = metadataDir.resolve("jobtest").resolve("_mappings");

        for (String filename : MAPPING_RESOURCES) {
            logger.debug("Copying [{}]...", filename);
            Path target = targetResourceDir.resolve(filename);
            copyResourceFile(CLASSPATH_RESOURCES_ROOT + filename, target);
        }

        logger.debug("  --> Mappings generated in [{}]", targetResourceDir);
    }

    @Test
    public void fsSettingsForDocVersionNotSupported() throws Exception {
        try {
            readJsonFile(rootTmpDir, metadataDir, 0, INDEX_SETTINGS_FILE);
            fail("We should have thrown an exception for an unknown elasticsearch version");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains("does not exist for elasticsearch version");
        }
    }

    @Test
    public void fsSettingsForFolderVersionNotSupported() throws Exception {
        try {
            readJsonFile(rootTmpDir, metadataDir, 0, INDEX_SETTINGS_FOLDER_FILE);
            fail("We should have thrown an exception for an unknown elasticsearch version");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains("does not exist for elasticsearch version");
        }
    }
}
