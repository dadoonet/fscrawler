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

import java.util.Arrays;

public class SearchRequest {

    private String query;

    private String[] fields;

    private Integer size;

    public static Builder builder() {
        return new Builder();
    }

    public SearchRequest() {

    }

    public SearchRequest(String query, String[] fields, Integer size) {
        this.query = query;
        this.fields = fields;
        this.size = size;
    }

    public Integer getSize() {
        return size;
    }

    public String getQuery() {
        return query;
    }

    public String[] getFields() {
        return fields;
    }

    @Override
    public String   toString() {
        final StringBuilder sb = new StringBuilder("SearchRequest{");
        sb.append("query=").append(query);
        sb.append(", fields=").append(fields == null ? "null" : Arrays.asList(fields).toString());
        sb.append(", size=").append(size);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SearchRequest that = (SearchRequest) o;

        if (query != null ? !query.equals(that.query) : that.query != null) return false;
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        if (!Arrays.equals(fields, that.fields)) return false;
        return size != null ? size.equals(that.size) : that.size == null;

    }

    @Override
    public int hashCode() {
        int result = query != null ? query.hashCode() : 0;
        result = 31 * result + Arrays.hashCode(fields);
        result = 31 * result + (size != null ? size.hashCode() : 0);
        return result;
    }

    public static class Builder {
        private String query;

        private String[] fields;

        private Integer size;

        public Builder setQuery(String query) {
            this.query = query;
            return this;
        }

        public Builder setFields(String... fields) {
            this.fields = fields;
            return this;
        }

        public Builder setSize(Integer size) {
            this.size = size;
            return this;
        }

        public SearchRequest build() {
            return new SearchRequest(query, fields, size);
        }
    }
}
