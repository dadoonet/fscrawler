/*
 * Licensed to David Pilato under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

package fr.pilato.elasticsearch.crawler.fs.framework;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Helper to wait for a given resource to be available
 * @param <T> Type of object that the supplier must provide (the resource)
 */
public class Waiter<T> {
    // After 1s, we stop growing the sleep interval exponentially and just sleep 1s until maxWaitTime
    private static final long AWAIT_BUSY_THRESHOLD = 1000L;

    private final long maxTimeInMillis;

    /**
     * Wait by default for 10s
     */
    public Waiter() {
        maxTimeInMillis = 10000L;
    }

    /**
     * Build a waiter for a given timeout
     * @param maxWaitTime   Max time
     * @param unit          Unit for maxWaitTime
     */
    public Waiter(long maxWaitTime, TimeUnit unit) {
        maxTimeInMillis = TimeUnit.MILLISECONDS.convert(maxWaitTime, unit);
    }

    /**
     * Wait for a given amount of time for a resource to be available
     * @param breakSupplier the resource supplier. While the resource is null, the waiter will continue to wait.
     * @return the resource found
     * @throws InterruptedException in case the thread was interrupted while waiting
     */
    public T awaitBusy(Supplier<T> breakSupplier) throws InterruptedException {
        long timeInMillis = 1;
        long sum = 0;
        while (sum + timeInMillis < maxTimeInMillis) {
            T resource = breakSupplier.get();
            if (resource != null) {
                return resource;
            }
            Thread.sleep(timeInMillis);
            sum += timeInMillis;
            timeInMillis = Math.min(AWAIT_BUSY_THRESHOLD, timeInMillis * 2);
        }
        timeInMillis = maxTimeInMillis - sum;
        Thread.sleep(Math.max(timeInMillis, 0));
        return null;
    }
}
