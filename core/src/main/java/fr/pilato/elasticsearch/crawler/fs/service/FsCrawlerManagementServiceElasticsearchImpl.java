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
import fr.pilato.elasticsearch.crawler.fs.beans.PathParser;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchHit;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.client.ESTermQuery;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClient;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientUtil;
import fr.pilato.elasticsearch.crawler.fs.framework.SignTool;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;

public class FsCrawlerManagementServiceElasticsearchImpl implements FsCrawlerManagementService {

    private static final Logger logger = LogManager.getLogger(FsCrawlerManagementServiceElasticsearchImpl.class);
    private static final String PATH_ROOT = Doc.FIELD_NAMES.PATH + "." + fr.pilato.elasticsearch.crawler.fs.beans.Path.FIELD_NAMES.ROOT;
    private static final String FILE_FILENAME = Doc.FIELD_NAMES.FILE + "." + fr.pilato.elasticsearch.crawler.fs.beans.File.FIELD_NAMES.FILENAME;

    // TODO Optimize it. We can probably use a search for a big array of filenames instead of
    // searching fo 10000 files (which is somehow limited).
    private static final int REQUEST_SIZE = 10000;

    private final ElasticsearchClient client;
    private final FsSettings settings;

    public FsCrawlerManagementServiceElasticsearchImpl(Path config, FsSettings settings) {
        this.settings = settings;
        this.client = ElasticsearchClientUtil.getInstance(config, settings);
    }

    @Override
    public void start() throws IOException {
        client.start();
        logger.debug("Elasticsearch Management Service started");
    }

    @Override
    public ElasticsearchClient getClient() {
        return client;
    }

    @Override
    public void close() throws IOException {
        client.close();
        logger.debug("Elasticsearch Management Service stopped");
    }

    @Override
    public Collection<String> getFileDirectory(String path)
            throws Exception {

        logger.trace("Querying elasticsearch for files in dir [{}:{}]", PATH_ROOT, SignTool.sign(path));
        Collection<String> files = new ArrayList<>();
        ESSearchResponse response = client.search(
                new ESSearchRequest()
                        .withIndex(settings.getElasticsearch().getIndex())
                        .withSize(REQUEST_SIZE)
                        .addField(FILE_FILENAME)
                        .withESQuery(new ESTermQuery(PATH_ROOT, SignTool.sign(path))));

        logger.trace("Response [{}]", response.toString());
        if (response.getHits() != null) {
            for (ESSearchHit hit : response.getHits()) {
                String name;
                if (hit.getFields() != null
                        && hit.getFields().get(FILE_FILENAME) != null) {
                    // In case someone disabled _source which is not recommended
                    name = hit.getFields().get(FILE_FILENAME).getValue();
                } else {
                    // Houston, we have a problem ! We can't get the old files from ES
                    logger.warn("Can't find stored field name to check existing filenames in path [{}]. " +
                            "Please set store: true on field [{}]", path, FILE_FILENAME);
                    throw new RuntimeException("Mapping is incorrect: please set stored: true on field [" +
                            FILE_FILENAME + "].");
                }
                files.add(name);
            }
        }

        logger.trace("We found: {}", files);

        return files;
    }

    @Override
    public Collection<String> getFolderDirectory(String path) throws Exception {
        Collection<String> files = new ArrayList<>();

        ESSearchResponse response = client.search(
                new ESSearchRequest()
                        .withIndex(settings.getElasticsearch().getIndexFolder())
                        .withSize(REQUEST_SIZE) // TODO: WHAT? DID I REALLY WROTE THAT? :p
                        .withESQuery(new ESTermQuery(fr.pilato.elasticsearch.crawler.fs.beans.Path.FIELD_NAMES.ROOT, SignTool.sign(path))));

        if (response.getHits() != null) {
            for (ESSearchHit hit : response.getHits()) {
                String name = hit.getSourceAsMap().get(fr.pilato.elasticsearch.crawler.fs.beans.Path.FIELD_NAMES.REAL).toString();
                files.add(name);
            }
        }

        return files;
    }

    @Override
    public void storeVisitedDirectory(String indexFolder, String id, fr.pilato.elasticsearch.crawler.fs.beans.Path path) throws IOException {
        client.indexRawJson(indexFolder, id, PathParser.toJson(path), null);
    }
}
