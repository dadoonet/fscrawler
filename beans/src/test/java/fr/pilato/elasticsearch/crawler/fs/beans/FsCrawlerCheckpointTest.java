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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import java.io.IOException;
import java.time.LocalDateTime;

import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.prettyMapper;
import static org.assertj.core.api.Assertions.assertThat;

public class FsCrawlerCheckpointTest extends AbstractFSCrawlerTestCase {
    private static final Logger logger = LogManager.getLogger();

    private void checkpointTester(FsCrawlerCheckpoint source) throws IOException {
        String json = prettyMapper.writeValueAsString(source);

        logger.info("-> generated checkpoint: [{}]", json);
        FsCrawlerCheckpoint generated = prettyMapper.readValue(json, FsCrawlerCheckpoint.class);
        assertThat(generated).isEqualTo(source);
    }

    @Test
    public void parseEmptyCheckpoint() throws IOException {
        checkpointTester(new FsCrawlerCheckpoint());
    }

    @Test
    public void parseNewCheckpoint() throws IOException {
        FsCrawlerCheckpoint checkpoint = FsCrawlerCheckpoint.newCheckpoint("/test/path");
        checkpointTester(checkpoint);
    }

    @Test
    public void parseCheckpointWithData() throws IOException {
        FsCrawlerCheckpoint checkpoint = new FsCrawlerCheckpoint();
        checkpoint.setScanId("test-scan-123");
        checkpoint.setScanStartTime(LocalDateTime.now());
        checkpoint.setCurrentPath("/current/path");
        checkpoint.addPath("/pending/path1");
        checkpoint.addPath("/pending/path2");
        checkpoint.markCompleted("/completed/path1");
        checkpoint.setFilesProcessed(100);
        checkpoint.setFilesDeleted(5);
        checkpoint.setState(CrawlerState.RUNNING);
        checkpoint.setRetryCount(2);
        checkpoint.setLastError("Test error");
        checkpoint.setScanDate(LocalDateTime.now().minusDays(1));
        
        checkpointTester(checkpoint);
    }

    @Test
    public void testNewCheckpoint() {
        FsCrawlerCheckpoint checkpoint = FsCrawlerCheckpoint.newCheckpoint("/root/path");
        
        assertThat(checkpoint.getScanId()).isNotNull();
        assertThat(checkpoint.getScanStartTime()).isNotNull();
        assertThat(checkpoint.getState()).isEqualTo(CrawlerState.RUNNING);
        assertThat(checkpoint.hasPendingWork()).isTrue();
        assertThat(checkpoint.peekNextPath()).isEqualTo("/root/path");
    }

    @Test
    public void testPendingPathsOperations() {
        FsCrawlerCheckpoint checkpoint = new FsCrawlerCheckpoint();
        
        assertThat(checkpoint.hasPendingWork()).isFalse();
        
        checkpoint.addPath("/path1");
        checkpoint.addPath("/path2");
        
        assertThat(checkpoint.hasPendingWork()).isTrue();
        assertThat(checkpoint.peekNextPath()).isEqualTo("/path1");
        
        String polled = checkpoint.pollNextPath();
        assertThat(polled).isEqualTo("/path1");
        assertThat(checkpoint.peekNextPath()).isEqualTo("/path2");
        
        checkpoint.addPathFirst("/priority/path");
        assertThat(checkpoint.peekNextPath()).isEqualTo("/priority/path");
    }

    @Test
    public void testCompletedPaths() {
        FsCrawlerCheckpoint checkpoint = new FsCrawlerCheckpoint();
        
        assertThat(checkpoint.isCompleted("/path1")).isFalse();
        
        checkpoint.markCompleted("/path1");
        
        assertThat(checkpoint.isCompleted("/path1")).isTrue();
        assertThat(checkpoint.isCompleted("/path2")).isFalse();
        assertThat(checkpoint.getCompletedPaths()).hasSize(1);
    }

    @Test
    public void testCounters() {
        FsCrawlerCheckpoint checkpoint = new FsCrawlerCheckpoint();
        
        assertThat(checkpoint.getFilesProcessed()).isZero();
        assertThat(checkpoint.getFilesDeleted()).isZero();
        assertThat(checkpoint.getRetryCount()).isZero();
        
        checkpoint.incrementFilesProcessed();
        checkpoint.incrementFilesProcessed();
        assertThat(checkpoint.getFilesProcessed()).isEqualTo(2);
        
        checkpoint.incrementFilesDeleted();
        assertThat(checkpoint.getFilesDeleted()).isEqualTo(1);
        
        checkpoint.incrementRetryCount();
        checkpoint.incrementRetryCount();
        assertThat(checkpoint.getRetryCount()).isEqualTo(2);
        
        checkpoint.resetRetryCount();
        assertThat(checkpoint.getRetryCount()).isZero();
    }

    @Test
    public void testCrawlerStates() {
        for (CrawlerState state : CrawlerState.values()) {
            FsCrawlerCheckpoint checkpoint = new FsCrawlerCheckpoint();
            checkpoint.setState(state);
            assertThat(checkpoint.getState()).isEqualTo(state);
        }
    }

    /**
     * Test that date serialization is stable
     */
    @Test
    public void dateTimeSerialization() throws IOException {
        LocalDateTime now = LocalDateTime.now();
        FsCrawlerCheckpoint checkpoint = new FsCrawlerCheckpoint();
        checkpoint.setScanStartTime(now);
        checkpoint.setScanDate(now);
        
        String json = prettyMapper.writeValueAsString(checkpoint);
        FsCrawlerCheckpoint generated = prettyMapper.readValue(json, FsCrawlerCheckpoint.class);
        
        assertThat(generated.getScanStartTime()).isEqualTo(now);
        assertThat(generated.getScanDate()).isEqualTo(now);
    }
}
