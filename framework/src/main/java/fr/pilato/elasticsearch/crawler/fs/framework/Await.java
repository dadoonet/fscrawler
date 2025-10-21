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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * Await utility class
 */
public class Await {

    private static final Logger logger = LogManager.getLogger();

    // After 5s, we stop growing the sleep interval exponentially and just sleep 5s until maxWaitTime
    private static final long AWAIT_BUSY_THRESHOLD = 5000L;

    /**
     * Wait until a condition is met or a 10s timeout is reached
     * @param breakSupplier the condition to check
     * @return true if the condition is met, false if the timeout is reached
     * @throws InterruptedException if the thread is interrupted
     */
    public static boolean awaitBusy(BooleanSupplier breakSupplier) throws InterruptedException {
        return Await.awaitBusy(breakSupplier, TimeValue.timeValueSeconds(10));
    }

    /**
     * Wait until a condition is met or a timeout is reached
     * @param breakSupplier the condition to check
     * @param maxWaitTime   max wait time
     * @return true if the condition is met, false if the timeout is reached
     * @throws InterruptedException if the thread is interrupted
     */
    public static boolean awaitBusy(BooleanSupplier breakSupplier, TimeValue maxWaitTime) throws InterruptedException {
        long maxTimeInMillis = maxWaitTime.millis();
        long timeInMillis = 500;
        long sum = 0;
        while (sum + timeInMillis < maxTimeInMillis) {
            if (breakSupplier.getAsBoolean()) {
                return true;
            }
            Thread.sleep(timeInMillis);
            sum += timeInMillis;
            timeInMillis = Math.min(AWAIT_BUSY_THRESHOLD, timeInMillis * 2);
        }
        timeInMillis = maxTimeInMillis - sum;
        Thread.sleep(Math.max(timeInMillis, 0));
        return breakSupplier.getAsBoolean();
    }

    /**
     * Wait until a condition is met or a timeout is reached
     * @param breakSupplier the condition to check
     * @param expected     the expected value
     * @param maxWaitTime  max wait time
     * @return the value of the breakSupplier
     * @throws InterruptedException if the thread is interrupted
     */
    public static long awaitBusy(LongSupplier breakSupplier, Long expected, TimeValue maxWaitTime) throws InterruptedException {
        long maxTimeInMillis = maxWaitTime.millis();
        long timeInMillis = 500;
        long sum = 0;

        while (sum + timeInMillis < maxTimeInMillis) {
            long current = breakSupplier.getAsLong();
            logger.trace("Check if {} is equal to {}", current, expected);
            if ((expected == null && current >= 1) || (expected != null && current == expected)) {
                return current;
            }
            logger.trace("Sleep for {} because {} is not equal to {}", timeInMillis, current, expected);
            Thread.sleep(timeInMillis);
            sum += timeInMillis;
            timeInMillis = Math.min(AWAIT_BUSY_THRESHOLD, timeInMillis * 2);
        }
        timeInMillis = maxTimeInMillis - sum;
        Thread.sleep(Math.max(timeInMillis, 0));
        return breakSupplier.getAsLong();
    }
}
