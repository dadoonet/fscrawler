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
package fr.pilato.elasticsearch.crawler.plugins.elasticsearch;

import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClient;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientException;
import fr.pilato.elasticsearch.crawler.fs.client.IElasticsearchClient;
import fr.pilato.elasticsearch.crawler.fs.framework.ByteSizeValue;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.settings.Elasticsearch;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.plugin.PluginSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.settings.plugin.PluginSettingsWriter;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPluginException;
import fr.pilato.elasticsearch.crawler.plugins.pipeline.PipelineContext;
import fr.pilato.elasticsearch.crawler.plugins.pipeline.output.AbstractOutputPlugin;
import org.pf4j.Extension;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Elasticsearch output plugin for the FSCrawler pipeline.
 * Sends documents to Elasticsearch using the ElasticsearchClient.
 * 
 * Configuration example (v2 pipeline format):
 * <pre>
 * outputs:
 *   - id: es-output
 *     type: elasticsearch
 *     elasticsearch:
 *       urls:
 *         - "https://localhost:9200"
 *       username: "elastic"
 *       password: "changeme"
 *       # OR api_key: "base64encodedkey"
 *       index: "my_index"
 *       index_folder: "my_index_folder"
 *       bulk_size: 100
 *       flush_interval: "5s"
 *       byte_size: "10mb"
 *       pipeline: "my_pipeline"
 *       push_templates: true
 *       ssl_verification: true
 *       ca_certificate: "/path/to/ca.crt"
 * </pre>
 */
@Extension
public class ElasticsearchOutputPlugin extends AbstractOutputPlugin {

    private static final String TYPE = "elasticsearch";
    public static final String DEFAULT_YAML_RESOURCE = "/fr/pilato/elasticsearch/crawler/plugins/elasticsearch/elasticsearch-output-default.yaml";
    public static final String DEFAULT_PROPERTIES_RESOURCE = "/fr/pilato/elasticsearch/crawler/plugins/elasticsearch/elasticsearch-output-default.properties";

    // Typed settings for per-plugin configuration
    private ElasticsearchOutputSettings settings;

    // Elasticsearch client
    private IElasticsearchClient client;
    
    // Configuration fields
    private List<String> urls;
    private String index;
    private String indexFolder;
    private Integer bulkSize;
    private String flushInterval;
    private String byteSize;
    private String apiKey;
    private String username;
    private String password;
    private String caCertificate;
    private String pipeline;
    private String pathPrefix;
    private Boolean sslVerification;
    private Boolean pushTemplates;
    private Boolean forcePushTemplates;
    private Boolean semanticSearch;

    @Override
    public String getType() {
        return TYPE;
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
        return "01-elasticsearch.yaml";
    }

    @Override
    public String getPluginCategory() {
        return "outputs";
    }

    @Override
    public String getDescription() {
        return "Elasticsearch output configuration";
    }

    @Override
    public void loadSettings(Path configFile) throws IOException, FsCrawlerIllegalConfigurationException {
        logger.debug("Loading Elasticsearch output settings from [{}]", configFile);
        this.settings = PluginSettingsLoader.load(configFile, ElasticsearchOutputSettings.class);
        applySettings();
    }

    @Override
    public void saveSettings(Path configFile) throws IOException {
        if (settings == null) {
            settings = createSettingsFromCurrentConfig();
        }
        logger.debug("Saving Elasticsearch output settings to [{}]", configFile);
        PluginSettingsWriter.write(settings, configFile);
    }

    @Override
    public void migrateFromV1(FsSettings v1Settings) {
        logger.debug("Migrating Elasticsearch output settings from V1 FsSettings");
        settings = new ElasticsearchOutputSettings();
        settings.setVersion(ElasticsearchOutputSettings.CURRENT_VERSION);
        settings.setId(id != null ? id : "default");
        settings.setType(TYPE);

        if (v1Settings.getElasticsearch() != null) {
            Elasticsearch es = v1Settings.getElasticsearch();
            settings.setUrls(es.getUrls() != null ? new ArrayList<>(es.getUrls()) : null);
            settings.setIndex(es.getIndex());
            settings.setIndexFolder(es.getIndexFolder());
            settings.setApiKey(es.getApiKey());
            settings.setUsername(es.getUsername());
            settings.setPassword(es.getPassword());
            settings.setCaCertificate(es.getCaCertificate());
            settings.setPipeline(es.getPipeline());
            settings.setPathPrefix(es.getPathPrefix());
            settings.setSslVerification(es.isSslVerification());
            settings.setBulkSize(es.getBulkSize());
            if (es.getFlushInterval() != null) {
                settings.setFlushInterval(es.getFlushInterval().toString());
            }
            if (es.getByteSize() != null) {
                settings.setByteSize(es.getByteSize().toString());
            }
            settings.setPushTemplates(es.isPushTemplates());
            settings.setForcePushTemplates(es.isForcePushTemplates());
        }

        applySettings();
    }

