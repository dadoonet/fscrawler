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

package fr.pilato.elasticsearch.crawler.fs.client;

import fr.pilato.elasticsearch.crawler.fs.meta.settings.TimeValue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bulk processor
 */
public class BulkProcessor {

    private static final Logger logger = LogManager.getLogger(BulkProcessor.class);

    private final int bulkActions;
    private final ElasticsearchClient client;
    private final Listener listener;
    private BulkRequest bulkRequest;
    private final ScheduledExecutorService executor;
    private volatile boolean closed = false;
    private final AtomicLong executionIdGen = new AtomicLong();
    private final String pipeline;

    private BulkProcessor(ElasticsearchClient client, Listener listener, int bulkActions, TimeValue flushInterval, String pipeline) {
        this.bulkActions = bulkActions;
        this.bulkRequest = new BulkRequest();
        this.client = client;
        this.listener = listener;
        this.listener.setBulkProcessor(this);

        if (flushInterval != null) {
            executor = Executors.newScheduledThreadPool(1);
            executor.scheduleWithFixedDelay(this::executeWhenNeeded, 0, flushInterval.millis(), TimeUnit.MILLISECONDS);
        } else {
            executor = null;
        }
        this.pipeline = pipeline;
    }

    public void close() throws InterruptedException {
        if (closed) {
            return;
        }
        closed = true;

        if (executor != null) {
            logger.debug("Closing BulkProcessor");
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
            logger.debug("BulkProcessor is now closed");
        }

        if (bulkRequest.numberOfActions() > 0) {
            logger.debug("Executing [{}] remaining actions", bulkRequest.numberOfActions());
            execute();
        }
    }

    /**
     * Adds an {@link IndexRequest} to the list of actions to execute. Follows the same behavior of {@link IndexRequest}
     * (for example, if no id is provided, one will be generated, or usage of the create flag).
     */
    public BulkProcessor add(IndexRequest request) {
        return add((SingleBulkRequest) request);
    }

    /**
     * Adds an {@link DeleteRequest} to the list of actions to execute.
     */
    @SuppressWarnings("UnusedReturnValue")
    public BulkProcessor add(DeleteRequest request) {
        return add((SingleBulkRequest) request);
    }

