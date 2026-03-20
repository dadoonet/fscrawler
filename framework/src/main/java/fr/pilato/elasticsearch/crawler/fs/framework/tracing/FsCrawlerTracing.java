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
package fr.pilato.elasticsearch.crawler.fs.framework.tracing;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;

/**
 * Thin wrapper around the OpenTelemetry API for FSCrawler instrumentation.
 *
 * <p>Uses {@link GlobalOpenTelemetry} so that when the elastic-otel-javaagent is attached it automatically picks up the
 * configured SDK. Without the agent the noop implementation bundled inside {@code opentelemetry-api} is used, which has
 * zero overhead.
 *
 * <p>Typical usage:
 *
 * <pre>{@code
 * Span span = FsCrawlerTracing.startSpan("fscrawler.file.index");
 * try (Scope scope = span.makeCurrent()) {
 *     span.setAttribute("fs.path", path);
 *     // ... do work ...
 * } catch (Exception e) {
 *     span.recordException(e);
 *     span.setStatus(StatusCode.ERROR, e.getMessage());
 *     throw e;
 * } finally {
 *     span.end();
 * }
 * }</pre>
 */
public final class FsCrawlerTracing {

    /** Instrumentation scope name used for all FSCrawler spans. */
    public static final String INSTRUMENTATION_NAME = "fscrawler";

    private FsCrawlerTracing() {
        // utility class
    }

    /**
     * Returns the shared {@link Tracer} for FSCrawler instrumentation. The instance is resolved lazily from
     * {@link GlobalOpenTelemetry} on each call; the OTel SDK caches it internally so there is no performance penalty.
     */
    public static Tracer tracer() {
        return GlobalOpenTelemetry.getTracer(INSTRUMENTATION_NAME);
    }

    /**
     * Starts a new span with the given name as a child of the current active span (if any). The caller is responsible
     * for calling {@link Span#end()} in a {@code finally} block.
     */
    public static Span startSpan(String spanName) {
        return tracer().spanBuilder(spanName).startSpan();
    }
}
