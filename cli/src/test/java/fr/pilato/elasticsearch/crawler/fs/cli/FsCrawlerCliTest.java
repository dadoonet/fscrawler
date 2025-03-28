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
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.copyDefaultResources;
import static fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader.SETTINGS_YAML;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * We want to test FSCrawler main app
 */
public class FsCrawlerCliTest extends AbstractFSCrawlerTestCase {
    private static final Logger logger = LogManager.getLogger();
    private static Path metadataDir;

    @BeforeClass
    public static void createFsCrawlerJobDir() throws IOException {
        // We also need to create default mapping files
        metadataDir = rootTmpDir.resolve(".fscrawler");
        if (Files.notExists(metadataDir)) {
            Files.createDirectory(metadataDir);
        }
        copyDefaultResources(metadataDir);
        logger.debug("  --> Test metadata dir ready in [{}]", metadataDir);
    }

    @AfterClass
    public static void printMetadataDirContent() throws IOException {
        printLs(metadataDir);
    }

    private static void printLs(Path dir) throws IOException {
        logger.debug("ls -l {}", dir);
        Files.list(dir).forEach(path -> {
            if (Files.isDirectory(path)) {
                try {
                    printLs(path);
                } catch (IOException ignored) { }
            } else {
                logger.debug("{}", path);
            }
        });
    }

    @Test
    public void testRestartCommand() throws Exception {
        String jobName = "fscrawler_restart_command";

        // We generate a fake status first in metadata dir
        FsSettingsLoader fsSettingsLoader = new FsSettingsLoader(metadataDir);
        FsJobFileHandler fsJobFileHandler = new FsJobFileHandler(metadataDir);

        Path jobDir = metadataDir.resolve(jobName);
        Files.createDirectories(jobDir);

        fsSettingsLoader.write(jobName, new FsSettings());
        fsJobFileHandler.write(jobName, new FsJob());

        assertThat(Files.exists(jobDir.resolve(FsJobFileHandler.FILENAME)), is(true));

        String[] args = { "--config_dir", metadataDir.toString(), "--loop", "0", "--restart", jobName };

        FsCrawlerCli.main(args);

        assertThat(Files.exists(jobDir.resolve(FsJobFileHandler.FILENAME)), is(false));
    }

    @Test(expected = FsCrawlerIllegalConfigurationException.class)
    public void testWithWrongSettingsFile() throws Exception {
        String jobName = "fscrawler_wrong_settings";

        Path jobDir = metadataDir.resolve(jobName);
        Files.createDirectories(jobDir);
        Files.writeString(jobDir.resolve(SETTINGS_YAML),
                "name: \"fscrawler_wrong_settings\"\n" +
                "fs:\n" +
                "  url: \"/path/to/docs\"\n" +
                // Wrong indentation
                " follow_symlinks: false\n");

        String[] args = { "--config_dir", metadataDir.toString(), "--loop", "1", jobName };
        FsCrawlerCli.main(args);
    }

    @Test
    public void testWithEnvVariables() throws Exception {
        String jobName = "fscrawler_env_variables";

        Path jobDir = metadataDir.resolve(jobName);
        Files.createDirectories(jobDir);
        Files.writeString(jobDir.resolve(SETTINGS_YAML),
                "name: \"${MY_JOB_NAME}\"\n" +
                        "fs:\n" +
                        "  url: \"${FSCRAWLER_FS_URL:=/tmp/test}\"\n");

        String[] args = { "--config_dir", metadataDir.toString(), jobName };
        // Create an environment variable
        System.setProperty("MY_JOB_NAME", "fscrawler_env_variables");

        try {
            FsCrawlerCli.main(args);
        } finally {
            // Remove the environment variable
            System.clearProperty("MY_JOB_NAME");
        }
    }

    @Test
    public void testWithDefaultNamesForEnvVariables() throws Exception {
        String jobName = "fscrawler_env_variables_default";

        Path jobDir = metadataDir.resolve(jobName);
        Files.createDirectories(jobDir);

        String[] args = { "--config_dir", metadataDir.toString(), jobName };
        // Create an environment variable
        System.setProperty("FSCRAWLER_NAME", "fscrawler_env_variables_default");
        System.setProperty("FSCRAWLER_FS_URL", "/foo/bar");

        try {
            FsCrawlerCli.main(args);
        } finally {
            // Remove the environment variable
            System.clearProperty("FSCRAWLER_NAME");
            System.clearProperty("FSCRAWLER_FS_URL");
        }
    }

    @Test(expected = FsCrawlerIllegalConfigurationException.class)
    public void testWithEnvVariablesNotSet() throws Exception {
        String jobName = "fscrawler_env_variables";

        Path jobDir = metadataDir.resolve(jobName);
        Files.createDirectories(jobDir);
        Files.writeString(jobDir.resolve(SETTINGS_YAML),
                "name: \"${MY_JOB_NAME}\"\n" +
                        "fs:\n" +
                        "  url: \"${FSCRAWLER_FS_URL:=/tmp/test}\"\n");

        String[] args = { "--config_dir", metadataDir.toString(), jobName };
        FsCrawlerCli.main(args);
    }

    @Test
    public void testWithEmptySettings() throws Exception {
        String jobName = "fscrawler_empty_settings";
        Path jobDir = metadataDir.resolve(jobName);
        Files.createDirectories(jobDir);
        Files.writeString(jobDir.resolve(SETTINGS_YAML), "");

        String[] args = { "--config_dir", metadataDir.toString(), jobName };

        FsCrawlerCli.main(args);
    }
}
