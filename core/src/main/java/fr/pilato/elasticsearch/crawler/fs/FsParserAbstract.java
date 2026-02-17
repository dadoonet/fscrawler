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
import java.util.concurrent.atomic.AtomicReference;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.*;
import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.asMap;

public class FsParserAbstract extends FsParser {
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
    private final Map<String, String> aclHashCache;
    private boolean aclHashCacheDirty;
    private final FsCrawlerExtensionFsProvider crawlerPlugin;
    private final String metadataFilename;
    private final byte[] staticMetadata;
    private static final TimeValue CHECK_JOB_INTERVAL = TimeValue.timeValueSeconds(5);

    // Checkpoint for current scan
    private final AtomicReference<FsCrawlerCheckpoint> checkpoint = new AtomicReference<>(new FsCrawlerCheckpoint());
    private int filesSinceLastCheckpoint = 0;

    public FsParserAbstract(FsSettings fsSettings, Path config, FsCrawlerManagementService managementService,
                           FsCrawlerDocumentService documentService, Integer loop,
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
        logger.debug("creating fs crawler thread [{}] for [{}] every [{}]", fsSettings.getName(),
                fsSettings.getFs().getUrl(),
                fsSettings.getFs().getUpdateRate());

        metadataFilename = resolveMetadataFilename(fsSettings);
        staticMetadata = loadStaticMetadata(fsSettings);
    }

    @Override
    public CrawlerState getState() {
        // Capture volatile field to avoid race condition
        FsCrawlerCheckpoint localCheckpoint = checkpoint.get();
        if (localCheckpoint != null) {
            return localCheckpoint.getState();
        }
        if (closed.get()) {
            return CrawlerState.STOPPED;
        }
        if (paused.get()) {
            return CrawlerState.PAUSED;
        }
        return CrawlerState.RUNNING;
    }

    @Override
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

    /**
     * Resume the crawler after a pause.
     */
    @Override
    public void resume() {
        super.resume();

        // Also update the checkpoint
        checkpoint.get().setState(CrawlerState.RUNNING);
        saveCheckpoint();
        logger.trace("Crawler resumed. Checkpoint updated.");
    }

