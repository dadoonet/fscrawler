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

package fr.pilato.elasticsearch.crawler.fs.thirdparty.wpsearch;

import fr.pilato.elasticsearch.crawler.fs.framework.bulk.FsCrawlerOperation;

import java.util.Map;

public class WPSearchOperation implements FsCrawlerOperation<WPSearchOperation> {
    private final String customSourceId;
    private final Map<String, Object> document;

    public WPSearchOperation(String customSourceId, Map<String, Object> document) {
        this.customSourceId = customSourceId;
        this.document = document;
    }

    public String getCustomSourceId() {
        return customSourceId;
    }

    public Map<String, Object> getDocument() {
        return document;
    }

    @Override
    public int compareTo(WPSearchOperation request) {
        // We check on the id field
        String id1 = (String) document.get("id");
        String id2 = (String) request.document.get("id");
        return id1.compareTo(id2);
    }
}
