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

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents a checkpoint for the crawler, allowing pause/resume functionality.
 * This is persisted to disk to enable recovery after crashes or manual stops.
 */
public class FsCrawlerCheckpoint {

    /**
     * Unique identifier for this scan session
     */
    private String scanId;

    /**
     * When this scan started
     */
    private LocalDateTime scanStartTime;

    /**
     * The directory currently being processed
     */
    private String currentPath;

    /**
     * Directories waiting to be processed (FIFO queue)
     */
    private Deque<String> pendingPaths;

    /**
     * Set of paths in {@link #pendingPaths} for O(1) membership checks during crawl.
     * Not persisted; rebuilt from pendingPaths in {@link #ensureConcurrentCollections()}.
     */
    @JsonIgnore
    private Set<String> pendingPathsSet;

    /**
     * Directories that have been fully processed
     */
    private Set<String> completedPaths;

    /**
     * Number of files indexed during this scan.
     * Atomic to allow concurrent increments by the crawler thread and reads by REST/checkpoint.
     */
    private final AtomicLong filesProcessed = new AtomicLong(0);

    /**
     * Number of files deleted during this scan.
     * Atomic to allow concurrent increments by the crawler thread and reads by REST/checkpoint.
     */
    private final AtomicLong filesDeleted = new AtomicLong(0);

    /**
     * Current state of the crawler.
     * Atomic to allow writes by the crawler thread and reads by REST/other threads (visibility).
     */
    private final AtomicReference<CrawlerState> state = new AtomicReference<>(CrawlerState.STOPPED);

    /**
     * Number of retry attempts after network errors.
     * Atomic to allow concurrent increments by the crawler thread and reads by REST/checkpoint.
     */
    private final AtomicInteger retryCount = new AtomicInteger(0);

    /**
     * Last error message encountered
     */
    private String lastError;

    /**
     * The scan date used for comparison (to detect modified files)
     */
    private LocalDateTime scanDate;

    /**
     * When this scan was completed (replaces FsJob.lastrun)
     */
    private LocalDateTime scanEndTime;

    /**
     * When the next scan should run (replaces FsJob.nextCheck)
     */
    private LocalDateTime nextCheck;

    /**
     * When the crawler was interrupted (pause/close) while processing {@link #currentPath},
     * this is the number of files already indexed in that directory. On resume we re-process
     * the directory from scratch (idempotent re-index) but skip incrementing {@link #filesProcessed}
     * for the first this-many files to avoid double-counting.
     */
    private int currentPathFilesIndexedCount;

    /**
     * True if this instance was loaded or created for the current run and installed via
     * FsParser.setCurrentCheckpoint(). Not persisted; ensures the error handler only overwrites
     * the checkpoint file when we actually loaded/created one this run (avoids overwriting a
     * valid checkpoint with the default empty instance).
     */
    @JsonIgnore
    private boolean loadedThisRun;

    public FsCrawlerCheckpoint() {
        this.pendingPaths = new ConcurrentLinkedDeque<>();
        this.pendingPathsSet = ConcurrentHashMap.newKeySet();
        this.completedPaths = ConcurrentHashMap.newKeySet();
        this.state.set(CrawlerState.STOPPED);
        this.filesProcessed.set(0);
        this.filesDeleted.set(0);
        this.retryCount.set(0);
    }

    /**
     * Ensure pending and completed paths use thread-safe collections so that
     * saveCheckpoint() can serialize while the crawler thread modifies them
     * (avoids ConcurrentModificationException). Call after deserializing from
     * JSON, since Jackson may create LinkedList/ArrayDeque and HashSet.
     */
    public void ensureConcurrentCollections() {
        if (pendingPaths == null) {
            this.pendingPaths = new ConcurrentLinkedDeque<>();
            this.pendingPathsSet = ConcurrentHashMap.newKeySet();
        } else {
            this.pendingPaths = new ConcurrentLinkedDeque<>(pendingPaths);
            if (pendingPathsSet == null) {
                pendingPathsSet = ConcurrentHashMap.newKeySet();
            } else {
                pendingPathsSet.clear();
            }
            pendingPathsSet.addAll(this.pendingPaths);
        }
        if (completedPaths == null) {
            this.completedPaths = ConcurrentHashMap.newKeySet();
        } else {
            Set<String> concurrent = ConcurrentHashMap.newKeySet();
            concurrent.addAll(completedPaths);
            this.completedPaths = concurrent;
        }
    }

