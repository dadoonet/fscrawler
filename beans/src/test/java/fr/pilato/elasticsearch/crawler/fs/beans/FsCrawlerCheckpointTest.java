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

import fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class FsCrawlerCheckpointTest extends AbstractFSCrawlerTestCase {
    private static final Logger logger = LogManager.getLogger();

    private void checkpointTester(FsCrawlerCheckpoint source) throws IOException {
        String json = JsonUtil.prettyMapper.writeValueAsString(source);

        logger.info("-> generated checkpoint: [{}]", json);
        FsCrawlerCheckpoint generated = JsonUtil.prettyMapper.readValue(json, FsCrawlerCheckpoint.class);
        Assertions.assertThat(generated).isEqualTo(source);
    }

    @Test
    void parseEmptyCheckpoint() throws IOException {
        checkpointTester(new FsCrawlerCheckpoint());
    }

    @Test
    void parseNewCheckpoint() throws IOException {
        FsCrawlerCheckpoint checkpoint = FsCrawlerCheckpoint.newCheckpoint("/test/path");
        checkpointTester(checkpoint);
    }

    @Test
    void parseCheckpointWithData() throws IOException {
        FsCrawlerCheckpoint checkpoint = new FsCrawlerCheckpoint();
        checkpoint.setScanId("test-scan-123");
        checkpoint.setScanStartTime(Instant.now());
        checkpoint.setCurrentPath("/current/path");
        checkpoint.addPath("/pending/path1");
        checkpoint.addPath("/pending/path2");
        checkpoint.markCompleted("/completed/path1");
        checkpoint.setFilesProcessed(100);
        checkpoint.setFilesDeleted(5);
        checkpoint.setState(CrawlerState.RUNNING);
        checkpoint.setRetryCount(2);
        checkpoint.setLastError("Test error");
        checkpoint.setScanDate(Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS));
        checkpoint.setScanEndTime(Instant.now());
        checkpoint.setNextCheck(Instant.now().plus(1, java.time.temporal.ChronoUnit.HOURS));

        checkpointTester(checkpoint);
    }

    @Test
    void testNewCheckpoint() {
        FsCrawlerCheckpoint checkpoint = FsCrawlerCheckpoint.newCheckpoint("/root/path");

        Assertions.assertThat(checkpoint.getScanId()).isNotNull();
        Assertions.assertThat(checkpoint.getScanStartTime()).isNotNull();
        Assertions.assertThat(checkpoint.getState()).isEqualTo(CrawlerState.RUNNING);
        Assertions.assertThat(checkpoint.hasPendingWork()).isTrue();
        Assertions.assertThat(checkpoint.peekNextPath()).isEqualTo("/root/path");
    }

    @Test
    void testPendingPathsOperations() {
        FsCrawlerCheckpoint checkpoint = new FsCrawlerCheckpoint();

        Assertions.assertThat(checkpoint.hasPendingWork()).isFalse();

        checkpoint.addPath("/path1");
        checkpoint.addPath("/path2");

        Assertions.assertThat(checkpoint.hasPendingWork()).isTrue();
        Assertions.assertThat(checkpoint.peekNextPath()).isEqualTo("/path1");

        String polled = checkpoint.pollNextPath();
        Assertions.assertThat(polled).isEqualTo("/path1");
        Assertions.assertThat(checkpoint.peekNextPath()).isEqualTo("/path2");

        checkpoint.addPathFirst("/priority/path");
        Assertions.assertThat(checkpoint.peekNextPath()).isEqualTo("/priority/path");
    }

    @Test
    void testIsPendingAndClearPendingPaths() {
        FsCrawlerCheckpoint checkpoint = new FsCrawlerCheckpoint();
        Assertions.assertThat(checkpoint.isPending("/path1")).isFalse();

        checkpoint.addPath("/path1");
        checkpoint.addPath("/path2");
        Assertions.assertThat(checkpoint.isPending("/path1")).isTrue();
        Assertions.assertThat(checkpoint.isPending("/path2")).isTrue();
        Assertions.assertThat(checkpoint.isPending("/path3")).isFalse();

        checkpoint.pollNextPath();
        Assertions.assertThat(checkpoint.isPending("/path1")).isFalse();
        Assertions.assertThat(checkpoint.isPending("/path2")).isTrue();

        checkpoint.clearPendingPaths();
        Assertions.assertThat(checkpoint.hasPendingWork()).isFalse();
        Assertions.assertThat(checkpoint.isPending("/path2")).isFalse();
    }

    @Test
    void testIsPendingRebuildsLookupSetWhenNull() throws Exception {
        FsCrawlerCheckpoint checkpoint = new FsCrawlerCheckpoint();
        checkpoint.addPath("/path1");

        Field pendingPathsSetField = FsCrawlerCheckpoint.class.getDeclaredField("pendingPathsSet");
        pendingPathsSetField.setAccessible(true);
        pendingPathsSetField.set(checkpoint, null);

        Assertions.assertThat(checkpoint.isPending("/path1")).isTrue();
    }

    @Test
    void testCompletedPaths() {
        FsCrawlerCheckpoint checkpoint = new FsCrawlerCheckpoint();

        Assertions.assertThat(checkpoint.isCompleted("/path1")).isFalse();

        checkpoint.markCompleted("/path1");

        Assertions.assertThat(checkpoint.isCompleted("/path1")).isTrue();
        Assertions.assertThat(checkpoint.isCompleted("/path2")).isFalse();
        Assertions.assertThat(checkpoint.getCompletedPaths()).hasSize(1);
    }

    @Test
    void testCounters() {
        FsCrawlerCheckpoint checkpoint = new FsCrawlerCheckpoint();

        Assertions.assertThat(checkpoint.getFilesProcessed()).isZero();
        Assertions.assertThat(checkpoint.getFilesDeleted()).isZero();
        Assertions.assertThat(checkpoint.getRetryCount()).isZero();

        checkpoint.incrementFilesProcessed();
        checkpoint.incrementFilesProcessed();
        Assertions.assertThat(checkpoint.getFilesProcessed()).isEqualTo(2);

        checkpoint.incrementFilesDeleted();
        Assertions.assertThat(checkpoint.getFilesDeleted()).isEqualTo(1);

        checkpoint.incrementRetryCount();
        checkpoint.incrementRetryCount();
        Assertions.assertThat(checkpoint.getRetryCount()).isEqualTo(2);

        checkpoint.resetRetryCount();
        Assertions.assertThat(checkpoint.getRetryCount()).isZero();
    }

    @Test
    void testCrawlerStates() {
        for (CrawlerState state : CrawlerState.values()) {
            FsCrawlerCheckpoint checkpoint = new FsCrawlerCheckpoint();
            checkpoint.setState(state);
            Assertions.assertThat(checkpoint.getState()).isEqualTo(state);
        }
    }

    /** Test that date serialization is stable */
    @Test
    void dateTimeSerialization() throws IOException {
        Instant now = Instant.now();
        FsCrawlerCheckpoint checkpoint = new FsCrawlerCheckpoint();
        checkpoint.setScanStartTime(now);
        checkpoint.setScanDate(now);
        checkpoint.setScanEndTime(now);
        checkpoint.setNextCheck(now.plus(1, java.time.temporal.ChronoUnit.HOURS));

        String json = JsonUtil.prettyMapper.writeValueAsString(checkpoint);
        FsCrawlerCheckpoint generated = JsonUtil.prettyMapper.readValue(json, FsCrawlerCheckpoint.class);

        Assertions.assertThat(generated.getScanStartTime()).isEqualTo(now);
        Assertions.assertThat(generated.getScanDate()).isEqualTo(now);
        Assertions.assertThat(generated.getScanEndTime()).isEqualTo(now);
        Assertions.assertThat(generated.getNextCheck()).isEqualTo(now.plus(1, java.time.temporal.ChronoUnit.HOURS));
    }

    /** Test completed checkpoint scenario (replaces FsJob) */
    @Test
    void testCompletedCheckpoint() throws IOException {
        FsCrawlerCheckpoint checkpoint = new FsCrawlerCheckpoint();
        checkpoint.setScanId("completed-scan-123");
        checkpoint.setState(CrawlerState.COMPLETED);
        checkpoint.setScanEndTime(Instant.now());
        checkpoint.setNextCheck(Instant.now().plus(15, java.time.temporal.ChronoUnit.MINUTES));
        checkpoint.setFilesProcessed(500);
        checkpoint.setFilesDeleted(10);

        checkpointTester(checkpoint);

        // Verify the completed state can be used for next run
        Assertions.assertThat(checkpoint.getState()).isEqualTo(CrawlerState.COMPLETED);
        Assertions.assertThat(checkpoint.getScanEndTime()).isNotNull();
        Assertions.assertThat(checkpoint.getNextCheck()).isNotNull();
    }

    /**
     * When a checkpoint file contains explicit null for pendingPaths or completedPaths (e.g. manual edit or
     * corruption), deserialization must not leave nulls, and toString() must not throw NPE (e.g. when used in logging).
     */
    @Test
    void parseCheckpointWithNullCollectionsDoesNotThrow() throws IOException {
        String json = "{\"scan_id\":\"x\",\"state\":\"PAUSED\",\"pending_paths\":null,\"completed_paths\":null}";
        FsCrawlerCheckpoint checkpoint = JsonUtil.prettyMapper.readValue(json, FsCrawlerCheckpoint.class);

        // prettyMapper uses SNAKE_CASE: camelCase keys are ignored, so scanId would stay null
        Assertions.assertThat(checkpoint.getScanId())
                .as(
                        "JSON keys must use snake_case so mapper deserializes them; otherwise setters (e.g. for null collections) are never called")
                .isEqualTo("x");

        // Setters normalize null to empty collections
        Assertions.assertThat(checkpoint.getPendingPaths()).isNotNull();
        Assertions.assertThat(checkpoint.getPendingPaths()).isEmpty();
        Assertions.assertThat(checkpoint.getCompletedPaths()).isNotNull();
        Assertions.assertThat(checkpoint.getCompletedPaths()).isEmpty();

        // toString() must not throw (defensive null check)
        Assertions.assertThat(checkpoint.toString()).contains("pendingPaths=0").contains("completedPaths=0");

        // ensureConcurrentCollections() must not throw and must leave collections non-null
        checkpoint.ensureConcurrentCollections();
        Assertions.assertThat(checkpoint.getPendingPaths()).isNotNull();
        Assertions.assertThat(checkpoint.getCompletedPaths()).isNotNull();
        Assertions.assertThat(checkpoint.toString()).contains("pendingPaths=0").contains("completedPaths=0");
    }

    @Test
    void legacy_local_date_time_checkpoint_deserializes_to_instant() throws IOException {
        // Pre-3.0 checkpoints stored zone-less LocalDateTime strings.
        String legacyJson = """
                {
                  "scan_id" : "legacy",
                  "scan_start_time" : "2026-07-02T12:10:57",
                  "scan_date" : "2026-07-02T12:10:55",
                  "scan_end_time" : "2026-07-02T12:11:00",
                  "next_check" : "2026-07-02T12:26:00",
                  "state" : "COMPLETED",
                  "files_processed" : 3,
                  "files_deleted" : 0,
                  "retry_count" : 0,
                  "pending_paths" : [ ],
                  "completed_paths" : [ ]
                }
                """;
        FsCrawlerCheckpoint checkpoint = JsonUtil.prettyMapper.readValue(legacyJson, FsCrawlerCheckpoint.class);
        ZoneId zone = ZoneId.systemDefault();
        Assertions.assertThat(checkpoint.getScanStartTime())
                .isEqualTo(
                        LocalDateTime.parse("2026-07-02T12:10:57").atZone(zone).toInstant());
        Assertions.assertThat(checkpoint.getScanDate())
                .isEqualTo(
                        LocalDateTime.parse("2026-07-02T12:10:55").atZone(zone).toInstant());
        Assertions.assertThat(checkpoint.getScanEndTime())
                .isEqualTo(
                        LocalDateTime.parse("2026-07-02T12:11:00").atZone(zone).toInstant());
        Assertions.assertThat(checkpoint.getNextCheck())
                .isEqualTo(
                        LocalDateTime.parse("2026-07-02T12:26:00").atZone(zone).toInstant());
    }
}
