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

package fr.pilato.elasticsearch.crawler.fs;

import com.jayway.jsonpath.DocumentContext;
import fr.pilato.elasticsearch.crawler.fs.beans.*;
import fr.pilato.elasticsearch.crawler.fs.beans.FileAbstractModel;
import fr.pilato.elasticsearch.crawler.fs.framework.*;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentService;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerManagementService;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.Server.PROTOCOL;
import fr.pilato.elasticsearch.crawler.fs.tika.TikaDocParser;
import fr.pilato.elasticsearch.crawler.fs.tika.XmlDocParser;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerExtensionFsProvider;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.io.File;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.*;
import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.asMap;

public class FsParser implements Runnable, AutoCloseable {
    static final Object semaphore = new Object();
    final AtomicInteger runNumber = new AtomicInteger(0);
    final AtomicBoolean closed = new AtomicBoolean(true);
    final AtomicBoolean paused = new AtomicBoolean(false);
    /** When true, between-runs wait does not exit on timeout; only resume() starts the next run. */
    private final AtomicBoolean userStopped = new AtomicBoolean(false);

    private static final Logger logger = LogManager.getLogger();

    private static final String FSCRAWLER_IGNORE_FILENAME = ".fscrawlerignore";
    private static final String FULL_STACKTRACE_LOG_MESSAGE = "Full stacktrace";

    // Checkpoint configuration
    private static final int CHECKPOINT_INTERVAL_FILES = 100;  // Save checkpoint every N files
    private static final int MAX_RETRIES = 10;
    private static final long INITIAL_RETRY_DELAY_MS = 1000;

    final FsSettings fsSettings;
    private final FsCrawlerCheckpointFileHandler checkpointHandler;
    private final FsAclsFileHandler fsAclsFileHandler;

    private final FsCrawlerManagementService managementService;
    private final FsCrawlerDocumentService documentService;
    private final Integer loop;
    private final boolean rest;
    private final Map<String, String> aclHashCache;
    private boolean aclHashCacheDirty;
    /** Null when loop == 0 (REST-only mode); no crawl is performed. */
    private final FsCrawlerExtensionFsProvider crawlerPlugin;
    private final String metadataFilename;
    private final byte[] staticMetadata;
    private static final TimeValue CHECK_JOB_INTERVAL = TimeValue.timeValueSeconds(5);

    // Checkpoint for current scan
    private final AtomicReference<FsCrawlerCheckpoint> checkpoint = new AtomicReference<>(new FsCrawlerCheckpoint());
    private int filesSinceLastCheckpoint = 0;
    /** Lock for serializing checkpoint file writes (close/pause/crawler threads). */
    private final Object checkpointWriteLock = new Object();

    public FsParser(FsSettings fsSettings, Path config, FsCrawlerManagementService managementService,
                           FsCrawlerDocumentService documentService, Integer loop, boolean rest,
                           FsCrawlerExtensionFsProvider crawlerPlugin) {
        this.fsSettings = fsSettings;
        this.checkpointHandler = new FsCrawlerCheckpointFileHandler(config);
        this.fsAclsFileHandler = initializeAclsFileHandler(fsSettings, config);
        this.aclHashCache = initializeAclCache(fsSettings);
        this.aclHashCacheDirty = false;
        this.managementService = managementService;
        this.documentService = documentService;
        this.crawlerPlugin = crawlerPlugin;

        this.loop = loop;
        this.rest = rest;
        logger.debug("creating fs crawler thread [{}] for [{}] every [{}]", fsSettings.getName(),
                fsSettings.getFs().getUrl(),
                fsSettings.getFs().getUpdateRate());

        metadataFilename = resolveMetadataFilename(fsSettings);
        staticMetadata = loadStaticMetadata(fsSettings);
    }

    public CrawlerState getState() {
        // Check closed/paused first so status matches isPaused()/isClosed() even when
        // checkpoint state is stale (e.g. pause() during COMPLETED sleep phase does not update checkpoint).
        if (closed.get()) {
            return CrawlerState.STOPPED;
        }
        if (paused.get()) {
            return CrawlerState.PAUSED;
        }
        FsCrawlerCheckpoint localCheckpoint = checkpoint.get();
        if (localCheckpoint != null) {
            return localCheckpoint.getState();
        }
        return CrawlerState.RUNNING;
    }

    public FsCrawlerCheckpoint getCheckpoint() {
        return checkpoint.get();
    }

    /**
     * Get the checkpoint handler for external access (e.g., REST API)
     * @return the checkpoint file handler
     */
    public FsCrawlerCheckpointFileHandler getCheckpointHandler() {
        return checkpointHandler;
    }

    public boolean isClosed() {
        return closed.get();
    }

    public boolean isPaused() {
        return paused.get();
    }

    Object getSemaphore() {
        return semaphore;
    }

    public int getRunNumber() {
        return runNumber.get();
    }

    /**
     * Resume the crawler after a pause.
     */
    public void resume() {
        // Update checkpoint to RUNNING before waking crawler so disk state is correct (avoids race)
        synchronized (checkpointWriteLock) {
            FsCrawlerCheckpoint localCheckpoint = checkpoint.get();
            if (localCheckpoint != null && localCheckpoint.getState() == CrawlerState.PAUSED) {
                localCheckpoint.setState(CrawlerState.RUNNING);
                saveCheckpoint();
                logger.trace("Crawler resumed. Checkpoint updated.");
            }
        }
        this.userStopped.set(false);
        this.paused.set(false);
        synchronized (semaphore) {
            semaphore.notifyAll();
        }
    }

    @Override
    public void close() {
        this.closed.set(true);
        this.userStopped.set(false);
        logger.trace("Closing the parser {}", this.getClass().getSimpleName());
        
        // Capture volatile field to avoid race condition
        FsCrawlerCheckpoint localCheckpoint = checkpoint.get();
        // Save checkpoint before closing if we have one in progress
        if (localCheckpoint != null && localCheckpoint.getState() == CrawlerState.RUNNING) {
            localCheckpoint.setState(CrawlerState.STOPPED);
            saveCheckpoint();
        }
        
        try {
            if (crawlerPlugin != null) {
                crawlerPlugin.closeConnection();
            }
        } catch (Exception e) {
            logger.error("Error while closing crawler plugin", e);
            throw new RuntimeException(e);
        }
    }

    public void pause() {
        this.userStopped.set(true);
        this.paused.set(true);
        // Only overwrite checkpoint when a scan is in progress; do not replace COMPLETED during sleep phase
        FsCrawlerCheckpoint localCheckpoint = checkpoint.get();
        if (localCheckpoint != null && localCheckpoint.getState() != CrawlerState.COMPLETED
                && localCheckpoint.getState() != CrawlerState.ERROR) {
            localCheckpoint.setState(CrawlerState.PAUSED);
            saveCheckpoint();
            logger.trace("Crawler paused. Checkpoint saved.");
        }
    }

