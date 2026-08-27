/*
 * Licensed to David Pilato (the "Author") under one
 * or more contributor license agreements. See the NOTICE file
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
 *
 * Made from 🇫🇷🇪🇺 with ❤️ - 2011-2026
 */
package fr.pilato.elasticsearch.crawler.plugins;

import com.jayway.jsonpath.PathNotFoundException;
import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil;
import fr.pilato.elasticsearch.crawler.fs.settings.Server;
import java.io.IOException;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Abstract base class for remote filesystem providers (SSH, FTP, etc.).
 *
 * <p>This class provides common functionality for providers that connect to remote servers, including:
 *
 * <ul>
 *   <li>Server connection settings (hostname, port, username, password)
 *   <li>Remote path handling and normalization
 *   <li>Common document creation logic
 * </ul>
 */
@SuppressWarnings("removal")
public abstract class FsCrawlerExtensionRemoteProviderAbstract extends FsCrawlerExtensionFsProviderAbstract {
    private static final Logger logger = LogManager.getLogger();

    // Common fields for remote providers
    protected String remotePath;
    protected String hostname;
    protected int port;
    protected String username;
    protected String password;
    protected String pemPath;

    /**
     * Get the file size of the remote file. Each provider implements this based on how they retrieve file metadata.
     *
     * @return the file size in bytes
     */
    protected abstract long getFilesize();

    /**
     * Hook for parsing protocol-specific settings from JSON. Override this method to parse additional settings specific
     * to the protocol.
     *
     * @throws PathNotFoundException if a required setting is missing
     */
    protected void parseProtocolSpecificSettings() throws PathNotFoundException {
        // Default implementation does nothing
        // Override in subclasses if needed (e.g., SSH pemPath)
    }

    /**
     * Validate the remote file exists and is accessible. This method is called after the connection is opened.
     * Implementations should check if the file exists and store any file metadata needed.
     *
     * @throws FsCrawlerPluginException if the file doesn't exist or is not accessible
     */
    protected abstract void doValidateFile() throws FsCrawlerPluginException;

    // ========== Common implementations ==========

    @Override
    public boolean supportsCrawling() {
        return true;
    }

    @Override
    protected void parseSettings() throws PathNotFoundException {
        RemoteConnectionSettings resolved =
                RemoteConnectionSettings.resolve(getType(), document, fsSettings, defaultPort(), defaultUsername());
        remotePath = resolved.remotePath();
        hostname = resolved.hostname();
        port = resolved.port();
        username = resolved.username();
        password = resolved.password();
        pemPath = resolved.pemPath();
        for (String warning : resolved.deprecationWarnings()) {
            logger.warn(warning);
        }

        parseProtocolSpecificSettings();
    }

    /**
     * Default TCP port when neither REST, {@code fs.providers.<type>.port} nor {@code server.port} is set.
     *
     * @return {@code 0} unless a plugin overrides this
     */
    protected int defaultPort() {
        return 0;
    }

    /**
     * Default username when none is configured.
     *
     * @return {@code null} unless a plugin overrides this
     */
    protected String defaultUsername() {
        return null;
    }

    /**
     * URL scheme used by {@link #toFileUrl(String)}. Defaults to {@link #getType()}.
     *
     * @return a scheme such as {@code ftp} or {@code sftp}
     */
    protected String urlScheme() {
        return getType();
    }

    @Override
    protected void validateSettings() throws IOException {
        if (hostname == null || hostname.isBlank()) {
            throw new IOException("Provider [" + getType() + "] requires fs.providers." + getType() + ".hostname");
        }
        if (username == null || username.isBlank()) {
            throw new IOException("Provider [" + getType() + "] requires fs.providers." + getType() + ".username");
        }

        // Crawler mode: no REST path. Connection settings are used later in openConnection().
        if (remotePath == null || remotePath.isEmpty()) {
            if (document != null) {
                throw new IOException(getType() + " path is missing");
            }
            return;
        }

        // Normalize the path
        remotePath = normalizeRemotePath(remotePath);

        // Open connection and validate file with proper resource management
        boolean success = false;
        try {
            openConnection();
            doValidateFile();
            success = true;
        } catch (FsCrawlerPluginException e) {
            throw e;
        } catch (Exception e) {
            throw new FsCrawlerPluginException(
                    "Failed to connect to " + getType().toUpperCase() + " server: " + e.getMessage(), e);
        } finally {
            if (!success) {
                // Close connection on validation failure to prevent resource leak
                try {
                    closeConnection();
                } catch (Exception e) {
                    logger.warn(
                            "Error closing {} connection after validation failure: {}",
                            getType().toUpperCase(),
                            e.getMessage());
                }
            }
        }
    }