    @Override
    public void close() {
        super.close();
        logger.trace("Closing the parser {}", this.getClass().getSimpleName());
        
        // Capture volatile field to avoid race condition
        FsCrawlerCheckpoint localCheckpoint = checkpoint.get();
        // Save checkpoint before closing if we have one in progress
        if (localCheckpoint != null && localCheckpoint.getState() == CrawlerState.RUNNING) {
            localCheckpoint.setState(CrawlerState.STOPPED);
            saveCheckpoint();
        }
        
        try {
            crawlerPlugin.closeConnection();
        } catch (Exception e) {
            logger.error("Error while closing crawler plugin", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void pause() {
        super.pause();
        // Also update the checkpoint state to PAUSED so that it is saved on disk immediately
        checkpoint.get().setState(CrawlerState.PAUSED);
        saveCheckpoint();
        logger.trace("Crawler paused. Checkpoint saved.");
    }

    @Override
    public void run() {
        logger.info("FS crawler started for [{}] for [{}] every [{}]", fsSettings.getName(),
                fsSettings.getFs().getUrl(),
                fsSettings.getFs().getUpdateRate());
        closed.set(false);
        while (true) {
            if (closed.get()) {
                logger.debug("FS crawler thread [{}] is now marked as closed...", fsSettings.getName());
                return;
            }

            int run = runNumber.incrementAndGet();

            try {
                logger.info("Run #{}: job [{}]: starting...", run, fsSettings.getName());
                ScanStatistic stats = new ScanStatistic(fsSettings.getFs().getUrl());
                LocalDateTime startDate = LocalDateTime.now();
                stats.setStartTime(startDate);

                crawlerPlugin.openConnection();

                if (!crawlerPlugin.exists(fsSettings.getFs().getUrl())) {
                    throw new RuntimeException(fsSettings.getFs().getUrl() + " doesn't exists.");
                }

                String rootPathId = SignTool.sign(fsSettings.getFs().getUrl());
                stats.setRootPathId(rootPathId);

                // We need to round that latest date to the lower second and remove 2 seconds.
                // See #82: https://github.com/dadoonet/fscrawler/issues/82
                LocalDateTime scanDatenew = startDate.minusSeconds(2);

                // Load or create checkpoint (handles migration from legacy _status.json)
                checkpoint.set(loadOrCreateCheckpoint(fsSettings.getFs().getUrl()));
                
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
                    indexDirectory(fsSettings.getFs().getUrl(), fsSettings.getFs().getUrl());
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

                // If we completed successfully, update the checkpoint with completion info
                if (!closed.get() && !paused.get() && checkpoint.get().getState() != CrawlerState.ERROR) {
                    updateCheckpointAsCompleted(scanDatenew, nextCheck);
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
                try {
                    logger.debug("Closing FS crawler plugin [{}].", crawlerPlugin.getType());
                    crawlerPlugin.closeConnection();
                } catch (Exception e) {
                    logger.warn("Error while closing the connection: {}", e.getMessage());
                    logger.debug(FULL_STACKTRACE_LOG_MESSAGE, e);
                }
            }

            if (loop > 0 && run >= loop) {
                logger.info("FS crawler is stopping after {} run{}", run, run > 1 ? "s" : "");
                closed.set(true);
                return;
            }

            try {
                logger.debug("Fs crawler is going to sleep for {}", fsSettings.getFs().getUpdateRate());

                // The problem here is that there is no wait to close the thread while we are sleeping.
                // Which leads to Zombie threads in our tests

                if (!closed.get()) {
                    synchronized (semaphore) {
                        long totalWaitTime = 0;
                        long maxWaitTime = fsSettings.getFs().getUpdateRate().millis();
                        while (totalWaitTime < maxWaitTime && !closed.get()) {
                            long waitTime = Math.min(CHECK_JOB_INTERVAL.millis(), maxWaitTime - totalWaitTime);
                            semaphore.wait(waitTime);
                            totalWaitTime += waitTime;
                            logger.trace("Waking up after {} ms to check if the condition changed. We waited for {} in total on {}...", waitTime, totalWaitTime, maxWaitTime);

                            // Read again the checkpoint to check if we need to stop the crawler
                            try {
                                FsCrawlerCheckpoint savedCheckpoint = checkpointHandler.read(fsSettings.getName());
                                if (savedCheckpoint == null || savedCheckpoint.getNextCheck() == null
                                        || LocalDateTime.now().isAfter(savedCheckpoint.getNextCheck())) {
                                    logger.debug("Fs crawler is waking up because next check time [{}] is in the past.", 
                                            savedCheckpoint != null ? savedCheckpoint.getNextCheck() : "null");
                                    break; // Exit the loop to re-run the crawler
                                }
                            } catch (IOException e) {
                                logger.warn("Error while reading checkpoint: {}", e.getMessage());
                            }
                        }
                        logger.debug("Fs crawler is now waking up again after a total wait time of {}...", totalWaitTime);
                    }
                }
            } catch (InterruptedException e) {
                logger.debug("Fs crawler thread has been interrupted: [{}]", e.getMessage());
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Load an existing checkpoint or create a new one.
     * Handles three cases:
     * 1. Existing checkpoint with pending work (resume interrupted scan)
     * 2. Existing checkpoint with COMPLETED state (use its scanEndTime as lastrun)
     * 3. No checkpoint but legacy _status.json exists (migrate)
     * 4. Nothing exists (fresh start)
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
            } else if (existing.hasPendingWork()) {
                // Interrupted scan - resume
                logger.info("Found existing checkpoint for job [{}] with pending work, resuming scan", fsSettings.getName());
                existing.setState(CrawlerState.RUNNING);
                existing.resetRetryCount();
                return existing;
            } else {
                // Checkpoint exists but no pending work and not completed - start fresh
                logger.debug("Found checkpoint for job [{}] but no pending work, starting fresh", fsSettings.getName());
                FsCrawlerCheckpoint newCheckpoint = FsCrawlerCheckpoint.newCheckpoint(rootPath);
                newCheckpoint.setScanDate(existing.getScanEndTime() != null ? existing.getScanEndTime() : LocalDateTime.MIN);
                return newCheckpoint;
            }
        }

        return FsCrawlerCheckpoint.newCheckpoint(rootPath);
    }

    /**
     * Save the current checkpoint to disk
     */
    private void saveCheckpoint() {
        try {
            checkpointHandler.write(fsSettings.getName(), checkpoint.get());
            logger.trace("✅ Checkpoint saved: {} files processed, {} directories pending",
                    checkpoint.get().getFilesProcessed(), checkpoint.get().getPendingPaths().size());
        } catch (IOException e) {
            logger.warn("Failed to save checkpoint: {}", e.getMessage());
            logger.debug(FULL_STACKTRACE_LOG_MESSAGE, e);
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
        localCheckpoint.setScanEndTime(scanEndTime);
        localCheckpoint.setNextCheck(nextCheck);
        // Clear the working state (not needed after completion)
        localCheckpoint.getPendingPaths().clear();
        localCheckpoint.getCompletedPaths().clear();
        localCheckpoint.setCurrentPath(null);
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

            checkpoint.get().setCurrentPath(currentPath);
            
            try {
                boolean fullyProcessed = processDirectory(currentPath, lastScanDate, stats);
                if (fullyProcessed) {
                    checkpoint.get().markCompleted(currentPath);
                    checkpoint.get().resetRetryCount();
                    maybeSaveCheckpoint();
                } else {
                    // Directory was interrupted - re-add to pending queue for resume
                    checkpoint.get().addPath(currentPath);
                    saveCheckpoint();
                    return;  // Exit the processing loop
                }
            } catch (IOException e) {
                if (isNetworkError(e)) {
                    handleNetworkError(e, currentPath);
                } else if (fsSettings.getFs().isContinueOnError()) {
                    logger.warn("Error processing directory {}, continuing: {}", currentPath, e.getMessage());
                    checkpoint.get().markCompleted(currentPath);
                    checkpoint.get().setLastError(e.getMessage());
                } else {
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
     * Check if an exception is a network-related error
     */
    private boolean isNetworkError(Exception e) {
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
     * Handle network errors with retry and exponential backoff
     */
    private void handleNetworkError(Exception e, String failedPath) {
        checkpoint.get().setLastError(e.getMessage());
        checkpoint.get().incrementRetryCount();

        if (checkpoint.get().getRetryCount() > MAX_RETRIES) {
            checkpoint.get().setState(CrawlerState.ERROR);
            saveCheckpoint();
            throw new RuntimeException("Max retries (" + MAX_RETRIES + ") exceeded for network errors", e);
        }

        // Re-add the path to retry
        checkpoint.get().addPathFirst(failedPath);
        saveCheckpoint();

        // Exponential backoff
        long delay = INITIAL_RETRY_DELAY_MS * (long) Math.pow(2, checkpoint.get().getRetryCount() - (double) 1);
        logger.warn("Network error on path [{}], retry {}/{} in {}ms: {}",
                failedPath, checkpoint.get().getRetryCount(), MAX_RETRIES, delay, e.getMessage());

        // Close and reopen connection
        crawlerPlugin.closeConnection();
        FsCrawlerUtil.waitFor(Duration.ofMillis(delay));
        crawlerPlugin.openConnection();
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
                                        stats.addFile();
                                        checkpoint.get().incrementFilesProcessed();
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
                            // Add subdirectory to pending queue instead of recursive call
                            if (!checkpoint.get().isCompleted(child.getFullpath())) {
                                checkpoint.get().addPath(child.getFullpath());
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
