/*
 * Licensed to David Pilato under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

import java.util.ArrayList;
import java.util.List;

public class ESSearchRequest {

    private String index;
    private String sort;
    private Integer size;
    private ESQuery ESQuery;
    private final List<String> storedFields = new ArrayList<>();
    private final List<String> highlighters = new ArrayList<>();
    private final List<ESTermsAggregation> aggregations = new ArrayList<>();

    public String getIndex() {
        return index;
    }

    public ESSearchRequest withIndex(String index) {
        this.index = index;
        return this;
    }

    public Integer getSize() {
        return size;
    }

    public ESSearchRequest withSize(Integer size) {
        this.size = size;
        return this;
    }

    public ESQuery getESQuery() {
        return ESQuery;
    }

    public ESSearchRequest withESQuery(ESQuery query) {
        this.ESQuery = query;
        return this;
    }

    public List<String> getStoredFields() {
        return storedFields;
    }

    public ESSearchRequest addStoredField(String storedField) {
        this.storedFields.add(storedField);
        return this;
    }

    public String getSort() {
        return sort;
    }

    public ESSearchRequest withSort(String sort) {
        this.sort = sort;
        return this;
    }

    public List<String> getHighlighters() {
        return highlighters;
    }

    public ESSearchRequest addHighlighter(String field) {
        highlighters.add(field);
        return this;
    }

    public List<ESTermsAggregation> getAggregations() {
        return aggregations;
    }

    public ESSearchRequest withAggregation(ESTermsAggregation aggregation) {
        this.aggregations.add(aggregation);
        return this;
    }
}
