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

package fr.pilato.elasticsearch.crawler.fs.beans;

import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(checkpoint).isNull();
    }

    @Test
    public void testExistsNonExistent() {
        assertThat(handler.exists("non-existent-job")).isFalse();
    }

    @Test
    public void testWriteAndRead() throws IOException {
        String jobName = getCurrentTestName();
        
        FsCrawlerCheckpoint checkpoint = FsCrawlerCheckpoint.newCheckpoint("/test/path");
        checkpoint.setFilesProcessed(42);
        checkpoint.setState(CrawlerState.PAUSED);
        
        handler.write(jobName, checkpoint);
        
        assertThat(handler.exists(jobName)).isTrue();
        
        FsCrawlerCheckpoint read = handler.read(jobName);
        assertThat(read).isNotNull();
        assertThat(read.getScanId()).isEqualTo(checkpoint.getScanId());
        assertThat(read.getFilesProcessed()).isEqualTo(42);
        assertThat(read.getState()).isEqualTo(CrawlerState.PAUSED);
    }

    @Test
    public void testClean() throws IOException {
        String jobName = getCurrentTestName();
        
        FsCrawlerCheckpoint checkpoint = FsCrawlerCheckpoint.newCheckpoint("/test/path");
        handler.write(jobName, checkpoint);
        
        assertThat(handler.exists(jobName)).isTrue();
        
        handler.clean(jobName);
        
        assertThat(handler.exists(jobName)).isFalse();
        assertThat(handler.read(jobName)).isNull();
    }

    @Test
    public void testCleanNonExistent() throws IOException {
        // Should not throw
        handler.clean("non-existent-job");
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
        assertThat(read.getFilesProcessed()).isEqualTo(20);
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
        
        assertThat(read.getScanId()).isEqualTo("test-scan-id");
        assertThat(read.getCurrentPath()).isEqualTo("/current");
        assertThat(read.getPendingPaths()).hasSize(2);
        assertThat(read.getCompletedPaths()).hasSize(2);
        assertThat(read.getFilesProcessed()).isEqualTo(100);
        assertThat(read.getFilesDeleted()).isEqualTo(5);
        assertThat(read.getState()).isEqualTo(CrawlerState.RUNNING);
        assertThat(read.getRetryCount()).isEqualTo(3);
        assertThat(read.getLastError()).isEqualTo("Some error");
    }
}
