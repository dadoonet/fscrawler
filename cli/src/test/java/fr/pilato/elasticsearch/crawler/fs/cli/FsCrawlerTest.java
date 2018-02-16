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

package fr.pilato.elasticsearch.crawler.fs.cli;

import fr.pilato.elasticsearch.crawler.fs.beans.FsJob;
import fr.pilato.elasticsearch.crawler.fs.beans.FsJobFileHandler;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsFileHandler;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.copyDefaultResources;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.copyResourceFile;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * We want to test FSCrawler main app
 */
public class FsCrawlerTest extends AbstractFSCrawlerTestCase {

    private static final String CLASSPATH_RESOURCES_ROOT = "/legacy/2_0/";
    private static final String[] LEGACY_RESOURCES = {
            "david.json", "david_status.json"
    };

    private static Path metadataDir;

    @BeforeClass
    public static void createFsCrawlerJobDir() throws IOException, URISyntaxException {
        // We also need to create default mapping files
        metadataDir = rootTmpDir.resolve(".fscrawler");
        if (Files.notExists(metadataDir)) {
            Files.createDirectory(metadataDir);
        }
        copyDefaultResources(metadataDir);
        staticLogger.debug("  --> Test metadata dir ready in [{}]", metadataDir);

        for (String filename : LEGACY_RESOURCES) {
            AbstractFSCrawlerTestCase.staticLogger.debug("Copying [{}]...", filename);
            Path target = metadataDir.resolve(filename);
            copyResourceFile(CLASSPATH_RESOURCES_ROOT + filename, target);
        }
    }

    @AfterClass
    public static void printMetadataDirContent() throws IOException {
        staticLogger.debug("ls -l {}", metadataDir);
        Files.list(metadataDir).forEach(path -> staticLogger.debug("{}", path));
    }

    @Test
    public void testRestartCommand() throws Exception {
        String jobName = "fscrawler_restart_command";

        // We generate a fake status first in metadata dir
        FsSettingsFileHandler fsSettingsFileHandler = new FsSettingsFileHandler(metadataDir);
        FsJobFileHandler fsJobFileHandler = new FsJobFileHandler(metadataDir);

        Path jobDir = metadataDir.resolve(jobName);
        Files.createDirectories(jobDir);


        fsSettingsFileHandler.write(FsSettings.builder(jobName).build());
        fsJobFileHandler.write(jobName, FsJob.builder().build());

        assertThat(Files.exists(jobDir.resolve(FsJobFileHandler.FILENAME)), is(true));

        String[] args = { "--config_dir", metadataDir.toString(), "--loop", "0", "--restart", jobName };

        FsCrawler.main(args);

        assertThat(Files.exists(jobDir.resolve(FsJobFileHandler.FILENAME)), is(false));
    }

    @Test
    public void testFrom2_0Version() {
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
