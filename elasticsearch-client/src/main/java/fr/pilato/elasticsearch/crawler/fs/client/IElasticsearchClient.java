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

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

/**
 * Simple Elasticsearch client over HTTP or HTTPS.
 * Only needed methods are exposed.
 */
public interface IElasticsearchClient extends Closeable {

    /**
     * Type name when using very old Elasticsearch versions like 6.x
     */
    String INDEX_TYPE_DOC = "_doc";

    /**
     * Start the client and its internal resources. This must be called before any operation can be performed.
     * @throws IOException in case of communication error with the cluster
     */
    void start() throws IOException, ElasticsearchClientException;

    /**
     * Get the list of the active nodes. Could be use in an admin status API.
     * @return the list of available nodes
     */
    List<String> getAvailableNodes();

    /**
     * Get version about the node it's connected to
     * @throws IOException in case of communication error with the cluster
     */
    String getVersion() throws IOException, ElasticsearchClientException;

    /**
     * Get the major version about the node it's connected to
     */
    int getMajorVersion();

    /**
     * Create an index
     * @param index index name
     * @param ignoreExistingIndex don't fail if the index already exists
     * @param indexSettings index settings if any
     * @throws IOException In case of error
     */
    void createIndex(String index, boolean ignoreExistingIndex, String indexSettings) throws IOException, ElasticsearchClientException;

    /**
     * Check if an index exists
     * @param index index name
     * @return true if the index exists, false otherwise
     * @throws IOException In case of error
     */
    boolean isExistingIndex(String index) throws IOException, ElasticsearchClientException;

    /**
     * Check if a pipeline exists
     * @param pipeline pipeline name
     * @return true if the pipeline exists, false otherwise
     * @throws IOException In case of error
     */
    boolean isExistingPipeline(String pipeline) throws IOException, ElasticsearchClientException;

    /**
     * Refresh an index
     * @param index index name
     * @throws IOException In case of error
     */
    void refresh(String index) throws IOException, ElasticsearchClientException;

    /**
     * Wait for an index to become at least yellow (all primaries assigned)
     * @param index index name
     * @throws IOException In case of error
     */
    void waitForHealthyIndex(String index) throws IOException, ElasticsearchClientException;

    /**
     * Index a document (might use a BulkProcessor behind the scenes)
     * @param index     Index name
     * @param id        Document ID
     * @param doc       Document to index
     * @param pipeline  Pipeline (can be null)
     */
    void index(String index, String id, Doc doc, String pipeline);

    /**
     * Index a Raw Json in Elasticsearch
     * @param index     Index name
     * @param id        Document ID
     * @param json      Document to index
     * @param pipeline  Pipeline (can be null)
     */
    void indexRawJson(String index, String id, String json, String pipeline);

    /**
     * Index a single document using the Index Document API
     * @param index     Index name
     * @param id        Document ID
     * @param json      Document to index
     * @param pipeline  Pipeline (can be null)
     */
    void indexSingle(String index, String id, String json, String pipeline) throws IOException, ElasticsearchClientException;

    /**
     * Delete a document using a BulkProcessor behind the scenes
     * @param index     Index name
     * @param id        Document ID
     */
    void delete(String index, String id);

    /**
     * Delete a single document using the Delete Document API
     * @param index     Index name
     * @param id        Document ID
     */
    void deleteSingle(String index, String id) throws ElasticsearchClientException;

    /**
     * Create all needed indices
     * @throws Exception in case of error
     * @deprecated replace with an index template
     */
    @Deprecated
    void createIndices() throws Exception;

    /**
     * Run a search
     * @param request Search Request
     * @return A search response object
     * @throws IOException In case of error
     */
    ESSearchResponse search(ESSearchRequest request) throws IOException, ElasticsearchClientException;

    /**
     * Remove an index
     * @param index Index name
     * @throws IOException In case of error
     */
    void deleteIndex(String index) throws IOException, ElasticsearchClientException;

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
     * @return the response from the server
     */
    String performLowLevelRequest(String method, String endpoint, String jsonEntity) throws IOException, ElasticsearchClientException;

    /**
     * Get a document by its ID
     * @param index Index name
     * @param id    Document id
     * @return A Search Hit
     * @throws IOException In case of error
     */
    ESSearchHit get(String index, String id) throws IOException, ElasticsearchClientException;

    /**
     * Check that a document exists
     * @param index Index name
     * @param id    Document id
     * @return true if it exists, false otherwise
     * @throws IOException In case of error
     */
    boolean exists(String index, String id) throws IOException, ElasticsearchClientException;

    /**
     * Send a _bulk request to Elasticsearch
     * @param ndjson    the bulk content to send
     * @return  the outcome
     */
    String bulk(String ndjson) throws ElasticsearchClientException;
}
