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


import fr.pilato.elasticsearch.crawler.fs.beans.Doc;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * Workplace Search Client when we don't need at all any Workplace feature.
 */
public class WorkplaceSearchClientNoOp implements WorkplaceSearchClient {

    @Override
    public String compatibleVersion() {
        return "NoOp";
    }

    @Override
    public void start() throws IOException {
    }

    @Override
    public String getVersion() throws IOException {
        return "NoOp";
    }

    /**
     * Create an index
     * @param index index name
     * @param ignoreErrors don't fail if the index already exists
     * @param indexSettings index settings if any
     * @throws IOException In case of error
     */
    public void createIndex(String index, boolean ignoreErrors, String indexSettings) throws IOException {
    }

    /**
     * Check if an index exists
     * @param index index name
     * @return true if the index exists, false otherwise
     * @throws IOException In case of error
     */
    public boolean isExistingIndex(String index) throws IOException {
        throw new IOException("NoOp");
    }

    /**
     * Check if a pipeline exists
     * @param pipelineName pipeline name
     * @return true if the pipeline exists, false otherwise
     * @throws IOException In case of error
     */
    public boolean isExistingPipeline(String pipelineName) throws IOException {
        return false;
    }

    /**
     * Refresh an index
     * @param index index name
     * @throws IOException In case of error
     */
    public void refresh(String index) throws IOException {
        throw new IOException("NoOp");
    }

    /**
     * Wait for an index to become at least yellow (all primaries assigned)
     * @param index index name
     * @throws IOException In case of error
     */
    public void waitForHealthyIndex(String index) throws IOException {
    }

    // Utility methods

    public boolean isIngestSupported() {
        return false;
    }

    public String getDefaultTypeName() {
        return "NoOp";
    }

    @Override
    public void index(String index, String id, Doc doc, String pipeline) {
    }

    @Override
    public void indexSingle(String index, String id, Doc doc) {
    }

    @Override
    public void delete(String index, String id) {
    }

    @Override
    public void close() throws IOException {
    }

    public void createIndices() throws Exception {
    }

    @Override
    public ESSearchResponse search(ESSearchRequest request) throws IOException {
        return new ESSearchResponse();
    }

    @Override
    public void deleteIndex(String index) throws IOException {
        throw new IOException("NoOp");
    }

    @Override
    public void flush() {
    }

    @Override
    public void performLowLevelRequest(String method, String endpoint, String jsonEntity) throws IOException {
        throw new IOException("NoOp");
    }

    @Override
    public ESSearchHit get(String index, String id) throws IOException {
        throw new IOException("NoOp");
    }

    @Override
    public boolean exists(String index, String id) throws IOException {
        throw new IOException("NoOp");
    }
}