    @Override
    public void run() {
        logger.info("FS crawler started for [{}] for [{}] every [{}]", fsSettings.getName(),
                fsSettings.getFs().getUrl(),
                fsSettings.getFs().getUpdateRate());
        // If close() was called before this thread ran, exit without overwriting closed
        if (closed.get()) {
            return;
        }
        closed.set(false);
        while (true) {
            if (closed.get()) {
                logger.debug("FS crawler thread [{}] is now marked as closed...", fsSettings.getName());
                return;
            }

            // loop == 0: wait for resume before each run (start in pause)
            if (loop != null && loop == 0) {
                paused.set(true);
                waitForResume();
                if (closed.get()) {
                    return;
                }
                paused.set(false);
            }

            int run = runNumber.incrementAndGet();

            try {
                logger.info("Run #{}: job [{}]: starting...", run, fsSettings.getName());
                filesSinceLastCheckpoint = 0;

                String url = fsSettings.getFs().getUrl();
                if (crawlerPlugin == null || isNullOrEmpty(url)) {
                    // REST-only (no provider) or no folder to monitor: no-op run, checkpoint completed with 0 files
                    logger.info("Run #{}: job [{}]: skipping crawl (REST-only or no fs.url).", run, fsSettings.getName());
                    LocalDateTime scanDatenew = LocalDateTime.now().minusSeconds(2);
                    LocalDateTime nextCheck = scanDatenew.plus(fsSettings.getFs().getUpdateRate().millis(), ChronoUnit.MILLIS);
                    checkpoint.set(FsCrawlerCheckpoint.newCheckpoint(""));
                    checkpoint.get().ensureConcurrentCollections();
                    updateCheckpointAsCompleted(scanDatenew, nextCheck);
                } else {
                    ScanStatistic stats = new ScanStatistic(url);
                    LocalDateTime startDate = LocalDateTime.now();
                    stats.setStartTime(startDate);

                    crawlerPlugin.openConnection();

                    if (!crawlerPlugin.exists(url)) {
                        throw new RuntimeException(url + " doesn't exists.");
                    }

                    String rootPathId = SignTool.sign(url);
                    stats.setRootPathId(rootPathId);

                    // We need to round that latest date to the lower second and remove 2 seconds.
                    // See #82: https://github.com/dadoonet/fscrawler/issues/82
                    LocalDateTime scanDatenew = startDate.minusSeconds(2);

                    // Load or create checkpoint (handles migration from legacy _status.json)
                    checkpoint.set(loadOrCreateCheckpoint(url));
                    checkpoint.get().ensureConcurrentCollections();

                    // Restore stats from checkpoint if resuming
                    if (checkpoint.get().getFilesProcessed() > 0) {
                        stats.setNbDocScan((int) checkpoint.get().getFilesProcessed());
                        stats.setNbDocDeleted((int) checkpoint.get().getFilesDeleted());
                        logger.info("Resuming from checkpoint: {} files processed, {} directories pending",
                                checkpoint.get().getFilesProcessed(), checkpoint.get().getPendingPaths().size());
                    }

                    // We only index the root directory once (first run)
                    // That means that we don't have a scanDate yet (checkpoint.scanDate is MIN)
                    LocalDateTime scanDate = checkpoint.get().getScanDate();
                    if ((scanDate == null || scanDate.equals(LocalDateTime.MIN)) && fsSettings.getFs().isIndexFolders()) {
                        indexDirectory(url, url);
                    }

                    LocalDateTime effectiveScanDate = scanDate != null ? scanDate : LocalDateTime.MIN;

                    // Process directories using work queue instead of recursion
                    processDirectoriesWithCheckpoint(effectiveScanDate, stats);

                    stats.setEndTime(LocalDateTime.now());
                    stats.setNbDocScan((int) checkpoint.get().getFilesProcessed());
                    stats.setNbDocDeleted((int) checkpoint.get().getFilesDeleted());

                    // Compute the next check time by adding fsSettings.getFs().getUpdateRate().millis()
                    LocalDateTime nextCheck = scanDatenew.plus(fsSettings.getFs().getUpdateRate().millis(), ChronoUnit.MILLIS);
                    logger.info("Run #{}: job [{}]: indexed [{}], deleted [{}], documents up to [{}]. " +
                                    "Started at [{}], finished at [{}], took [{}]. " +
                                    "Will restart at [{}].", run, fsSettings.getName(),
                            stats.getNbDocScan(), stats.getNbDocDeleted(), scanDatenew,
                            stats.getStartTime(), stats.getEndTime(), durationToString(stats.computeDuration()),
                            nextCheck);

                    // If we completed successfully, update the checkpoint with completion info.
                    // Do not gate on !paused.get(): a pause arriving just after the scan finishes must not
                    // skip this, or scanEndTime stays null and the next run does a full rescan (losing progress).
                    if (!closed.get() && checkpoint.get().getState() != CrawlerState.ERROR) {
                        updateCheckpointAsCompleted(scanDatenew, nextCheck);
                    }
                }
            } catch (Exception e) {
                logger.warn("Error while crawling {}: {}", fsSettings.getFs().getUrl(), e.getMessage() == null ? e.getClass().getName() : e.getMessage());
                logger.debug(FULL_STACKTRACE_LOG_MESSAGE, e);
                
                // Save checkpoint on error so we can resume
                checkpoint.get().setState(CrawlerState.ERROR);
                checkpoint.get().setLastError(e.getMessage());
                saveCheckpoint();
            } finally {
                persistAclHashCacheIfNeeded();
                if (crawlerPlugin != null) {
                    try {
                        logger.debug("Closing FS crawler plugin [{}].", crawlerPlugin.getType());
                        crawlerPlugin.closeConnection();
                    } catch (Exception e) {
                        logger.warn("Error while closing the connection: {}", e.getMessage());
                        logger.debug(FULL_STACKTRACE_LOG_MESSAGE, e);
                    }
                }
            }

            // Unified between-runs: pause, then wait for resume OR updateRate (same behavior REST and non-REST)
            if (loop != null && loop > 0 && run >= loop) {
                // After run: exit if loop limit reached and !rest
                if (!rest) {
                    logger.info("FS crawler is stopping after {} run{}", run, run > 1 ? "s" : "");
                    closed.set(true);
                    return;
                }

                logger.info("FS crawler completed {} run{}, entering pause. Resume or wait for next run.", run, run > 1 ? "s" : "");
                userStopped.set(true);
            } else {
                logger.debug("Run completed. Entering pause. Resume or wait {} for next run.", fsSettings.getFs().getUpdateRate());
            }
            paused.set(true);
            FsCrawlerCheckpoint localCheckpoint = checkpoint.get();
            // Do not overwrite COMPLETED so REST /status can expose it (scanEndTime, nextCheck) between runs
            if (localCheckpoint != null && localCheckpoint.getState() != CrawlerState.ERROR
                    && localCheckpoint.getState() != CrawlerState.COMPLETED) {
                localCheckpoint.setState(CrawlerState.PAUSED);
                saveCheckpoint();
            }
            try {
                if (!closed.get()) {
                    synchronized (semaphore) {
                        long totalWaitTime = 0;
                        long maxWaitTime = fsSettings.getFs().getUpdateRate().millis();
                        while ((totalWaitTime < maxWaitTime || userStopped.get()) && paused.get() && !closed.get()) {
                            long waitTime = userStopped.get()
                                    ? CHECK_JOB_INTERVAL.millis()
                                    : Math.min(CHECK_JOB_INTERVAL.millis(), maxWaitTime - totalWaitTime);
                            semaphore.wait(waitTime);
                            totalWaitTime += waitTime;
                            logger.trace("Waking up after {} ms (resume or timeout). Waited {} / {} ms.", waitTime, totalWaitTime, maxWaitTime);
                            // When not user-stopped, allow external checkpoint change (e.g. nextCheck in past) to trigger early run
                            if (!userStopped.get()) {
                                try {
                                    FsCrawlerCheckpoint savedCheckpoint = checkpointHandler.read(fsSettings.getName());
                                    if (savedCheckpoint == null || savedCheckpoint.getNextCheck() == null
                                            || LocalDateTime.now().isAfter(savedCheckpoint.getNextCheck())) {
                                        logger.debug("Fs crawler is waking up because next check time [{}] is in the past.",
                                                savedCheckpoint != null ? savedCheckpoint.getNextCheck() : "null");
                                        break;
                                    }
                                } catch (IOException e) {
                                    logger.warn("Error while reading checkpoint: {}", e.getMessage());
                                }
                            }
                        }
                        logger.debug("Exiting pause: {} (resume or {} elapsed)", paused.get() ? "timeout" : "resume", totalWaitTime);
                    }
                }
            } catch (InterruptedException e) {
                logger.debug("Fs crawler thread has been interrupted: [{}]", e.getMessage());
                Thread.currentThread().interrupt();
            }
            if (closed.get()) {
                return;
            }
            paused.set(false);
        }
    }

