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
     * Shutdown the internal REST Low Level client
     * @throws IOException In case of error
     */
    void shutdown() throws IOException;

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
     * Index a document
     * @param index     Index name
     * @param type      Type name
     * @param id        Document ID
     * @param json      JSON
     * @param pipeline  Pipeline (can be null)
     */
    void index(String index, String type, String id, String json, String pipeline);

    /**
     * Delete a document
     * @param index     Index name
     * @param type      Type name
     * @param id        Document ID
     */
    void delete(String index, String type, String id);

    /**
     * Close the client. Helpful to close all internal resources like bulk processors.
     * You also need to call super.close();
     */
    void close() throws IOException;
}