    /**
     * Applies the loaded settings to the plugin's internal state.
     */
    private void applySettings() {
        this.id = settings.getId();
        this.urls = settings.getUrls();
        this.index = settings.getIndex();
        this.indexFolder = settings.getIndexFolder();
        this.apiKey = settings.getApiKey();
        this.username = settings.getUsername();
        this.password = settings.getPassword();
        this.caCertificate = settings.getCaCertificate();
        this.pipeline = settings.getPipeline();
        this.pathPrefix = settings.getPathPrefix();
        this.sslVerification = settings.getSslVerification() != null ? settings.getSslVerification() : true;
        this.bulkSize = settings.getBulkSize();
        this.flushInterval = settings.getFlushInterval();
        this.byteSize = settings.getByteSize();
        this.pushTemplates = settings.getPushTemplates() != null ? settings.getPushTemplates() : true;
        this.forcePushTemplates = settings.getForcePushTemplates() != null ? settings.getForcePushTemplates() : false;
        this.semanticSearch = settings.getSemanticSearch() != null ? settings.getSemanticSearch() : true;

        if (settings.getWhen() != null) {
            setWhen(settings.getWhen());
        }

        logger.debug("Elasticsearch output [{}] configured with urls: {}", id, urls);
    }

    /**
     * Creates a settings object from the current plugin configuration.
     */
    private ElasticsearchOutputSettings createSettingsFromCurrentConfig() {
        ElasticsearchOutputSettings s = new ElasticsearchOutputSettings();
        s.setVersion(ElasticsearchOutputSettings.CURRENT_VERSION);
        s.setId(id);
        s.setType(TYPE);
        s.setWhen(getWhen());
        s.setUrls(urls);
        s.setIndex(index);
        s.setIndexFolder(indexFolder);
        s.setApiKey(apiKey);
        s.setUsername(username);
        s.setPassword(password);
        s.setCaCertificate(caCertificate);
        s.setPipeline(pipeline);
        s.setPathPrefix(pathPrefix);
        s.setSslVerification(sslVerification);
        s.setBulkSize(bulkSize);
        s.setFlushInterval(flushInterval);
        s.setByteSize(byteSize);
        s.setPushTemplates(pushTemplates);
        s.setForcePushTemplates(forcePushTemplates);
        s.setSemanticSearch(semanticSearch);
        return s;
    }

    /**
     * Returns the typed settings for this plugin.
     * @return the settings, or null if not loaded via per-plugin config
     */
    public ElasticsearchOutputSettings getSettings() {
        return settings;
    }

    // ========== Legacy Configuration (backward compatibility) ==========

