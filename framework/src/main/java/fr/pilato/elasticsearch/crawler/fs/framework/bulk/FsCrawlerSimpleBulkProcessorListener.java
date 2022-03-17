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

public class FsCrawlerSimpleBulkProcessorListener<
        O extends FsCrawlerOperation<O>,
        REQ extends FsCrawlerBulkRequest<O>,
        RES extends FsCrawlerBulkResponse<O>
        > implements FsCrawlerBulkProcessor.Listener<O, REQ, RES> {
    private static final Logger logger = LogManager.getLogger(FsCrawlerSimpleBulkProcessorListener.class);

    protected FsCrawlerBulkProcessor<O, REQ, RES> bulkProcessor;

    @Override
    public void beforeBulk(long executionId, REQ request) {
        logger.debug("Going to execute new bulk composed of {} actions", request.numberOfActions());
    }

    @Override
    public void afterBulk(long executionId, REQ request, RES response) {
        logger.debug("Executed bulk composed of {} actions", request.numberOfActions());
        if (response.hasFailures()) {
            logger.warn("There was failures while executing bulk", response.buildFailureMessage());
            if (logger.isDebugEnabled()) {
                for (FsCrawlerBulkResponse.BulkItemResponse<O> item : response.getItems()) {
                    if (item.isFailed()) {
                        logger.debug("Error for [{}]: {}", item.getOperation(), item.getFailureMessage());
                    }
                }
            }
        }
    }

    @Override
    public void afterBulk(long executionId, REQ request, Throwable failure) {
        logger.warn("Error executing bulk", failure);
    }

    @Override
    public void setBulkProcessor(FsCrawlerBulkProcessor<O, REQ, RES> bulkProcessor) {
        this.bulkProcessor = bulkProcessor;
    }
}
