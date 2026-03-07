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

import fr.pilato.elasticsearch.crawler.fs.beans.CrawlerState;
import fr.pilato.elasticsearch.crawler.fs.beans.FsCrawlerCheckpoint;
import fr.pilato.elasticsearch.crawler.fs.beans.FsCrawlerCheckpointFileHandler;
import fr.pilato.elasticsearch.crawler.fs.beans.FsJob;
import fr.pilato.elasticsearch.crawler.fs.beans.FsJobFileHandler;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import fr.pilato.elasticsearch.crawler.fs.settings.Defaults;
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
import java.time.LocalDateTime;

import static fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader.SETTINGS_YAML;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

/**
 * We want to test FSCrawler main app
 */
public class FsCrawlerCliTest extends AbstractFSCrawlerTestCase {
    private static final Logger logger = LogManager.getLogger();
    private static Path metadataDir;

    @BeforeClass
    public static void createFsCrawlerJobDir() throws IOException {
        metadataDir = rootTmpDir.resolve(".fscrawler");
        if (Files.notExists(metadataDir)) {
            Files.createDirectory(metadataDir);
        }
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
                } catch (IOException ignored) {
                    // It's just for debugging, we don't care if it fails
                }
            } else {
                logger.debug("{}", path);
            }
        });
    }

    @Test
    public void restartCommand() throws Exception {
        String jobName = "fscrawler_restart_command";

        // We generate fake status and checkpoint files first in metadata dir
        FsSettingsLoader fsSettingsLoader = new FsSettingsLoader(metadataDir);
        FsJobFileHandler fsJobFileHandler = new FsJobFileHandler(metadataDir);
        FsCrawlerCheckpointFileHandler checkpointHandler = new FsCrawlerCheckpointFileHandler(metadataDir);

        Path jobDir = metadataDir.resolve(jobName);
        Files.createDirectories(jobDir);

        fsSettingsLoader.write(jobName, new FsSettings());
        // Write legacy status file
        fsJobFileHandler.write(jobName, new FsJob());
        // Write checkpoint file
        FsCrawlerCheckpoint checkpoint = new FsCrawlerCheckpoint();
        checkpoint.setState(CrawlerState.COMPLETED);
        checkpointHandler.write(jobName, checkpoint);

        assertThat(jobDir.resolve(FsJobFileHandler.FILENAME)).exists();
        assertThat(jobDir.resolve(FsCrawlerCheckpointFileHandler.FILENAME)).exists();

        String[] args = { "--config_dir", metadataDir.toString(), "--loop", "0", "--restart", jobName };

        FsCrawlerCli.main(args);

        // Both files should be cleaned
        assertThat(jobDir.resolve(FsJobFileHandler.FILENAME)).doesNotExist();
        assertThat(jobDir.resolve(FsCrawlerCheckpointFileHandler.FILENAME)).doesNotExist();
    }

    @Test
    public void testWithWrongSettingsFile() throws Exception {
        String jobName = "fscrawler_wrong_settings";
        Path jobDir = metadataDir.resolve(jobName);
        Files.createDirectories(jobDir);
        // Wrong indentation
        Files.writeString(jobDir.resolve(SETTINGS_YAML),
                """
                        name: "fscrawler_wrong_settings"
                        fs:
                          url: "/path/to/docs"
                         follow_symlinks: false
                        """);
        String[] args = {"--config_dir", metadataDir.toString(), "--loop", "1", jobName};
        assertThatExceptionOfType(FsCrawlerIllegalConfigurationException.class).isThrownBy(() ->
                FsCrawlerCli.main(args));
    }

    @Test
    public void withEnvVariables() throws Exception {
        String jobName = "fscrawler_env_variables";

        Path jobDir = metadataDir.resolve(jobName);
        Files.createDirectories(jobDir);
        Files.writeString(jobDir.resolve(SETTINGS_YAML),
                """
                name: "${MY_JOB_NAME}"
                fs:
                  url: "${FSCRAWLER_FS_URL:=/tmp/test}"
                """);

        String[] args = { "--config_dir", metadataDir.toString(), jobName };
        // Create an environment variable
        System.setProperty("MY_JOB_NAME", "fscrawler_env_variables");

        try {
            assertThatNoException().isThrownBy(() -> FsCrawlerCli.main(args));
        } finally {
            // Remove the environment variable
            System.clearProperty("MY_JOB_NAME");
        }
    }

    @Test
    public void withDefaultNamesForEnvVariables() throws Exception {
        String jobName = "fscrawler_env_variables_default";

        Path jobDir = metadataDir.resolve(jobName);
        Files.createDirectories(jobDir);

        String[] args = { "--config_dir", metadataDir.toString(), jobName };
        // Create an environment variable
        System.setProperty("FSCRAWLER_NAME", "fscrawler_env_variables_default");
        System.setProperty("FSCRAWLER_FS_URL", "/foo/bar");

        try {
            assertThatNoException().isThrownBy(() -> FsCrawlerCli.main(args));
        } finally {
            // Remove the environment variable
            System.clearProperty("FSCRAWLER_NAME");
            System.clearProperty("FSCRAWLER_FS_URL");
        }
    }

    @Test
    public void withEnvVariablesNotSet() throws IOException {
        String jobName = "fscrawler_env_variables";
        Path jobDir = metadataDir.resolve(jobName);
        Files.createDirectories(jobDir);
        Files.writeString(jobDir.resolve(SETTINGS_YAML),
                    """
                    name: "${MY_JOB_NAME}"
                    fs:
                      url: "${FSCRAWLER_FS_URL:=/tmp/test}"
                    """);
        String[] args = {"--config_dir", metadataDir.toString(), jobName};
        assertThatExceptionOfType(FsCrawlerIllegalConfigurationException.class).isThrownBy(() ->
                FsCrawlerCli.main(args));
    }

    @Test
    public void withEmptySettings() throws Exception {
        String jobName = "fscrawler_empty_settings";
        Path jobDir = metadataDir.resolve(jobName);
        Files.createDirectories(jobDir);
        Files.writeString(jobDir.resolve(SETTINGS_YAML), "");

        String[] args = { "--config_dir", metadataDir.toString(), jobName };

        assertThatNoException().isThrownBy(() -> FsCrawlerCli.main(args));
    }

    @Test
    public void testWithNoJobName() throws Exception {
        Path jobDir = metadataDir.resolve(Defaults.JOB_NAME_DEFAULT);
        Files.createDirectories(jobDir);
        Files.writeString(jobDir.resolve(SETTINGS_YAML), "");

        String[] args = { "--config_dir", metadataDir.toString() };

        assertThatNoException().isThrownBy(() -> FsCrawlerCli.main(args));
    }

    @Test
    public void testSetupJob() throws Exception {
        String jobName = "fscrawler_setup_job";
        String[] args = { "--config_dir", metadataDir.toString(), "--setup", jobName };
        FsCrawlerCli.main(args);

        Path jobDir = metadataDir.resolve(jobName);
        assertThat(jobDir).exists();
        assertThat(jobDir.resolve(SETTINGS_YAML)).exists();
    }

    @Test
    public void testListJobs() throws Exception {
        String[] argsJob1 = { "--config_dir", metadataDir.toString(), "--setup", "fscrawler_list_jobs_1" };
        FsCrawlerCli.main(argsJob1);
        String[] argsJob2 = { "--config_dir", metadataDir.toString(), "--setup", "fscrawler_list_jobs_2" };
        FsCrawlerCli.main(argsJob2);

        String[] argsListJobs = { "--config_dir", metadataDir.toString(), "--list" };
        assertThatNoException().isThrownBy(() -> FsCrawlerCli.main(argsListJobs));
    }

    @Test
    public void migrate_legacy_job() throws Exception {
        String jobName = getCurrentTestName();

        // We generate fake status and checkpoint files first in metadata dir
        FsSettingsLoader fsSettingsLoader = new FsSettingsLoader(metadataDir);
        FsJobFileHandler legacyHandler = new FsJobFileHandler(metadataDir);
        FsCrawlerCheckpointFileHandler checkpointHandler = new FsCrawlerCheckpointFileHandler(metadataDir);

        Path jobDir = metadataDir.resolve(jobName);
        Files.createDirectories(jobDir);

        FsSettings fsSettings = new FsSettings();
        fsSettings.setName(jobName);
        fsSettingsLoader.write(jobName, fsSettings);

        // Write legacy status file
        FsJob legacyJob = new FsJob();
        legacyJob.setLastrun(LocalDateTime.now().minusHours(1));
        legacyJob.setNextCheck(LocalDateTime.now().plusHours(1));
        legacyJob.setIndexed(50);
        legacyJob.setDeleted(2);
        legacyHandler.write(getCurrentTestName(), legacyJob);

        assertThat(jobDir.resolve(FsJobFileHandler.FILENAME)).exists();
        assertThat(jobDir.resolve(FsCrawlerCheckpointFileHandler.FILENAME)).doesNotExist();

        String[] args = { "--config_dir", metadataDir.toString(), "--loop", "0", jobName };
        FsCrawlerCli.main(args);

        // Both files should be cleaned
        assertThat(jobDir.resolve(FsJobFileHandler.FILENAME)).doesNotExist();
        assertThat(jobDir.resolve(FsCrawlerCheckpointFileHandler.FILENAME)).exists();

        FsCrawlerCheckpoint checkpoint = checkpointHandler.read(jobName);
        assertThat(checkpoint).isNotNull();
        assertThat(checkpoint.getScanDate()).isEqualTo(legacyJob.getLastrun());
        assertThat(checkpoint.getNextCheck()).isEqualTo(legacyJob.getNextCheck());
        assertThat(checkpoint.getFilesProcessed()).isEqualTo(legacyJob.getIndexed());
        assertThat(checkpoint.getFilesDeleted()).isEqualTo(legacyJob.getDeleted());
        assertThat(checkpoint.getState()).isEqualTo(CrawlerState.COMPLETED);
    }
}
