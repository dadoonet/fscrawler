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
            logger.warn(
                    "There were failures while executing bulk of {} actions: {}",
                    request.numberOfActions(),
                    failure.getMessage(),
                    failure);
            for (FsCrawlerBulkResponse.BulkItemResponse<O> item : response.getItems()) {
                if (item.isFailed()) {
                    logger.warn("Bulk item failure for [{}]: {}", item.getOperation(), item.getFailureMessage());
                }
            }
        }
    }

    @Override
    public void afterBulk(long executionId, Q request, Throwable failure) {
        logger.warn(
                "Error executing bulk of {} actions: {}",
                request.numberOfActions(),
                failure.getMessage() != null
                        ? failure.getMessage()
                        : failure.getClass().getName(),
                failure);
    }

    @Override
    public void setBulkProcessor(FsCrawlerBulkProcessor<O, Q, S> bulkProcessor) {
        this.bulkProcessor = bulkProcessor;
    }
}
