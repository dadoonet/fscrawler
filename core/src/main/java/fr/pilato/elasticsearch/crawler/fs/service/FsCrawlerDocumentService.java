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

package fr.pilato.elasticsearch.crawler.fs.service;

import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchHit;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientException;

import java.io.IOException;

public interface FsCrawlerDocumentService extends FsCrawlerService {
    /**
     * Create a schema for the dataset. This is called when the service starts
     * @throws Exception in case of error
     */
    void createSchema() throws Exception;

    /**
     * Send a document to the target service
     * @param index     Index name
     * @param id        Document id
     * @param doc       Document to index
     * @param pipeline  Pipeline (can be null)
     */
    void index(String index, String id, Doc doc, String pipeline);

    /**
     * Send a Raw Json to the target service
     * @param index     Index name
     * @param id        Document ID
     * @param json      Document to index
     * @param pipeline  Pipeline (can be null)
     */
    void indexRawJson(String index, String id, String json, String pipeline);

    /**
     * Remove a document from the target service (could be asynchronous)
     * @param index     Index name
     * @param id        Document ID
     */
    void delete(String index, String id);

    /**
     * Remove a document from the target service
     * @param index     Index name
     * @param id        Document ID
     */
    void deleteSingle(String index, String id) throws ElasticsearchClientException;

    /**
     * Refresh the document database to make changes visible
     * @param index     Optional index name
     */
    void refresh(String index) throws IOException, ElasticsearchClientException;

    /**
     * Search for information
     * @param request   The request
     * @return a response from the document service
     */
    ESSearchResponse search(ESSearchRequest request) throws IOException, ElasticsearchClientException;

    /**
     * Remove a document from the target service
     * @param index     Index name
     * @param id        Document ID
     * @return true if the document exists
     */
    boolean exists(String index, String id) throws IOException, ElasticsearchClientException;

    /**
     * Get a document from the target service
     * @param index     Index name
     * @param id        Document ID
     * @return the document or null
     */
    ESSearchHit get(String index, String id) throws IOException, ElasticsearchClientException;

    /**
     * Flush any pending operation
     */
    void flush();
}
