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

package fr.pilato.elasticsearch.crawler.fs.rest;

import fr.pilato.elasticsearch.crawler.fs.beans.CrawlerState;
import fr.pilato.elasticsearch.crawler.fs.beans.FsCrawlerCheckpoint;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Response object for the crawler status endpoint
 */
public class CrawlerStatusResponse extends RestResponse {

    private CrawlerState state;
    private String currentPath;
    private int pendingDirectories;
    private int completedDirectories;
    private long filesProcessed;
    private long filesDeleted;
    private LocalDateTime scanStartTime;
    private String elapsedTime;
    private int retryCount;
    private String lastError;
    private String scanId;

    public CrawlerStatusResponse() {
    }

    public CrawlerStatusResponse(FsCrawlerCheckpoint checkpoint) {
        this.state = checkpoint.getState();
        this.scanId = checkpoint.getScanId();
        this.currentPath = checkpoint.getCurrentPath();
        this.pendingDirectories = checkpoint.getPendingPaths().size();
        this.completedDirectories = checkpoint.getCompletedPaths().size();
        this.filesProcessed = checkpoint.getFilesProcessed();
        this.filesDeleted = checkpoint.getFilesDeleted();
        this.scanStartTime = checkpoint.getScanStartTime();
        this.retryCount = checkpoint.getRetryCount();
        this.lastError = checkpoint.getLastError();

        if (checkpoint.getScanStartTime() != null) {
            Duration elapsed = Duration.between(checkpoint.getScanStartTime(), LocalDateTime.now());
            setElapsedTime(elapsed);
        }
    }

    public CrawlerState getState() {
        return state;
    }

    public void setState(CrawlerState state) {
        this.state = state;
    }

    public String getCurrentPath() {
        return currentPath;
    }

    public void setCurrentPath(String currentPath) {
        this.currentPath = currentPath;
    }

    public int getPendingDirectories() {
        return pendingDirectories;
    }

    public void setPendingDirectories(int pendingDirectories) {
        this.pendingDirectories = pendingDirectories;
    }

    public int getCompletedDirectories() {
        return completedDirectories;
    }

    public void setCompletedDirectories(int completedDirectories) {
        this.completedDirectories = completedDirectories;
    }

    public long getFilesProcessed() {
        return filesProcessed;
    }

    public void setFilesProcessed(long filesProcessed) {
        this.filesProcessed = filesProcessed;
    }

    public long getFilesDeleted() {
        return filesDeleted;
    }

    public void setFilesDeleted(long filesDeleted) {
        this.filesDeleted = filesDeleted;
    }

    public LocalDateTime getScanStartTime() {
        return scanStartTime;
    }

    public void setScanStartTime(LocalDateTime scanStartTime) {
        this.scanStartTime = scanStartTime;
    }

    public String getElapsedTime() {
        return elapsedTime;
    }

    public void setElapsedTime(String elapsedTime) {
        this.elapsedTime = elapsedTime;
    }

    public void setElapsedTime(Duration duration) {
        if (duration != null) {
            long hours = duration.toHours();
            long minutes = duration.toMinutesPart();
            long seconds = duration.toSecondsPart();
            if (hours > 0) {
                this.elapsedTime = String.format("%dh %dm %ds", hours, minutes, seconds);
            } else if (minutes > 0) {
                this.elapsedTime = String.format("%dm %ds", minutes, seconds);
            } else {
                this.elapsedTime = String.format("%ds", seconds);
            }
        }
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public String getScanId() {
        return scanId;
    }

    public void setScanId(String scanId) {
        this.scanId = scanId;
    }
}