    @Override
    protected void configureTypeSpecific(Map<String, Object> typeConfig) {
        // Read Elasticsearch-specific configuration
        this.urls = getConfigList(typeConfig, "urls");
        this.index = getConfigValue(typeConfig, "index", String.class, null);
        this.indexFolder = getConfigValue(typeConfig, "index_folder", String.class, null);
        this.bulkSize = getConfigValue(typeConfig, "bulk_size", Integer.class, null);
        this.flushInterval = getConfigValue(typeConfig, "flush_interval", String.class, null);
        this.byteSize = getConfigValue(typeConfig, "byte_size", String.class, null);
        this.apiKey = getConfigValue(typeConfig, "api_key", String.class, null);
        this.username = getConfigValue(typeConfig, "username", String.class, null);
        this.password = getConfigValue(typeConfig, "password", String.class, null);
        this.caCertificate = getConfigValue(typeConfig, "ca_certificate", String.class, null);
        this.pipeline = getConfigValue(typeConfig, "pipeline", String.class, null);
        this.pathPrefix = getConfigValue(typeConfig, "path_prefix", String.class, null);
        this.sslVerification = getConfigValue(typeConfig, "ssl_verification", Boolean.class, null);
        this.pushTemplates = getConfigValue(typeConfig, "push_templates", Boolean.class, null);
        this.forcePushTemplates = getConfigValue(typeConfig, "force_push_templates", Boolean.class, null);
        this.semanticSearch = getConfigValue(typeConfig, "semantic_search", Boolean.class, null);
        
        // Fallback to global settings if not specified in plugin config
        if (globalSettings != null && globalSettings.getElasticsearch() != null) {
            Elasticsearch globalEs = globalSettings.getElasticsearch();
            
            if (urls == null || urls.isEmpty()) {
                urls = globalEs.getUrls();
            }
            if (index == null) {
                index = globalEs.getIndex();
            }
            if (indexFolder == null) {
                indexFolder = globalEs.getIndexFolder();
            }
            if (bulkSize == null) {
                // getBulkSize() returns int and may throw NPE if not set
                try {
                    bulkSize = globalEs.getBulkSize();
                } catch (NullPointerException ignored) {
                    // bulkSize not set in global settings
                }
            }
            if (flushInterval == null && globalEs.getFlushInterval() != null) {
                flushInterval = globalEs.getFlushInterval().toString();
            }
            if (byteSize == null && globalEs.getByteSize() != null) {
                byteSize = globalEs.getByteSize().toString();
            }
            if (apiKey == null) {
                apiKey = globalEs.getApiKey();
            }
            if (username == null) {
                username = globalEs.getUsername();
            }
            if (password == null) {
                password = globalEs.getPassword();
            }
            if (caCertificate == null) {
                caCertificate = globalEs.getCaCertificate();
            }
            if (pipeline == null) {
                pipeline = globalEs.getPipeline();
            }
            if (pathPrefix == null) {
                pathPrefix = globalEs.getPathPrefix();
            }
            if (sslVerification == null) {
                // isSslVerification() returns boolean and may throw NPE if not set
                try {
                    sslVerification = globalEs.isSslVerification();
                } catch (NullPointerException ignored) {
                    // sslVerification not set in global settings
                }
            }
            if (pushTemplates == null) {
                // isPushTemplates() returns boolean and may throw NPE if not set
                try {
                    pushTemplates = globalEs.isPushTemplates();
                } catch (NullPointerException ignored) {
                    // pushTemplates not set in global settings
                }
            }
            if (forcePushTemplates == null) {
                // isForcePushTemplates() returns boolean and may throw NPE if not set
                try {
                    forcePushTemplates = globalEs.isForcePushTemplates();
                } catch (NullPointerException ignored) {
                    // forcePushTemplates not set in global settings
                }
            }
            if (semanticSearch == null) {
                // isSemanticSearch() returns boolean and may throw NPE if not set
                try {
                    semanticSearch = globalEs.isSemanticSearch();
                } catch (NullPointerException ignored) {
                    // semanticSearch not set in global settings
                }
            }
        }
        
        // Apply defaults for non-null required fields
        if (sslVerification == null) {
            sslVerification = true;
        }
        if (pushTemplates == null) {
            pushTemplates = true;
        }
        if (forcePushTemplates == null) {
            forcePushTemplates = false;
        }
        if (semanticSearch == null) {
            semanticSearch = true;
        }
    }

    @Override
    public void validateConfiguration() throws FsCrawlerIllegalConfigurationException {
        super.validateConfiguration();
        
        if (urls == null || urls.isEmpty()) {
            throw new FsCrawlerIllegalConfigurationException(
                    "Elasticsearch output plugin requires at least one URL. " +
                    "Please configure 'elasticsearch.urls' in your output configuration.");
        }
        
        // Index name will be determined at runtime from context if not configured
        logger.debug("Elasticsearch output plugin [{}] configured with urls: {}", id, urls);
    }

