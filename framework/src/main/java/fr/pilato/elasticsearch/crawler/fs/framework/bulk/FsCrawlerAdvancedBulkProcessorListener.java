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

import java.util.concurrent.atomic.AtomicInteger;

/**
 * This Listener exposes the number of successive errors that might have seen previously. So the caller can if needed slow down
 * a bit the injection.
 * A retry mechanism is implemented in RetryBulkProcessorListener
 * @see FsCrawlerRetryBulkProcessorListener
 */
public class FsCrawlerAdvancedBulkProcessorListener<
        O extends FsCrawlerOperation<O>,
        REQ extends FsCrawlerBulkRequest<O>,
        RES extends FsCrawlerBulkResponse<O>
        > extends FsCrawlerSimpleBulkProcessorListener<O, REQ, RES> {
    private static final Logger logger = LogManager.getLogger(FsCrawlerAdvancedBulkProcessorListener.class);

    private final AtomicInteger successiveErrors = new AtomicInteger(0);

    public int getErrors() {
        return successiveErrors.get();
    }

    @Override
    public void afterBulk(long executionId, REQ request, RES response) {
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
