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
public class SearchResponse {

    private Hits hits;

    public Hits getHits() {
        return hits;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("SearchResponse{");
        sb.append("hits=").append(hits);
        sb.append('}');
        return sb.toString();
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
            final StringBuilder sb = new StringBuilder("Hits{");
            sb.append("hits=").append(hits);
            sb.append(", total=").append(total);
            sb.append('}');
            return sb.toString();
        }
    }

    public static class Hit {

        @JsonProperty("_source")
        private Map<String, Object> source;

        private Map<String, Object> fields;

        public Map<String, Object> getSource() {
            return source;
        }

        public Map<String, Object> getFields() {
            return fields;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("Hit{");
            sb.append("source=").append(source);
            sb.append(", fields=").append(fields);
            sb.append('}');
            return sb.toString();
        }
    }
}
