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

package fr.pilato.elasticsearch.crawler.fs.framework.tracing;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;

/**
 * OTel metrics for FSCrawler functional counters.
 * <p>
 * Emits per-run metrics that complement the {@code fscrawler.crawl} span attributes:
 * <ul>
 *   <li>{@code fscrawler.docs.added}   — documents indexed during a run</li>
 *   <li>{@code fscrawler.docs.deleted} — documents deleted during a run</li>
 *   <li>{@code fscrawler.scan.duration} — wall-clock duration of a run (ms)</li>
 * </ul>
 * All instruments carry a {@code job.name} attribute for per-job breakdown.
 * </p>
 * <p>
 * Uses {@link GlobalOpenTelemetry} so that the noop implementation is used
 * when no OTel agent is present (zero overhead).
 * </p>
 */
public final class FsCrawlerMetrics {

    private FsCrawlerMetrics() {
        // utility class
    }

    private static Meter meter() {
        return GlobalOpenTelemetry.getMeter(FsCrawlerTracing.INSTRUMENTATION_NAME);
    }

    /**
     * Records the outcome of a completed crawl run as OTel metrics.
     *
     * @param jobName     FSCrawler job name (used as {@code job.name} attribute)
     * @param docsAdded   number of documents indexed during this run
     * @param docsDeleted number of documents deleted during this run
     * @param durationMs  wall-clock duration of the run in milliseconds
     */
    public static void recordScanCompletion(String jobName, int docsAdded, int docsDeleted, long durationMs) {
        Meter m = meter();
        Attributes attrs = Attributes.of(AttributeKey.stringKey("job.name"), jobName);

        m.counterBuilder("fscrawler.docs.added")
                .setDescription("Documents indexed during a crawl run")
                .setUnit("{document}")
                .build()
                .add(docsAdded, attrs);

        m.counterBuilder("fscrawler.docs.deleted")
                .setDescription("Documents deleted during a crawl run")
                .setUnit("{document}")
                .build()
                .add(docsDeleted, attrs);

        m.histogramBuilder("fscrawler.scan.duration")
                .setDescription("Wall-clock duration of a crawl run")
                .setUnit("ms")
                .ofLongs()
                .build()
                .record(durationMs, attrs);
    }
}
