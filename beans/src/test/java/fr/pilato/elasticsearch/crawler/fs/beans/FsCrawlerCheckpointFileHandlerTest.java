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
package fr.pilato.elasticsearch.crawler.fs.beans;

import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;

public class FsCrawlerCheckpointFileHandlerTest extends AbstractFSCrawlerTestCase {

    private Path rootDir;
    private FsCrawlerCheckpointFileHandler handler;

    @Before
    public void setUp() throws IOException {
        rootDir = rootTmpDir.resolve("checkpoint-test");
        Files.createDirectories(rootDir);
        handler = new FsCrawlerCheckpointFileHandler(rootDir);
    }

    @Test
    public void testReadNonExistent() throws IOException {
        FsCrawlerCheckpoint checkpoint = handler.read("non-existent-job");
        Assertions.assertThat(checkpoint).isNull();
    }

    @Test
    public void testExistsNonExistent() {
        Assertions.assertThat(handler.exists("non-existent-job")).isFalse();
    }

    @Test
    public void testWriteAndRead() throws IOException {
        String jobName = getCurrentTestName();

        FsCrawlerCheckpoint checkpoint = FsCrawlerCheckpoint.newCheckpoint("/test/path");
        checkpoint.setFilesProcessed(42);
        checkpoint.setState(CrawlerState.PAUSED);

        handler.write(jobName, checkpoint);

        Assertions.assertThat(handler.exists(jobName)).isTrue();

        FsCrawlerCheckpoint read = handler.read(jobName);
        Assertions.assertThat(read).isNotNull();
        Assertions.assertThat(read.getScanId()).isEqualTo(checkpoint.getScanId());
        Assertions.assertThat(read.getFilesProcessed()).isEqualTo(42);
        Assertions.assertThat(read.getState()).isEqualTo(CrawlerState.PAUSED);
    }

    @Test
    public void testClean() throws IOException {
        String jobName = getCurrentTestName();

        FsCrawlerCheckpoint checkpoint = FsCrawlerCheckpoint.newCheckpoint("/test/path");
        handler.write(jobName, checkpoint);

        Assertions.assertThat(handler.exists(jobName)).isTrue();

        handler.clean(jobName);

        Assertions.assertThat(handler.exists(jobName)).isFalse();
        Assertions.assertThat(handler.read(jobName)).isNull();
    }

    @Test
    public void testCleanNonExistent() {
        // Should not throw an exception even if the job does not exist
        Assertions.assertThatNoException().isThrownBy(() -> handler.clean("non-existent-job"));
    }

    @Test
    public void testOverwrite() throws IOException {
        String jobName = getCurrentTestName();

        FsCrawlerCheckpoint checkpoint1 = FsCrawlerCheckpoint.newCheckpoint("/path1");
        checkpoint1.setFilesProcessed(10);
        handler.write(jobName, checkpoint1);

        FsCrawlerCheckpoint checkpoint2 = FsCrawlerCheckpoint.newCheckpoint("/path2");
        checkpoint2.setFilesProcessed(20);
        handler.write(jobName, checkpoint2);

        FsCrawlerCheckpoint read = handler.read(jobName);
        Assertions.assertThat(read.getFilesProcessed()).isEqualTo(20);
    }

    @Test
    public void testCompleteCheckpointRoundTrip() throws IOException {
        String jobName = getCurrentTestName();

        FsCrawlerCheckpoint checkpoint = new FsCrawlerCheckpoint();
        checkpoint.setScanId("test-scan-id");
        checkpoint.setScanStartTime(LocalDateTime.now());
        checkpoint.setCurrentPath("/current");
        checkpoint.addPath("/pending1");
        checkpoint.addPath("/pending2");
        checkpoint.markCompleted("/completed1");
        checkpoint.markCompleted("/completed2");
        checkpoint.setFilesProcessed(100);
        checkpoint.setFilesDeleted(5);
        checkpoint.setState(CrawlerState.RUNNING);
        checkpoint.setRetryCount(3);
        checkpoint.setLastError("Some error");
        checkpoint.setScanDate(LocalDateTime.now().minusDays(1));

        handler.write(jobName, checkpoint);

        FsCrawlerCheckpoint read = handler.read(jobName);

        Assertions.assertThat(read.getScanId()).isEqualTo("test-scan-id");
        Assertions.assertThat(read.getCurrentPath()).isEqualTo("/current");
        Assertions.assertThat(read.getPendingPaths()).hasSize(2);
        Assertions.assertThat(read.getCompletedPaths()).hasSize(2);
        Assertions.assertThat(read.getFilesProcessed()).isEqualTo(100);
        Assertions.assertThat(read.getFilesDeleted()).isEqualTo(5);
        Assertions.assertThat(read.getState()).isEqualTo(CrawlerState.RUNNING);
        Assertions.assertThat(read.getRetryCount()).isEqualTo(3);
        Assertions.assertThat(read.getLastError()).isEqualTo("Some error");
    }

    @Test
    public void migrate_from_legacy_status() throws IOException {
        String jobName = getCurrentTestName();

        // Create a legacy _status.json file
        FsJobFileHandler legacyHandler = new FsJobFileHandler(rootDir);
        FsJob legacyJob = new FsJob();
        legacyJob.setLastrun(LocalDateTime.now().minusHours(1));
        legacyJob.setNextCheck(LocalDateTime.now().plusHours(1));
        legacyJob.setIndexed(50);
        legacyJob.setDeleted(2);
        legacyHandler.write(jobName, legacyJob);

        // Migrate to checkpoint format
        handler.migrateLegacyStatus(jobName);

        // Check that the checkpoint was created with the expected values
        FsCrawlerCheckpoint checkpoint = handler.read(jobName);
        Assertions.assertThat(checkpoint).isNotNull();
        Assertions.assertThat(checkpoint.getScanDate()).isEqualTo(legacyJob.getLastrun());
        Assertions.assertThat(checkpoint.getNextCheck()).isEqualTo(legacyJob.getNextCheck());
        Assertions.assertThat(checkpoint.getFilesProcessed()).isEqualTo(legacyJob.getIndexed());
        Assertions.assertThat(checkpoint.getFilesDeleted()).isEqualTo(legacyJob.getDeleted());
        Assertions.assertThat(checkpoint.getState()).isEqualTo(CrawlerState.COMPLETED);
    }
}
