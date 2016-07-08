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

package fr.pilato.elasticsearch.crawler.fs.test.unit;

import fr.pilato.elasticsearch.crawler.fs.FsCrawler;
import fr.pilato.elasticsearch.crawler.fs.meta.job.FsJobFileHandler;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.FsSettingsFileHandler;
import fr.pilato.elasticsearch.crawler.fs.test.AbstractFSCrawlerTestCase;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import static fr.pilato.elasticsearch.crawler.fs.util.FsCrawlerUtil.copyResourceFile;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Tests to check that Legacy files are correctly renamed
 */
public class LegacyTest extends AbstractFSCrawlerTestCase {

    private static final String CLASSPATH_RESOURCES_ROOT = "/legacy/2_0/";
    public static final String[] LEGACY_RESOURCES = {
            "david.json", "david_status.json"
    };

    @BeforeClass
    public static void copyLegacyResources2_0() throws IOException, URISyntaxException {
        for (String filename : LEGACY_RESOURCES) {
            staticLogger.debug("Copying [{}]...", filename);
            Path target = metadataDir.resolve(filename);
            copyResourceFile(CLASSPATH_RESOURCES_ROOT + filename, target);
        }
    }

    @Test
    public void testFrom2_0Version() throws IOException, URISyntaxException {
        FsCrawler.moveLegacyResources(metadataDir);

        // We should have now our files in david dir
        Path david = metadataDir.resolve("david");
        assertThat(Files.isDirectory(david), is(true));
        Path jobSettings = david.resolve(FsSettingsFileHandler.FILENAME);
        assertThat(Files.exists(jobSettings), is(true));
        Path jobStatus = david.resolve(FsJobFileHandler.FILENAME);
        assertThat(Files.exists(jobStatus), is(true));
    }

}
