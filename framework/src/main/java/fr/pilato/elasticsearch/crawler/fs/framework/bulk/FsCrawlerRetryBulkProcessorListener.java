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

import java.time.Duration;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This Listener implements a simple and naive retry mechanism. When a document is rejected because of an
 * es_rejected_execution_exception the same document is sent again to the bulk processor.
 */
public class FsCrawlerRetryBulkProcessorListener<
                O extends FsCrawlerOperation<O>, Q extends FsCrawlerBulkRequest<O>, S extends FsCrawlerBulkResponse<O>>
        extends FsCrawlerAdvancedBulkProcessorListener<O, Q, S> {

    private static final Logger logger = LogManager.getLogger();

    private final String[] errorMessages;
    private final Duration maxDuration;
    private final Duration initialDelay;
    private final Duration maxDelay;
    private final Map<O, RetryState> retries = new IdentityHashMap<>();
    private static final int MAX_RETRIES = 3;

    private static final class RetryState {
        final long started = System.nanoTime();
        int attempts;
    }

    /**
     * List part of error messages which will trigger a retry
     *
     * @param errorMessages error messages
     */
    public FsCrawlerRetryBulkProcessorListener(String... errorMessages) {
        this(Duration.ofSeconds(30), Duration.ofMillis(100), Duration.ofSeconds(1), errorMessages);
    }

    public FsCrawlerRetryBulkProcessorListener(
            Duration maxDuration, Duration initialDelay, Duration maxDelay, String... errorMessages) {
        this.maxDuration = maxDuration;
        this.initialDelay = initialDelay;
        this.maxDelay = maxDelay;
        this.errorMessages = errorMessages;
    }

    @Override
    public void afterBulk(long executionId, Q request, S response) {
        super.afterBulk(executionId, request, response);
        if (response.getItems().isEmpty()) {
            forget(request);
        }
        for (int position = 0; position < response.getItems().size(); position++) {
            var item = response.getItems().get(position);
            // Bulk responses preserve request order; IDs alone cannot distinguish repeated writes.
            O operation = position < request.getOperations().size()
                    ? request.getOperations().get(position)
                    : null;
            if (operation != null && operation.compareTo(item.getOperation()) != 0) operation = null;
            if (!item.isFailed()) {
                forgetOperation(operation);
                continue;
            }
            Exception retryFailure = null;
            if (shouldRetry(item)) {
                try {
                    if (retryItem(operation, item)) {
                        continue;
                    }
                    retryFailure = new IllegalStateException("Original operation was not found for retry");
                } catch (Exception e) {
                    // For example, shutdown may already have closed the processor to new operations.
                    retryFailure = e;
                }
            }
            forgetOperation(operation);
            onTerminalItemFailure(executionId, request, item, retryFailure);
        }
    }

    /**
     * Called for a failed item that was not requeued. Subclasses can propagate the terminal outcome to callers. The
     * response has already been logged by the superclass; a successfully requeued item is handled on its next
     * completion instead. The optional cause explains why an attempted retry could not be accepted.
     */
    protected void onTerminalItemFailure(
            long executionId, Q request, FsCrawlerBulkResponse.BulkItemResponse<O> item, Exception retryFailure) {}

    /**
     * Tells whether a failed bulk item should be retried, i.e. it failed with one of the configured error messages.
     *
     * @param item the bulk item response to inspect
     * @return {@code true} if the item failed with a retryable error message
     */
    private boolean shouldRetry(FsCrawlerBulkResponse.BulkItemResponse<O> item) {
        if (!item.isFailed()) return false;
        if (item.getFailureType() != null) {
            return Arrays.stream(errorMessages).anyMatch(s -> s.equals(item.getFailureType()));
        }
        // Compatibility for other backends that do not provide structured errors.
        return item.getFailureMessage() != null
                && Arrays.stream(errorMessages)
                        .anyMatch(s -> item.getFailureMessage().contains(s));
    }

    /**
     * Re-adds the operation matching the failed item to the bulk processor so that it can be retried.
     *
     * @param operation the original operation at the response item position
     * @param item the failed bulk item to retry
     */
    private boolean retryItem(O operation, FsCrawlerBulkResponse.BulkItemResponse<O> item) {
        if (operation == null) return false;
        RetryState state;
        int attempt;
        synchronized (retries) {
            state = retries.computeIfAbsent(operation, ignored -> new RetryState());
            if (state.attempts >= MAX_RETRIES) {
                throw new IllegalStateException("Item retry limit exhausted after " + MAX_RETRIES + " retries");
            }
            attempt = ++state.attempts;
        }
        long remaining = maxDuration.toNanos() - (System.nanoTime() - state.started);
        long cap = Math.min(maxDelay.toNanos(), initialDelay.toNanos() * (1L << (attempt - 1)));
        long delay = cap <= 1 ? Math.max(0, cap) : ThreadLocalRandom.current().nextLong(cap / 2, cap + 1);
        if (remaining <= delay) throw new IllegalStateException("Item retry deadline exhausted");
        try {
            pauseBeforeRetry(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Item retry interrupted", e);
        }
        if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("Item retry interrupted");
        if (System.nanoTime() - state.started >= maxDuration.toNanos()) {
            throw new IllegalStateException("Item retry deadline exhausted");
        }
        this.bulkProcessor.addRetry(operation);
        return true;
    }

    /** Backoff runs outside the processor queue lock and remains interruptible. */
    protected void pauseBeforeRetry(long nanos) throws InterruptedException {
        TimeUnit.NANOSECONDS.sleep(nanos);
    }

    @Override
    public void afterBulk(long executionId, Q request, Throwable failure) {
        forget(request);
        super.afterBulk(executionId, request, failure);
    }

    private void forget(Q request) {
        synchronized (retries) {
            for (O operation : request.getOperations()) retries.remove(operation);
        }
    }

    private void forgetOperation(O operation) {
        synchronized (retries) {
            retries.remove(operation);
        }
    }
}
