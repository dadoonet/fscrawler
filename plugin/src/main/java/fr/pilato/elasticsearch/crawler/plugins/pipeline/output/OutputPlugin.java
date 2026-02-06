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

package fr.pilato.elasticsearch.crawler.plugins.pipeline.output;

import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPluginException;
import fr.pilato.elasticsearch.crawler.plugins.pipeline.ConditionalPlugin;
import fr.pilato.elasticsearch.crawler.plugins.ConfigurablePlugin;
import fr.pilato.elasticsearch.crawler.plugins.pipeline.PipelineContext;
import org.pf4j.ExtensionPoint;

/**
 * Output plugin interface for document destinations in the pipeline.
 * Outputs receive processed documents and send them to their destination.
 * Examples: Elasticsearch, file output, other databases.
 * 
 * Multiple outputs can receive the same document (fan-out).
 * Each output can have conditions to filter which documents it receives.
 */
public interface OutputPlugin extends ConfigurablePlugin, ConditionalPlugin, ExtensionPoint, AutoCloseable {

    // ========== Lifecycle methods ==========

    /**
     * Starts the output plugin.
     * Called when the crawler starts. Should establish connections.
     *
     * @throws FsCrawlerPluginException if an error occurs during startup
     */
    void start() throws FsCrawlerPluginException;

    /**
     * Stops the output plugin.
     * Called when the crawler stops. Should flush and close connections.
     *
     * @throws FsCrawlerPluginException if an error occurs during shutdown
     */
    void stop() throws FsCrawlerPluginException;

    // ========== Document operations ==========

    /**
     * Indexes a document to the output destination.
     *
     * @param index the target index name
     * @param id the document ID
     * @param doc the document to index
     * @param context the pipeline context
     * @throws FsCrawlerPluginException if an error occurs
     */
    void index(String index, String id, Doc doc, PipelineContext context) throws FsCrawlerPluginException;

    /**
     * Deletes a document from the output destination.
     *
     * @param index the target index name
     * @param id the document ID to delete
     * @throws FsCrawlerPluginException if an error occurs
     */
    void delete(String index, String id) throws FsCrawlerPluginException;

    /**
     * Flushes any buffered documents to the output destination.
     * Called periodically and before shutdown.
     *
     * @throws FsCrawlerPluginException if an error occurs
     */
    void flush() throws FsCrawlerPluginException;

    // ========== AutoCloseable ==========

    @Override
    default void close() throws Exception {
        flush();
        stop();
    }
}