    @Override
    public void start() throws FsCrawlerPluginException {
        logger.info("Starting Elasticsearch output plugin [{}]", id);
        
        try {
            // Build FsSettings object to pass to ElasticsearchClient
            FsSettings internalSettings = buildInternalSettings();
            
            // Create and start the client
            client = new ElasticsearchClient(internalSettings);
            client.start();
            
            logger.info("Elasticsearch output plugin [{}] connected to {} (version {})", 
                    id, urls, client.getVersion());
            
            // Create index and component templates if configured
            if (pushTemplates) {
                logger.debug("Creating index and component templates for [{}]", id);
                client.createIndexAndComponentTemplates();
            }
            
        } catch (ElasticsearchClientException e) {
            throw new FsCrawlerPluginException("Failed to start Elasticsearch client: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new FsCrawlerPluginException("Failed to initialize Elasticsearch output plugin: " + e.getMessage(), e);
        }
    }

    @Override
    public void stop() throws FsCrawlerPluginException {
        logger.info("Stopping Elasticsearch output plugin [{}]", id);
        
        if (client != null) {
            try {
                // Flush any pending operations before closing
                client.flush();
                client.close();
            } catch (IOException e) {
                throw new FsCrawlerPluginException("Error closing Elasticsearch client: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public void index(String indexName, String docId, Doc doc, PipelineContext context) throws FsCrawlerPluginException {
        // Determine the index name: use provided index, then plugin config, then context
        String targetIndex = indexName;
        if (targetIndex == null && index != null) {
            targetIndex = index;
        }
        if (targetIndex == null && context != null && context.getIndex() != null) {
            targetIndex = context.getIndex();
        }
        if (targetIndex == null) {
            throw new FsCrawlerPluginException("No index name specified for document " + docId);
        }
        
        logger.debug("Indexing document [{}] to index [{}]", docId, targetIndex);
        
        try {
            client.index(targetIndex, docId, doc, pipeline);
        } catch (Exception e) {
            throw new FsCrawlerPluginException("Failed to index document " + docId + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String indexName, String docId) throws FsCrawlerPluginException {
        // Determine the index name
        String targetIndex = indexName;
        if (targetIndex == null && index != null) {
            targetIndex = index;
        }
        if (targetIndex == null) {
            throw new FsCrawlerPluginException("No index name specified for deletion of document " + docId);
        }
        
        logger.debug("Deleting document [{}] from index [{}]", docId, targetIndex);
        
        try {
            client.delete(targetIndex, docId);
        } catch (Exception e) {
            throw new FsCrawlerPluginException("Failed to delete document " + docId + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void flush() throws FsCrawlerPluginException {
        logger.debug("Flushing Elasticsearch output plugin [{}]", id);
        
        if (client != null) {
            try {
                client.flush();
            } catch (Exception e) {
                throw new FsCrawlerPluginException("Failed to flush Elasticsearch client: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Build an internal FsSettings object from the plugin configuration.
     * This is needed because ElasticsearchClient expects FsSettings.
     */
    private FsSettings buildInternalSettings() {
        Elasticsearch esConfig = new Elasticsearch();
        esConfig.setUrls(urls);
        esConfig.setIndex(index);
        esConfig.setIndexFolder(indexFolder);
        esConfig.setBulkSize(bulkSize);
        
        if (flushInterval != null) {
            esConfig.setFlushInterval(TimeValue.parseTimeValue(flushInterval));
        }
        if (byteSize != null) {
            esConfig.setByteSize(ByteSizeValue.parseBytesSizeValue(byteSize));
        }
        
        esConfig.setApiKey(apiKey);
        esConfig.setUsername(username);
        esConfig.setPassword(password);
        esConfig.setCaCertificate(caCertificate);
        esConfig.setPipeline(pipeline);
        esConfig.setPathPrefix(pathPrefix);
        esConfig.setSslVerification(sslVerification);
        esConfig.setPushTemplates(pushTemplates);
        esConfig.setForcePushTemplates(forcePushTemplates);
        esConfig.setSemanticSearch(semanticSearch);
        
        // Build FsSettings with Elasticsearch configuration
        FsSettings settings = new FsSettings();
        settings.setName(id);
        settings.setElasticsearch(esConfig);
        
        // Copy Fs settings from global settings if available (needed for some template generation)
        if (globalSettings != null && globalSettings.getFs() != null) {
            settings.setFs(globalSettings.getFs());
        }
        
        return settings;
    }

    // ========== Getters for testing ==========

    /**
     * Get the Elasticsearch client (for testing purposes).
     */
    public IElasticsearchClient getClient() {
        return client;
    }

    /**
     * Get the configured URLs.
     */
    public List<String> getUrls() {
        return urls;
    }

    /**
     * Get the configured index name.
     */
    public String getIndex() {
        return index;
    }

    /**
     * Get the configured pipeline name.
     */
    public String getPipeline() {
        return pipeline;
    }
}
