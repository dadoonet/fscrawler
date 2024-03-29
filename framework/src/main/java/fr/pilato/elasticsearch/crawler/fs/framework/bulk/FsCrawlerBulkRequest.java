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

import fr.pilato.elasticsearch.crawler.fs.framework.ByteSizeValue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.serialize;

public abstract class FsCrawlerBulkRequest<T extends FsCrawlerOperation<T>> {

    private final Logger logger = LogManager.getLogger(FsCrawlerBulkRequest.class);

    private final List<T> operations = new ArrayList<>();
    private int totalByteSize = 0;
    private int maxNumberOfActions;
    private ByteSizeValue maxBulkSize;

    public int numberOfActions() {
        return operations.size();
    }

    public int totalByteSize() {
        return totalByteSize;
    }

    public void maxNumberOfActions(int maxNumberOfActions) {
        this.maxNumberOfActions = maxNumberOfActions;
    }

    public void maxBulkSize(ByteSizeValue maxBulkSize) {
        this.maxBulkSize = maxBulkSize;
    }

    public void add(T request) {
        operations.add(request);
        // There's a cost of serializing the request. We need to take it into account
        // and only compute the size if we need to.
        if (maxBulkSize != null && maxBulkSize.getBytes() > 0) {
            // TODO may be we should just add the serialized request to the T object as an optional payload?
            String jsonValue = serialize(request);
            byte[] bytes = jsonValue.getBytes();
            totalByteSize += bytes.length;
        }
    }

    public List<T> getOperations() {
        return operations;
    }

    boolean isOverTheLimit() {
        logger.trace("Checking if we need to flush the bulk processor: [{}] >= [{}] actions, [{}] >= [{}] bytes",
                numberOfActions(), maxNumberOfActions, totalByteSize(), maxBulkSize != null ? maxBulkSize.getBytes() : null);
        return (maxBulkSize != null && maxBulkSize.getBytes() > 0 && totalByteSize >= maxBulkSize.getBytes()) ||
                (maxNumberOfActions > 0 && numberOfActions() >= maxNumberOfActions);
    }
}
