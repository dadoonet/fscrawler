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

package fr.pilato.elasticsearch.crawler.plugins.service.rest;

import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import fr.pilato.elasticsearch.crawler.fs.rest.RestServer;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentService;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerManagementService;
import fr.pilato.elasticsearch.crawler.fs.settings.Defaults;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.plugin.PluginSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.settings.plugin.PluginSettingsWriter;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPluginException;
import fr.pilato.elasticsearch.crawler.plugins.service.AbstractServicePlugin;
import org.pf4j.Extension;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * REST API service plugin. Exposes HTTP endpoints for document upload and server status.
 * <p>
 * This plugin is disabled by default; set {@code enabled: true} in _settings/services/01-rest.yaml to start the REST server.
 */
@Extension
public class RestServicePlugin extends AbstractServicePlugin {

    public static final String TYPE = "rest";
    public static final String DEFAULT_YAML_RESOURCE = "/fr/pilato/elasticsearch/crawler/plugins/service/rest/rest-service-default.yaml";
    public static final String DEFAULT_PROPERTIES_RESOURCE = "/fr/pilato/elasticsearch/crawler/plugins/service/rest/rest-service-default.properties";

    private RestServiceSettings settings;
    private RestServer restServer;

    @Override
    public String getType() {
        return TYPE;
    }

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
        return "01-rest.yaml";
    }

    @Override
    public String getDescription() {
        return "REST API service (upload, status endpoints)";
    }

    @Override
    public void loadSettings(Path configFile) throws IOException, FsCrawlerIllegalConfigurationException {
        logger.debug("Loading REST service settings from [{}]", configFile);
        this.settings = PluginSettingsLoader.load(configFile, RestServiceSettings.class,
                getDefaultPropertiesResource(), null);
        applySettings();
    }

    @Override
    public void saveSettings(Path configFile) throws IOException {
        if (settings == null) {
            settings = createSettingsFromCurrentConfig();
        }
        logger.debug("Saving REST service settings to [{}]", configFile);
        PluginSettingsWriter.write(settings, configFile);
    }

    @Override
    public void migrateFromV1(FsSettings v1Settings) {
        logger.debug("Migrating REST service settings from V1 FsSettings");
        settings = new RestServiceSettings();
        settings.setVersion(RestServiceSettings.CURRENT_VERSION);
        settings.setId(id != null ? id : "rest-service");
        settings.setType(TYPE);
        if (v1Settings.getRest() != null) {
            settings.setUrl(v1Settings.getRest().getUrl());
            settings.setEnableCors(v1Settings.getRest().isEnableCors());
        }
        settings.setEnabled(true);
        applySettings();
    }

    private void applySettings() {
        this.id = settings.getId();
        if (settings.getEnabled() != null) {
            setEnabled(settings.getEnabled());
        }
        logger.debug("REST service plugin [{}] configured with url [{}], enabled [{}]", id, settings.getUrl(), isEnabled());
    }

    private RestServiceSettings createSettingsFromCurrentConfig() {
        RestServiceSettings s = new RestServiceSettings();
        s.setVersion(RestServiceSettings.CURRENT_VERSION);
        s.setId(id);
        s.setType(TYPE);
        s.setEnabled(isEnabled());
        return s;
    }

    @Override
    protected void configureTypeSpecific(Map<String, Object> typeConfig) {
        if (settings == null) {
            settings = new RestServiceSettings();
            settings.setVersion(RestServiceSettings.CURRENT_VERSION);
            settings.setId(id);
            settings.setType(TYPE);
        }
        if (typeConfig == null) return;
        @SuppressWarnings("unchecked")
        Map<String, Object> restConfig = (Map<String, Object>) typeConfig.get("rest");
        if (restConfig != null) {
            Object url = restConfig.get("url");
            if (url != null) settings.setUrl(url.toString());
            Object enableCors = restConfig.get("enableCors");
            if (enableCors instanceof Boolean) settings.setEnableCors((Boolean) enableCors);
            Object enabled = restConfig.get("enabled");
            if (enabled instanceof Boolean) setEnabled((Boolean) enabled);
        }
        if (globalSettings != null && globalSettings.getRest() != null) {
            if (settings.getUrl() == null) settings.setUrl(globalSettings.getRest().getUrl());
            if (settings.getEnableCors() == null) settings.setEnableCors(globalSettings.getRest().isEnableCors());
        }
    }

    @Override
    protected void doStart() throws FsCrawlerPluginException {
        if (servicePluginContext == null) {
            throw new FsCrawlerPluginException("REST service plugin requires a context (set by the runner before start)");
        }
        if (settings == null) {
            throw new FsCrawlerPluginException("REST service plugin requires settings (configure or migrate from V1)");
        }
        FsSettings fsSettings = servicePluginContext.getFsSettings();
        Object mgmt = servicePluginContext.getManagementService();
        Object doc = servicePluginContext.getDocumentService();
        if (fsSettings == null || mgmt == null || doc == null) {
            throw new FsCrawlerPluginException("REST service plugin context is incomplete (missing FsSettings or services)");
        }
        String url = settings.getUrl() != null ? settings.getUrl() : Defaults.REST_URL_DEFAULT;
        boolean enableCors = settings.getEnableCors() != null && settings.getEnableCors();

        restServer = new RestServer(url, enableCors,
                fsSettings,
                (FsCrawlerManagementService) mgmt,
                (FsCrawlerDocumentService) doc,
                servicePluginContext.getPluginsManager(),
                null);
        restServer.start();
        logger.info("REST service plugin [{}] started (bind URL: {})", id, url);
    }

    @Override
    protected void doStop() throws FsCrawlerPluginException {
        if (restServer != null) {
            try {
                restServer.close();
            } finally {
                restServer = null;
            }
            logger.debug("REST service plugin [{}] stopped", id);
        }
    }

    public RestServiceSettings getSettings() {
        return settings;
    }
}
