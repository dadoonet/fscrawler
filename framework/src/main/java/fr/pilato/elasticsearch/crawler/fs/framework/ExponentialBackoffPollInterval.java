/*
 * Licensed to David Pilato (the "Author") under one
 * or more contributor license agreements. See the NOTICE file
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
 *
 * Made from 🇫🇷🇪🇺 with ❤️ - 2011-2026
 */
package fr.pilato.elasticsearch.crawler.fs.framework;

import java.time.Duration;
import org.awaitility.pollinterval.PollInterval;

/**
 * An implementation of {@link PollInterval} that provides an exponential backoff strategy. The wait time starts at a
 * specified duration and doubles with each poll, up to a defined maximum duration. This is useful for scenarios where
 * you want to gradually increase the wait time between polling attempts, such as when waiting for a resource to become
 * available without going above a max duration.
 */
public class ExponentialBackoffPollInterval implements PollInterval {

    private final Duration startDuration;
    private final Duration maxDuration;

    public ExponentialBackoffPollInterval(Duration startDuration, Duration maxDuration) {
        this.startDuration = startDuration;
        this.maxDuration = maxDuration;
    }

    /**
     * Syntactic sugar for creating a {@link ExponentialBackoffPollInterval}.
     *
     * @param startDuration The start duration (initial function value)
     * @param maxDuration The max duration (we don't go beyond this value)
     * @return A new instance of {@link ExponentialBackoffPollInterval}
     */
    public static ExponentialBackoffPollInterval exponential(Duration startDuration, Duration maxDuration) {
        return new ExponentialBackoffPollInterval(startDuration, maxDuration);
    }

    @Override
    public Duration next(int pollCount, Duration previousDuration) {
        if (pollCount == 1) {
            return startDuration;
        }
        Duration duration = previousDuration.multipliedBy(2);
        if (duration.compareTo(maxDuration) > 0) {
            duration = maxDuration;
        }
        return duration;
    }
}
