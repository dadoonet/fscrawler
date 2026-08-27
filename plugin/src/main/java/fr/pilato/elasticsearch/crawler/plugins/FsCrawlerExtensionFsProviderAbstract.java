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

import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.ProviderSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.Server;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class FsCrawlerExtensionFsProviderAbstract implements FsCrawlerExtensionFsProvider {
    private static final Logger logger = LogManager.getLogger();
    protected FsSettings fsSettings;
    protected Map<String, Object> overlay = Map.of();

    protected abstract void parseSettings() throws IOException;

    protected abstract void validateSettings() throws IOException;

    @Override
    public void start(FsSettings fsSettings, Map<String, Object> overlay) {
        this.fsSettings = fsSettings;
        this.overlay = copyOverlay(overlay);

        if (hasOverlay()) {
            logger.trace("with overlay keys {}", this.overlay.keySet());
        } else {
            logger.trace("No overlay provided");
        }

        try {
            parseSettings();
            validateSettings();
        } catch (IOException e) {
            throw new FsCrawlerIllegalConfigurationException(e.getMessage(), e);
        }
    }

    @Override
    public void stop() throws FsCrawlerPluginException {}

    @Override
    public void close() throws Exception {
        logger.debug("Closing FsCrawlerExtensionFsProviderAbstract");
        stop();
    }

    protected boolean hasOverlay() {
        return !overlay.isEmpty();
    }

    protected String overlayString(String field) {
        Object value = overlay.get(field);
        return value == null ? null : String.valueOf(value);
    }

    protected void requireProviderSetting(String field, String value) throws IOException {
        if (value == null || value.isBlank()) {
            throw new IOException("Provider [" + getType() + "] requires fs.providers." + getType() + "." + field);
        }
    }

    /**
     * Hostname and username are required. With an empty overlay (crawler mode) returns {@code null} when no file path
     * is set. With a non-empty overlay (one-shot file), returns the normalized path; the caller must then
     * {@link #connectAndValidateFile()}.
     */
    protected String requireHostUserAndNormalizeFilePath(String hostname, String username, String remotePath)
            throws IOException {
        requireProviderSetting("hostname", hostname);
        requireProviderSetting("username", username);
        if (remotePath == null || remotePath.isEmpty()) {
            if (hasOverlay()) {
                throw new IOException(getType() + " path is missing");
            }
            return null;
        }
        return normalizeRemotePath(remotePath);
    }

    /**
     * Resolve hostname/port/username/password/path for a host-based provider. Does not know SSH vs FTP: callers pass
     * their own port/username defaults and read extra fields (e.g. {@code pem_path}) from
     * {@link HostConnection#lookup()} before logging {@link ProviderSettings#deprecationWarnings()}.
     */
    @SuppressWarnings("removal")
    protected HostConnection parseHostConnection(int defaultPort, String defaultUsername) {
        ProviderSettings lookup = ProviderSettings.of(getType(), fsSettings, overlay);
        Server server = fsSettings != null ? fsSettings.getServer() : null;
        String hostname = lookup.string("hostname", server != null ? server.getHostname() : null);
        int port =
                lookup.integer("port", defaultPort, server != null && server.getPort() > 0 ? server.getPort() : null);
        String username = lookup.string("username", server != null ? server.getUsername() : null, defaultUsername);
        String password = lookup.secret("password", server != null ? server.getPassword() : null);
        return new HostConnection(hostname, port, username, password, lookup.overlayString("path"), lookup);
    }

    /** Normalize the overlay path and, when present, open the connection and {@link #validateFile()}. */
    protected String requirePathAndConnect(String hostname, String username, String remotePath) throws IOException {
        String normalized = requireHostUserAndNormalizeFilePath(hostname, username, remotePath);
        if (normalized == null) {
            return null;
        }
        connectAndValidateFile();
        return normalized;
    }

    protected record HostConnection(
            String hostname, int port, String username, String password, String path, ProviderSettings lookup) {}

    protected String normalizeRemotePath(String path) throws IOException {
        if (path == null) {
            return null;
        }
        if (!path.startsWith("/")) {
            String rootPath = fsSettings.getFs() != null ? fsSettings.getFs().getUrl() : null;
            if (rootPath == null || rootPath.isEmpty()) {
                throw new IOException("Cannot resolve relative path [" + path + "]: fs.url is not configured. "
                        + "Please use an absolute path starting with '/' or configure fs.url in the job settings.");
            }
            return rootPath.endsWith("/") ? rootPath + path : rootPath + "/" + path;
        }
        return path;
    }

    protected Doc createFileDocument(String remotePath, long filesize) {
        String filename = FilenameUtils.getName(remotePath);
        logger.debug("Creating document from {} for file {}", getType(), filename);

        Doc doc = new Doc();
        doc.getFile().setFilename(filename);
        doc.getFile().setFilesize(filesize);

        String rootUrl = (fsSettings.getFs() != null && fsSettings.getFs().getUrl() != null)
                ? fsSettings.getFs().getUrl()
                : "/";
        doc.getPath().setVirtual(FsCrawlerUtil.computeVirtualPathName(rootUrl, remotePath));
        doc.getPath().setReal(remotePath);
        return doc;
    }

    protected void connectAndValidateFile() throws IOException {
        boolean success = false;
        try {
            openConnection();
            validateFile();
            success = true;
        } catch (FsCrawlerPluginException e) {
            throw e;
        } catch (Exception e) {
            throw new FsCrawlerPluginException(
                    "Failed to connect to " + getType().toUpperCase() + " server: " + e.getMessage(), e);
        } finally {
            if (!success) {
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

    /** One-shot file: verify the remote file after {@link #openConnection()}. */
    protected void validateFile() throws FsCrawlerPluginException {}

    private static Map<String, Object> copyOverlay(Map<String, Object> overlay) {
        if (overlay == null || overlay.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        overlay.forEach((key, value) -> copy.put(String.valueOf(key), value));
        return Collections.unmodifiableMap(copy);
    }
}
