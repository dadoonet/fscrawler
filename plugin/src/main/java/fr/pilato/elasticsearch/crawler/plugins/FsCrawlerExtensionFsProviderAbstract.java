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

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.PathNotFoundException;
import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil;
import fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class FsCrawlerExtensionFsProviderAbstract implements FsCrawlerExtensionFsProvider {
    private static final Logger logger = LogManager.getLogger();
    protected DocumentContext document;
    protected FsSettings fsSettings;

    protected abstract void parseSettings() throws PathNotFoundException, IOException;

    protected abstract void validateSettings() throws PathNotFoundException, IOException;

    @Override
    public void start(FsSettings fsSettings, String restSettings) {
        this.fsSettings = fsSettings;

        if (hasRestSettings(restSettings)) {
            logger.trace("with rest settings {}", restSettings);
            document = JsonUtil.parseJsonAsDocumentContext(restSettings);
        } else {
            logger.trace("No REST settings provided");
        }

        try {
            parseSettings();
            validateSettings();
        } catch (PathNotFoundException | IOException e) {
            throw new FsCrawlerIllegalConfigurationException(e.getMessage(), e);
        }
    }

    private static boolean hasRestSettings(String restSettings) {
        return restSettings != null && !restSettings.isEmpty() && !"{}".equals(restSettings);
    }

    @Override
    public void stop() throws FsCrawlerPluginException {}

    @Override
    public void close() throws Exception {
        logger.debug("Closing FsCrawlerExtensionFsProviderAbstract");
        stop();
    }

    /** REST JSON block for this provider type ({@code $.<type>}), or an empty map when none is present. */
    protected Map<String, Object> restTypeSettings() {
        if (document == null) {
            return Map.of();
        }
        try {
            Object section = document.read("$." + getType());
            if (section instanceof Map<?, ?> map) {
                Map<String, Object> copy = new LinkedHashMap<>();
                map.forEach((key, value) -> copy.put(String.valueOf(key), value));
                return copy;
            }
            return Map.of();
        } catch (PathNotFoundException e) {
            return Map.of();
        }
    }

    protected void requireProviderSetting(String field, String value) throws IOException {
        if (value == null || value.isBlank()) {
            throw new IOException("Provider [" + getType() + "] requires fs.providers." + getType() + "." + field);
        }
    }

    /**
     * Hostname and username are required. In crawler mode (no REST path) returns {@code null}. In REST mode, returns
     * the normalized path; the caller must then {@link #connectAndValidateRestFile()}.
     */
    protected String requireHostUserAndNormalizeRestPath(String hostname, String username, String remotePath)
            throws IOException {
        requireProviderSetting("hostname", hostname);
        requireProviderSetting("username", username);
        if (remotePath == null || remotePath.isEmpty()) {
            if (document != null) {
                throw new IOException(getType() + " path is missing");
            }
            return null;
        }
        return normalizeRemotePath(remotePath);
    }

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

    protected void connectAndValidateRestFile() throws IOException {
        boolean success = false;
        try {
            openConnection();
            validateRestFile();
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

    /** REST-only: verify the remote file after {@link #openConnection()}. */
    protected void validateRestFile() throws FsCrawlerPluginException {}
}