    /**
     * Load an existing checkpoint or create a new one.
     * Handles three cases:
     * 1. Existing checkpoint with pending work (resume interrupted scan)
     * 2. Existing checkpoint with COMPLETED state (use its scanEndTime as lastrun)
     * 3. No checkpoint but legacy _status.json exists (migrate)
     * 4. Nothing exists (fresh start)
     *
     * When resuming, re-adds {@link FsCrawlerCheckpoint#getCurrentPath() currentPath} to the front
     * of pendingPaths if it is set and not already in the queue. This is required because the
     * crawler polls a path from pendingPaths and then sets currentPath; any checkpoint saved
     * between those points or during directory processing has currentPath set but the path
     * absent from pendingPaths, so recovery would otherwise lose that directory.
     */
    private FsCrawlerCheckpoint loadOrCreateCheckpoint(String rootPath) throws IOException {
        FsCrawlerCheckpoint existing = checkpointHandler.read(fsSettings.getName());
        if (existing != null) {
            if (existing.getState() == CrawlerState.COMPLETED) {
                // Previous scan completed - start fresh but use its scanEndTime as reference
                logger.debug("Found completed checkpoint for job [{}], starting new scan with lastrun [{}]",
                        fsSettings.getName(), existing.getScanEndTime());
                FsCrawlerCheckpoint newCheckpoint = FsCrawlerCheckpoint.newCheckpoint(rootPath);
                newCheckpoint.setScanDate(existing.getScanEndTime());
                return newCheckpoint;
            } else if (existing.hasPendingWork() || (existing.getCurrentPath() != null && !existing.getCurrentPath().isEmpty())) {
                // Interrupted scan - resume (either pending queue non-empty or currentPath set but path was polled out)
                if (existing.getCurrentPath() != null && !existing.getCurrentPath().isEmpty()
                        && !existing.isPending(existing.getCurrentPath())) {
                    existing.addPathFirst(existing.getCurrentPath());
                    logger.debug("Re-added currentPath [{}] to pending queue for resume", existing.getCurrentPath());
                }
                if (!existing.hasPendingWork()) {
                    logger.info("Found existing checkpoint for job [{}] with currentPath but empty pending queue, resuming scan", fsSettings.getName());
                } else {
                    logger.info("Found existing checkpoint for job [{}] with pending work, resuming scan", fsSettings.getName());
                }
                existing.setCurrentPath(null);
                existing.setState(CrawlerState.RUNNING);
                existing.resetRetryCount();
                return existing;
            } else {
                // Checkpoint exists but no pending work and no currentPath - start fresh
                logger.debug("Found checkpoint for job [{}] but no pending work, starting fresh", fsSettings.getName());
                FsCrawlerCheckpoint newCheckpoint = FsCrawlerCheckpoint.newCheckpoint(rootPath);
                newCheckpoint.setScanDate(existing.getScanEndTime() != null ? existing.getScanEndTime() : LocalDateTime.MIN);
                return newCheckpoint;
            }
        }

        return FsCrawlerCheckpoint.newCheckpoint(rootPath);
    }

    /**
     * Save the current checkpoint to disk.
     * Synchronized so close(), pause(), and the crawler thread do not write concurrently.
     */
    private void saveCheckpoint() {
        synchronized (checkpointWriteLock) {
            try {
                checkpointHandler.write(fsSettings.getName(), checkpoint.get());
                logger.trace("✅ Checkpoint saved: {} files processed, {} directories pending",
                        checkpoint.get().getFilesProcessed(), checkpoint.get().getPendingPaths().size());
            } catch (IOException e) {
                logger.warn("Failed to save checkpoint: {}", e.getMessage());
                logger.debug(FULL_STACKTRACE_LOG_MESSAGE, e);
            }
        }
    }

