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

import fr.pilato.elasticsearch.crawler.fs.beans.FileAbstractModel;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPluginException;
import org.pf4j.ExtensionPoint;

import java.io.InputStream;
import java.util.Collection;
import java.util.List;

/**
 * Input plugin interface for data sources in the pipeline.
 * Inputs are responsible for discovering and reading files from various sources
 * like local filesystem, SSH, FTP, S3, HTTP, etc.
 */
public interface InputPlugin extends ConfigurablePlugin, ExtensionPoint, AutoCloseable {

    // ========== Lifecycle methods ==========

    /**
     * Starts the input plugin.
     * Called when the crawler starts.
     *
     * @throws FsCrawlerPluginException if an error occurs during startup
     */
    void start() throws FsCrawlerPluginException;

    /**
     * Stops the input plugin.
     * Called when the crawler stops.
     *
     * @throws FsCrawlerPluginException if an error occurs during shutdown
     */
    void stop() throws FsCrawlerPluginException;

    // ========== Configuration methods ==========

    /**
     * Returns the tags that should be added to documents from this input.
     * Used for conditional routing in filters and outputs.
     *
     * @return list of tags, or null if no tags
     */
    List<String> getTags();

    /**
     * Returns the update rate for this input.
     * How often the input should check for new or updated files.
     *
     * @return the update rate as a string (e.g., "15m", "1h")
     */
    String getUpdateRate();

    // ========== Crawling capability ==========

    /**
     * Indicates whether this input supports directory crawling.
     * Inputs that return false only support single-file operations (REST API).
     *
     * @return true if this input supports crawling
     */
    boolean supportsCrawling();

    // ========== Connection methods (for crawling) ==========

    /**
     * Opens a connection to the data source.
     * Called before starting a crawl operation.
     *
     * @throws FsCrawlerPluginException if the connection cannot be established
     */
    void openConnection() throws FsCrawlerPluginException;

    /**
     * Closes the connection to the data source.
     * Called after a crawl operation completes.
     *
     * @throws FsCrawlerPluginException if an error occurs while closing
     */
    void closeConnection() throws FsCrawlerPluginException;

    /**
     * Checks if a directory exists at the given path.
     *
     * @param directory the path to check
     * @return true if the directory exists
     * @throws FsCrawlerPluginException if an error occurs
     */
    boolean exists(String directory) throws FsCrawlerPluginException;

    /**
     * Lists all files and subdirectories in the given directory.
     *
     * @param directory the directory to list
     * @return a collection of file models
     * @throws FsCrawlerPluginException if an error occurs
     */
    Collection<FileAbstractModel> getFiles(String directory) throws FsCrawlerPluginException;

    /**
     * Gets an input stream for reading a file.
     *
     * @param file the file to read
     * @return an input stream for the file content
     * @throws FsCrawlerPluginException if an error occurs
     */
    InputStream getInputStream(FileAbstractModel file) throws FsCrawlerPluginException;

    /**
     * Closes an input stream previously opened with {@link #getInputStream(FileAbstractModel)}.
     *
     * @param inputStream the input stream to close
     * @throws FsCrawlerPluginException if an error occurs
     */
    void closeInputStream(InputStream inputStream) throws FsCrawlerPluginException;

    // ========== REST API methods ==========

    /**
     * Reads a file for REST API upload.
     * Used when a file is uploaded via the REST API.
     *
     * @return an input stream for the file
     * @throws FsCrawlerPluginException if an error occurs
     */
    InputStream readFile() throws FsCrawlerPluginException;

    /**
     * Creates the initial pipeline context for a file.
     * Sets up metadata like filename, size, path, etc.
     *
     * @return the pipeline context
     * @throws FsCrawlerPluginException if an error occurs
     */
    PipelineContext createContext() throws FsCrawlerPluginException;

    // ========== AutoCloseable ==========

    @Override
    default void close() throws Exception {
        stop();
    }
}
