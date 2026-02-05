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

package fr.pilato.elasticsearch.crawler.plugins.pipeline;

import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPluginException;
import fr.pilato.elasticsearch.crawler.plugins.pipeline.filter.FilterPlugin;
import fr.pilato.elasticsearch.crawler.plugins.pipeline.input.InputPlugin;
import fr.pilato.elasticsearch.crawler.plugins.pipeline.output.OutputPlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Pipeline that orchestrates the flow of documents through inputs, filters, and outputs.
 * 
 * Behavior:
 * - Inputs: All inputs run in parallel (each in its own thread)
 * - Filters: Applied sequentially in order (if conditions match)
 * - Outputs: Fan-out to all matching outputs
 */
public class Pipeline implements AutoCloseable {

    private static final Logger logger = LogManager.getLogger(Pipeline.class);

    private final List<InputPlugin> inputs;
    private final List<FilterPlugin> filters;
    private final List<OutputPlugin> outputs;

    public Pipeline(List<InputPlugin> inputs, List<FilterPlugin> filters, List<OutputPlugin> outputs) {
        this.inputs = inputs != null ? inputs : new ArrayList<>();
        this.filters = filters != null ? filters : new ArrayList<>();
        this.outputs = outputs != null ? outputs : new ArrayList<>();
    }

    /**
     * Processes a document through all matching filters and outputs.
     *
     * @param inputStream the raw input stream from the input
     * @param doc the initial document with metadata
     * @param context the pipeline context with tags, metadata, etc.
     * @throws FsCrawlerPluginException if processing fails
     */
    public void processDocument(InputStream inputStream, Doc doc, PipelineContext context) throws FsCrawlerPluginException {
        logger.debug("Processing document through pipeline: {}", context.getFilename());

        // Apply filters sequentially
        for (FilterPlugin filter : filters) {
            if (filter.shouldApply(context)) {
                logger.trace("Applying filter [{}] to document [{}]", filter.getId(), context.getFilename());
                doc = filter.process(inputStream, doc, context);
                // After first filter that consumes the stream, subsequent filters won't have access
                // This is intentional - most pipelines have one primary filter (e.g., Tika)
                inputStream = null;
            } else {
                logger.trace("Skipping filter [{}] for document [{}] (condition not met)", filter.getId(), context.getFilename());
            }
        }

        // Fan-out to all matching outputs
        for (OutputPlugin output : outputs) {
            if (output.shouldApply(context)) {
                logger.trace("Sending document [{}] to output [{}]", context.getFilename(), output.getId());
                String index = context.getIndex();
                String id = generateDocId(context);
                output.index(index, id, doc, context);
            } else {
                logger.trace("Skipping output [{}] for document [{}] (condition not met)", output.getId(), context.getFilename());
            }
        }
    }

    /**
     * Starts all output plugins.
     * Should be called before processing any documents.
     *
     * @throws FsCrawlerPluginException if starting fails
     */
    public void startOutputs() throws FsCrawlerPluginException {
        logger.debug("Starting {} output plugins", outputs.size());
        for (OutputPlugin output : outputs) {
            output.start();
        }
    }

    /**
     * Starts all input plugins.
     * Each input is started in its own thread for parallel crawling.
     *
     * @throws FsCrawlerPluginException if starting fails
     */
    public void startInputs() throws FsCrawlerPluginException {
        logger.debug("Starting {} input plugins", inputs.size());
        for (InputPlugin input : inputs) {
            input.start();
        }
    }

    /**
     * Stops the pipeline.
     * Flushes outputs and stops all plugins.
     */
    public void stop() {
        logger.debug("Stopping pipeline");

        // Stop inputs first
        for (InputPlugin input : inputs) {
            try {
                input.stop();
            } catch (Exception e) {
                logger.warn("Error stopping input [{}]: {}", input.getId(), e.getMessage());
            }
        }

        // Flush and stop outputs
        for (OutputPlugin output : outputs) {
            try {
                output.flush();
                output.stop();
            } catch (Exception e) {
                logger.warn("Error stopping output [{}]: {}", output.getId(), e.getMessage());
            }
        }
    }

    @Override
    public void close() throws Exception {
        stop();
    }

    // ========== Getters ==========

    public List<InputPlugin> getInputs() {
        return inputs;
    }

    public List<FilterPlugin> getFilters() {
        return filters;
    }

    public List<OutputPlugin> getOutputs() {
        return outputs;
    }

    // ========== Private methods ==========

    private String generateDocId(PipelineContext context) {
        // Generate a document ID based on the path
        String path = context.getPath();
        if (path != null) {
            return path.replace("/", "_").replace("\\", "_");
        }
        return context.getFilename();
    }
}