    /**
     * Update the checkpoint as completed with scan end time and next check time.
     * The checkpoint is kept (not deleted) to serve as the source of truth for lastrun.
     */
    private void updateCheckpointAsCompleted(LocalDateTime scanEndTime, LocalDateTime nextCheck) {
        FsCrawlerCheckpoint localCheckpoint = checkpoint.get();
        if (localCheckpoint == null) {
            return;
        }
        
        localCheckpoint.setState(CrawlerState.COMPLETED);
        localCheckpoint.setScanDate(scanEndTime);
        localCheckpoint.setScanEndTime(scanEndTime);
        localCheckpoint.setNextCheck(nextCheck);
        // Clear the working state (not needed after completion)
        localCheckpoint.clearPendingPaths();
        localCheckpoint.getCompletedPaths().clear();
        localCheckpoint.setCurrentPath(null);
        localCheckpoint.setCurrentPathFilesIndexedCount(0);
        localCheckpoint.setLastError(null);
        localCheckpoint.resetRetryCount();
        
        saveCheckpoint();
        logger.debug("💾 Checkpoint updated as completed: scanEndTime [{}], nextCheck [{}], indexed [{}], deleted [{}]",
                scanEndTime, nextCheck, localCheckpoint.getFilesProcessed(), localCheckpoint.getFilesDeleted());
    }

    /**
     * Maybe save checkpoint based on file count
     */
    private void maybeSaveCheckpoint() {
        filesSinceLastCheckpoint++;
        if (filesSinceLastCheckpoint >= CHECKPOINT_INTERVAL_FILES) {
            saveCheckpoint();
            filesSinceLastCheckpoint = 0;
        }
    }

    /**
     * Process directories using a work queue with checkpoint support
     */
    private void processDirectoriesWithCheckpoint(LocalDateTime lastScanDate, ScanStatistic stats) throws Exception {
        while (checkpoint.get().hasPendingWork() && !closed.get()) {
            // Handle pause
            if (paused.get()) {
                checkpoint.get().setState(CrawlerState.PAUSED);
                saveCheckpoint();
                waitForResume();
                if (closed.get()) {
                    return;
                }
                checkpoint.get().setState(CrawlerState.RUNNING);
            }

            String currentPath = checkpoint.get().pollNextPath();
            if (currentPath == null) {
                break;
            }
            
            // Skip if already completed (in case of resume with duplicates)
            if (checkpoint.get().isCompleted(currentPath)) {
                logger.debug("Skipping already completed directory: {}", currentPath);
                continue;
            }

            try {
                boolean fullyProcessed = processDirectory(currentPath, lastScanDate, stats);
                if (fullyProcessed) {
                    checkpoint.get().markCompleted(currentPath);
                    checkpoint.get().resetRetryCount();
                    maybeSaveCheckpoint();
                } else {
                    // Directory was interrupted (pause or close) - re-add to pending queue
                    checkpoint.get().addPath(currentPath);
                    saveCheckpoint();
                    // Use continue so the while loop re-checks paused/closed and can call
                    // waitForResume() directly instead of exiting to run() and triggering
                    // a full new scan cycle (connection close/reopen, checkpoint reload).
                    continue;
                }
            } catch (Exception e) {
                // Path already re-added in handleNetworkError; do not add again to avoid duplicate in pending queue
                if (e instanceof NetworkErrorRecoveryException) {
                    throw e;
                }
                // Network errors (including FsCrawlerPluginException with SocketException etc. cause) get retry
                if (isNetworkError(e)) {
                    handleNetworkError(e, currentPath);
                } else if (e instanceof IOException && fsSettings.getFs().isContinueOnError()) {
                    logger.warn("Error processing directory {}, continuing: {}", currentPath, e.getMessage());
                    checkpoint.get().markCompleted(currentPath);
                    checkpoint.get().setLastError(e.getMessage());
                } else {
                    checkpoint.get().addPathFirst(currentPath);
                    saveCheckpoint();
                    throw e;
                }
            }
        }
    }

