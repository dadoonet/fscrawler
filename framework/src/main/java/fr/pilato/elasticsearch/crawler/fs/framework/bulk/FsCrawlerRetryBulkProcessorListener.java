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

import java.util.Arrays;
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

    /**
     * List part of error messages which will trigger a retry
     *
     * @param errorMessages error messages
     */
    public FsCrawlerRetryBulkProcessorListener(String... errorMessages) {
        this.errorMessages = errorMessages;
    }

    @Override
    public void afterBulk(long executionId, Q request, S response) {
        super.afterBulk(executionId, request, response);
        if (!response.hasFailures()) {
            return;
        }
        for (FsCrawlerBulkResponse.BulkItemResponse<O> item : response.getItems()) {
            if (shouldRetry(item)) {
                retryItem(request, item);
            }
        }
    }

    /**
     * Tells whether a failed bulk item should be retried, i.e. it failed with one of the configured error messages.
     *
     * @param item the bulk item response to inspect
     * @return {@code true} if the item failed with a retryable error message
     */
    private boolean shouldRetry(FsCrawlerBulkResponse.BulkItemResponse<O> item) {
        return item.isFailed()
                && Arrays.stream(errorMessages)
                        .anyMatch(s -> item.getFailureMessage().contains(s));
    }

    /**
     * Re-adds the operation matching the failed item to the bulk processor so that it can be retried.
     *
     * @param request the original bulk request holding the operations
     * @param item the failed bulk item to retry
     */
    private void retryItem(Q request, FsCrawlerBulkResponse.BulkItemResponse<O> item) {
        logger.debug(
                "We are going to retry document [{}] because of [{}]", item.getOperation(), item.getFailureMessage());
        // Find the matching operation in the original request
        for (O operation : request.getOperations()) {
            if (operation.compareTo(item.getOperation()) == 0) {
                this.bulkProcessor.add(operation);
                logger.debug("Document [{}] found. Can be retried.", item.getOperation());
                return;
            }
        }
        logger.warn("Can not retry document [{}] because we can't find it anymore.", item.getOperation());
    }
}