    /**
     * Create a new checkpoint for a fresh scan
     * @param rootPath the root path to start scanning from
     * @return a new checkpoint initialized for scanning
     */
    public static FsCrawlerCheckpoint newCheckpoint(String rootPath) {
        FsCrawlerCheckpoint checkpoint = new FsCrawlerCheckpoint();
        checkpoint.setScanId(UUID.randomUUID().toString());
        checkpoint.setScanStartTime(LocalDateTime.now());
        checkpoint.addPath(rootPath);
        checkpoint.setState(CrawlerState.RUNNING);
        return checkpoint;
    }

    // Getters and Setters

    public String getScanId() {
        return scanId;
    }

    public void setScanId(String scanId) {
        this.scanId = scanId;
    }

    public LocalDateTime getScanStartTime() {
        return scanStartTime;
    }

    public void setScanStartTime(LocalDateTime scanStartTime) {
        this.scanStartTime = scanStartTime;
    }

    public String getCurrentPath() {
        return currentPath;
    }

    public void setCurrentPath(String currentPath) {
        this.currentPath = currentPath;
    }

    public Deque<String> getPendingPaths() {
        return pendingPaths;
    }

    public void setPendingPaths(Deque<String> pendingPaths) {
        if (pendingPaths == null) {
            this.pendingPaths = new ConcurrentLinkedDeque<>();
            if (pendingPathsSet == null) {
                pendingPathsSet = ConcurrentHashMap.newKeySet();
            } else {
                pendingPathsSet.clear();
            }
        } else {
            this.pendingPaths = pendingPaths;
            if (pendingPathsSet == null) {
                pendingPathsSet = ConcurrentHashMap.newKeySet();
            } else {
                pendingPathsSet.clear();
            }
            pendingPathsSet.addAll(pendingPaths);
        }
    }

    public Set<String> getCompletedPaths() {
        return completedPaths;
    }

    public void setCompletedPaths(Set<String> completedPaths) {
        this.completedPaths = completedPaths == null ? ConcurrentHashMap.newKeySet() : completedPaths;
    }

    public long getFilesProcessed() {
        return filesProcessed.get();
    }

    public void setFilesProcessed(long filesProcessed) {
        this.filesProcessed.set(filesProcessed);
    }

    public void incrementFilesProcessed() {
        this.filesProcessed.incrementAndGet();
    }

    public long getFilesDeleted() {
        return filesDeleted.get();
    }

    public void setFilesDeleted(long filesDeleted) {
        this.filesDeleted.set(filesDeleted);
    }

    public void incrementFilesDeleted() {
        this.filesDeleted.incrementAndGet();
    }

    public CrawlerState getState() {
        return state.get();
    }

    public void setState(CrawlerState state) {
        this.state.set(state);
    }

    public int getRetryCount() {
        return retryCount.get();
    }

    public void setRetryCount(int retryCount) {
        this.retryCount.set(retryCount);
    }

    public void incrementRetryCount() {
        this.retryCount.incrementAndGet();
    }

