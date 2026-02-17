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

package fr.pilato.elasticsearch.crawler.fs.test.integration.elasticsearch;

import fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl;
import fr.pilato.elasticsearch.crawler.fs.beans.CrawlerState;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.rest.CrawlerStatusResponse;
import fr.pilato.elasticsearch.crawler.fs.rest.RestJsonProvider;
import fr.pilato.elasticsearch.crawler.fs.rest.RestServer;
import fr.pilato.elasticsearch.crawler.fs.rest.SimpleResponse;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.carrotsearch.randomizedtesting.RandomizedTest.*;
import static fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl.LOOP_INFINITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for the REST API crawler control endpoints (pause, resume, checkpoint).
 * These tests use a real crawler alongside the REST server to test the control functionality.
 */
public class FsCrawlerRestCrawlerControlIT extends AbstractFsCrawlerITCase {
    private static final Logger logger = LogManager.getLogger();

    private RestServer restServer;
    private Client httpClient;
    private WebTarget target;
    private int restPort;

    @Before
    public void startRestServer() throws Exception {
        // Find an available port
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            restPort = serverSocket.getLocalPort();
        }
        logger.info("🛠️ Using REST port [{}] for test", restPort);

        // Create HTTP client
        httpClient = ClientBuilder.newBuilder()
                .register(RestJsonProvider.class)
                .register(JacksonFeature.class)
                .build();
        target = httpClient.target("http://127.0.0.1:" + restPort + "/fscrawler");
    }

    @After
    public void stopRestServer() {
        if (restServer != null) {
            restServer.close();
            restServer = null;
        }
        if (httpClient != null) {
            httpClient.close();
            httpClient = null;
        }
    }

    /**
     * Test scenario 1: Pause the crawler, verify no documents indexed during pause,
     * then resume and verify indexing completes.
     * <p/>
     * Note: We create a deep directory structure to slow down the crawler enough
     * to be able to pause it mid-scan. We verify that NO NEW documents are indexed
     * while paused (some might have been indexed before the pause took effect).
     */
    @Test
    public void test_pause_resume_indexing() throws Exception {
        // Create a deep directory structure with many files to slow down scanning
        long nbFolders = randomLongBetween(5, 20);
        long nbFiles = randomLongBetween(100, 1000);

        Path testDir = currentTestResourceDir;
        for (long i = 0; i < nbFolders; i++) {
            Path subDir = testDir.resolve("subdir_" + i);
            Files.createDirectories(subDir);
            for (long j = 0; j < nbFiles; j++) {
                Files.writeString(subDir.resolve("file_" + j + ".txt"), "Content of file " + i + "_" + j);
            }
        }

        long expectedDocs = 1L + nbFolders * nbFiles; // x subdirs * y files + 1 from _common
        logger.info("📂 Created {} documents within {} folders", expectedDocs, nbFolders);

        // Create settings with a reasonable update rate
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setUpdateRate(TimeValue.timeValueMinutes(5)); // Long update rate to avoid second scan

        // Start crawler with REST
        try (FsCrawlerImpl fsCrawler = startCrawlerWithRest(fsSettings)) {
            // We pause the crawler immediately to test the pause functionality, but it might take a moment for the crawler to start and begin indexing.
            fsCrawler.getFsParser().pause();

            // Wait for the crawler to start and index some documents
            ESSearchResponse responseAfterStart = countTestHelper(new ESSearchRequest().withIndex(fsSettings.getElasticsearch().getIndex()), null, null);

            // Pause the crawler
            logger.info("⏸️ Pausing crawler. We have {} documents indexed so far on {} expected...",
                    responseAfterStart.getTotalHits(),
                    expectedDocs);
            SimpleResponse pauseResponse = restPauseCrawler();
            assertThat(pauseResponse.isOk()).isTrue();
            assertThat(pauseResponse.getMessage()).contains("paused");

            // Verify status shows PAUSED
            CrawlerStatusResponse status = restGetCrawlerStatus();
            assertThat(status.getState()).isEqualTo(CrawlerState.PAUSED);

            ESSearchResponse response = countTestHelper(new ESSearchRequest().withIndex(fsSettings.getElasticsearch().getIndex()), null, null);
            long docsBeforePause = response.getTotalHits();
            logger.info("📍 Documents indexed before pause: {}", docsBeforePause);

            FsCrawlerUtil.waitFor(Duration.ofSeconds(1));

            // Record how many documents we have now (after pause and flush)
            refresh(fsSettings.getElasticsearch().getIndex());
            ESSearchResponse searchAfterPause = client.search(new ESSearchRequest().withIndex(fsSettings.getElasticsearch().getIndex()));
            long docsAfterPause = searchAfterPause.getTotalHits();
            logger.info("📍 Documents indexed after pause: {}", docsAfterPause);

            // Verify no new documents were indexed during the pause period
            assertThat(docsAfterPause)
                    .as("No new documents should be indexed while paused")
                    .isEqualTo(docsBeforePause);

            // Resume the crawler
            logger.info("⏯️ Resuming crawler...");
            SimpleResponse resumeResponse = restResumeCrawler();
            assertThat(resumeResponse.isOk()).isTrue();
            assertThat(resumeResponse.getMessage()).contains("resumed");

            // Count expected files
            countTestHelper(new ESSearchRequest().withIndex(fsSettings.getElasticsearch().getIndex()), expectedDocs, testDir);
        }
    }

    /**
     * Test scenario 2: Delete checkpoint and verify documents are re-indexed.
     */
    @Test
    public void test_delete_checkpoint_reindex() throws Exception {
        // Create a small set of test files
        long nbFiles = randomLongBetween(5, 10);
        for (int i = 0; i < nbFiles; i++) {
            Files.writeString(currentTestResourceDir.resolve("doc_" + i + ".txt"), "Document content " + i);
        }

        // Create settings
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setUpdateRate(TimeValue.timeValueMinutes(10)); // Long rate to control scans

        // Start crawler with REST
        try (FsCrawlerImpl fsCrawler = startCrawlerWithRest(fsSettings)) {
            // Wait for initial indexing to complete
            long expectedDocs = Files.list(currentTestResourceDir).filter(Files::isRegularFile).count();
            logger.info("⏳ Waiting for {} documents to be indexed...", expectedDocs);
            countTestHelper(new ESSearchRequest().withIndex(fsSettings.getElasticsearch().getIndex()), expectedDocs, currentTestResourceDir);

            // Wait for the scan to complete (state should be COMPLETED)
            await().atMost(Duration.ofSeconds(30))
                    .pollInterval(Duration.ofMillis(500))
                    .until(() -> fsCrawler.getFsParser().getState() == CrawlerState.COMPLETED);

            // Pause the crawler before deleting checkpoint
            logger.info("⏸️ Pausing crawler before clearing checkpoint...");
            SimpleResponse pauseResponse = restPauseCrawler();
            assertThat(pauseResponse.isOk()).isTrue();
            // Verify crawler is paused
            assertThat(fsCrawler.getFsParser().getState()).isEqualTo(CrawlerState.PAUSED);

            // Delete the checkpoint
            logger.info("⌫ Deleting checkpoint...");
            SimpleResponse deleteResponse = restDeleteCheckpoint();
            assertThat(deleteResponse.isOk()).isTrue();
            assertThat(deleteResponse.getMessage()).contains("cleared");
            // Verify crawler is still paused
            assertThat(fsCrawler.getFsParser().getState()).isEqualTo(CrawlerState.PAUSED);

            // Clear the Elasticsearch index to verify re-indexing
            logger.info("⌫ Clearing Elasticsearch index to verify re-indexing...");
            client.deleteIndex(fsSettings.getElasticsearch().getIndex());

            // Resume the crawler - it should re-index everything
            logger.info("⏯️ Resuming crawler after checkpoint deletion...");
            SimpleResponse resumeResponse = restResumeCrawler();
            assertThat(resumeResponse.isOk()).isTrue();
            // Verify crawler is now running again
            assertThat(fsCrawler.getFsParser().getState()).isEqualTo(CrawlerState.RUNNING);

            // Wait for documents to be re-indexed
            logger.info("⏳ Waiting for {} documents to be re-indexed...", expectedDocs);
            countTestHelper(new ESSearchRequest().withIndex(fsSettings.getElasticsearch().getIndex()), expectedDocs, currentTestResourceDir);

            // Wait until the crawler is marked as COMPLETED again
            await().atMost(Duration.ofSeconds(30))
                    .pollInterval(Duration.ofMillis(500))
                    .until(() -> fsCrawler.getFsParser().getState() == CrawlerState.COMPLETED);
        }
    }

    /**
     * Test scenario 3: Verify crawler status endpoint returns correct information
     * including scanEndTime and nextCheck after a completed scan.
     */
    @Test
    public void test_status_after_completed_scan() throws Exception {
        // Create test files
        long nbFiles = randomLongBetween(3, 10);
        for (int i = 0; i < nbFiles; i++) {
            Files.writeString(currentTestResourceDir.resolve("status_test_" + i + ".txt"), "Status test " + i);
        }

        // Create settings with short update rate
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setUpdateRate(TimeValue.timeValueSeconds(30));

        // Start crawler with REST
        FsCrawlerImpl fsCrawler = startCrawlerWithRest(fsSettings);

        // Wait for initial indexing
        long expectedDocs = Files.list(currentTestResourceDir).filter(Files::isRegularFile).count();
        countTestHelper(new ESSearchRequest().withIndex(fsSettings.getElasticsearch().getIndex()), expectedDocs, currentTestResourceDir);

        // Wait for the scan to be marked as COMPLETED
        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> fsCrawler.getFsParser().getState() == CrawlerState.COMPLETED);

        // Get status and verify it contains the expected fields
        CrawlerStatusResponse status = restGetCrawlerStatus();
        logger.info("✅ Crawler status: state={}, filesProcessed={}, scanEndTime={}, nextCheck={}",
                status.getState(), status.getFilesProcessed(), status.getScanEndTime(), status.getNextCheck());

        assertThat(status.getState()).isEqualTo(CrawlerState.COMPLETED);
        assertThat(status.getFilesProcessed()).isEqualTo(expectedDocs);
        assertThat(status.getScanEndTime()).isNotNull();
        assertThat(status.getNextCheck())
                .isNotNull()
                .isAfter(status.getScanEndTime());
    }

    @Test
    public void test_pause_already_paused() throws Exception {
        long nbFiles = randomLongBetween(3, 10);
        for (int i = 0; i < nbFiles; i++) {
            Files.writeString(currentTestResourceDir.resolve("status_paused_" + i + ".txt"), "Already Paused test " + i);
        }

        // Create settings with a reasonable update rate
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setUpdateRate(TimeValue.timeValueMinutes(5)); // Long update rate to avoid second scan

        // Start crawler with REST
        try (FsCrawlerImpl fsCrawler = startCrawlerWithRest(fsSettings)) {
            // We pause the crawler immediately to test the pause functionality, but it might take a moment for the crawler to start and begin indexing.
            logger.info("⏸️ Pausing crawler.");
            SimpleResponse pauseResponse = restPauseCrawler();
            assertThat(pauseResponse.isOk()).isTrue();
            assertThat(pauseResponse.getMessage()).contains("Crawler paused. Checkpoint saved.");
            assertThat(fsCrawler.getFsParser().getState()).isEqualTo(CrawlerState.PAUSED);

            logger.info("⏸️ Pausing again the crawler.");
            SimpleResponse pauseResponse2 = restPauseCrawler();
            assertThat(pauseResponse2.isOk()).isTrue();
            assertThat(pauseResponse2.getMessage()).contains("Crawler is already paused.");
            assertThat(fsCrawler.getFsParser().getState()).isEqualTo(CrawlerState.PAUSED);

            // Resume the crawler
            logger.info("⏯️ Resuming crawler...");
            SimpleResponse resumeResponse = restResumeCrawler();
            assertThat(resumeResponse.isOk()).isTrue();
            assertThat(resumeResponse.getMessage()).contains("Crawler resumed.");
            assertThat(fsCrawler.getFsParser().getState()).isEqualTo(CrawlerState.RUNNING);

            // Count expected files
            countTestHelper(new ESSearchRequest().withIndex(fsSettings.getElasticsearch().getIndex()), nbFiles + 1, currentTestResourceDir);
        }
    }

    @Test
    public void test_delete_checkpoint_while_running() throws Exception {
        long nbFiles = randomLongBetween(3, 10);
        for (int i = 0; i < nbFiles; i++) {
            Files.writeString(currentTestResourceDir.resolve("status_paused_" + i + ".txt"), "Already Paused test " + i);
        }

        // Create settings with a reasonable update rate
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setUpdateRate(TimeValue.timeValueMinutes(5)); // Long update rate to avoid second scan

        // Start crawler with REST
        try (FsCrawlerImpl fsCrawler = startCrawlerWithRest(fsSettings)) {
            assertThat(fsCrawler.getFsParser().getState()).isEqualTo(CrawlerState.RUNNING);

            logger.info("⌫ Deleting checkpoint...");
            SimpleResponse deleteResponse = restDeleteCheckpoint();
            assertThat(deleteResponse.isOk()).isFalse();
            assertThat(deleteResponse.getMessage()).contains("Cannot clear checkpoint while crawler is running. Pause or stop it first.");
            // Verify crawler is still running
            assertThat(fsCrawler.getFsParser().getState()).isEqualTo(CrawlerState.RUNNING);

            // Count expected files
            countTestHelper(new ESSearchRequest().withIndex(fsSettings.getElasticsearch().getIndex()), nbFiles + 1, currentTestResourceDir);
        }
    }

    @Test
    public void status_during_scan() throws Exception {
        // Create a deep directory structure with many files to slow down scanning
        long nbFolders = randomLongBetween(5, 20);
        long nbFiles = randomLongBetween(100, 1000);

        Path testDir = currentTestResourceDir;
        for (long i = 0; i < nbFolders; i++) {
            Path subDir = testDir.resolve("subdir_" + i);
            Files.createDirectories(subDir);
            for (long j = 0; j < nbFiles; j++) {
                Files.writeString(subDir.resolve("file_" + j + ".txt"), "Content of file " + i + "_" + j);
            }
        }

        long expectedDocs = 1L + nbFolders * nbFiles; // x subdirs * y files + 1 from _common
        logger.info("📂 Created {} documents within {} folders", expectedDocs, nbFolders);

        // Create settings with a reasonable update rate
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setUpdateRate(TimeValue.timeValueMinutes(5)); // Long update rate to avoid second scan

        // Start crawler with REST
        try (FsCrawlerImpl fsCrawler = startCrawlerWithRest(fsSettings)) {
            // Verify status shows RUNNING
            CrawlerStatusResponse status = restGetCrawlerStatus();
            assertThat(status.getState()).isEqualTo(CrawlerState.RUNNING);

            final AtomicLong counterFiles = new AtomicLong(status.getFilesProcessed());
            final AtomicInteger counterCompletedDirs = new AtomicInteger(status.getCompletedDirectories());
            final AtomicInteger counterPendingDirs = new AtomicInteger(status.getPendingDirectories());
            await()
                    .pollInterval(Duration.ofSeconds(1))
                    .timeout(Duration.ofMinutes(5))
                    .until(() -> {
                        CrawlerStatusResponse s = restGetCrawlerStatus();
                        logger.info("📊 Crawler status while scanning: completedDirs={}, filesProcessed={}, pendingDirs={}",
                                s.getCompletedDirectories(),
                                s.getFilesProcessed(),
                                s.getPendingDirectories());
                        long oldFiles = counterFiles.getAndSet(s.getFilesProcessed());
                        int oldCompletedDirs = counterCompletedDirs.getAndSet(s.getCompletedDirectories());
                        int oldPendingDirs = counterPendingDirs.getAndSet(s.getPendingDirectories());
                        // We should see progress in both files and directories processed
                        assertThat(s.getFilesProcessed()).isGreaterThanOrEqualTo(oldFiles);
                        // But if the crawler is completed, we should have no completed directories anymore (since we reset the checkpoint)
                        if (s.getState() == CrawlerState.COMPLETED) {
                            assertThat(s.getCompletedDirectories()).isZero();
                            assertThat(s.getPendingDirectories()).isZero();
                        } else {
                            assertThat(s.getCompletedDirectories()).isGreaterThanOrEqualTo(oldCompletedDirs);
                            assertThat(s.getPendingDirectories()).isLessThanOrEqualTo(oldPendingDirs);
                        }

                        // We wait until the crawler is completed and has no pending directories
                        return s.getState() == CrawlerState.COMPLETED && s.getPendingDirectories() == 0;
                    });

            assertThat(fsCrawler.getFsParser().getState()).isEqualTo(CrawlerState.COMPLETED);

            // Count expected files
            countTestHelper(new ESSearchRequest().withIndex(fsSettings.getElasticsearch().getIndex()), expectedDocs, testDir);
        }
    }

    // Utility methods for REST API calls

    /**
     * Start the crawler with REST server enabled
     */
    private FsCrawlerImpl startCrawlerWithRest(FsSettings fsSettings) throws Exception {
        // Configure REST
        fsSettings.getRest().setUrl("http://127.0.0.1:" + restPort + "/fscrawler");

        // Create the crawler
        crawler = new FsCrawlerImpl(metadataDir, fsSettings, LOOP_INFINITE, true);
        // Create the Rest server
        restServer = new RestServer(
                fsSettings,
                crawler.getManagementService(),
                crawler.getDocumentService(),
                crawler.getPluginsManager(),
                crawler.getFsParser()
        );

        // Start the crawler
        crawler.start();

        // Start the REST server
        restServer.start();

        return crawler;
    }

    /**
     * Call POST /_crawler/pause
     */
    private SimpleResponse restPauseCrawler() {
        try (Response response = target.path("/_crawler/pause")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(""))) {
            return response.readEntity(SimpleResponse.class);
        }
    }

    /**
     * Call POST /_crawler/resume
     */
    private SimpleResponse restResumeCrawler() {
        try (Response response = target.path("/_crawler/resume")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(""))) {
            return response.readEntity(SimpleResponse.class);
        }
    }

    /**
     * Call GET /_crawler/status
     */
    private CrawlerStatusResponse restGetCrawlerStatus() {
        return target.path("/_crawler/status")
                .request(MediaType.APPLICATION_JSON)
                .get(CrawlerStatusResponse.class);
    }

    /**
     * Call DELETE /_crawler/checkpoint
     */
    private SimpleResponse restDeleteCheckpoint() {
        try (Response response = target.path("/_crawler/checkpoint")
                .request(MediaType.APPLICATION_JSON)
                .delete()) {
            return response.readEntity(SimpleResponse.class);
        }
    }
}