    /**
     * Wait for resume signal when paused
     */
    private void waitForResume() {
        logger.info("Crawler is paused. Waiting for resume...");
        synchronized (semaphore) {
            while (paused.get() && !closed.get()) {
                try {
                    semaphore.wait(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        if (!closed.get()) {
            logger.info("Crawler resumed.");
        }
    }

    /**
     * Check if an exception is a network-related error (including when wrapped in
     * FsCrawlerPluginException or other RuntimeException by FTP/SSH plugins).
     */
    private boolean isNetworkError(Throwable e) {
        if (e instanceof java.net.SocketException
                || e instanceof java.net.SocketTimeoutException
                || e instanceof java.net.UnknownHostException) {
            return true;
        }
        // Safely check cause - only recurse if it's an Exception, not an Error
        if (e.getCause() instanceof Exception exception) {
            return isNetworkError(exception);
        }
        return false;
    }

    /**
     * Handle network errors with retry and exponential backoff.
     * On throw, the path has already been re-added; callers must not add it again
     * (they detect this via NetworkErrorRecoveryException).
     */
    private void handleNetworkError(Throwable e, String failedPath) {
        checkpoint.get().setLastError(e.getMessage());
        checkpoint.get().incrementRetryCount();

        // Re-add the path so it is not lost from the checkpoint (on resume or when we throw)
        checkpoint.get().addPathFirst(failedPath);
        saveCheckpoint();

        if (checkpoint.get().getRetryCount() > MAX_RETRIES) {
            checkpoint.get().setState(CrawlerState.ERROR);
            saveCheckpoint();
            throw new NetworkErrorRecoveryException(
                    "Max retries (" + MAX_RETRIES + ") exceeded for network errors", e);
        }

        // Exponential backoff (wait in chunks so we can respect shutdown; avoids blocking close() for minutes)
        long delayMs = INITIAL_RETRY_DELAY_MS * (long) Math.pow(2, checkpoint.get().getRetryCount() - (double) 1);
        logger.warn("Network error on path [{}], retry {}/{} in {}ms: {}",
                failedPath, checkpoint.get().getRetryCount(), MAX_RETRIES, delayMs, e.getMessage());

        try {
            crawlerPlugin.closeConnection();
            if (!FsCrawlerUtil.waitWithAbortCheck(Duration.ofMillis(delayMs), () -> closed.get())) {
                throw new NetworkErrorRecoveryException("Crawler closed during backoff", null);
            }
            crawlerPlugin.openConnection();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new NetworkErrorRecoveryException("Interrupted during backoff", ie);
        } catch (NetworkErrorRecoveryException recoveryEx) {
            throw recoveryEx; // rethrow as-is (e.g. "Crawler closed during backoff") to avoid double-wrapping
        } catch (Exception ex) {
            throw new NetworkErrorRecoveryException("Reconnect failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Process a single directory (non-recursive).
     * Subdirectories are added to the checkpoint's pending queue.
     * @return true if the directory was fully processed, false if interrupted by pause/close
     */
    private boolean processDirectory(String filepath, LocalDateTime lastScanDate, ScanStatistic stats) throws Exception {
        logger.debug("indexing [{}] content", filepath);

        if (closed.get()) {
            logger.debug("FS crawler thread [{}] is now marked as closed...", fsSettings.getName());
            return false;
        }

        // When resuming this path after an interrupt, skip incrementing filesProcessed for the first N files
        // we index (we still re-index them for idempotency) so we don't double-count. Only apply skip count
        // when this directory is the same as the one that was interrupted (checkpoint's currentPath is set on
        // interrupt and must not be overwritten before this check; we set it below after this block).
        FsCrawlerCheckpoint cp = checkpoint.get();
        int skipCount = (filepath.equals(cp.getCurrentPath()) && cp.getCurrentPathFilesIndexedCount() > 0)
                ? cp.getCurrentPathFilesIndexedCount()
                : 0;
        if (skipCount > 0) {
            cp.setCurrentPathFilesIndexedCount(0);
            logger.debug("Resuming directory [{}]: skipping count for first {} already-indexed files", filepath, skipCount);
        }
        cp.setCurrentPath(filepath);

        // Number of files we've indexed in this pass (counted or not); used when interrupted to persist resume state.
        int indexedInThisPass = 0;

        final Collection<FileAbstractModel> children = crawlerPlugin.getFiles(filepath);
        Collection<String> fsFiles = new ArrayList<>();
        Collection<String> fsFolders = new ArrayList<>();

        if (children != null) {
            boolean ignoreFolder = false;
            FileAbstractModel metadataFile = null;
            for (FileAbstractModel child : children) {
                // We check if we have a .fscrawlerignore file within this folder in which case
                // we want to ignore all files and subdirs
                if (child.getName().equalsIgnoreCase(FSCRAWLER_IGNORE_FILENAME)) {
                    logger.debug("We found a [{}] file in folder: [{}]. Let's skip it.", FSCRAWLER_IGNORE_FILENAME, filepath);
                    ignoreFolder = true;
                    break;
                }

                // We check if we have a .meta.yml file (or equivalent) within this folder in which case
                // we want to merge its content with the current file metadata
                if (child.getName().equalsIgnoreCase(metadataFilename)) {
                    logger.debug("We found a [{}] file in folder: [{}]", metadataFilename, filepath);
                    metadataFile = child;
                }
            }

            if (!ignoreFolder) {
                for (FileAbstractModel child : children) {
                    // Check for pause/close during processing
                    if (closed.get() || paused.get()) {
                        // Persist how many files we've indexed in this directory so on resume we skip
                        // counting them (avoid double-count); we do not roll back filesProcessed.
                        if (indexedInThisPass > 0) {
                            checkpoint.get().setCurrentPathFilesIndexedCount(indexedInThisPass);
                        }
                        saveCheckpoint();
                        return false;  // Interrupted - directory not fully processed
                    }

                    logger.trace("FileAbstractModel = {}", child);
                    String filename = child.getName();

                    // If the filename is the expected metadata file, we skip it
                    if (filename.equalsIgnoreCase(metadataFilename)) {
                        logger.trace("Skipping metadata file [{}]", filename);
                        continue;
                    }

                    String virtualFileName = computeVirtualPathName(stats.getRootPath(), computeRealPathName(filepath, filename));

                    // https://github.com/dadoonet/fscrawler/issues/1 : Filter documents
                    boolean isIndexable = isIndexable(child.isDirectory(), virtualFileName, fsSettings.getFs().getIncludes(), fsSettings.getFs().getExcludes());

                    logger.trace("[{}] can be indexed: [{}]", virtualFileName, isIndexable);
                    if (isIndexable) {
                        if (child.isFile()) {
                            logger.trace("  - file: {}", virtualFileName);
                            fsFiles.add(filename);
                            if (shouldIndexBecauseOfChanges(child, lastScanDate, filename, filepath)) {
                                logger.trace("    - modified: creation date {} , file date {}, last scan date {}",
                                        child.getCreationDate(), child.getLastModifiedDate(), lastScanDate);

                                if (isFileSizeUnderLimit(fsSettings.getFs().getIgnoreAbove(), child.getSize())) {
                                    InputStream inputStream = null;
                                    InputStream metadataStream = null;
                                    try {
                                        if (fsSettings.getFs().isIndexContent() || fsSettings.getFs().isStoreSource()) {
                                            inputStream = crawlerPlugin.getInputStream(child);
                                        }
                                        if (metadataFile != null) {
                                            metadataStream = crawlerPlugin.getInputStream(metadataFile);
                                        }
                                        indexFile(child, stats, filepath, inputStream, child.getSize(), metadataStream);
                                        if (skipCount > 0) {
                                            skipCount--;
                                        } else {
                                            stats.addFile();
                                            checkpoint.get().incrementFilesProcessed();
                                        }
                                        indexedInThisPass++;
                                        maybeSaveCheckpoint();
                                    } catch (Exception e) {
                                        if (fsSettings.getFs().isContinueOnError()) {
                                            logger.warn("Unable to index {}, skipping...: {}", filename, e.getMessage());
                                        } else {
                                            throw e;
                                        }
                                    } finally {
                                        if (metadataStream != null) {
                                            crawlerPlugin.closeInputStream(metadataStream);
                                        }
                                        if (inputStream != null) {
                                            crawlerPlugin.closeInputStream(inputStream);
                                        }
                                    }
                                } else {
                                    logger.debug("file [{}] has a size [{}] above the limit [{}]. We skip it.", filename,
                                            new ByteSizeValue(child.getSize()), fsSettings.getFs().getIgnoreAbove());
                                }
                            } else {
                                logger.trace("    - not modified: creation date {} , file date {}, last scan date {}",
                                        child.getCreationDate(), child.getLastModifiedDate(), lastScanDate);
                            }
                        } else if (child.isDirectory()) {
                            logger.debug("  - folder: {}", filename);
                            if (fsSettings.getFs().isIndexFolders()) {
                                fsFolders.add(child.getFullpath());
                                indexDirectory(child.getFullpath(), fsSettings.getFs().getUrl());
                            }
                            // Add subdirectory to pending queue instead of recursive call (avoid duplicates on resume)
                            FsCrawlerCheckpoint cpSub = checkpoint.get();
                            if (!cpSub.isCompleted(child.getFullpath()) && !cpSub.isPending(child.getFullpath())) {
                                cpSub.addPath(child.getFullpath());
                            }
                        } else {
                            logger.debug("  - other: {}", filename);
                            logger.debug("Not a file nor a dir. Skipping {}", child.getFullpath());
                        }
                    } else {
                        logger.debug("  - ignored file/dir: {}", filename);
                    }
                }

                logger.trace("End of parsing the folder [{}]", filepath);
            }
        }

        // Handle deleted files
        if (fsSettings.getFs().isRemoveDeleted()) {
            logger.debug("Looking for removed files in [{}]...", filepath);
            Collection<String> esFiles = getFileDirectory(filepath);

            // for the delete files
            for (String esfile : esFiles) {
                logger.trace("Checking file [{}]", esfile);

                String virtualFileName = computeVirtualPathName(stats.getRootPath(), computeRealPathName(filepath, esfile));
                if (isIndexable(false, virtualFileName, fsSettings.getFs().getIncludes(), fsSettings.getFs().getExcludes())
                        && !fsFiles.contains(esfile)) {
                    logger.trace("Removing file [{}] in elasticsearch", esfile);
                    esDelete(documentService, fsSettings.getElasticsearch().getIndex(), generateIdFromFilename(esfile, filepath));
                    stats.removeFile();
                    checkpoint.get().incrementFilesDeleted();
                }
            }

            if (fsSettings.getFs().isIndexFolders()) {
                logger.debug("Looking for removed directories in [{}]...", filepath);
                Collection<String> esFolders = getFolderDirectory(filepath);

                // for the delete folder
                for (String esfolder : esFolders) {
                    String virtualFileName = computeVirtualPathName(stats.getRootPath(), computeRealPathName(filepath, esfolder));
                    if (isIndexable(true, virtualFileName, fsSettings.getFs().getIncludes(), fsSettings.getFs().getExcludes())) {
                        logger.trace("Checking directory [{}]", esfolder);
                        if (!fsFolders.contains(esfolder)) {
                            logger.trace("Removing recursively directory [{}] in elasticsearch", esfolder);
                            removeEsDirectoryRecursively(esfolder, stats);
                        }
                    }
                }
            }
        }
        return true;  // Directory fully processed
    }


    private Map<String, String> loadAclHashCache(String jobName) {
        if (fsAclsFileHandler == null) {
            return new HashMap<>();
        }
        try {
            return fsAclsFileHandler.read(jobName);
        } catch (IOException e) {
            logger.warn("Failed to load ACL cache for [{}]: {}", jobName, e.getMessage());
            logger.debug(FULL_STACKTRACE_LOG_MESSAGE, e);
            return new HashMap<>();
        }
    }

    private FsAclsFileHandler initializeAclsFileHandler(FsSettings fsSettings, Path config) {
        if (fsSettings.getFs().isAttributesSupport() && fsSettings.getFs().isAclSupport()) {
            return new FsAclsFileHandler(config);
        }
        return null;
    }

    private Map<String, String> initializeAclCache(FsSettings fsSettings) {
        if (fsAclsFileHandler == null) {
            return new HashMap<>();
        }
        return loadAclHashCache(fsSettings.getName());
    }

    private String resolveMetadataFilename(FsSettings fsSettings) {
        if (fsSettings.getTags() != null && !StringUtils.isEmpty(fsSettings.getTags().getMetaFilename())) {
            String filename = fsSettings.getTags().getMetaFilename();
            logger.debug("We are going to use [{}] as meta file if found while crawling dirs", filename);
            return filename;
        }
        return null;
    }

    private byte[] loadStaticMetadata(FsSettings fsSettings) {
        if (fsSettings.getTags() == null || StringUtils.isEmpty(fsSettings.getTags().getStaticMetaFilename())) {
            return new byte[0];
        }

        File staticMetadataFile = new File(fsSettings.getTags().getStaticMetaFilename());
        if (!staticMetadataFile.exists() || !staticMetadataFile.isFile()) {
            throw new FsCrawlerIllegalConfigurationException("Static meta file [" + staticMetadataFile.getAbsolutePath() + "] does not exist or is not a file.");
        }
        try {
            byte[] data = FileUtils.readFileToByteArray(staticMetadataFile);
            logger.debug("We are going to use [{}] as the static meta file for every document", fsSettings.getTags().getStaticMetaFilename());
            return data;
        } catch (IOException e) {
            throw new FsCrawlerIllegalConfigurationException("Static meta file [" + staticMetadataFile.getAbsolutePath() + "] cannot be read.", e);
        }
    }

    private void persistAclHashCacheIfNeeded() {
        if (fsAclsFileHandler == null || !aclHashCacheDirty) {
            return;
        }
        try {
            fsAclsFileHandler.write(fsSettings.getName(), aclHashCache);
            aclHashCacheDirty = false;
        } catch (IOException e) {
            logger.warn("Failed to store ACL cache for [{}]: {}", fsSettings.getName(), e.getMessage());
            logger.debug(FULL_STACKTRACE_LOG_MESSAGE, e);
        }
    }

    private boolean shouldTrackAclChanges() {
        return fsAclsFileHandler != null;
    }

    private boolean hasAclChanged(String filename, String filepath, FileAbstractModel fileAbstractModel) throws NoSuchAlgorithmException {
        if (!shouldTrackAclChanges()) {
            return false;
        }
        String id = generateIdFromFilename(filename, filepath);
        List<FileAcl> acls = fileAbstractModel.getAcls();
        if (acls == null || acls.isEmpty()) {
            return aclHashCache.containsKey(id);
        }
        String currentHash = fileAbstractModel.getAclHash();
        if (currentHash == null) {
            currentHash = FsCrawlerUtil.computeAclHash(acls);
        }
        if (currentHash == null) {
            return false;
        }
        String previousHash = aclHashCache.get(id);
        if (previousHash == null) {
            return true;
        }
        return !Objects.equals(previousHash, currentHash);
    }

    private void rememberCurrentAclHash(String id, FileAbstractModel fileAbstractModel) {
        if (!shouldTrackAclChanges()) {
            return;
        }
        String currentHash = fileAbstractModel.getAclHash();
        if (currentHash == null) {
            currentHash = FsCrawlerUtil.computeAclHash(fileAbstractModel.getAcls());
        }
        if (currentHash == null) {
            if (aclHashCache.remove(id) != null) {
                aclHashCacheDirty = true;
            }
            return;
        }
        String previous = aclHashCache.put(id, currentHash);
        if (!Objects.equals(previous, currentHash)) {
            aclHashCacheDirty = true;
        }
    }

    private void removeStoredAclHash(String id) {
        if (!shouldTrackAclChanges()) {
            return;
        }
        if (aclHashCache.remove(id) != null) {
            aclHashCacheDirty = true;
        }
    }

    private boolean shouldIndexBecauseOfChanges(FileAbstractModel child, LocalDateTime lastScanDate, String filename, String filepath)
            throws NoSuchAlgorithmException {
        if (child.getLastModifiedDate().isAfter(lastScanDate)) {
            return true;
        }
        if (child.getCreationDate() != null && child.getCreationDate().isAfter(lastScanDate)) {
            return true;
        }
        if (shouldTrackAclChanges() && hasAclChanged(filename, filepath, child)) {
            logger.trace("    - ACL change detected for {}", child.getFullpath());
            return true;
        }
        return false;
    }

    private Collection<String> getFileDirectory(String path)
            throws Exception {
        // If the crawler is being closed, we return
        if (closed.get()) {
            return new ArrayList<>();
        }
        return managementService.getFileDirectory(path);
    }

    private Collection<String> getFolderDirectory(String path) throws Exception {
        // If the crawler is being closed, we return
        if (closed.get()) {
            return new ArrayList<>();
        }
        return managementService.getFolderDirectory(path);
    }

    /**
     * Index a file
     */
    private void indexFile(FileAbstractModel fileAbstractModel, ScanStatistic stats, String dirname, InputStream inputStream,
                           long filesize, InputStream externalTags) throws Exception {
        final String filename = fileAbstractModel.getName();
        final LocalDateTime created = fileAbstractModel.getCreationDate();
        final LocalDateTime lastModified = fileAbstractModel.getLastModifiedDate();
        final LocalDateTime lastAccessed = fileAbstractModel.getAccessDate();
        final String extension = fileAbstractModel.getExtension();
        final long size = fileAbstractModel.getSize();

        logger.trace("fetching content from [{}],[{}]", dirname, filename);
        String fullFilename = computeRealPathName(dirname, filename);

        // Create the Doc object (only needed when we have add_as_inner_object: true (default) or when we don't index json or xml)
        String id = generateIdFromFilename(filename, dirname);
        if (fsSettings.getFs().isAddAsInnerObject() || (!fsSettings.getFs().isJsonSupport() && !fsSettings.getFs().isXmlSupport())) {
            Doc doc = new Doc();

            // File
            doc.getFile().setFilename(filename);
            doc.getFile().setCreated(localDateTimeToDate(created));
            doc.getFile().setLastModified(localDateTimeToDate(lastModified));
            doc.getFile().setLastAccessed(localDateTimeToDate(lastAccessed));
            doc.getFile().setIndexingDate(localDateTimeToDate(LocalDateTime.now()));
            if (fsSettings.getServer() == null || PROTOCOL.LOCAL.equals(fsSettings.getServer().getProtocol())) {
                doc.getFile().setUrl("file://" + fullFilename);
            } else if (PROTOCOL.FTP.equals(fsSettings.getServer().getProtocol())) {
                doc.getFile().setUrl(String.format("ftp://%s:%d%s", fsSettings.getServer().getHostname(), fsSettings.getServer().getPort(), fullFilename));
            }
            doc.getFile().setExtension(extension);
            if (fsSettings.getFs().isAddFilesize()) {
                doc.getFile().setFilesize(size);
            }
            // File

            // Path
            // Encoded version of the dir this file belongs to
            doc.getPath().setRoot(SignTool.sign(dirname));
            // The virtual URL (not including the initial root dir)
            doc.getPath().setVirtual(computeVirtualPathName(stats.getRootPath(), fullFilename));
            // The real and complete filename
            doc.getPath().setReal(fullFilename);
            // Path

            // Attributes
            if (fsSettings.getFs().isAttributesSupport()) {
                doc.setAttributes(new Attributes());
                doc.getAttributes().setOwner(fileAbstractModel.getOwner());
                doc.getAttributes().setGroup(fileAbstractModel.getGroup());
                if (fileAbstractModel.getPermissions() >= 0) {
                    doc.getAttributes().setPermissions(fileAbstractModel.getPermissions());
                }
                if (fsSettings.getFs().isAclSupport()) {
                    List<FileAcl> fileAcls = fileAbstractModel.getAcls();
                    if (!fileAcls.isEmpty()) {
                        doc.getAttributes().setAcl(fileAcls);
                    }
                }
            }
            // Attributes

            // If needed, we generate the content in addition to metadata
            if (fsSettings.getFs().isJsonSupport()) {
                // https://github.com/dadoonet/fscrawler/issues/5 : Support JSon files
                doc.setObject(asMap(inputStream));
            } else if (fsSettings.getFs().isXmlSupport()) {
                // https://github.com/dadoonet/fscrawler/issues/185 : Support Xml files
                doc.setObject(XmlDocParser.generateMap(inputStream));
            } else {
                // Extracting content with Tika
                TikaDocParser.generate(fsSettings, inputStream, doc, filesize);
            }

            // Merge static metadata if available
            Doc mergedDoc = doc;
            if (staticMetadata.length > 0) {
                mergedDoc = DocUtils.getMergedDoc(doc, fsSettings.getTags().getStaticMetaFilename(),
                        new ByteArrayInputStream(staticMetadata));
            }
            // Merge metadata if available in the same folder
            mergedDoc = DocUtils.getMergedDoc(mergedDoc, metadataFilename, externalTags);

            // We index the data structure
            if (isIndexable(mergedDoc.getContent(), fsSettings.getFs().getFilters())) {
                if (!closed.get()) {
                    FSCrawlerLogger.documentDebug(id,
                            computeVirtualPathName(stats.getRootPath(), fullFilename),
                            "Indexing content");
                    documentService.index(
                            fsSettings.getElasticsearch().getIndex(),
                            id,
                            mergedDoc,
                            fsSettings.getElasticsearch().getPipeline());
                    rememberCurrentAclHash(id, fileAbstractModel);
                } else {
                    logger.warn("trying to add new file while closing crawler. Document [{}]/[{}] has been ignored",
                            fsSettings.getElasticsearch().getIndex(), id);
                }
            } else {
                logger.debug("We ignore file [{}] because it does not match all the patterns {}", filename,
                        fsSettings.getFs().getFilters());
            }
        } else {
            if (fsSettings.getFs().isJsonSupport()) {
                FSCrawlerLogger.documentDebug(generateIdFromFilename(filename, dirname),
                        computeVirtualPathName(stats.getRootPath(), fullFilename),
                        "Indexing json content");
                // We need to check that the provided file is actually a JSON file which can be parsed
                try {
                    DocumentContext documentContext = JsonUtil.parseJsonAsDocumentContext(inputStream);
                    String jsonString = documentContext.jsonString();

                    // We index the json content directly
                    if (!closed.get()) {
                        documentService.indexRawJson(
                                fsSettings.getElasticsearch().getIndex(),
                                id,
                                jsonString,
                                fsSettings.getElasticsearch().getPipeline());
                        rememberCurrentAclHash(id, fileAbstractModel);
                    } else {
                        logger.warn("trying to add new file while closing crawler. Document [{}]/[{}] has been ignored",
                                fsSettings.getElasticsearch().getIndex(), id);
                    }
                } catch (Exception e) {
                    logger.warn("Unable to parse JSON file [{}] in [{}]: {}", filename, dirname, e.getMessage());
                    logger.debug(FULL_STACKTRACE_LOG_MESSAGE, e);
                } finally {
                    if (inputStream != null) {
                        crawlerPlugin.closeInputStream(inputStream);
                    }
                }
            } else if (fsSettings.getFs().isXmlSupport()) {
                FSCrawlerLogger.documentDebug(generateIdFromFilename(filename, dirname),
                        computeVirtualPathName(stats.getRootPath(), fullFilename),
                        "Indexing xml content");
                // We need to check that the provided file is actually a JSON file which can be parsed
                try {
                    // We index the xml content directly (after transformation to json)
                    if (!closed.get()) {
                        documentService.indexRawJson(
                                fsSettings.getElasticsearch().getIndex(),
                                id,
                                XmlDocParser.generate(inputStream),
                                fsSettings.getElasticsearch().getPipeline());
                        rememberCurrentAclHash(id, fileAbstractModel);
                    } else {
                        logger.warn("trying to add new file while closing crawler. Document [{}]/[{}] has been ignored",
                                fsSettings.getElasticsearch().getIndex(), id);
                    }
                } catch (Exception e) {
                    logger.warn("Unable to parse XML file [{}] in [{}]: {}", filename, dirname, e.getMessage());
                    logger.debug(FULL_STACKTRACE_LOG_MESSAGE, e);
                } finally {
                    if (inputStream != null) {
                        crawlerPlugin.closeInputStream(inputStream);
                    }
                }
            }
        }
    }

    private String generateIdFromFilename(String filename, String filepath) throws NoSuchAlgorithmException {
        String filepathForId = filepath.replace("\\", "/");
        String filenameForId = filename.replace("\\", "").replace("/", "");
        String idSource = filepathForId.endsWith("/") ? filepathForId.concat(filenameForId) : filepathForId.concat("/").concat(filenameForId);
        return fsSettings.getFs().isFilenameAsId() ? filename : SignTool.sign(idSource);
    }

    /**
     * Index a folder object in elasticsearch
     * @param id        id of the folder
     * @param folder    path object
     */
    private void indexDirectory(String id, Folder folder) {
        if (!closed.get()) {
            managementService.storeVisitedDirectory(fsSettings.getElasticsearch().getIndexFolder(), id, folder);
        } else {
            logger.warn("trying to add new file while closing crawler. Document [{}]/[{}] has been ignored",
                    fsSettings.getElasticsearch().getIndexFolder(), id);
        }
    }

    /**
     * Index a directory
     * @param path complete path like "/", "/path/to/subdir", "C:\\dir", "C:/dir", "/C:/dir", "//SOMEONE/dir"
     * @param rootPath the root path we started from
     */
    private void indexDirectory(String path, String rootPath) throws Exception {
        // Find the last separator regardless of type to handle mixed separator scenarios on Windows
        int lastSepIndex = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        if (lastSepIndex < 0) {
            // Edge case: no separator found (e.g., root path like "C:")
            logger.warn("Cannot index directory [{}]: no path separator found. " +
                    "On Windows, please use backslashes (e.g., C:\\\\path\\\\to\\\\dir) in fs.url setting.", path);
            return;
        }
        String name = path.substring(lastSepIndex + 1);
        String rootdir = path.substring(0, lastSepIndex);

        File folderInfo = new File(path);

        Folder folder = new Folder(name,
                SignTool.sign(rootdir),
                path,
                computeVirtualPathName(rootPath, path),
                getCreationTime(folderInfo),
                getModificationTime(folderInfo),
                getLastAccessTime(folderInfo));

        if (fsSettings.getFs().isAttributesSupport() && (fsSettings.getServer() == null || PROTOCOL.LOCAL.equals(fsSettings.getServer().getProtocol()))) {
            Attributes attributes = new Attributes();
            attributes.setOwner(getOwnerName(folderInfo));
            attributes.setGroup(getGroupName(folderInfo));
            int permissions = getFilePermissions(folderInfo);
            if (permissions >= 0) {
                attributes.setPermissions(permissions);
            }
            if (fsSettings.getFs().isAclSupport()) {
                List<FileAcl> folderAcls = getFileAcls(folderInfo.toPath());
                if (!folderAcls.isEmpty()) {
                    attributes.setAcl(folderAcls);
                }
            }

            if (attributes.getOwner() != null || attributes.getGroup() != null || attributes.getAcl() != null || permissions >= 0) {
                folder.setAttributes(attributes);
            }
        }

        indexDirectory(SignTool.sign(path), folder);
    }

    /**
     * Remove a full directory and sub dirs recursively
     */
    private void removeEsDirectoryRecursively(final String path, ScanStatistic stats) throws Exception {
        logger.debug("Delete folder [{}]", path);
        Collection<String> listFile = getFileDirectory(path);

        for (String esfile : listFile) {
            esDelete(managementService, fsSettings.getElasticsearch().getIndex(), generateIdFromFilename(esfile, path));
            stats.removeFile();
            checkpoint.get().incrementFilesDeleted();
        }

        Collection<String> listFolder = getFolderDirectory(path);
        for (String esfolder : listFolder) {
            removeEsDirectoryRecursively(esfolder, stats);
        }

        esDelete(managementService, fsSettings.getElasticsearch().getIndexFolder(), SignTool.sign(path));
    }

    /**
     * Remove a document with the document service
     */
    private void esDelete(FsCrawlerDocumentService service, String index, String id) {
        logger.debug("Deleting {}/{}", index, id);
        if (!closed.get()) {
            service.delete(index, id);
            removeStoredAclHash(id);
        } else {
            logger.warn("trying to remove a file while closing crawler. Document [{}]/[{}] has been ignored", index, id);
        }
    }

    /**
     * Remove a document with the management service
     */
    private void esDelete(FsCrawlerManagementService service, String index, String id) {
        logger.debug("Deleting {}/{}", index, id);
        if (!closed.get()) {
            service.delete(index, id);
            removeStoredAclHash(id);
        } else {
            logger.warn("trying to remove a file while closing crawler. Document [{}]/[{}] has been ignored", index, id);
        }
    }
}
