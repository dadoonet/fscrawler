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

import fr.pilato.elasticsearch.crawler.fs.beans.FileAbstractModel;

import java.io.InputStream;
import java.util.Collection;

/**
 * Extension point for filesystem crawling plugins.
 * <p>
 * This interface extends {@link FsCrawlerExtensionFsProvider} to add support for
 * recursive directory crawling, in addition to single file operations.
 * </p>
 * <p>
 * Plugins implementing this interface can be used both for:
 * <ul>
 *     <li>REST API uploads (single file operations via parent interface)</li>
 *     <li>Batch crawling (directory traversal via this interface)</li>
 * </ul>
 * </p>
 */
public interface FsCrawlerExtensionFsCrawler extends FsCrawlerExtensionFsProvider {

    /**
     * Open a connection to the filesystem.
     * This method is called before starting a crawl operation.
     *
     * @throws Exception if the connection cannot be established
     */
    void openConnection() throws Exception;

    /**
     * Close the connection to the filesystem.
     * This method is called after a crawl operation completes.
     *
     * @throws Exception if an error occurs while closing the connection
     */
    void closeConnection() throws Exception;

    /**
     * Check if a directory exists.
     *
     * @param directory the path to check
     * @return true if the directory exists, false otherwise
     */
    boolean exists(String directory);

    /**
     * List all files and subdirectories in the given directory.
     *
     * @param directory the directory to list
     * @return a collection of file abstract models representing the directory contents
     * @throws Exception if an error occurs while listing the directory
     */
    Collection<FileAbstractModel> getFiles(String directory) throws Exception;

    /**
     * Get an input stream for reading a file.
     *
     * @param file the file to read
     * @return an input stream for the file content
     * @throws Exception if an error occurs while opening the stream
     */
    InputStream getInputStream(FileAbstractModel file) throws Exception;

    /**
     * Close an input stream previously opened with {@link #getInputStream(FileAbstractModel)}.
     * <p>
     * Some implementations may need to perform additional cleanup after reading a file
     * (e.g., FTP requires calling completePendingCommand()).
     * </p>
     *
     * @param inputStream the input stream to close
     * @throws Exception if an error occurs while closing the stream
     */
    void closeInputStream(InputStream inputStream) throws Exception;
}
