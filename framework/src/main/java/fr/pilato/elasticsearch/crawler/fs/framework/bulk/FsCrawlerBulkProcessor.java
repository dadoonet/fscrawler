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
package fr.pilato.elasticsearch.crawler.fs.framework.bulk;

import fr.pilato.elasticsearch.crawler.fs.framework.ByteSizeValue;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.framework.tracing.FsCrawlerTracing;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Bulk processor */
public class FsCrawlerBulkProcessor<
                O extends FsCrawlerOperation<O>, Q extends FsCrawlerBulkRequest<O>, S extends FsCrawlerBulkResponse<O>>
        implements Closeable {

    private static final Logger logger = LogManager.getLogger();

    private final int bulkActions;
    private final ByteSizeValue byteSize;
    private final Listener<O, Q, S> listener;
    private final Engine<O, Q, S> engine;
    private Q bulkRequest;
    private final Supplier<Q> requestSupplier;
    private final ScheduledExecutorService executor;
    private final LongSupplier nanoTimeSupplier;
    private final PriorityQueue<DeferredRetry> retries =
            new PriorityQueue<>(Comparator.comparingLong((DeferredRetry retry) -> retry.dueNanos)
                    .thenComparingLong(retry -> retry.sequence));
    private long retrySequence;
    private ScheduledFuture<?> retryWakeup;
    private long retryWakeupGeneration;
    private boolean closed = false;

    private final class DeferredRetry {
        final O operation;
        final long dueNanos;
        final long deadlineNanos;
        final Consumer<Exception> terminalFailure;
        final long sequence;

        DeferredRetry(O operation, long dueNanos, long deadlineNanos, Consumer<Exception> terminalFailure) {
            this.operation = operation;
            this.dueNanos = dueNanos;
            this.deadlineNanos = deadlineNanos;
            this.terminalFailure = terminalFailure;
            this.sequence = retrySequence++;
        }
    }

    private final class Batch {
        final Q request;
        final List<DeferredRetry> retries;

        Batch(Q request, List<DeferredRetry> retries) {
            this.request = request;
            this.retries = retries;
        }
    }

    private boolean closeCompleted;
    private final AtomicLong executionIdGen = new AtomicLong();
    /** Guards queue ownership, lifecycle state, and registration/completion of detached batches. */
    private final Object queueLock = new Object();

    private int inFlightExecutions = 0;
    private boolean quiesced;
    /** Exclusive lock so crawl-end and REST cannot interleave two {@link #flushWhileQuiesced} calls. */
    private final ReentrantLock quiesceLock = new ReentrantLock();

    private FsCrawlerBulkProcessor(
            Engine<O, Q, S> engine,
            Listener<O, Q, S> listener,
            int bulkActions,
            ByteSizeValue byteSize,
            Supplier<Q> requestSupplier,
            ScheduledExecutorService executor,
            LongSupplier nanoTimeSupplier) {
        this.engine = engine;
        this.listener = listener;
        this.bulkActions = bulkActions;
        this.byteSize = byteSize;
        this.requestSupplier = requestSupplier;
        this.bulkRequest = supplyRequestWithLimits(requestSupplier, bulkActions, byteSize);
        this.executor = executor;
        this.nanoTimeSupplier = nanoTimeSupplier;
    }

    /** Close external submissions and drain accepted operations and their bounded retries. */
    @Override
    public void close() throws IOException {
        try {
            internalClose();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    private void internalClose() throws InterruptedException {
        quiesceLock.lockInterruptibly();
        try {
            synchronized (queueLock) {
                if (closeCompleted) {
                    return;
                }
                // An interrupted close still rejects new work, but a later close must finish draining.
                closed = true;
                quiesced = true;
                cancelRetryWakeup();
            }
            if (executor != null) {
                executor.shutdown();
            }
            try {
                drainAndRun(() -> {});
            } finally {
                synchronized (queueLock) {
                    quiesced = false;
                    rearmRetryWakeup();
                }
            }
            if (executor != null && !executor.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warn("Bulk processor timer did not terminate within 10 seconds");
            }
            synchronized (queueLock) {
                closeCompleted = true;
            }
        } finally {
            quiesceLock.unlock();
        }
    }

    /**
     * Add a request to the processor
     *
     * @param request request to add
     * @return this so we can link methods.
     */
    public FsCrawlerBulkProcessor<O, Q, S> add(O request) {
        Batch batch = null;
        synchronized (queueLock) {
            ensureOpen();
            bulkRequest.add(request);
            if (!isQuiescedForOtherThreads() && bulkRequest.isOverTheLimit()) {
                batch = detachBatch();
            }
        }
        execute(batch);
        return this;
    }

    /** Queue a listener retry without blocking its response callback. */
    void addRetry(O operation, long dueNanos, long deadlineNanos, Consumer<Exception> terminalFailure) {
        synchronized (queueLock) {
            if (closeCompleted) {
                throw new IllegalStateException("bulk process already closed");
            }
            retries.add(new DeferredRetry(operation, dueNanos, deadlineNanos, terminalFailure));
            queueLock.notifyAll();
            rearmRetryWakeup();
        }
    }

    /** Return the shared monotonic clock used for retry scheduling and expiry. */
    long nanoTime() {
        return nanoTimeSupplier.getAsLong();
    }

    /** Must be called with queueLock held. */
    private void cancelRetryWakeup() {
        retryWakeupGeneration++;
        if (retryWakeup != null) {
            retryWakeup.cancel(false);
            retryWakeup = null;
        }
    }

    /** Must be called with queueLock held. Completion rearms retries deferred by an active execution. */
    private void rearmRetryWakeup() {
        cancelRetryWakeup();
        if (executor == null || closed || quiesced || retries.isEmpty() || inFlightExecutions > 0) {
            return;
        }
        long generation = retryWakeupGeneration;
        retryWakeup = executor.schedule(
                () -> executeReadyRetries(generation),
                Math.max(0, retries.peek().dueNanos - nanoTime()),
                TimeUnit.NANOSECONDS);
    }

    private void executeReadyRetries(long generation) {
        Batch batch;
        synchronized (queueLock) {
            if (generation != retryWakeupGeneration) {
                return;
            }
            retryWakeup = null;
            if (closed || quiesced || inFlightExecutions > 0) {
                return;
            }
            batch = detachReadyRetries();
            if (batch == null) {
                rearmRetryWakeup();
            }
        }
        execute(batch);
    }

    /** Must be called with queueLock held. */
    private Batch detachReadyRetries() {
        if (retries.isEmpty() || retries.peek().dueNanos - nanoTime() > 0) {
            return null;
        }
        Q request = supplyRequestWithLimits(requestSupplier, bulkActions, byteSize);
        List<DeferredRetry> selected = new ArrayList<>();
        while (!retries.isEmpty() && retries.peek().dueNanos - nanoTime() <= 0) {
            DeferredRetry retry = retries.remove();
            request.add(retry.operation);
            selected.add(retry);
            if (request.isOverTheLimit()) {
                break;
            }
        }
        inFlightExecutions++;
        return new Batch(request, selected);
    }

    /** Must be called with queueLock held. */
    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("bulk process already closed");
        }
    }

    private void executeWhenNeeded() {
        Batch batch;
        synchronized (queueLock) {
            // A scheduled invocation may already be running when close shuts down the executor.
            if (closed || isQuiescedForOtherThreads()) {
                return;
            }
            batch = detachBatch();
        }
        execute(batch);
    }

    /** Must be called with queueLock held. */
    private boolean isQuiescedForOtherThreads() {
        return quiesced && !quiesceLock.isHeldByCurrentThread();
    }

    /** Atomically detach and register a batch before releasing queueLock. */
    private Batch detachBatch() {
        if (bulkRequest.numberOfActions() == 0) {
            return null;
        }
        Q batch = bulkRequest;
        bulkRequest = supplyRequestWithLimits(requestSupplier, bulkActions, byteSize);
        inFlightExecutions++;
        return new Batch(batch, List.of());
    }

    /** Network I/O and listeners must run without queueLock held. */
    private void execute(Batch batch) {
        if (batch == null) {
            return;
        }
        Q br = batch.request;
        final long executionId = executionIdGen.incrementAndGet();

        Span bulkSpan = FsCrawlerTracing.startSpan("fscrawler.es.bulk");
        bulkSpan.setAttribute("es.bulk.actions", br.numberOfActions());

        // execute in a blocking fashion...
        boolean afterCalled = false;
        try (Scope ignored = bulkSpan.makeCurrent()) {
            listener.beforeBulk(executionId, br);
            if (!batch.retries.isEmpty()) {
                List<DeferredRetry> remaining = new ArrayList<>(batch.retries);
                while (true) {
                    List<DeferredRetry> expired = new ArrayList<>();
                    for (DeferredRetry retry : remaining) {
                        if (nanoTime() - retry.deadlineNanos >= 0) {
                            expired.add(retry);
                        }
                    }
                    remaining.removeAll(expired);
                    for (DeferredRetry retry : expired) {
                        try {
                            retry.terminalFailure.accept(new IllegalStateException("Item retry deadline exhausted"));
                        } catch (Exception failure) {
                            logger.warn("Could not report expired bulk item retry", failure);
                        }
                    }
                    if (remaining.isEmpty()) {
                        return;
                    }
                    Q ready = supplyRequestWithLimits(requestSupplier, bulkActions, byteSize);
                    for (DeferredRetry retry : remaining) {
                        ready.add(retry.operation);
                    }
                    // Callbacks and request serialization can consume another item's remaining budget.
                    long now = nanoTime();
                    if (remaining.stream().anyMatch(retry -> now - retry.deadlineNanos >= 0)) {
                        continue;
                    }
                    br = ready;
                    break;
                }
            }
            S bulkItemResponses = engine.bulk(br);
            afterCalled = true;
            listener.afterBulk(executionId, br, bulkItemResponses);
        } catch (Exception e) {
            bulkSpan.recordException(e);
            bulkSpan.setStatus(
                    StatusCode.ERROR,
                    e.getMessage() != null ? e.getMessage() : e.getClass().getName());
            if (!afterCalled) {
                listener.afterBulk(executionId, br, e);
            }
        } finally {
            bulkSpan.end();
            synchronized (queueLock) {
                inFlightExecutions--;
                queueLock.notifyAll();
                rearmRetryWakeup();
            }
        }
    }

    Q supplyRequestWithLimits(Supplier<Q> requestSupplier, int bulkActions, ByteSizeValue byteSize) {
        Q br = requestSupplier.get();
        br.maxNumberOfActions(bulkActions);
        br.maxBulkSize(byteSize);
        return br;
    }

    /** Return the listener associated with this processor. */
    public Listener<O, Q, S> getListener() {
        return listener;
    }

    /**
     * Flush pending actions if any, then wait for any in-flight bulk (including one started by the flush interval
     * timer) to finish. No-op HTTP when the queue is empty so callers do not POST an empty {@code _bulk} body.
     */
    public void flush() {
        flushWhileQuiesced(() -> {});
    }

    /**
     * Drain pending and in-flight batches (including listener retries), then run the action while idle. Queue changes
     * and timer/size-triggered detachment cannot race the final idle check or the action.
     */
    public void flushWhileQuiesced(Runnable action) {
        quiesceLock.lock();
        try {
            synchronized (queueLock) {
                ensureOpen();
                quiesced = true;
            }
            try {
                drainAndRun(action);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for in-flight bulk execution(s)", e);
            } finally {
                synchronized (queueLock) {
                    quiesced = false;
                    rearmRetryWakeup();
                }
            }
        } finally {
            quiesceLock.unlock();
        }
    }

    /** Called by the quiesce owner; wait releases queueLock so completing listeners can enqueue retries. */
    private void drainAndRun(Runnable action) throws InterruptedException {
        while (true) {
            Batch batch;
            synchronized (queueLock) {
                if (inFlightExecutions > 0) {
                    queueLock.wait();
                    continue;
                }
                batch = detachBatch();
                if (batch == null) {
                    batch = detachReadyRetries();
                }
                if (batch == null && !retries.isEmpty()) {
                    TimeUnit.NANOSECONDS.timedWait(queueLock, Math.max(1, retries.peek().dueNanos - nanoTime()));
                    continue;
                }
                if (batch == null) {
                    action.run();
                    return;
                }
            }
            execute(batch);
        }
    }

    public static class Builder<
            O extends FsCrawlerOperation<O>, Q extends FsCrawlerBulkRequest<O>, S extends FsCrawlerBulkResponse<O>> {

        private int bulkActions;
        private TimeValue flushInterval;
        private LongSupplier nanoTimeSupplier = System::nanoTime;
        private ByteSizeValue byteSize;
        private final Engine<O, Q, S> engine;
        private final Listener<O, Q, S> listener;
        private final Supplier<Q> requestSupplier;

        /**
         * Create a processor builder.
         *
         * @param engine bulk transport
         * @param listener completion listener
         * @param requestSupplier factory for empty bulk requests
         */
        public Builder(Engine<O, Q, S> engine, Listener<O, Q, S> listener, Supplier<Q> requestSupplier) {
            this.engine = engine;
            this.listener = listener;
            this.requestSupplier = requestSupplier;
        }

        /** Set the action limit used by ordinary and retry batches. */
        public Builder<O, Q, S> setBulkActions(int bulkActions) {
            this.bulkActions = bulkActions;
            return this;
        }

        /** Set the ordinary flush interval and enable independent retry wakeups. */
        public Builder<O, Q, S> setFlushInterval(TimeValue flushInterval) {
            this.flushInterval = flushInterval;
            return this;
        }

        /** Set the serialized byte limit used by ordinary and retry batches. */
        public Builder<O, Q, S> setByteSize(ByteSizeValue byteSizeValue) {
            this.byteSize = byteSizeValue;
            return this;
        }

        /** Replace the monotonic clock for deterministic scheduling tests. */
        Builder<O, Q, S> setNanoTimeSupplier(LongSupplier nanoTimeSupplier) {
            this.nanoTimeSupplier = nanoTimeSupplier;
            return this;
        }

        /** Build the processor and start its configured scheduler. */
        public FsCrawlerBulkProcessor<O, Q, S> build() {
            ScheduledExecutorService exec = flushInterval != null ? Executors.newScheduledThreadPool(1) : null;
            var processor = new FsCrawlerBulkProcessor<>(
                    engine, listener, bulkActions, byteSize, requestSupplier, exec, nanoTimeSupplier);
            processor.listener.setBulkProcessor(processor);
            if (exec != null) {
                exec.scheduleWithFixedDelay(
                        processor::executeWhenNeeded, 0, flushInterval.millis(), TimeUnit.MILLISECONDS);
            }
            return processor;
        }
    }

    public interface Listener<
            O extends FsCrawlerOperation<O>, Q extends FsCrawlerBulkRequest<O>, S extends FsCrawlerBulkResponse<O>> {

        void beforeBulk(long executionId, Q request);

        void afterBulk(long executionId, Q request, S response);

        void afterBulk(long executionId, Q request, Throwable failure);

        void setBulkProcessor(FsCrawlerBulkProcessor<O, Q, S> bulkProcessor);
    }
}
