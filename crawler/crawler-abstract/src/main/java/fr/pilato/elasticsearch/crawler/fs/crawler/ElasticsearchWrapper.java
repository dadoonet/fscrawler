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

package fr.pilato.elasticsearch.crawler.fs.crawler;

import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientManager;
import fr.pilato.elasticsearch.crawler.fs.framework.SignTool;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

class ElasticsearchWrapper {

    private static final Logger logger = LogManager.getLogger(ElasticsearchWrapper.class);

    private static final String PATH_ROOT = Doc.FIELD_NAMES.PATH + "." + fr.pilato.elasticsearch.crawler.fs.beans.Path.FIELD_NAMES.ROOT;
    private static final String FILE_FILENAME = Doc.FIELD_NAMES.FILE + "." + fr.pilato.elasticsearch.crawler.fs.beans.File.FIELD_NAMES.FILENAME;
    private static final int REQUEST_SIZE = 10000;

    private final ElasticsearchClientManager esClientManager;
    private final FsSettings fsSettings;

    ElasticsearchWrapper(ElasticsearchClientManager esClientManager, FsSettings fsSettings) {
        this.esClientManager = esClientManager;
        this.fsSettings = fsSettings;
    }

    /**
     * Add to bulk an IndexRequest in JSon format
     */
    void bulk(boolean closed, DocWriteRequest request) {
        if (!closed) {
            esClientManager.bulkProcessor().add(request);
        } else {
            logger.warn("trying to add a bulk request while closing crawler. Document [{}]/[doc]/[{}] has been ignored", request.index(),
                    request.id());
        }
    }

    static DocWriteRequest indexRequest(String index, String id, String json, String pipeline) {
        logger.debug("Indexing {}/doc/{}?pipeline={}", index, id, pipeline);
        logger.trace("JSon indexed : {}", json);
        return new IndexRequest(index, "doc", id).source(json, XContentType.JSON).setPipeline(pipeline);
    }

    static DocWriteRequest deleteRequest(String index, String id) {
        logger.debug("Deleting {}/doc/{}", index, id);
        return new DeleteRequest(index, "doc", id);
    }

    // TODO Optimize it. We can probably use a search for a big array of filenames instead of
    // Searching fo 10000 files (which is somehow limited).
    Collection<String> getFileDirectory(boolean closed, String path) throws Exception {
        // If the crawler is being closed, we return
        if (closed) {
            return Collections.emptyList();
        }

        logger.trace("Querying elasticsearch for files in dir [{}:{}]", PATH_ROOT, SignTool.sign(path));
        Collection<String> files;
        // Hack because the High Level Client is not compatible with versions < 5.0
        if (esClientManager.client().isIngestSupported()) {
            files = new ArrayList<>();
            SearchResponse response = esClientManager.client().search(
                    new SearchRequest(fsSettings.getElasticsearch().getIndex()).source(
                            new SearchSourceBuilder()
                                    .size(REQUEST_SIZE) // TODO: WHAT? DID I REALLY WROTE THAT? :p
                                    .storedField(FILE_FILENAME)
                                    .query(QueryBuilders.termQuery(PATH_ROOT, SignTool.sign(path)))));

            logger.trace("Response [{}]", response.toString());
            if (response.getHits() != null && response.getHits().getHits() != null) {
                for (SearchHit hit : response.getHits().getHits()) {
                    if (hit.getFields() != null
                            && hit.getFields().get(FILE_FILENAME) != null) {
                        // In case someone disabled _source which is not recommended
                        files.add(hit.getFields().get(FILE_FILENAME).getValue());
                    } else {
                        // Houston, we have a problem ! We can't get the old files from ES
                        logger.warn("Can't find stored field name to check existing filenames in path [{}]. " +
                                "Please set store: true on field [{}]", path, FILE_FILENAME);
                        throw new RuntimeException("Mapping is incorrect: please set stored: true on field [" +
                                FILE_FILENAME + "].");
                    }
                }
            }
        } else {
            files = esClientManager.client().getFromStoredFieldsV2(
                    fsSettings.getElasticsearch().getIndex(),
                    REQUEST_SIZE,    // TODO: WHAT? DID I REALLY WROTE THAT? :p
                    FILE_FILENAME,
                    path,
                    QueryBuilders.termQuery(PATH_ROOT, SignTool.sign(path)));
        }

        logger.trace("We found: {}", files);

        return files;
    }

    Collection<String> getFolderDirectory(boolean closed, String path) throws Exception {
        Collection<String> files = new ArrayList<>();

        // If the crawler is being closed, we return
        if (closed) {
            return files;
        }

        SearchResponse response = esClientManager.client().search(
                new SearchRequest(fsSettings.getElasticsearch().getIndexFolder()).source(
                        new SearchSourceBuilder()
                                .size(REQUEST_SIZE) // TODO: WHAT? DID I REALLY WROTE THAT? :p
                                .query(QueryBuilders.termQuery(fr.pilato.elasticsearch.crawler.fs.beans.Path.FIELD_NAMES.ROOT, SignTool.sign(path)))));

        if (response.getHits() != null && response.getHits().getHits() != null) {
            for (SearchHit hit : response.getHits().getHits()) {
                String name = hit.getSourceAsMap().get(fr.pilato.elasticsearch.crawler.fs.beans.Path.FIELD_NAMES.REAL).toString();
                files.add(name);
            }
        }

        return files;
    }
}
