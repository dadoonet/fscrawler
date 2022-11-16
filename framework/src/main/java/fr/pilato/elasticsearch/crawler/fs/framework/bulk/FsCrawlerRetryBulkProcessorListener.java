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

package fr.pilato.elasticsearch.crawler.fs.framework.bulk;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;

/**
 * This Listener implements a simple and naive retry mechanism. When a document is rejected because of an es_rejected_execution_exception
 * the same document is sent again to the bulk processor.
 */
public class FsCrawlerRetryBulkProcessorListener<
        O extends FsCrawlerOperation<O>,
        REQ extends FsCrawlerBulkRequest<O>,
        RES extends FsCrawlerBulkResponse<O>
        > extends FsCrawlerAdvancedBulkProcessorListener<O, REQ, RES> {

    private static final Logger logger = LogManager.getLogger(FsCrawlerRetryBulkProcessorListener.class);

    private final String[] errorMessages;

    /**
     * List part of error messages which will trigger a retry
     * @param errorMessages error messages
     */
    public FsCrawlerRetryBulkProcessorListener(String... errorMessages) {
        this.errorMessages = errorMessages;
    }

    @Override
    public void afterBulk(long executionId, REQ request, RES response) {
        super.afterBulk(executionId, request, response);
        if (response.hasFailures()) {
            for (RES.BulkItemResponse<O> item : response.getItems()) {
                if (item.isFailed() && Arrays.stream(errorMessages).anyMatch(s -> item.getFailureMessage().contains(s))) {
                    logger.debug("We are going to retry document [{}] because of [{}]",
                            item.getOperation(), item.getFailureMessage());
                    // Find request
                    boolean requestFound = false;
                    for (O operation : request.getOperations()) {
                        if (operation.compareTo(item.getOperation()) == 0) {
                            this.bulkProcessor.add(operation);
                            requestFound = true;
                            logger.debug("Document [{}] found. Can be retried.", item.getOperation());
                            break;
                        }
                    }
                    if (!requestFound) {
                        logger.warn("Can not retry document [{}] because we can't find it anymore.",
                                item.getOperation());
                    }
                }
            }
        }
    }
}
