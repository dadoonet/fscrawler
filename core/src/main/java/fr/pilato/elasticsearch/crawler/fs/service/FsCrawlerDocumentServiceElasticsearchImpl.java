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
import fr.pilato.elasticsearch.crawler.fs.client.IElasticsearchClient;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientException;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClient;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;

import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.serialize;

public class FsCrawlerDocumentServiceElasticsearchImpl implements FsCrawlerDocumentService {

    private static final Logger logger = LogManager.getLogger(FsCrawlerDocumentServiceElasticsearchImpl.class);

    private final IElasticsearchClient client;

    public FsCrawlerDocumentServiceElasticsearchImpl(Path config, FsSettings settings) {
        this.client = new ElasticsearchClient(config, settings);
    }

    public IElasticsearchClient getClient() {
        return client;
    }

    @Override
    public void start() throws IOException, ElasticsearchClientException {
        client.start();
        logger.debug("Elasticsearch Document Service started");
    }

    @Override
    public void close() throws IOException {
        client.close();
        logger.debug("Elasticsearch Document Service stopped");
    }

    @Override
    public String getVersion() throws IOException, ElasticsearchClientException {
        return client.getVersion();
    }

    @Override
    public void createSchema() throws Exception {
        client.createIndices();
    }

    @Override
    public void index(String index, String id, Doc doc, String pipeline) {
        indexRawJson(index, id, serialize(doc), pipeline);
    }

    @Override
    public void indexRawJson(String index, String id, String json, String pipeline) {
        logger.debug("Indexing {}/{}?pipeline={}", index, id, pipeline);
        client.indexRawJson(index, id, json, pipeline);
    }

    @Override
    public void delete(String index, String id) {
        logger.debug("Deleting {}/{}", index, id);
        client.delete(index, id);
    }

    @Override
    public void deleteSingle(String index, String id) throws ElasticsearchClientException {
        logger.debug("Deleting {}/{}", index, id);
        client.deleteSingle(index, id);
    }

    @Override
    public void refresh(String index) throws IOException, ElasticsearchClientException {
        logger.debug("Refreshing {}", index);
        client.refresh(index);
    }

    @Override
    public ESSearchResponse search(ESSearchRequest request) throws IOException, ElasticsearchClientException {
        logger.debug("Searching {}", request);
        return client.search(request);
    }

    @Override
    public boolean exists(String index, String id) throws IOException, ElasticsearchClientException {
        logger.debug("Search if document {}/{} exists", index, id);
        return client.exists(index, id);
    }

    @Override
    public ESSearchHit get(String index, String id) throws IOException, ElasticsearchClientException {
        logger.debug("Getting {}/{}", index, id);
        return client.get(index, id);
    }

    @Override
    public void flush() {
        logger.debug("Flushing");
        client.flush();
    }
}
