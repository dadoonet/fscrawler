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
import fr.pilato.elasticsearch.crawler.fs.client.ESBoolQuery;
import fr.pilato.elasticsearch.crawler.fs.client.ESMatchQuery;
import fr.pilato.elasticsearch.crawler.fs.client.ESPrefixQuery;
import fr.pilato.elasticsearch.crawler.fs.client.ESQuery;
import fr.pilato.elasticsearch.crawler.fs.client.ESRangeQuery;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchHit;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.client.ESTermQuery;
import fr.pilato.elasticsearch.crawler.fs.client.WorkplaceSearchClient;
import fr.pilato.elasticsearch.crawler.fs.client.WorkplaceSearchClientUtil;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;

public class FsCrawlerDocumentServiceWorkplaceSearchImpl implements FsCrawlerDocumentService {

    private static final Logger logger = LogManager.getLogger(FsCrawlerDocumentServiceWorkplaceSearchImpl.class);

    private final WorkplaceSearchClient client;

    public FsCrawlerDocumentServiceWorkplaceSearchImpl(Path config, FsSettings settings) throws RuntimeException {
        this.client = WorkplaceSearchClientUtil.getInstance(config, settings);

        if (client == null) {
            throw new FsCrawlerIllegalConfigurationException("As we can not find an existing Workplace Search client for elastic stack before 7.8," +
                    " you can't define workplace settings in your configuration. FSCrawler will refuse to start.");
        }
    }

    @Override
    public void start() throws IOException {
        client.start();
        logger.debug("Workplace Search Document Service started");
    }

    @Override
    public void close() throws IOException {
        client.close();
        logger.debug("Workplace Search Document Service stopped");
    }

    @Override
    public String getVersion() throws IOException {
        throw new RuntimeException("Not implemented yet");
    }

    @Override
    public void createSchema() {
        // TODO implement this as this is not true anymore
        // There is no way yet to create a schema in workplace before hand.
    }

    @Override
    public void index(String index, String id, Doc doc, String pipeline) {
        logger.debug("Indexing {}/{}?pipeline={}", index, id, pipeline);
        client.index(id, doc);
    }

    @Override
    public void indexRawJson(String index, String id, String json, String pipeline) {
        throw new RuntimeException("We can't send Raw Json Documents to Workplace Search");
    }

    @Override
    public void delete(String index, String id) {
        logger.debug("Deleting {}/{}", index, id);
        client.delete(id);
    }

    @Override
    public void refresh(String index) throws IOException {
        logger.debug("Refreshing {}", index);
    }

    @Override
    public ESSearchResponse search(ESSearchRequest request) throws IOException {
        logger.debug("Searching {}", request);

        // Convert the ESSearchRequest to a WPSearch request
        client.search(toWorkplaceSearchQuery(request.getESQuery()), null);
        return null;
    }

    @Override
    public boolean exists(String index, String id) throws IOException {
        logger.debug("Search if document {} exists", id);
        return client.exists(id);
    }

    @Override
    public ESSearchHit get(String index, String id) throws IOException {
        logger.debug("Getting {}/{}", index, id);
        String json = client.get(id);

        // TODO parse the json and return it as an ESSearchHit
        return null;
    }

    @Override
    public void flush() {
        logger.debug("Flushing");
        client.flush();
    }

    private String toWorkplaceSearchQuery(ESQuery query) {
        if (query instanceof ESTermQuery) {
            ESTermQuery esQuery = (ESTermQuery) query;
            return esQuery.getValue();
        }
        if (query instanceof ESMatchQuery) {
            ESMatchQuery esQuery = (ESMatchQuery) query;
            return esQuery.getValue();
        }
        if (query instanceof ESPrefixQuery) {
            ESPrefixQuery esQuery = (ESPrefixQuery) query;
        }
        if (query instanceof ESRangeQuery) {
            ESRangeQuery esQuery = (ESRangeQuery) query;
            if (esQuery.getFrom() != null) {
            }
            if (esQuery.getTo() != null) {
            }
        }
        if (query instanceof ESBoolQuery) {
            ESBoolQuery esQuery = (ESBoolQuery) query;
            for (ESQuery clause : esQuery.getMustClauses()) {
            }
        }
        throw new IllegalArgumentException("Query " + query.getClass().getSimpleName() + " not implemented yet");
    }

}
