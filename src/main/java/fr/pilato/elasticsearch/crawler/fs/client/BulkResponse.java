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

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;

public class BulkResponse {

    private static final Logger logger = LogManager.getLogger(ElasticsearchClient.class);

    private boolean errors;
    private BulkItemTopLevelResponse[] items;

    public boolean hasFailures() {
        if (errors) return errors;
        for (BulkItemTopLevelResponse topLevelItem : items) {
            BulkItemResponse item = topLevelItem.getItemContent();
            if (item.failed) {
                return true;
            }
        }
        return false;
    }

    public BulkItemTopLevelResponse[] getItems() {
        return items;
    }

    public boolean isErrors() {
        return errors;
    }

    public Throwable buildFailureMessage() {
        StringBuilder sbf = new StringBuilder();
        int failures = 0;
        for (BulkItemTopLevelResponse topLevelItem : items) {
            BulkItemResponse item = topLevelItem.getItemContent();
            if (item.failed) {
                if (logger.isTraceEnabled()) {
                    sbf.append(item.getIndex()).append("/").append(item.getType()).append("/").append(item.getId());
                    sbf.append(":").append(item.getOpType()).append(":").append(item.getFailureMessage());
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

    public static class BulkItemTopLevelResponse {

        private BulkItemResponse index;
        private BulkItemResponse delete;

        public BulkItemResponse getDelete() {
            return delete;
        }

        public BulkItemResponse getIndex() {
            return index;
        }

        public BulkItemResponse getItemContent() {
            if (index != null) return index;
            if (delete != null) return delete;
            return null;
        }

        @Override
        public String toString() {
            final StringBuffer sb = new StringBuffer("BulkItemTopLevelResponse{");
            sb.append("index=").append(index);
            sb.append(", delete=").append(delete);
            sb.append('}');
            return sb.toString();
        }
    }

    public static class BulkItemResponse {
        private boolean failed;
        @JsonProperty("_index")
        private String index;
        @JsonProperty("_type")
        private String type;
        @JsonProperty("_id")
        private String id;
        private String opType;
        private String failureMessage;

        public boolean isFailed() {
            return failed;
        }

        public String getIndex() {
            return index;
        }

        public String getType() {
            return type;
        }

        public String getId() {
            return id;
        }

        public String getOpType() {
            return opType;
        }

        public String getFailureMessage() {
            return failureMessage;
        }

        public void setFailed(boolean failed) {
            this.failed = failed;
        }

        public void setIndex(String index) {
            this.index = index;
        }

        public void setType(String type) {
            this.type = type;
        }

        public void setId(String id) {
            this.id = id;
        }

        public void setOpType(String opType) {
            this.opType = opType;
        }

        public void setFailureMessage(String failureMessage) {
            this.failureMessage = failureMessage;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("BulkItemResponse{");
            sb.append("failed=").append(failed);
            sb.append(", index='").append(index).append('\'');
            sb.append(", type='").append(type).append('\'');
            sb.append(", id='").append(id).append('\'');
            sb.append(", opType=").append(opType);
            sb.append(", failureMessage='").append(failureMessage).append('\'');
            sb.append('}');
            return sb.toString();
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("BulkResponse{");
        sb.append("items=").append(items == null ? "null" : Arrays.asList(items).toString());
        sb.append('}');
        return sb.toString();
    }
}
