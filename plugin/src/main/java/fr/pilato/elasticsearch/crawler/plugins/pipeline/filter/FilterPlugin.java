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

package fr.pilato.elasticsearch.crawler.plugins.pipeline.filter;

import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPluginException;
import fr.pilato.elasticsearch.crawler.plugins.pipeline.ConditionalPlugin;
import fr.pilato.elasticsearch.crawler.plugins.pipeline.ConfigurablePlugin;
import fr.pilato.elasticsearch.crawler.plugins.pipeline.PipelineContext;
import org.pf4j.ExtensionPoint;

import java.io.InputStream;

/**
 * Filter plugin interface for document transformation in the pipeline.
 * Filters process the raw input stream and create/modify the Doc object.
 * Examples: Tika (content extraction), JSON parser, XML parser.
 * 
 * Filters are applied sequentially in the order they are defined.
 * Each filter can modify the Doc and PipelineContext.
 */
public interface FilterPlugin extends ConfigurablePlugin, ConditionalPlugin, ExtensionPoint {

    /**
     * Processes the input stream and creates/modifies the document.
     * This is the main transformation method called during pipeline execution.
     *
     * @param inputStream the raw input stream from the input plugin
     * @param doc the document to populate (may already have some metadata)
     * @param context the pipeline context with metadata for conditional logic
     * @return the processed document
     * @throws FsCrawlerPluginException if an error occurs during processing
     */
    Doc process(InputStream inputStream, Doc doc, PipelineContext context) throws FsCrawlerPluginException;

    /**
     * Indicates whether this filter needs the raw input stream.
     * Some filters (like metadata-only filters) may not need the content.
     *
     * @return true if this filter requires the input stream
     */
    default boolean requiresInputStream() {
        return true;
    }
}
