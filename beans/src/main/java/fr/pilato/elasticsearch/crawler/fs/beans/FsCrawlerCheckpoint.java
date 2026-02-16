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

import java.time.LocalDateTime;
import java.util.*;

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
     * Directories that have been fully processed
     */
    private Set<String> completedPaths;

    /**
     * Number of files indexed during this scan
     */
    private long filesProcessed;

    /**
     * Number of files deleted during this scan
     */
    private long filesDeleted;

    /**
     * Current state of the crawler
     */
    private CrawlerState state;

    /**
     * Number of retry attempts after network errors
     */
    private int retryCount;

    /**
     * Last error message encountered
     */
    private String lastError;

    /**
     * The scan date used for comparison (to detect modified files)
     */
    private LocalDateTime scanDate;

    public FsCrawlerCheckpoint() {
        this.pendingPaths = new LinkedList<>();
        this.completedPaths = new HashSet<>();
        this.state = CrawlerState.STOPPED;
        this.filesProcessed = 0;
        this.filesDeleted = 0;
        this.retryCount = 0;
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
        checkpoint.getPendingPaths().add(rootPath);
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
        this.pendingPaths = pendingPaths;
    }

    public Set<String> getCompletedPaths() {
        return completedPaths;
    }

    public void setCompletedPaths(Set<String> completedPaths) {
        this.completedPaths = completedPaths;
    }

    public long getFilesProcessed() {
        return filesProcessed;
    }

    public void setFilesProcessed(long filesProcessed) {
        this.filesProcessed = filesProcessed;
    }

    public void incrementFilesProcessed() {
        this.filesProcessed++;
    }

    public long getFilesDeleted() {
        return filesDeleted;
    }

    public void setFilesDeleted(long filesDeleted) {
        this.filesDeleted = filesDeleted;
    }

    public void incrementFilesDeleted() {
        this.filesDeleted++;
    }

    public CrawlerState getState() {
        return state;
    }

    public void setState(CrawlerState state) {
        this.state = state;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }

    public void resetRetryCount() {
        this.retryCount = 0;
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
        return pendingPaths.poll();
    }

    /**
     * Add a directory to the front of the queue (for retry)
     * @param path the directory path to add
     */
    public void addPathFirst(String path) {
        pendingPaths.addFirst(path);
    }

    /**
     * Add a directory to the end of the queue
     * @param path the directory path to add
     */
    public void addPath(String path) {
        pendingPaths.addLast(path);
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FsCrawlerCheckpoint that = (FsCrawlerCheckpoint) o;
        return filesProcessed == that.filesProcessed &&
                filesDeleted == that.filesDeleted &&
                retryCount == that.retryCount &&
                Objects.equals(scanId, that.scanId) &&
                Objects.equals(scanStartTime, that.scanStartTime) &&
                Objects.equals(currentPath, that.currentPath) &&
                Objects.equals(pendingPaths, that.pendingPaths) &&
                Objects.equals(completedPaths, that.completedPaths) &&
                state == that.state &&
                Objects.equals(lastError, that.lastError) &&
                Objects.equals(scanDate, that.scanDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scanId, scanStartTime, currentPath, pendingPaths, completedPaths,
                filesProcessed, filesDeleted, state, retryCount, lastError, scanDate);
    }

    @Override
    public String toString() {
        return "FsCrawlerCheckpoint{" +
                "scanId='" + scanId + '\'' +
                ", state=" + state +
                ", currentPath='" + currentPath + '\'' +
                ", pendingPaths=" + pendingPaths.size() +
                ", completedPaths=" + completedPaths.size() +
                ", filesProcessed=" + filesProcessed +
                ", filesDeleted=" + filesDeleted +
                ", retryCount=" + retryCount +
                '}';
    }
}
