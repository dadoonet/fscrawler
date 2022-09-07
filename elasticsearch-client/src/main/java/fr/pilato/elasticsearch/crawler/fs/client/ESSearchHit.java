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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ESSearchHit {
    private String index;
    private String id;
    private Long version;
    private String source;
    private Map<String, List<String>> storedFields;
    private final Map<String, List<String>> highlightFields = new HashMap<>();

    public Map<String, List<String>> getStoredFields() {
        return storedFields;
    }

    public void setStoredFields(Map<String, List<String>> storedFields) {
        this.storedFields = storedFields;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSource() {
        return source;
    }

    public Map<String, List<String>> getHighlightFields() {
        return highlightFields;
    }

    public void addHighlightField(String name, List<String> highlightField) {
        highlightFields.put(name, highlightField);
    }

    public String getIndex() {
        return index;
    }

    public void setIndex(String index) {
        this.index = index;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

}