    public void resetRetryCount() {
        this.retryCount.set(0);
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public LocalDateTime getScanDate() {
        return scanDate;
    }

    public void setScanDate(LocalDateTime scanDate) {
        this.scanDate = scanDate;
    }

    public LocalDateTime getScanEndTime() {
        return scanEndTime;
    }

    public void setScanEndTime(LocalDateTime scanEndTime) {
        this.scanEndTime = scanEndTime;
    }

    public LocalDateTime getNextCheck() {
        return nextCheck;
    }

    public void setNextCheck(LocalDateTime nextCheck) {
        this.nextCheck = nextCheck;
    }

    public int getCurrentPathFilesIndexedCount() {
        return currentPathFilesIndexedCount;
    }

    public void setCurrentPathFilesIndexedCount(int currentPathFilesIndexedCount) {
        this.currentPathFilesIndexedCount = Math.max(0, currentPathFilesIndexedCount);
    }

    @JsonIgnore
    public boolean isLoadedThisRun() {
        return loadedThisRun;
    }

    @JsonIgnore
    public void setLoadedThisRun(boolean loadedThisRun) {
        this.loadedThisRun = loadedThisRun;
    }

    /**
     * Check if there are more directories to process
     * @return true if there are pending directories
     */
    public boolean hasPendingWork() {
        return !pendingPaths.isEmpty();
    }

    /**
     * Get the next directory to process without removing it
     * @return the next directory path or null if empty
     */
    public String peekNextPath() {
        return pendingPaths.peek();
    }

    /**
     * Get and remove the next directory to process
     * @return the next directory path or null if empty
     */
    public String pollNextPath() {
        String path = pendingPaths.poll();
        if (path != null && pendingPathsSet != null) {
            pendingPathsSet.remove(path);
        }
        return path;
    }

    /**
     * Add a directory to the front of the queue (for retry)
     * @param path the directory path to add
     */
    public void addPathFirst(String path) {
        pendingPaths.addFirst(path);
        if (pendingPathsSet != null) {
            pendingPathsSet.add(path);
        }
    }

    /**
     * Add a directory to the end of the queue
     * @param path the directory path to add
     */
    public void addPath(String path) {
        pendingPaths.addLast(path);
        if (pendingPathsSet != null) {
            pendingPathsSet.add(path);
        }
    }

    /**
     * Check if a path is in the pending queue (O(1)).
     * Use this instead of {@code getPendingPaths().contains(path)} to avoid O(n) scan.
     * @param path the directory path to check
     * @return true if the path is pending
     */
    public boolean isPending(String path) {
        if (path == null || pendingPaths == null || pendingPaths.isEmpty()) {
            return false;
        }
        if (pendingPathsSet == null) {
            // Defensive rebuild: pendingPathsSet is @JsonIgnore and may be null on partially initialized instances.
            pendingPathsSet = ConcurrentHashMap.newKeySet();
            pendingPathsSet.addAll(pendingPaths);
        }
        return pendingPathsSet.contains(path);
    }

    /**
     * Clear the pending queue and its lookup set (e.g. when marking scan completed).
     */
    public void clearPendingPaths() {
        pendingPaths.clear();
        if (pendingPathsSet != null) {
            pendingPathsSet.clear();
        }
    }

    /**
     * Mark a directory as completed
     * @param path the directory path that was completed
     */
    public void markCompleted(String path) {
        completedPaths.add(path);
    }

    /**
     * Check if a directory has already been processed
     * @param path the directory path to check
     * @return true if the directory was already processed
     */
    public boolean isCompleted(String path) {
        return completedPaths.contains(path);
    }

    /**
     * Compare pending paths by content and order so that equality holds after
     * round-trip serialization (Jackson may deserialize Deque to ArrayDeque
     * while we initialize as LinkedList, and LinkedList.equals(ArrayDeque) is false).
     */
    private static boolean pendingPathsEqual(Deque<String> a, Deque<String> b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.size() != b.size()) return false;
        Iterator<String> itA = a.iterator();
        Iterator<String> itB = b.iterator();
        while (itA.hasNext()) {
            if (!Objects.equals(itA.next(), itB.next())) return false;
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FsCrawlerCheckpoint that = (FsCrawlerCheckpoint) o;
        return filesProcessed.get() == that.filesProcessed.get() &&
                filesDeleted.get() == that.filesDeleted.get() &&
                retryCount.get() == that.retryCount.get() &&
                Objects.equals(scanId, that.scanId) &&
                Objects.equals(scanStartTime, that.scanStartTime) &&
                Objects.equals(currentPath, that.currentPath) &&
                pendingPathsEqual(pendingPaths, that.pendingPaths) &&
                Objects.equals(completedPaths, that.completedPaths) &&
                Objects.equals(state.get(), that.state.get()) &&
                Objects.equals(lastError, that.lastError) &&
                Objects.equals(scanDate, that.scanDate) &&
                Objects.equals(scanEndTime, that.scanEndTime) &&
                Objects.equals(nextCheck, that.nextCheck) &&
                currentPathFilesIndexedCount == that.currentPathFilesIndexedCount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(scanId, scanStartTime, currentPath,
                pendingPaths == null ? null : new ArrayList<>(pendingPaths),
                completedPaths,
                filesProcessed.get(), filesDeleted.get(), state.get(), retryCount.get(), lastError, scanDate,
                scanEndTime, nextCheck, currentPathFilesIndexedCount);
    }

    @Override
    public String toString() {
        return "FsCrawlerCheckpoint{" +
                "scanId='" + scanId + '\'' +
                ", state=" + state.get() +
                ", currentPath='" + currentPath + '\'' +
                ", pendingPaths=" + (pendingPaths != null ? pendingPaths.size() : 0) +
                ", completedPaths=" + (completedPaths != null ? completedPaths.size() : 0) +
                ", filesProcessed=" + filesProcessed.get() +
                ", filesDeleted=" + filesDeleted.get() +
                ", retryCount=" + retryCount.get() +
                ", scanEndTime=" + scanEndTime +
                ", nextCheck=" + nextCheck +
                ", currentPathFilesIndexedCount=" + currentPathFilesIndexedCount +
                '}';
    }
}