    /**
     * Adds either a delete or an index request.
     */
    private BulkProcessor add(SingleBulkRequest request) {
        // We do that only if debug
        if (logger.isDebugEnabled()) {
            StringBuffer sbf = new StringBuffer();
            sbf.append("{");
            String header = JsonUtil.serialize(request);
            if (request instanceof DeleteRequest) {
                sbf.append("\"delete\":").append(header).append("}");
            }
            if (request instanceof IndexRequest) {
                sbf.append("\"index\":").append(header).append("}");
                // Index Request: header line + body
                if (logger.isTraceEnabled()) {
                    sbf.append("\n").append(((IndexRequest) request).content().replaceAll("\n", ""));
                }
            }
            logger.debug("{}", sbf);
        }
        return internalAdd(request);
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("bulk process already closed");
        }
    }

    private synchronized BulkProcessor internalAdd(SingleBulkRequest request) {
        ensureOpen();
        bulkRequest.add(request);
        executeIfNeeded();
        return this;
    }

    private void executeIfNeeded() {
        ensureOpen();
        if (isOverTheLimit()) {
            execute();
        }
    }

    private void executeWhenNeeded() {
        ensureOpen();
        if (bulkRequest.numberOfActions() > 0) {
            execute();
        }
    }

    private void execute() {
        final BulkRequest bulkRequest = this.bulkRequest;
        this.bulkRequest = new BulkRequest();
        final long executionId = executionIdGen.incrementAndGet();

        // execute in a blocking fashion...
        boolean afterCalled = false;
        try {
            listener.beforeBulk(executionId, bulkRequest);
            BulkResponse bulkItemResponses = client.bulk(bulkRequest, pipeline);
            afterCalled = true;
            listener.afterBulk(executionId, bulkRequest, bulkItemResponses);
        } catch (Exception e) {
            if (!afterCalled) {
                listener.afterBulk(executionId, bulkRequest, e);
            }
        }
    }

    private boolean isOverTheLimit() {
        return (bulkActions != -1) && (bulkRequest.numberOfActions() >= bulkActions);
    }

    public Listener getListener() {
        return listener;
    }

    public static class Builder {

        private int bulkActions;
        private TimeValue flushInterval;
        private final ElasticsearchClient client;
        private final Listener listener;
        private String pipeline = null;

        public Builder(ElasticsearchClient client, Listener listener) {
            this.client = client;
            this.listener = listener;
        }

        public Builder setBulkActions(int bulkActions) {
            this.bulkActions = bulkActions;
            return this;
        }

        public Builder setFlushInterval(TimeValue flushInterval) {
            this.flushInterval = flushInterval;
            return this;
        }

        public Builder setPipeline(String pipeline) {
            this.pipeline = pipeline;
            return this;
        }

        public BulkProcessor build() {
            return new BulkProcessor(client, listener, bulkActions, flushInterval, pipeline);
        }
    }

    private static Builder builder(ElasticsearchClient client, Listener listener) {
        return new Builder(client, listener);
    }

    public interface Listener {

        void beforeBulk(long executionId, BulkRequest request);

        void afterBulk(long executionId, BulkRequest request, BulkResponse response);

        void afterBulk(long executionId, BulkRequest request, Throwable failure);

        void setBulkProcessor(BulkProcessor bulkProcessor);
    }

    /**
     * Build an simple elasticsearch bulk processor
     * @param client elasticsearch client
     * @param bulkSize bulk size
     * @param flushInterval flush interval in milliseconds
     * @param pipeline Node Ingest Pipeline if any. Null otherwise.
     * @return a bulk processor
     * @see SimpleBulkProcessorListener
     */
    public static BulkProcessor simpleBulkProcessor(ElasticsearchClient client, int bulkSize, TimeValue flushInterval, String pipeline) {
        logger.debug("Creating a bulk processor with size [{}], flush [{}], pipeline [{}]", bulkSize, flushInterval, pipeline);
        return builder(client, new SimpleBulkProcessorListener())
                .setBulkActions(bulkSize)
                .setFlushInterval(flushInterval)
                .setPipeline(pipeline)
                .build();
    }

    /**
     * Build an advanced elasticsearch bulk processor
     * @param client elasticsearch client
     * @param bulkSize bulk size
     * @param flushInterval flush interval in milliseconds
     * @param pipeline Node Ingest Pipeline if any. Null otherwise.
     * @return a bulk processor
     * @see AdvancedBulkProcessorListener
     */
    public static BulkProcessor advancedBulkProcessor(ElasticsearchClient client, int bulkSize, TimeValue flushInterval, String pipeline) {
        logger.debug("Creating a bulk processor with size [{}], flush [{}], pipeline [{}]", bulkSize, flushInterval, pipeline);
        return builder(client, new AdvancedBulkProcessorListener())
                .setBulkActions(bulkSize)
                .setFlushInterval(flushInterval)
                .setPipeline(pipeline)
                .build();
    }

    /**
     * Build an advanced elasticsearch bulk processor with retry mechanism
     * @param client elasticsearch client
     * @param bulkSize bulk size
     * @param flushInterval flush interval in milliseconds
     * @param pipeline Node Ingest Pipeline if any. Null otherwise.
     * @return a bulk processor
     * @see RetryBulkProcessorListener
     */
    public static BulkProcessor retryBulkProcessor(ElasticsearchClient client, int bulkSize, TimeValue flushInterval, String pipeline) {
        logger.debug("Creating a bulk processor with size [{}], flush [{}], pipeline [{}]", bulkSize, flushInterval, pipeline);
        return builder(client, new RetryBulkProcessorListener())
                .setBulkActions(bulkSize)
                .setFlushInterval(flushInterval)
                .setPipeline(pipeline)
                .build();
    }

    protected static class SimpleBulkProcessorListener implements Listener {
        protected BulkProcessor bulkProcessor;

        @Override
        public void beforeBulk(long executionId, BulkRequest request) {
            logger.debug("Going to execute new bulk composed of {} actions", request.numberOfActions());
        }

        @Override
        public void afterBulk(long executionId, BulkRequest request, BulkResponse response) {
            logger.debug("Executed bulk composed of {} actions", request.numberOfActions());
            if (response.hasFailures()) {
                logger.warn("There was failures while executing bulk", response.buildFailureMessage());
                if (logger.isDebugEnabled()) {
                    for (BulkResponse.BulkItemTopLevelResponse topLevelItem : response.getItems()) {
                        BulkResponse.BulkItemResponse item = topLevelItem.getItemContent();
                        if (item.isFailed()) {
                            logger.debug("Error for {}/{}/{} for {} operation: {}", item.getIndex(),
                                    item.getType(), item.getId(), item.getOpType(), item.getFailureMessage());
                        }
                    }
                }
            }
        }

        @Override
        public void afterBulk(long executionId, BulkRequest request, Throwable failure) {
            logger.warn("Error executing bulk", failure);
        }

        @Override
        public void setBulkProcessor(BulkProcessor bulkProcessor) {
            this.bulkProcessor = bulkProcessor;
        }
    }

    /**
     * This Listener exposes the number of successive errors that might have seen previously. So the caller can if needed slow down
     * a bit the injection.
     * A retry mechanism is implemented in RetryBulkProcessorListener
     * @see RetryBulkProcessorListener
     */
    public static class AdvancedBulkProcessorListener extends SimpleBulkProcessorListener {
        private AtomicInteger successiveErrors = new AtomicInteger(0);

        public int getErrors() {
            return successiveErrors.get();
        }

        @Override
        public void afterBulk(long executionId, BulkRequest request, BulkResponse response) {
            super.afterBulk(executionId, request, response);
            if (response.hasFailures()) {
                int previousErrors = successiveErrors.getAndIncrement();
                logger.warn("Throttling is activated. Got [{}] successive errors so far.", previousErrors);
            } else {
                int previousErrors = successiveErrors.get();
                if (previousErrors > 0) {
                    successiveErrors.set(0);
                    logger.debug("We are back to normal behavior after [{}] errors. \\o/", previousErrors);
                }
            }
        }
    }

    /**
     * This Listener implements a simple and naive retry mechanism. When a document is rejected because of a es_rejected_execution_exception
     * the same document is sent again to the bulk processor.
     */
    public static class RetryBulkProcessorListener extends AdvancedBulkProcessorListener {
        @Override
        public void afterBulk(long executionId, BulkRequest request, BulkResponse response) {
            super.afterBulk(executionId, request, response);
            if (response.hasFailures()) {
                for (BulkResponse.BulkItemTopLevelResponse bulkResponse : response.getItems()) {
                    BulkResponse.BulkItemResponse itemContent = bulkResponse.getItemContent();
                    if (itemContent.isFailed() && itemContent.getFailureMessage().contains("es_rejected_execution_exception")) {
                        logger.debug("We are going to retry document [{}]/[{}]/[{}] because of [{}]",
                                itemContent.getIndex(), itemContent.getType(), itemContent.getId(), itemContent.getFailureMessage());
                        // Find request
                        boolean requestFound = false;
                        for (SingleBulkRequest singleBulkRequest : request.getRequests()) {
                            if (singleBulkRequest.getIndex().equals(itemContent.getIndex()) &&
                                    singleBulkRequest.getType().equals(itemContent.getType()) &&
                                    singleBulkRequest.getId().equals(itemContent.getId())) {
                                this.bulkProcessor.add(singleBulkRequest);
                                requestFound = true;
                                logger.debug("Document [{}]/[{}]/[{}] found. Can be retried.", itemContent.getIndex(), itemContent.getType(), itemContent.getId());
                                break;
                            }
                        }
                        if (!requestFound) {
                            logger.warn("Can not retry document [{}]/[{}]/[{}] because we can't find it anymore.",
                                    itemContent.getIndex(), itemContent.getType(), itemContent.getId());
                        }
                    }
                }
            }
        }
    }
}
