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

import com.jayway.jsonpath.PathNotFoundException;
import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.settings.Server;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.computeVirtualPathName;

/**
 * Abstract base class for remote filesystem providers (SSH, FTP, etc.).
 * <p>
 * This class provides common functionality for providers that connect to remote servers,
 * including:
 * <ul>
 *     <li>Server connection settings (hostname, port, username, password)</li>
 *     <li>Remote path handling and normalization</li>
 *     <li>Common document creation logic</li>
 * </ul>
 * </p>
 */
public abstract class FsCrawlerExtensionRemoteProviderAbstract extends FsCrawlerExtensionFsProviderAbstract {
    private static final Logger logger = LogManager.getLogger();

    // Common fields for remote providers
    protected String remotePath;
    protected String hostname;
    protected int port;
    protected String username;
    protected String password;

    /**
     * Get the file size of the remote file.
     * Each provider implements this based on how they retrieve file metadata.
     *
     * @return the file size in bytes
     */
    protected abstract long getFilesize();

    /**
     * Hook for parsing protocol-specific settings from JSON.
     * Override this method to parse additional settings specific to the protocol.
     *
     * @throws PathNotFoundException if a required setting is missing
     */
    protected void parseProtocolSpecificSettings() throws PathNotFoundException {
        // Default implementation does nothing
        // Override in subclasses if needed (e.g., SSH pemPath)
    }

    /**
     * Hook for protocol-specific validation.
     * Override this method to add validation logic specific to the protocol.
     *
     * @throws IOException if validation fails
     */
    protected abstract void validateProtocolSpecificSettings() throws IOException;

    // ========== Common implementations ==========

    @Override
    public boolean supportsCrawling() {
        return true;
    }

    @Override
    protected void parseSettings() throws PathNotFoundException {
        String prefix = getType();
        remotePath = document.read("$." + prefix + ".path");

        // Parse optional server connection details from JSON
        hostname = readOptionalString("$." + prefix + ".hostname");
        port = readOptionalInt("$." + prefix + ".port");
        username = readOptionalString("$." + prefix + ".username");
        password = readOptionalString("$." + prefix + ".password");

        // Allow subclasses to parse additional settings
        parseProtocolSpecificSettings();
    }

    @Override
    protected void validateSettings() throws IOException {
        if (remotePath == null || remotePath.isEmpty()) {
            throw new IOException(getType() + " path is missing");
        }

        // Normalize the path
        remotePath = normalizeRemotePath(remotePath);

        // Delegate to protocol-specific validation
        validateProtocolSpecificSettings();
    }

    @Override
    public Doc createDocument() {
        String filename = getFilename();
        logger.debug("Creating document from {} for file {}", getType(), filename);

        Doc doc = new Doc();
        doc.getFile().setFilename(filename);
        doc.getFile().setFilesize(getFilesize());
        doc.getPath().setVirtual(computeVirtualPathName(fsSettings.getFs().getUrl(), remotePath));
        doc.getPath().setReal(remotePath);
        return doc;
    }

    @Override
    public void stop() throws Exception {
        closeConnection();
    }

    // ========== Helper methods ==========

    /**
     * Get the filename from the remote path.
     *
     * @return the filename
     */
    protected String getFilename() {
        return FilenameUtils.getName(remotePath);
    }

    /**
     * Get the remote path.
     *
     * @return the remote path
     */
    protected String getRemotePath() {
        return remotePath;
    }

    /**
     * Normalize the remote path, resolving relative paths against the root URL.
     *
     * @param path the path to normalize
     * @return the normalized path
     */
    protected String normalizeRemotePath(String path) {
        if (path == null) {
            return null;
        }
        String rootPath = fsSettings.getFs().getUrl();
        if (!path.startsWith("/")) {
            // Relative path - resolve against root
            return rootPath.endsWith("/") ? rootPath + path : rootPath + "/" + path;
        }
        return path;
    }

    /**
     * Get the effective hostname, using JSON settings if available, otherwise falling back to job settings.
     *
     * @return the effective hostname
     */
    protected String getEffectiveHostname() {
        Server server = fsSettings.getServer();
        return hostname != null ? hostname : server.getHostname();
    }

    /**
     * Get the effective port, using JSON settings if available, otherwise falling back to job settings.
     *
     * @return the effective port
     */
    protected int getEffectivePort() {
        Server server = fsSettings.getServer();
        return port > 0 ? port : server.getPort();
    }

    /**
     * Get the effective username, using JSON settings if available, otherwise falling back to job settings.
     *
     * @return the effective username
     */
    protected String getEffectiveUsername() {
        Server server = fsSettings.getServer();
        return username != null ? username : server.getUsername();
    }

    /**
     * Get the effective password, using JSON settings if available, otherwise falling back to job settings.
     *
     * @return the effective password
     */
    protected String getEffectivePassword() {
        Server server = fsSettings.getServer();
        return password != null ? password : server.getPassword();
    }

    /**
     * Read an optional string value from JSON settings.
     *
     * @param jsonPath the JSON path to read
     * @return the value, or null if not found
     */
    protected String readOptionalString(String jsonPath) {
        try {
            return document.read(jsonPath);
        } catch (PathNotFoundException e) {
            return null;
        }
    }

    /**
     * Read an optional integer value from JSON settings.
     *
     * @param jsonPath the JSON path to read
     * @return the value, or 0 if not found
     */
    protected int readOptionalInt(String jsonPath) {
        try {
            return document.read(jsonPath);
        } catch (PathNotFoundException e) {
            return 0;
        }
    }
}
