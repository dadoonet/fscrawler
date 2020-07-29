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

package fr.pilato.elasticsearch.crawler.fs.client.v7;


import com.sstory.workplace.search.client.Client;
import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchHit;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClient;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientUtil;
import fr.pilato.elasticsearch.crawler.fs.client.WorkplaceSearchClient;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Workplace Search Client for Clusters running v7.
 * It also starts an embedded Elasticsearch Client.
 */
public class WorkplaceSearchClientV7 implements WorkplaceSearchClient {

    private static final Logger logger = LogManager.getLogger(WorkplaceSearchClientV7.class);
    private final Path config;
    private final FsSettings settings;

    private ElasticsearchClient esClient = null;
    private Client client = null;

    public WorkplaceSearchClientV7(Path config, FsSettings settings) {
        this.config = config;
        this.settings = settings;
    }

    @Override
    public String compatibleVersion() {
        return "7";
    }

    @Override
    public void start() throws IOException {
        client = new Client(settings.getWorkplaceSearch().getAccessToken());
        esClient = ElasticsearchClientUtil.getInstance(config, settings);
        esClient.start();
    }

    @Override
    public String getVersion() throws IOException {
        return esClient.getVersion();
    }

    /**
     * Create an index
     * @param index index name
     * @param ignoreErrors don't fail if the index already exists
     * @param indexSettings index settings if any
     * @throws IOException In case of error
     */
    public void createIndex(String index, boolean ignoreErrors, String indexSettings) throws IOException {
        esClient.createIndex(index, ignoreErrors, indexSettings);
    }

    /**
     * Check if an index exists
     * @param index index name
     * @return true if the index exists, false otherwise
     * @throws IOException In case of error
     */
    public boolean isExistingIndex(String index) throws IOException {
        return esClient.isExistingIndex(index);
    }

    /**
     * Check if a pipeline exists
     * @param pipelineName pipeline name
     * @return true if the pipeline exists, false otherwise
     * @throws IOException In case of error
     */
    public boolean isExistingPipeline(String pipelineName) throws IOException {
        return esClient.isExistingPipeline(pipelineName);
    }

    /**
     * Refresh an index
     * @param index index name
     * @throws IOException In case of error
     */
    public void refresh(String index) throws IOException {
        esClient.refresh(index);
    }

    /**
     * Wait for an index to become at least yellow (all primaries assigned)
     * @param index index name
     * @throws IOException In case of error
     */
    public void waitForHealthyIndex(String index) throws IOException {
        esClient.waitForHealthyIndex(index);
    }

    // Utility methods

    public boolean isIngestSupported() {
        return true;
    }

    public String getDefaultTypeName() {
        return esClient.getDefaultTypeName();
    }

    @Override
    public void index(String index, String id, Doc doc, String pipeline) {
        indexSingle(index, id, doc);
    }

    @Override
    public void indexSingle(String index, String id, Doc doc) {
        Map<String, Object> document = new HashMap<>();
        document.put("body", doc.getContent()); // to content,
        document.put("name", doc.getFile().getFilename()); // to file.name,
        document.put("mime_type", doc.getFile().getContentType()); // to tikaResult.metadata.mimeType,
        document.put("extension", doc.getFile().getExtension()); // to tikaResult.metadata.extension,
        document.put("size", doc.getFile().getFilesize()); // to tikaResult.metadata.origSize,
        document.put("text_size", doc.getFile().getIndexedChars()); // to tikaResult.metadata.textSize,
        document.put("url", "file://" + doc.getPath().getVirtual()); // to file.toURI().toURL().toString().replace("file:", "file://"),
        document.put("path", doc.getPath().getReal()); // to file.absolutePath,
        document.put("last_modified", doc.getFile().getLastModified()); // to Instant.ofEpochMilli(file.lastModified()).atZone(ZoneId.systemDefault()).format(format),
        document.put("created_at", doc.getFile().getCreated()); // to attributes.creationTime().toInstant().atZone(ZoneId.systemDefault()).format(format),

        List<Map<String, Object>> documents = Collections.singletonList(document);

        client.indexDocuments(settings.getWorkplaceSearch().getContentSourceKey(), documents);
    }

    @Override
    public void delete(String index, String id) {
        throw new RuntimeException("Not implemented yet");
    }

    @Override
    public void close() throws IOException {
        if (esClient != null) {
            esClient.close();
        }
    }

    public void createIndices() throws Exception {
        esClient.createIndices();
    }

    @Override
    public ESSearchResponse search(ESSearchRequest request) throws IOException {
        throw new RuntimeException("Not implemented yet");
        // return esClient.search(request);
    }

    @Override
    public void deleteIndex(String index) throws IOException {
        esClient.deleteIndex(index);
    }

    @Override
    public void flush() {
        esClient.flush();
    }

    @Override
    public void performLowLevelRequest(String method, String endpoint, String jsonEntity) throws IOException {
        esClient.performLowLevelRequest(method, endpoint, jsonEntity);
    }

    @Override
    public ESSearchHit get(String index, String id) throws IOException {
        throw new RuntimeException("Not implemented yet");
    }

    @Override
    public boolean exists(String index, String id) throws IOException {
        throw new RuntimeException("Not implemented yet");
        // return esClient.exists(index, id);
    }
}
