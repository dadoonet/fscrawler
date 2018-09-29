/*
 * Licensed to Elasticsearch under one or more contributor
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

import fr.pilato.elasticsearch.crawler.fs.client.ESSearchHit;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.client.ESVersion;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClient;

public abstract class ElasticsearchClientDummyBase implements ElasticsearchClient {

    @Override
    public byte compatibleVersion() {
        return 0;
    }

    @Override
    public void start() {

    }

    @Override
    public ESVersion getVersion() {
        return null;
    }

    @Override
    public void createIndex(String index, boolean ignoreErrors, String indexSettings) {

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

    }

    @Override
    public void waitForHealthyIndex(String index) {

    }

    @Override
    public int reindex(String sourceIndex, String sourceType, String targetIndex) {
        return 0;
    }

    @Override
    public void deleteByQuery(String index, String type) {

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
    public void index(String index, String type, String id, String json, String pipeline) {

    }

    @Override
    public void indexSingle(String index, String type, String id, String json) {

    }

    @Override
    public void delete(String index, String type, String id) {

    }

    @Override
    public void createIndices() {

    }

    @Override
    public ESSearchResponse search(ESSearchRequest request) {
        return null;
    }

    @Override
    public void deleteIndex(String index) {

    }

    @Override
    public void flush() {

    }

    @Override
    public void performLowLevelRequest(String method, String endpoint, String jsonEntity) {

    }

    @Override
    public ESSearchHit get(String index, String type, String id) {
        return null;
    }

    @Override
    public boolean exists(String index, String type, String id) {
        return false;
    }

    @Override
    public void close() {

    }
}
