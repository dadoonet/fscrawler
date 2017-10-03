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

import java.util.List;
import java.util.Map;

/**
 * Search response object. We only unwrap needed fields.
 */
@SuppressWarnings("CanBeFinal")
public class SearchResponse {

    private Hits hits;

    public Hits getHits() {
        return hits;
    }

    private Map<String, Object> aggregations;

    public Map<String, Object> getAggregations() {
        return aggregations;
    }

    @Override
    public String toString() {
        return "SearchResponse{" + "hits=" + hits +
                ", aggregations=" + aggregations +
                '}';
    }

    public static class Hits {

        private List<Hit> hits;

        private long total;

        public List<Hit> getHits() {
            return hits;
        }

        public long getTotal() {
            return total;
        }

        @Override
        public String toString() {
            String sb = "Hits{" + "hits=" + hits +
                    ", total=" + total +
                    '}';
            return sb;
        }
    }

    public static class Hit {

        @JsonProperty("_source")
        private Map<String, Object> source;

        @JsonProperty("_index")
        private String index;

        @JsonProperty("_type")
        private String type;

        @JsonProperty("_id")
        private String id;

        @JsonProperty("_version")
        private Long version;

        private Map<String, Object> fields;

        private Map<String, List<String>> highlight;

        public Map<String, Object> getSource() {
            return source;
        }

        public Map<String, Object> getFields() {
            return fields;
        }

        public Map<String, List<String>> getHighlight() {
            return highlight;
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

        public Long getVersion() {
            return version;
        }

        @Override
        public String toString() {
            return "Hit{" +
                    "index=" + index +
                    ", type=" + type +
                    ", id=" + id +
                    ", version=" + version +
                    ", source=" + source +
                    ", fields=" + fields +
                    ", highlight=" + highlight +
                    '}';
        }
    }
}
