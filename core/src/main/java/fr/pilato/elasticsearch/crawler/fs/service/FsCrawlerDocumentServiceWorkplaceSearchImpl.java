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

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.client.ESBoolQuery;
import fr.pilato.elasticsearch.crawler.fs.client.ESMatchQuery;
import fr.pilato.elasticsearch.crawler.fs.client.ESQuery;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchHit;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.client.ESTermQuery;
import fr.pilato.elasticsearch.crawler.fs.client.IWorkplaceSearchClient;
import fr.pilato.elasticsearch.crawler.fs.client.WorkplaceSearchClient;
import fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import jakarta.ws.rs.ServiceUnavailableException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.mapper;
import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.parseJsonAsDocumentContext;

public class FsCrawlerDocumentServiceWorkplaceSearchImpl implements FsCrawlerDocumentService {

    private static final Logger logger = LogManager.getLogger(FsCrawlerDocumentServiceWorkplaceSearchImpl.class);
    private final Configuration conf = Configuration.builder().jsonProvider(new JacksonJsonProvider(mapper)).build();

    private final IWorkplaceSearchClient client;

    public FsCrawlerDocumentServiceWorkplaceSearchImpl(Path config, FsSettings settings) throws RuntimeException {
        this.client = new WorkplaceSearchClient(config, settings);
    }

    @Override
    public void start() throws IOException {
        try {
            client.start();
        } catch (ServiceUnavailableException e) {
            logger.fatal("Can not start the Workplace Search client.");
        }
        logger.debug("Workplace Search Document Service started");
    }

    @Override
    public void close() throws IOException {
        client.close();
        logger.debug("Workplace Search Document Service stopped");
    }

    @Override
    public String getVersion() {
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
    public void deleteSingle(String index, String id) {
        logger.debug("Deleting {}/{}", index, id);
        client.delete(id);
    }

    @Override
    public void refresh(String index) {
        // We do nothing
    }

    @Override
    public ESSearchResponse search(ESSearchRequest request) throws IOException {
        logger.debug("Searching {}", request);

        String json = client.search(toWorkplaceSearchQuery(request.getESQuery()),
                toWorkplaceSearchFilters(request.getESQuery()));

        DocumentContext document = parseJsonAsDocumentContext(json);
        ESSearchResponse response = new ESSearchResponse(json);

        int totalHits = document.read("$.meta.page.total_results");
        response.setTotalHits(totalHits);
        for (int i = 0; i < totalHits; i++) {
            ESSearchHit hit = new ESSearchHit();
            // We read the hit and transform it again as a json document using Jackson
            hit.setSource(mapper.writeValueAsString(document.read("$.results[" + i + "]")));
            response.addHit(hit);
        }

        return response;
    }

    @Override
    public boolean exists(String index, String id) {
        logger.debug("Search if document {} exists", id);
        return client.exists(id);
    }

    @Override
    public ESSearchHit get(String index, String id) {
        logger.debug("Getting {}/{}", index, id);
        String json = client.get(id);

        ESSearchHit hit = new ESSearchHit();
        hit.setIndex(index);
        hit.setId(id);
        hit.setSource(json);

        return hit;
    }

    @Override
    public void flush() {
        logger.debug("Flushing");
        client.flush();
    }

    /**
     * We extract the {@link ESMatchQuery} from the {@link ESQuery}.
     * We ignore totally the {@link ESTermQuery} if any, and we fail for the others.
     * @param query the query to transform as a fulltext search content
     * @return a fulltext search content
     */
    private String toWorkplaceSearchQuery(ESQuery query) {
        if (query == null) {
            return null;
        }
        if (query instanceof ESMatchQuery) {
            ESMatchQuery esQuery = (ESMatchQuery) query;
            return esQuery.getValue();
        }
        if (query instanceof ESTermQuery) {
            return null;
        }
        if (query instanceof ESBoolQuery) {
            ESBoolQuery esQuery = (ESBoolQuery) query;
            for (ESQuery clause : esQuery.getMustClauses()) {
                String fulltextQuery = toWorkplaceSearchQuery(clause);
                if (fulltextQuery != null) {
                    return fulltextQuery;
                }
            }
            return null;
        }
        throw new IllegalArgumentException("Query " + query.getClass().getSimpleName() + " is not supported for " +
                "fulltext search within Workplace Search");
    }

    /**
     * We extract the {@link ESTermQuery} from the {@link ESQuery}.
     * It also supports the {@link ESBoolQuery}.
     * We ignore totally the {@link ESMatchQuery} if any, and we fail for the others.
     * @param query the query to transform as filter
     * @return the filter to apply
     */
    private Map<String, Object> toWorkplaceSearchFilters(ESQuery query) {
        if (query == null) {
            return null;
        }
        if (query instanceof ESTermQuery) {
            ESTermQuery esQuery = (ESTermQuery) query;
            return Collections.singletonMap(query.getField(), List.of(esQuery.getValue()));
        }
        if (query instanceof ESMatchQuery) {
            return null;
        }
        if (query instanceof ESBoolQuery) {
            ESBoolQuery esQuery = (ESBoolQuery) query;
            Map<String, Object> filters = new HashMap<>();
            List<Map<String, Object>> all = new ArrayList<>();

            for (ESQuery clause : esQuery.getMustClauses()) {
                Map<String, Object> filter = toWorkplaceSearchFilters(clause);
                if (filter != null) {
                    all.add(filter);
                }
            }
            if (!all.isEmpty()) {
                filters.put("all", all);
            }
            return filters;
        }
        throw new IllegalArgumentException("Query " + query.getClass().getSimpleName() + " is not supported for " +
                "filtering within Workplace Search");
    }
}
