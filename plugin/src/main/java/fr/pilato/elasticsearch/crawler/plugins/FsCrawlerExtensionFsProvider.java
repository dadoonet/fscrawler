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
package fr.pilato.elasticsearch.crawler.plugins;

import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.beans.FileAbstractModel;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import org.pf4j.ExtensionPoint;

import java.io.InputStream;
import java.util.Collection;

/**
 * Extension point for filesystem provider plugins.
 * <p>
 * This interface provides methods for both:
 * <ul>
 *     <li>REST API uploads (single file operations)</li>
 *     <li>Batch crawling (directory traversal) - if {@link #supportsCrawling()} returns true</li>
 * </ul>
 * </p>
 * <p>
 * Plugins that only support single file operations (like HTTP or S3) should not override
 * {@link #supportsCrawling()} and will use the default implementations that throw
 * {@link FsCrawlerPluginException}.
 * </p>
 * <p>
 * Plugins that support directory crawling (like local, FTP, SSH) must override
 * {@link #supportsCrawling()} to return true and implement all crawling methods.
 * </p>
 */
public interface FsCrawlerExtensionFsProvider extends ExtensionPoint, AutoCloseable {

    // ========== Common methods (required) ==========

    /**
     * Start the provider with the given settings.
     *
     * @param fsSettings the FSCrawler settings
     * @param restSettings JSON settings from REST API (may be null for batch crawling)
     */
    void start(FsSettings fsSettings, String restSettings);

    /**
     * Stop the provider and release resources.
     *
     * @throws FsCrawlerPluginException if an error occurs while stopping
     */
    void stop() throws FsCrawlerPluginException;

    /**
     * Get the provider type identifier.
     *
     * @return the type (e.g., "local", "ftp", "ssh", "http", "s3")
     */
    String getType();

    // ========== REST API methods (required) ==========

    /**
     * Read the file content as an input stream.
     * Used by REST API for single file uploads.
     *
     * @return an input stream for reading the file
     * @throws FsCrawlerPluginException if an error occurs while reading
     */
    InputStream readFile() throws FsCrawlerPluginException;

    /**
     * Create the document with metadata from the provider.
     * Used by REST API for single file uploads.
     *
     * @return the created document
     * @throws FsCrawlerPluginException if an error occurs while creating the document
     */
    Doc createDocument() throws FsCrawlerPluginException;

    // ========== Crawling capability ==========

    /**
     * Indicates whether this provider supports directory crawling.
     * <p>
     * Providers that return true must implement all crawling methods:
     * {@link #openConnection()}, {@link #closeConnection()}, {@link #exists(String)},
     * {@link #getFiles(String)}, {@link #getInputStream(FileAbstractModel)},
     * {@link #closeInputStream(InputStream)}.
     * </p>
     *
     * @return true if this provider supports crawling, false otherwise
     */
    default boolean supportsCrawling() {
        return false;
    }

    // ========== Crawling methods (optional - default throws FsCrawlerPluginException) ==========

    /**
     * Open a connection to the filesystem.
     * This method is called before starting a crawl operation.
     *
     * @throws FsCrawlerPluginException if the connection cannot be established or if crawling is not supported
     */
    default void openConnection() throws FsCrawlerPluginException {
        throw new FsCrawlerPluginException("Crawling not supported by " + getType() + " provider");
    }

    /**
     * Close the connection to the filesystem.
     * This method is called after a crawl operation completes.
     *
     * @throws FsCrawlerPluginException if an error occurs while closing the connection or if crawling is not supported
     */
    default void closeConnection() throws FsCrawlerPluginException {
        throw new FsCrawlerPluginException("Crawling not supported by " + getType() + " provider");
    }

    /**
     * Check if a directory exists.
     *
     * @param directory the path to check
     * @return true if the directory exists, false otherwise
     * @throws FsCrawlerPluginException if crawling is not supported
     */
    default boolean exists(String directory) {
        throw new FsCrawlerPluginException("Crawling not supported by " + getType() + " provider");
    }

    /**
     * List all files and subdirectories in the given directory.
     *
     * @param directory the directory to list
     * @return a collection of file abstract models representing the directory contents
     * @throws FsCrawlerPluginException if an error occurs while listing the directory or if crawling is not supported
     */
    default Collection<FileAbstractModel> getFiles(String directory) throws FsCrawlerPluginException {
        throw new FsCrawlerPluginException("Crawling not supported by " + getType() + " provider");
    }

    /**
     * Get an input stream for reading a file during crawling.
     *
     * @param file the file to read
     * @return an input stream for the file content
     * @throws FsCrawlerPluginException if an error occurs while opening the stream or if crawling is not supported
     */
    default InputStream getInputStream(FileAbstractModel file) throws FsCrawlerPluginException {
        throw new FsCrawlerPluginException("Crawling not supported by " + getType() + " provider");
    }

    /**
     * Close an input stream previously opened with {@link #getInputStream(FileAbstractModel)}.
     * <p>
     * Some implementations may need to perform additional cleanup after reading a file
     * (e.g., FTP requires calling completePendingCommand()).
     * </p>
     *
     * @param inputStream the input stream to close
     * @throws FsCrawlerPluginException if an error occurs while closing the stream or if crawling is not supported
     */
    default void closeInputStream(InputStream inputStream) throws FsCrawlerPluginException {
        throw new FsCrawlerPluginException("Crawling not supported by " + getType() + " provider");
    }
}
