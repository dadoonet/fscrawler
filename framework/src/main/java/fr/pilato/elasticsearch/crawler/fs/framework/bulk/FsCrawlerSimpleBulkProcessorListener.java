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

import fr.pilato.elasticsearch.crawler.fs.framework.FSCrawlerLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FsCrawlerSimpleBulkProcessorListener<
                O extends FsCrawlerOperation<O>, Q extends FsCrawlerBulkRequest<O>, S extends FsCrawlerBulkResponse<O>>
        implements FsCrawlerBulkProcessor.Listener<O, Q, S> {
    private static final Logger logger = LogManager.getLogger();

    protected FsCrawlerBulkProcessor<O, Q, S> bulkProcessor;

    @Override
    public void beforeBulk(long executionId, Q request) {
        logger.debug("Going to execute new bulk composed of {} actions", request.numberOfActions());
    }

    @Override
    public void afterBulk(long executionId, Q request, S response) {
        logger.debug("Executed bulk composed of {} actions", request.numberOfActions());
        if (response.hasFailures()) {
            Throwable failure = response.buildFailureMessage();
            String reason = shortReason(failure);
            logger.warn(
                    "There were failures while executing bulk of {} actions: {}. See {} for action details.",
                    request.numberOfActions(),
                    failure.getMessage(),
                    FSCrawlerLogger.BULK_FAILURES_LOG_FILE,
                    failure);
            FSCrawlerLogger.bulkFailureWarn(
                    reason,
                    "Bulk executionId={} item failures for {} actions. Request may have been partially applied.",
                    executionId,
                    request.numberOfActions());
            for (FsCrawlerBulkResponse.BulkItemResponse<O> item : response.getItems()) {
                if (item.isFailed()) {
                    String itemReason = shortReason(item.getFailureMessage());
                    logger.warn("Bulk item failure for [{}]: {}", item.getOperation(), item.getFailureMessage());
                    FSCrawlerLogger.bulkFailureWarn(
                            itemReason, "executionId={} failed item: {}", executionId, item.getOperation());
                    FSCrawlerLogger.bulkFailureTrace(
                            itemReason, "executionId={} failed item detail: {}", executionId, item.getFailureMessage());
                    logOperationPayloadTrace(itemReason, executionId, item.getOperation());
                }
            }
        }
    }

    @Override
    public void afterBulk(long executionId, Q request, Throwable failure) {
        String reason = shortReason(failure);
        logger.warn(
                "Error executing bulk of {} actions: {}. See {} for action details.",
                request.numberOfActions(),
                failure.getMessage() != null
                        ? failure.getMessage()
                        : failure.getClass().getName(),
                FSCrawlerLogger.BULK_FAILURES_LOG_FILE,
                failure);
        FSCrawlerLogger.bulkFailureWarn(
                reason,
                "Bulk executionId={} failed for {} actions (whole request; may have been partially applied).",
                executionId,
                request.numberOfActions());
        int i = 0;
        for (O operation : request.getOperations()) {
            i++;
            FSCrawlerLogger.bulkFailureWarn(
                    reason, "executionId={} action {}/{}: {}", executionId, i, request.numberOfActions(), operation);
            logOperationPayloadTrace(reason, executionId, operation);
        }
    }

    @Override
    public void setBulkProcessor(FsCrawlerBulkProcessor<O, Q, S> bulkProcessor) {
        this.bulkProcessor = bulkProcessor;
    }

    /**
     * Optional TRACE dump of an operation payload. Overridden where the concrete operation type carries a document body
     * (e.g. Elasticsearch insert).
     */
    protected void logOperationPayloadTrace(String reason, long executionId, O operation) {
        FSCrawlerLogger.bulkFailureTrace(reason, "executionId={} operation detail: {}", executionId, operation);
    }

    static String shortReason(Throwable failure) {
        if (failure == null) {
            return "unknown";
        }
        return shortReason(
                failure.getMessage() != null
                        ? failure.getMessage()
                        : failure.getClass().getSimpleName());
    }

    static String shortReason(String message) {
        if (message == null || message.isBlank()) {
            return "unknown";
        }
        String trimmed = message.strip();
        // Keep a single-line, greppable prefix (HTTP timeouts often end with "Read timed out")
        int newline = trimmed.indexOf('\n');
        if (newline > 0) {
            trimmed = trimmed.substring(0, newline).strip();
        }
        if (trimmed.length() > 200) {
            trimmed = trimmed.substring(0, 200) + "…";
        }
        return trimmed;
    }
}
