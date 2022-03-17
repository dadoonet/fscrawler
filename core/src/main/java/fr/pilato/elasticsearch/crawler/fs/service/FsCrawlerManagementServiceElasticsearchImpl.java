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

import com.jayway.jsonpath.JsonPath;
import fr.pilato.elasticsearch.crawler.fs.beans.Folder;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchHit;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.client.ESTermQuery;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClient;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientException;
import fr.pilato.elasticsearch.crawler.fs.client.IElasticsearchClient;
import fr.pilato.elasticsearch.crawler.fs.framework.SignTool;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;

import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.serialize;

public class FsCrawlerManagementServiceElasticsearchImpl implements FsCrawlerManagementService {

    private static final Logger logger = LogManager.getLogger(FsCrawlerManagementServiceElasticsearchImpl.class);

    // TODO Optimize it. We can probably use a search for a big array of filenames instead of
    // searching fo 10000 files (which is somehow limited).
    private static final int REQUEST_SIZE = 10000;

    private final IElasticsearchClient client;
    private final FsSettings settings;

    public FsCrawlerManagementServiceElasticsearchImpl(Path config, FsSettings settings) {
        this.settings = settings;
        this.client = new ElasticsearchClient(config, settings);
    }

    public IElasticsearchClient getClient() {
        return client;
    }

    @Override
    public void start() throws IOException, ElasticsearchClientException {
        client.start();
        logger.debug("Elasticsearch Management Service started");
    }

    @Override
    public void close() throws IOException {
        client.close();
        logger.debug("Elasticsearch Management Service stopped");
    }

    @Override
    public String getVersion() throws IOException, ElasticsearchClientException {
        return client.getVersion();
    }

    @Override
    public Collection<String> getFileDirectory(String path)
            throws Exception {

        if (logger.isTraceEnabled()) {
            logger.trace("Querying elasticsearch for files in dir [path.root:{}]", SignTool.sign(path));
        }

        Collection<String> files = new ArrayList<>();
        ESSearchResponse response = client.search(
                new ESSearchRequest()
                        .withIndex(settings.getElasticsearch().getIndex())
                        .withSize(REQUEST_SIZE)
                        .addStoredField("file.filename")
                        .withESQuery(new ESTermQuery("path.root", SignTool.sign(path))));

        if (response.getHits() != null) {
            for (ESSearchHit hit : response.getHits()) {
                String name;
                if (hit.getStoredFields() != null
                        && hit.getStoredFields().get("file.filename") != null) {
                    // In case someone disabled _source which is not recommended
                    name = hit.getStoredFields().get("file.filename").get(0);
                } else {
                    // Houston, we have a problem ! We can't get the old files from ES
                    logger.warn("Can't find stored field name to check existing filenames in path [{}]. " +
                            "Please set store: true on field [file.filename]", path);
                    throw new RuntimeException("Mapping is incorrect: please set stored: true on field [file.filename].");
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
                        .withESQuery(new ESTermQuery("path.root", SignTool.sign(path))));

        if (response.getHits() != null) {
            for (ESSearchHit hit : response.getHits()) {
                files.add(JsonPath.read(hit.getSource(), "$.path.real"));
            }
        }

        return files;
    }

    @Override
    public void storeVisitedDirectory(String indexFolder, String id, Folder folder) {
        client.indexRawJson(indexFolder, id, serialize(folder), null);
    }

    @Override
    public void delete(String index, String id) {
        client.delete(index, id);
    }
}
