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

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("CanBeFinal")
public abstract class FsCrawlerBulkResponse<O extends FsCrawlerOperation<O>> {

    private static final Logger logger = LogManager.getLogger(FsCrawlerBulkResponse.class);

    protected boolean errors;
    protected List<BulkItemResponse<O>> items = new ArrayList<>();

    @SuppressWarnings("ConstantConditions")
    public boolean hasFailures() {
        if (errors) return errors;
        for (BulkItemResponse<O> item : items) {
            if (item.failed) {
                return true;
            }
        }
        return false;
    }

    public List<BulkItemResponse<O>> getItems() {
        return items;
    }

    public boolean isErrors() {
        return errors;
    }

    public Throwable buildFailureMessage() {
        StringBuilder sbf = new StringBuilder();
        int failures = 0;
        for (BulkItemResponse<O> item : items) {
            if (item.failed) {
                if (logger.isTraceEnabled()) {
                    sbf.append(item.getOperation());
                    sbf.append(":").append(item.getOperation().toString()).append(":").append(item.getFailureMessage());
                }
                failures++;
            }
        }
        if (logger.isTraceEnabled()) {
            sbf.append("\n");
        }
        sbf.append(failures).append(" failures");
        return new RuntimeException(sbf.toString());
    }

    @SuppressWarnings("CanBeFinal")
    public static class BulkItemResponse<O extends FsCrawlerOperation<O>> {
        private boolean failed;
        private O operation;
        private String failureMessage;

        public boolean isFailed() {
            return failed;
        }

        public O getOperation() {
            return operation;
        }

        public String getFailureMessage() {
            return failureMessage;
        }

        public void setFailed(boolean failed) {
            this.failed = failed;
        }

        public void setOperation(O operation) {
            this.operation = operation;
        }

        public void setFailureMessage(String failureMessage) {
            this.failureMessage = failureMessage;
        }

        @Override
        public String toString() {
            return "BulkItemResponse{" + "failed=" + failed +
                    ", operation='" + operation + '\'' +
                    ", failureMessage='" + failureMessage + '\'' +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "BulkResponse{" + "items=" + (items == null ? "null" : List.of(items).toString()) + '}';
    }
}
