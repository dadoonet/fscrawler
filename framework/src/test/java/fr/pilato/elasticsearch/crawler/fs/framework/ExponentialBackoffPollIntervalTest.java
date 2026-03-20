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
import org.assertj.core.api.Assertions;
import org.awaitility.pollinterval.PollInterval;
import org.junit.Test;

public class ExponentialBackoffPollIntervalTest {

    @Test
    public void testExponentialBackoffPollInterval() {
        PollInterval pollInterval = new ExponentialBackoffPollInterval(Duration.ofMillis(500), Duration.ofSeconds(5));
        Duration duration = null;
        int pollCount = 0;

        // We use the start duration for the first poll
        duration = pollInterval.next(++pollCount, duration);
        Assertions.assertThat(duration).hasMillis(500);

        // Then we double it until we reach the max duration
        duration = pollInterval.next(++pollCount, duration);
        Assertions.assertThat(duration).hasSeconds(1);

        // Then we double it until we reach the max duration
        duration = pollInterval.next(++pollCount, duration);
        Assertions.assertThat(duration).hasSeconds(2);

        // Then we double it until we reach the max duration
        duration = pollInterval.next(++pollCount, duration);
        Assertions.assertThat(duration).hasSeconds(4);

        // Now we have reached the max duration
        duration = pollInterval.next(++pollCount, duration);
        Assertions.assertThat(duration).hasSeconds(5);

        // We stay at the max duration
        duration = pollInterval.next(++pollCount, duration);
        Assertions.assertThat(duration).hasSeconds(5);
    }
}
