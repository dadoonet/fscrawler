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
}