    @Override
    public Doc createDocument() {
        String filename = getFilename();
        logger.debug("Creating document from {} for file {}", getType(), filename);

        Doc doc = new Doc();
        doc.getFile().setFilename(filename);
        doc.getFile().setFilesize(getFilesize());

        // Compute virtual path - use root URL if configured, otherwise use "/" as default
        String rootUrl = (fsSettings.getFs() != null && fsSettings.getFs().getUrl() != null)
                ? fsSettings.getFs().getUrl()
                : "/";
        doc.getPath().setVirtual(FsCrawlerUtil.computeVirtualPathName(rootUrl, remotePath));
        doc.getPath().setReal(remotePath);
        return doc;
    }

    @Override
    public void stop() throws FsCrawlerPluginException {
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
     * Normalize the remote path, resolving relative paths against the root URL.
     *
     * @param path the path to normalize
     * @return the normalized path
     * @throws IOException if a relative path is provided but no root URL is configured
     */
    protected String normalizeRemotePath(String path) throws IOException {
        if (path == null) {
            return null;
        }
        if (!path.startsWith("/")) {
            // Relative path - need to resolve against root URL
            String rootPath = fsSettings.getFs() != null ? fsSettings.getFs().getUrl() : null;
            if (rootPath == null || rootPath.isEmpty()) {
                throw new IOException("Cannot resolve relative path [" + path + "]: fs.url is not configured. "
                        + "Please use an absolute path starting with '/' or configure fs.url in the job settings.");
            }
            return rootPath.endsWith("/") ? rootPath + path : rootPath + "/" + path;
        }
        return path;
    }

    /**
     * Get the effective hostname, using JSON settings if available, otherwise falling back to job settings.
     *
     * @return the effective hostname, or null if not configured
     */
    protected String getEffectiveHostname() {
        if (hostname != null) {
            return hostname;
        }
        Server server = fsSettings.getServer();
        return server != null ? server.getHostname() : null;
    }

    /**
     * Get the effective port, using JSON settings if available, otherwise falling back to job settings.
     *
     * @return the effective port, or 0 if not configured
     */
    protected int getEffectivePort() {
        if (port > 0) {
            return port;
        }
        Server server = fsSettings.getServer();
        return server != null ? server.getPort() : 0;
    }

    /**
     * Get the effective username, using JSON settings if available, otherwise falling back to job settings.
     *
     * @return the effective username, or null if not configured
     */
    protected String getEffectiveUsername() {
        if (username != null) {
            return username;
        }
        Server server = fsSettings.getServer();
        return server != null ? server.getUsername() : null;
    }

    /**
     * Get the effective password, using JSON settings if available, otherwise falling back to job settings.
     *
     * @return the effective password, or null if not configured
     */
    protected String getEffectivePassword() {
        if (password != null) {
            return password;
        }
        Server server = fsSettings != null ? fsSettings.getServer() : null;
        return server != null ? server.getPassword() : null;
    }

    /**
     * Get the effective PEM path, using JSON settings if available, otherwise falling back to job settings.
     *
     * @return the effective PEM path, or null if not configured
     */
    protected String getEffectivePemPath() {
        if (pemPath != null) {
            return pemPath;
        }
        Server server = fsSettings != null ? fsSettings.getServer() : null;
        return server != null ? server.getPemPath() : null;
    }

    @Override
    public String toFileUrl(String fullPath) {
        return String.format("%s://%s:%d%s", urlScheme(), getEffectiveHostname(), getEffectivePort(), fullPath);
    }

    /**
     * Read an optional string value from JSON settings.
     *
     * @param jsonPath the JSON path to read
     * @return the value, or null if not found
     */
    protected String readOptionalString(String jsonPath) {
        if (document == null) {
            return null;
        }
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
        if (document == null) {
            return 0;
        }
        try {
            return document.read(jsonPath);
        } catch (PathNotFoundException e) {
            return 0;
        }
    }
}
