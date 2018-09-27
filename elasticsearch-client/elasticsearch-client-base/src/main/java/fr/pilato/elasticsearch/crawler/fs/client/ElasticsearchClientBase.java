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


import java.io.Closeable;
import java.io.IOException;

/**
 * Simple Elasticsearch client over HTTP or HTTPS.
 * Only needed methods are exposed.
 */
public interface ElasticsearchClientBase extends Closeable {

    /**
     * Start the client and its internal resources. This must be called before any operation can be performed.
     * @throws IOException in case of communication error with the cluster
     */
    void start() throws IOException;

    /**
     * Get version about the node it's connected to
     * @throws IOException in case of communication error with the cluster
     */
    ESVersion getVersion() throws IOException;

    /**
     * Create an index
     * @param index index name
     * @param ignoreErrors don't fail if the index already exists
     * @param indexSettings index settings if any
     * @throws IOException In case of error
     */
    void createIndex(String index, boolean ignoreErrors, String indexSettings) throws IOException;

    /**
     * Check if an index exists
     * @param index index name
     * @return true if the index exists, false otherwise
     * @throws IOException In case of error
     */
    boolean isExistingIndex(String index) throws IOException;

    /**
     * Check if a pipeline exists
     * @param pipeline pipeline name
     * @return true if the pipeline exists, false otherwise
     * @throws IOException In case of error
     */
    boolean isExistingPipeline(String pipeline) throws IOException;

    /**
     * Refresh an index
     * @param index index name
     * @throws IOException In case of error
     */
    void refresh(String index) throws IOException;

    /**
     * Wait for an index to become at least yellow (all primaries assigned)
     * @param index index name
     * @throws IOException In case of error
     */
    void waitForHealthyIndex(String index) throws IOException;

    /**
     * Reindex data from one index/type to another index
     * @param sourceIndex source index name
     * @param sourceType source type name
     * @param targetIndex target index name
     * @return The number of documents that have been reindexed
     * @throws IOException In case of error
     */
    int reindex(String sourceIndex, String sourceType, String targetIndex) throws IOException;

    /**
     * Fully removes a type from an index (removes data)
     * @param index index name
     * @param type type
     * @throws IOException In case of error
     */
    void deleteByQuery(String index, String type) throws IOException;

    // Utility methods

    boolean isIngestSupported();

    String getDefaultTypeName();

    /**
     * Index a document using a BulkProcessor behind the scenes
     * @param index     Index name
     * @param type      Type name
     * @param id        Document ID
     * @param json      JSON
     * @param pipeline  Pipeline (can be null)
     */
    void index(String index, String type, String id, String json, String pipeline);

    /**
     * Index a document (for test purposes only)
     * @param index     Index name
     * @param type      Type name
     * @param id        Document ID
     * @param json      JSON
     */
    void indexSingle(String index, String type, String id, String json) throws IOException;

    /**
     * Delete a document using a BulkProcessor behind the scenes
     * @param index     Index name
     * @param type      Type name
     * @param id        Document ID
     */
    void delete(String index, String type, String id);

    /**
     * Create all needed indices
     * @throws Exception in case of error
     * @deprecated replace with an index template
     */
    public void createIndices() throws Exception;

    /**
     * Run a search
     * @param request Search Request
     * @return A search response object
     * @throws IOException In case of error
     */
    ESSearchResponse search(ESSearchRequest request) throws IOException;

    /**
     * Remove an index
     * @param index Index name
     * @throws IOException In case of error
     */
    void deleteIndex(String index) throws IOException;

    /**
     * Flush any pending Bulk operation. Used for tests only.
     * Note that flushing means immediate execution of the bulk but it does
     * not wait for the bulk to be fully executed.
     */
    void flush();

    /**
     * Perform a LowLevel Request
     * @param method        HTTP method
     * @param endpoint      Endpoint
     * @param jsonEntity    Json entity if any
     * @throws IOException In case of error
     */
    void performLowLevelRequest(String method, String endpoint, String jsonEntity) throws IOException;

    /**
     * Get a document by its ID
     * @param index Index name
     * @param type  Type
     * @param id    Document id
     * @return A Search Hit
     * @throws IOException In case of error
     */
    ESSearchHit get(String index, String type, String id) throws IOException;

    /**
     * Check that a document exists
     * @param index Index name
     * @param type  Type
     * @param id    Document id
     * @return true if it exists, false otherwise
     * @throws IOException In case of error
     */
    boolean exists(String index, String type, String id) throws IOException;
}
