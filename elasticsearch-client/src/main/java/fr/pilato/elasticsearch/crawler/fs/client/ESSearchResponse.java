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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ESSearchResponse {
    private final List<ESSearchHit> hits = new ArrayList<>();
    private long totalHits;
    private final Map<String, ESTermsAggregation> aggregations = new HashMap<>();
    private final String json;

    public ESSearchResponse(String json) {
        this.json = json;
    }

    public List<ESSearchHit> getHits() {
        return hits;
    }

    public void addHit(ESSearchHit hit) {
        this.hits.add(hit);
    }

    public long getTotalHits() {
        return totalHits;
    }

    public void setTotalHits(long totalHits) {
        this.totalHits = totalHits;
    }

    public void setTotalHits(int totalHits) {
        this.totalHits = totalHits;
    }

    public Map<String, ESTermsAggregation> getAggregations() {
        return aggregations;
    }

    public void addAggregation(String name, ESTermsAggregation aggregation) {
        aggregations.put(name, aggregation);
    }

    public String getJson() {
        return json;
    }
}
