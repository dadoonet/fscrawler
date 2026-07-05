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
            if (logger.isDebugEnabled()) {
                logger.warn("There was failures while executing bulk.", response.buildFailureMessage());
                for (FsCrawlerBulkResponse.BulkItemResponse<O> item : response.getItems()) {
                    if (item.isFailed()) {
                        logger.warn("Error for [{}]: {}", item.getOperation(), item.getFailureMessage());
                    }
                }
            } else {
                logger.warn(
                        "There was failures while executing bulk. If you want to see the details, "
                                + "please activate DEBUG mode with FS_JAVA_OPTS=\"-DLOG_LEVEL=debug\". "
                                + "See https://fscrawler.readthedocs.io/en/latest/admin/logger.html for more details.",
                        response.buildFailureMessage());
            }
        }
    }

    @Override
    public void afterBulk(long executionId, Q request, Throwable failure) {
        logger.warn("Error executing bulk", failure);
    }

    @Override
    public void setBulkProcessor(FsCrawlerBulkProcessor<O, Q, S> bulkProcessor) {
        this.bulkProcessor = bulkProcessor;
    }
}
