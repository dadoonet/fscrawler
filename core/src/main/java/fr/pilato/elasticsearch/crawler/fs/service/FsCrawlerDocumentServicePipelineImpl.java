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
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPluginException;
import fr.pilato.elasticsearch.crawler.plugins.pipeline.Pipeline;
import fr.pilato.elasticsearch.crawler.plugins.pipeline.PipelineContext;
import fr.pilato.elasticsearch.crawler.plugins.pipeline.output.OutputPlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

/**
 * Document service implementation that uses the Pipeline architecture.
 * <p>
 * This service delegates document indexing and deletion to the Pipeline's output plugins,
 * while keeping a reference to the underlying Elasticsearch service for query operations
 * (search, exists, get) which are not part of the pipeline flow.
 * </p>
 */
public class FsCrawlerDocumentServicePipelineImpl implements FsCrawlerDocumentService {

    private static final Logger logger = LogManager.getLogger();

    private final Pipeline pipeline;
    private final FsCrawlerDocumentService queryService;
    private final String defaultIndex;

    /**
     * Creates a new Pipeline-based document service.
     *
     * @param pipeline     the pipeline with configured output plugins
     * @param queryService the underlying service for query operations (search, exists, get)
     * @param defaultIndex the default index name to use when not specified
     */
    public FsCrawlerDocumentServicePipelineImpl(Pipeline pipeline, FsCrawlerDocumentService queryService, String defaultIndex) {
        this.pipeline = pipeline;
        this.queryService = queryService;
        this.defaultIndex = defaultIndex;
    }

    @Override
    public void start() throws IOException, ElasticsearchClientException {
        logger.debug("Starting Pipeline Document Service");
        
        // Start the query service first (for schema creation, etc.)
        if (queryService != null) {
            queryService.start();
        }
        
        // Start all output plugins
        try {
            pipeline.startOutputs();
        } catch (FsCrawlerPluginException e) {
            throw new IOException("Failed to start pipeline outputs: " + e.getMessage(), e);
        }
        
        logger.debug("Pipeline Document Service started with {} outputs", pipeline.getOutputs().size());
    }

    @Override
    public void close() throws IOException {
        logger.debug("Closing Pipeline Document Service");
        
        // Stop the pipeline (flushes and stops all outputs)
        try {
            pipeline.stop();
        } catch (Exception e) {
            logger.warn("Error stopping pipeline: {}", e.getMessage());
        }
        
        // Close the query service
        if (queryService != null) {
            queryService.close();
        }
        
        logger.debug("Pipeline Document Service closed");
    }

    @Override
    public String getVersion() throws IOException, ElasticsearchClientException {
        // Delegate to query service
        if (queryService != null) {
            return queryService.getVersion();
        }
        return "unknown";
    }

    @Override
    public void createSchema() throws Exception {
        // Delegate to query service (templates are created by the underlying ES client)
        if (queryService != null) {
            queryService.createSchema();
        }
    }

    @Override
    public void index(String index, String id, Doc doc, String pipeline) {
        logger.debug("Indexing document [{}] to index [{}] via Pipeline", id, index);
        
        // Create a context for the document
        PipelineContext context = createContext(index, id, doc);
        
        // Send to all matching outputs (filters already applied by processAndIndex or externally)
        sendToOutputs(index, id, doc, context);
    }

    /**
     * Process a document through the pipeline filters and then index it.
     * This is the main entry point for documents that need content extraction.
     *
     * @param inputStream the raw content stream
     * @param doc the document with metadata already populated
     * @param index the target index
     * @param id the document ID
     * @param fileSize the file size for processing
     */
    public void processAndIndex(java.io.InputStream inputStream, Doc doc, String index, String id, long fileSize) {
        logger.debug("Processing and indexing document [{}] to index [{}] via Pipeline", id, index);
        
        // Create a context for the document
        PipelineContext context = createContext(index, id, doc);
        context.withSize(fileSize);
        
        try {
            // Apply filters sequentially
            pipeline.processDocument(inputStream, doc, context);
        } catch (FsCrawlerPluginException e) {
            logger.error("Error processing document [{}] through pipeline: {}", id, e.getMessage());
            throw new RuntimeException("Failed to process document: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a PipelineContext from document information.
     */
    private PipelineContext createContext(String index, String id, Doc doc) {
        return new PipelineContext()
                .withIndex(index != null ? index : defaultIndex)
                .withFilename(doc.getFile() != null ? doc.getFile().getFilename() : id)
                .withPath(doc.getPath() != null ? doc.getPath().getReal() : null);
    }

    /**
     * Sends a document to all matching output plugins.
     */
    private void sendToOutputs(String index, String id, Doc doc, PipelineContext context) {
        for (OutputPlugin output : this.pipeline.getOutputs()) {
            if (output.shouldApply(context)) {
                try {
                    output.index(index != null ? index : defaultIndex, id, doc, context);
                } catch (FsCrawlerPluginException e) {
                    logger.error("Error indexing document [{}] via output [{}]: {}", id, output.getId(), e.getMessage());
                    throw new RuntimeException("Failed to index document: " + e.getMessage(), e);
                }
            }
        }
    }

    @Override
    public void indexRawJson(String index, String id, String json, String pipeline) {
        // For raw JSON, we need to delegate to the query service since OutputPlugin
        // works with Doc objects, not raw JSON
        logger.debug("Indexing raw JSON [{}] to index [{}] - delegating to query service", id, index);
        if (queryService != null) {
            queryService.indexRawJson(index, id, json, pipeline);
        } else {
            logger.warn("Cannot index raw JSON [{}] - no query service available", id);
        }
    }

    @Override
    public void delete(String index, String id) {
        logger.debug("Deleting document [{}] from index [{}] via Pipeline", id, index);
        
        // Send delete to all outputs
        for (OutputPlugin output : pipeline.getOutputs()) {
            try {
                output.delete(index != null ? index : defaultIndex, id);
            } catch (FsCrawlerPluginException e) {
                logger.error("Error deleting document [{}] via output [{}]: {}", id, output.getId(), e.getMessage());
                throw new RuntimeException("Failed to delete document: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public void deleteSingle(String index, String id) throws ElasticsearchClientException {
        // Delegate to query service for synchronous delete
        if (queryService != null) {
            queryService.deleteSingle(index, id);
        } else {
            // Fall back to async delete via pipeline
            delete(index, id);
        }
    }

    @Override
    public ESSearchResponse search(ESSearchRequest request) throws IOException, ElasticsearchClientException {
        // Delegate to query service
        if (queryService != null) {
            return queryService.search(request);
        }
        throw new UnsupportedOperationException("Search not available - no query service configured");
    }

    @Override
    public boolean exists(String index, String id) throws IOException, ElasticsearchClientException {
        // Delegate to query service
        if (queryService != null) {
            return queryService.exists(index, id);
        }
        throw new UnsupportedOperationException("Exists check not available - no query service configured");
    }

    @Override
    public ESSearchHit get(String index, String id) throws IOException, ElasticsearchClientException {
        // Delegate to query service
        if (queryService != null) {
            return queryService.get(index, id);
        }
        throw new UnsupportedOperationException("Get not available - no query service configured");
    }

    /**
     * Get the underlying pipeline.
     */
    public Pipeline getPipeline() {
        return pipeline;
    }

    /**
     * Flush all output plugins.
     */
    public void flush() {
        for (OutputPlugin output : pipeline.getOutputs()) {
            try {
                output.flush();
            } catch (FsCrawlerPluginException e) {
                logger.warn("Error flushing output [{}]: {}", output.getId(), e.getMessage());
            }
        }
    }
}
