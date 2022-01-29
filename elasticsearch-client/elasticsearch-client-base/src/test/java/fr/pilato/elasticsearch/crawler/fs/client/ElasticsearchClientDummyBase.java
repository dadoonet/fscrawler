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

import fr.pilato.elasticsearch.crawler.fs.beans.Doc;

public abstract class ElasticsearchClientDummyBase implements ElasticsearchClient {

    @Override
    public String compatibleVersion() {
        return "0";
    }

    @Override
    public void start() {
        // Testing purpose only
    }

    @Override
    public String getVersion() {
        return null;
    }

    @Override
    public void createIndex(String index, boolean ignoreErrors, String indexSettings) {
        // Testing purpose only
    }

    @Override
    public boolean isExistingIndex(String index) {
        return false;
    }

    @Override
    public boolean isExistingPipeline(String pipeline) {
        return false;
    }

    @Override
    public void refresh(String index) {
        // Testing purpose only
    }

    @Override
    public void waitForHealthyIndex(String index) {
        // Testing purpose only
    }

    @Override
    public boolean isIngestSupported() {
        return false;
    }

    @Override
    public String getDefaultTypeName() {
        return null;
    }

    @Override
    public void index(String index, String id, Doc doc, String pipeline) {
        // Testing purpose only
    }

    @Override
    public void indexRawJson(String index, String id, String json, String pipeline) {
        // Testing purpose only
    }

    @Override
    public void indexSingle(String index, String id, String json, String pipeline) {
        // Testing purpose only
    }

    @Override
    public void delete(String index, String id) {
        // Testing purpose only
    }

    @Override
    public boolean deleteSingle(String index, String id) {
        // Testing purpose only
        return false;
    }

    @Override
    public void createIndices() {
        // Testing purpose only
    }

    @Override
    public ESSearchResponse search(ESSearchRequest request) {
        return null;
    }

    @Override
    public void deleteIndex(String index) {
        // Testing purpose only
    }

    @Override
    public void flush() {
        // Testing purpose only
    }

    @Override
    public String performLowLevelRequest(String method, String endpoint, String jsonEntity) {
        // Testing purpose only
        return method;
    }

    @Override
    public ESSearchHit get(String index, String id) {
        return null;
    }

    @Override
    public boolean exists(String index, String id) {
        return false;
    }

    @Override
    public void close() {
        // Testing purpose only
    }
}
