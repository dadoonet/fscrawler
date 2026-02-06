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

package fr.pilato.elasticsearch.crawler.plugins.input.local;

import fr.pilato.elasticsearch.crawler.fs.beans.FileAbstractModel;
import fr.pilato.elasticsearch.crawler.fs.framework.FileAcl;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.plugin.PluginSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.settings.plugin.PluginSettingsWriter;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPluginException;
import fr.pilato.elasticsearch.crawler.plugins.pipeline.PipelineContext;
import fr.pilato.elasticsearch.crawler.plugins.pipeline.input.AbstractInputPlugin;
import org.pf4j.Extension;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.*;

/**
 * Input plugin for crawling local filesystem.
 * <p>
 * Configuration example:
 * <pre>
 * inputs:
 *   - type: "local"
 *     id: "my-local-input"
 *     local:
 *       path: "/path/to/documents"
 *     update_rate: "15m"
 *     includes: ["*.pdf", "*.doc"]
 *     excludes: ["~*"]
 * </pre>
 */
@Extension
public class LocalInputPlugin extends AbstractInputPlugin {

    public static final String TYPE = "local";
    public static final String DEFAULT_YAML_RESOURCE = "/fr/pilato/elasticsearch/crawler/plugins/input/local/local-input-default.yaml";
    public static final String DEFAULT_PROPERTIES_RESOURCE = "/fr/pilato/elasticsearch/crawler/plugins/input/local/local-input-default.properties";

    // Typed settings for per-plugin configuration
    private LocalInputSettings settings;

    // Configuration fields (populated from settings or legacy config)
    private String basePath;
    private boolean followSymlinks;
    private boolean aclSupport;
    private boolean attributesSupport;

    private static final Comparator<Path> PATH_COMPARATOR = Comparator.comparing(
            file -> getModificationOrCreationTime(file.toFile()),
            Comparator.nullsLast(Comparator.naturalOrder()));

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public boolean supportsCrawling() {
        return true;
    }

    // ========== Per-Plugin Configuration (new approach) ==========

    @Override
    public boolean supportsPerPluginConfig() {
        return true;
    }

    @Override
    public String getDefaultYamlResource() {
        return DEFAULT_YAML_RESOURCE;
    }

    @Override
    public String getDefaultPropertiesResource() {
        return DEFAULT_PROPERTIES_RESOURCE;
    }

    @Override
    public String getDefaultSettingsFilename() {
        return "01-local.yaml";
    }

    @Override
    public String getPluginCategory() {
        return "inputs";
    }

    @Override
    public String getDescription() {
        return "Local filesystem input configuration";
    }

    @Override
    public void loadSettings(Path configFile) throws IOException, FsCrawlerIllegalConfigurationException {
        logger.debug("Loading local input settings from [{}]", configFile);
        this.settings = PluginSettingsLoader.load(configFile, LocalInputSettings.class);
        applySettings();
    }

    @Override
    public void saveSettings(Path configFile) throws IOException {
        if (settings == null) {
            settings = createSettingsFromCurrentConfig();
        }
        logger.debug("Saving local input settings to [{}]", configFile);
        PluginSettingsWriter.write(settings, configFile);
    }

    @Override
    public void migrateFromV1(FsSettings v1Settings) {
        logger.debug("Migrating local input settings from V1 FsSettings");
        settings = new LocalInputSettings();
        settings.setVersion(LocalInputSettings.CURRENT_VERSION);
        settings.setId(id != null ? id : "default");
        settings.setType(TYPE);

        if (v1Settings.getFs() != null) {
            settings.setPath(v1Settings.getFs().getUrl());
            if (v1Settings.getFs().getUpdateRate() != null) {
                settings.setUpdateRate(v1Settings.getFs().getUpdateRate().toString());
            }
            settings.setIncludes(v1Settings.getFs().getIncludes());
            settings.setExcludes(v1Settings.getFs().getExcludes());
            settings.setFollowSymlinks(v1Settings.getFs().isFollowSymlinks());
            settings.setAclSupport(v1Settings.getFs().isAclSupport());
            settings.setAttributesSupport(v1Settings.getFs().isAttributesSupport());
        }

        applySettings();
    }

    /**
     * Applies the loaded settings to the plugin's internal state.
     */
    private void applySettings() {
        this.id = settings.getId();
        this.basePath = settings.getPath();
        this.followSymlinks = settings.getFollowSymlinks() != null ? settings.getFollowSymlinks() : false;
        this.aclSupport = settings.getAclSupport() != null ? settings.getAclSupport() : false;
        this.attributesSupport = settings.getAttributesSupport() != null ? settings.getAttributesSupport() : false;

        // Apply common input settings to parent
        if (settings.getUpdateRate() != null) {
            setUpdateRate(settings.getUpdateRate());
        }
        if (settings.getIncludes() != null) {
            setIncludes(settings.getIncludes());
        }
        if (settings.getExcludes() != null) {
            setExcludes(settings.getExcludes());
        }
        if (settings.getTags() != null) {
            setTags(settings.getTags());
        }

        logger.debug("Local input plugin [{}] configured with path [{}]", id, basePath);
    }

