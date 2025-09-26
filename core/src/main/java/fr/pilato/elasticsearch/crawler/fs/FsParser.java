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

import java.util.concurrent.atomic.AtomicInteger;

public abstract class FsParser implements Runnable, AutoCloseable {
    static final Object semaphore = new Object();
    final AtomicInteger runNumber = new AtomicInteger(0);
    boolean closed = true;

    public void close() {
        this.closed = true;
    }

    public boolean isClosed() {
        return closed;
    }

    Object getSemaphore() {
        return semaphore;
    }

    public int getRunNumber() {
        return runNumber.get();
    }
}

