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

import fr.pilato.elasticsearch.crawler.fs.beans.CrawlerState;
import fr.pilato.elasticsearch.crawler.fs.beans.FsCrawlerCheckpoint;
import fr.pilato.elasticsearch.crawler.fs.beans.FsCrawlerCheckpointFileHandler;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class FsParser implements Runnable, AutoCloseable {
    static final Object semaphore = new Object();
    final AtomicInteger runNumber = new AtomicInteger(0);
    final AtomicBoolean closed = new AtomicBoolean(true);
    final AtomicBoolean paused = new AtomicBoolean(false);

    public void close() {
        this.closed.set(true);
    }

    public boolean isClosed() {
        return closed.get();
    }

    /**
     * Pause the crawler. The crawler will save its checkpoint and wait for resume.
     */
    public void pause() {
        this.paused.set(true);
    }

    /**
     * Resume the crawler after a pause.
     */
    public void resume() {
        this.paused.set(false);
        synchronized (semaphore) {
            semaphore.notifyAll();
        }
    }

    /**
     * Check if the crawler is paused.
     * @return true if paused
     */
    public boolean isPaused() {
        return paused.get();
    }

    /**
     * Get the current state of the crawler.
     * @return the crawler state
     */
    public abstract CrawlerState getState();

    /**
     * Get the current checkpoint (progress) of the crawler.
     * @return the checkpoint or null if no scan is in progress
     */
    public abstract FsCrawlerCheckpoint getCheckpoint();

    /**
     * Get the checkpoint file handler for persistence operations.
     * @return the checkpoint file handler or null if not supported
     */
    public abstract FsCrawlerCheckpointFileHandler getCheckpointHandler();

    Object getSemaphore() {
        return semaphore;
    }

    public int getRunNumber() {
        return runNumber.get();
    }
}