    /**
     * Creates a settings object from the current plugin configuration.
     * Useful for saveSettings() when settings were loaded via legacy configure().
     */
    private LocalInputSettings createSettingsFromCurrentConfig() {
        LocalInputSettings s = new LocalInputSettings();
        s.setVersion(LocalInputSettings.CURRENT_VERSION);
        s.setId(id);
        s.setType(TYPE);
        s.setPath(basePath);
        s.setUpdateRate(getUpdateRate());
        s.setIncludes(getIncludes());
        s.setExcludes(getExcludes());
        s.setTags(getTags());
        s.setFollowSymlinks(followSymlinks);
        s.setAclSupport(aclSupport);
        s.setAttributesSupport(attributesSupport);
        return s;
    }

    // ========== Legacy Configuration (backward compatibility) ==========

    @Override
    protected void configureTypeSpecific(Map<String, Object> typeConfig) {
        // Get path from local.path in config
        @SuppressWarnings("unchecked")
        Map<String, Object> localConfig = (Map<String, Object>) typeConfig.get("local");
        if (localConfig != null) {
            this.basePath = (String) localConfig.get("path");
        }

        // Fallback to global settings
        if (basePath == null && globalSettings.getFs() != null) {
            basePath = globalSettings.getFs().getUrl();
        }

        // Get other settings from global fs settings
        if (globalSettings.getFs() != null) {
            this.followSymlinks = globalSettings.getFs().isFollowSymlinks();
            this.aclSupport = globalSettings.getFs().isAclSupport();
            this.attributesSupport = globalSettings.getFs().isAttributesSupport();
        }

        logger.debug("Local input plugin [{}] configured with path [{}]", id, basePath);
    }

    // ========== Validation ==========

    @Override
    public void validateConfiguration() throws FsCrawlerPluginException {
        if (basePath == null || basePath.isEmpty()) {
            throw new FsCrawlerPluginException("Local input plugin requires 'path' to be configured");
        }

        Path path = Paths.get(basePath);
        if (!Files.exists(path)) {
            throw new FsCrawlerPluginException("Path does not exist: " + basePath);
        }

        logger.debug("Local input plugin [{}] configuration validated", id);
    }

    /**
     * Returns the typed settings for this plugin.
     * @return the settings, or null if not loaded via per-plugin config
     */
    public LocalInputSettings getSettings() {
        return settings;
    }

    // ========== Crawling methods ==========

    @Override
    public void openConnection() throws FsCrawlerPluginException {
        // No connection needed for local filesystem
        logger.debug("Opening local filesystem connection for path [{}]", basePath);
    }

    @Override
    public void closeConnection() throws FsCrawlerPluginException {
        // No connection to close for local filesystem
        logger.debug("Closing local filesystem connection");
    }

    @Override
    public boolean exists(String directory) throws FsCrawlerPluginException {
        return new File(directory).exists();
    }

    @Override
    public Collection<FileAbstractModel> getFiles(String dir) throws FsCrawlerPluginException {
        logger.debug("Listing local files from [{}]", dir);

        final Collection<FileAbstractModel> result = new ArrayList<>();
        try (Stream<Path> paths = Files.list(Paths.get(dir))) {
            paths.filter(p -> followSymlinks || !Files.isSymbolicLink(p))
                    .sorted(PATH_COMPARATOR.reversed())
                    .forEach(p -> result.add(toFileAbstractModel(dir, p.toFile())));
        } catch (IOException e) {
            logger.warn("Error listing files in [{}]: {}", dir, e.getMessage());
        }

        logger.debug("[{}] local files found in [{}]", result.size(), dir);
        return result;
    }

    @Override
    public InputStream getInputStream(FileAbstractModel file) throws FsCrawlerPluginException {
        try {
            return new FileInputStream(file.getFullpath());
        } catch (FileNotFoundException e) {
            throw new FsCrawlerPluginException("Can not get input stream for " + file.getFullpath(), e);
        }
    }

    @Override
    public void closeInputStream(InputStream inputStream) throws FsCrawlerPluginException {
        try {
            inputStream.close();
        } catch (IOException e) {
            throw new FsCrawlerPluginException("Error while closing stream", e);
        }
    }

    @Override
    public InputStream readFile() throws FsCrawlerPluginException {
        throw new FsCrawlerPluginException("REST API readFile() not supported by local input plugin. Use getInputStream() for crawling.");
    }

    @Override
    public PipelineContext createContext() throws FsCrawlerPluginException {
        throw new FsCrawlerPluginException("REST API createContext() not supported by local input plugin.");
    }

    /**
     * Returns the base path configured for this input.
     * @return the base path
     */
    public String getBasePath() {
        return basePath;
    }

    /**
     * Convert a File to a FileAbstractModel.
     */
    private FileAbstractModel toFileAbstractModel(String path, File file) {
        List<FileAcl> fileAcls = aclSupport && attributesSupport
                ? getFileAcls(file.toPath())
                : Collections.emptyList();

        String separator = getPathSeparator(basePath);

        return new FileAbstractModel(
                file.getName(),
                file.isFile(),
                getModificationTime(file),
                getCreationTime(file),
                getLastAccessTime(file),
                getFileExtension(file),
                resolveSeparator(path, separator),
                resolveSeparator(file.getAbsolutePath(), separator),
                file.length(),
                getOwnerName(file),
                getGroupName(file),
                getFilePermissions(file),
                fileAcls,
                computeAclHash(fileAcls));
    }

    private String resolveSeparator(String path, String separator) {
        if (separator.equals("/")) {
            return path.replace("\\", "/");
        }
        return path.replace("/", "\\");
    }
}
