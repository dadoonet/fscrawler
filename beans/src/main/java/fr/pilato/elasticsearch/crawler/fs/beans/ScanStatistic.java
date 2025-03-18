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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provide Scan Statistics
 *
 * @author David Pilato (aka dadoonet)
 */
public class ScanStatistic {
    private final AtomicInteger nbDocScan = new AtomicInteger();
    private final AtomicInteger nbDocDeleted = new AtomicInteger();
    private String rootPath = "/";
    private String rootPathId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    public ScanStatistic() {
    }

    public ScanStatistic(String rootPath) {
        this.rootPath = rootPath;
    }

    /**
     * @return the nbDocScan
     */
    public int getNbDocScan() {
        return nbDocScan.get();
    }

    /**
     * @param nbDocScan the nbDocScan to set
     */
    public void setNbDocScan(int nbDocScan) {
        this.nbDocScan.set(nbDocScan);
    }

    /**
     * @return the nbDocDeleted
     */
    public int getNbDocDeleted() {
        return nbDocDeleted.get();
    }

    /**
     * @param nbDocDeleted the nbDocDeleted to set
     */
    public void setNbDocDeleted(int nbDocDeleted) {
        this.nbDocDeleted.set(nbDocDeleted);
    }

    /**
     * @return the rootPath
     */
    public String getRootPath() {
        return rootPath;
    }

    /**
     * @param rootPath the rootPath to set
     */
    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    /**
     * @return the rootPathId
     */
    public String getRootPathId() {
        return rootPathId;
    }

    /**
     * @param rootPathId the rootPathId to set
     */
    public void setRootPathId(String rootPathId) {
        this.rootPathId = rootPathId;
    }

    /**
     * Increment statistic for new files
     */
    public void addFile() {
        this.nbDocScan.incrementAndGet();
    }

    /**
     * Increment statistic for deleted files
     */
    public void removeFile() {
        this.nbDocDeleted.incrementAndGet();
    }

    /**
     * @return the start time of the scan
     */
    public LocalDateTime getStartTime() {
        return startTime;
    }

    /**
     * @param startTime the start time to set
     */
    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    /**
     * @return the end time of the scan
     */
    public LocalDateTime getEndTime() {
        return endTime;
    }

    /**
     * @param endTime the end time to set
     */
    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public Duration computeDuration() {
        if (startTime != null && endTime != null) {
            return Duration.between(startTime, endTime);
        }
        return Duration.ZERO;
    }
}
