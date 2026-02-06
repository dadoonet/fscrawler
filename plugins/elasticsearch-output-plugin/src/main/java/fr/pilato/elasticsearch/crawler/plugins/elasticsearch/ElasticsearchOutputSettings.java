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

import fr.pilato.elasticsearch.crawler.fs.settings.plugin.PluginSettings;

import java.util.List;
import java.util.Objects;

/**
 * Settings for the Elasticsearch output plugin.
 * <p>
 * Example configuration file (01-elasticsearch.yaml):
 * <pre>
 * version: 1
 * id: es-output
 * type: elasticsearch
 * urls:
 *   - https://localhost:9200
 * index: my_documents
 * index_folder: my_documents_folder
 * username: elastic
 * password: changeme
 * # OR api_key: base64encodedkey
 * bulk_size: 100
 * flush_interval: 5s
 * byte_size: 10mb
 * ssl_verification: true
 * ca_certificate: /path/to/ca.crt
 * push_templates: true
 * </pre>
 */
public class ElasticsearchOutputSettings extends PluginSettings {

    public static final int CURRENT_VERSION = 1;

    // ========== Conditional execution ==========

    /**
     * MVEL condition for when this output should be used.
     */
    private String when;

    // ========== Connection settings ==========

    /**
     * Elasticsearch URLs.
     */
    private List<String> urls;

    /**
     * API key for authentication (alternative to username/password).
     */
    private String apiKey;

    /**
     * Username for authentication.
     */
    private String username;

    /**
     * Password for authentication.
     */
    private String password;

    /**
     * Whether to verify SSL certificates.
     */
    private Boolean sslVerification;

    /**
     * Path to CA certificate for SSL verification.
     */
    private String caCertificate;

    /**
     * Path prefix for Elasticsearch API (for reverse proxies).
     */
    private String pathPrefix;

    // ========== Index settings ==========

    /**
     * Index name for documents.
     */
    private String index;

    /**
     * Index name for folder metadata.
     */
    private String indexFolder;

    /**
     * Ingest pipeline to use.
     */
    private String pipeline;

    // ========== Bulk settings ==========

    /**
     * Number of documents per bulk request.
     */
    private Integer bulkSize;

    /**
     * How often to flush bulk requests (e.g., "5s", "1m").
     */
    private String flushInterval;

    /**
     * Maximum size of bulk request in bytes (e.g., "10mb").
     */
    private String byteSize;

    // ========== Template settings ==========

    /**
     * Whether to push index templates on startup.
     */
    private Boolean pushTemplates;

    /**
     * Whether to force push templates even if they exist.
     */
    private Boolean forcePushTemplates;

    /**
     * Whether to enable semantic search features.
     */
    private Boolean semanticSearch;

    // ========== PluginSettings implementation ==========

    @Override
    public String getExpectedType() {
        return "elasticsearch";
    }

    @Override
    public int getCurrentVersion() {
        return CURRENT_VERSION;
    }

    // ========== Getters and Setters ==========

    public String getWhen() {
        return when;
    }

    public void setWhen(String when) {
        this.when = when;
    }

    public List<String> getUrls() {
        return urls;
    }

    public void setUrls(List<String> urls) {
        this.urls = urls;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Boolean getSslVerification() {
        return sslVerification;
    }

    public void setSslVerification(Boolean sslVerification) {
        this.sslVerification = sslVerification;
    }

    public String getCaCertificate() {
        return caCertificate;
    }

    public void setCaCertificate(String caCertificate) {
        this.caCertificate = caCertificate;
    }

    public String getPathPrefix() {
        return pathPrefix;
    }

    public void setPathPrefix(String pathPrefix) {
        this.pathPrefix = pathPrefix;
    }

    public String getIndex() {
        return index;
    }

    public void setIndex(String index) {
        this.index = index;
    }

    public String getIndexFolder() {
        return indexFolder;
    }

    public void setIndexFolder(String indexFolder) {
        this.indexFolder = indexFolder;
    }

    public String getPipeline() {
        return pipeline;
    }

    public void setPipeline(String pipeline) {
        this.pipeline = pipeline;
    }

    public Integer getBulkSize() {
        return bulkSize;
    }

    public void setBulkSize(Integer bulkSize) {
        this.bulkSize = bulkSize;
    }

    public String getFlushInterval() {
        return flushInterval;
    }

    public void setFlushInterval(String flushInterval) {
        this.flushInterval = flushInterval;
    }

    public String getByteSize() {
        return byteSize;
    }

    public void setByteSize(String byteSize) {
        this.byteSize = byteSize;
    }

    public Boolean getPushTemplates() {
        return pushTemplates;
    }

    public void setPushTemplates(Boolean pushTemplates) {
        this.pushTemplates = pushTemplates;
    }

    public Boolean getForcePushTemplates() {
        return forcePushTemplates;
    }

    public void setForcePushTemplates(Boolean forcePushTemplates) {
        this.forcePushTemplates = forcePushTemplates;
    }

    public Boolean getSemanticSearch() {
        return semanticSearch;
    }

    public void setSemanticSearch(Boolean semanticSearch) {
        this.semanticSearch = semanticSearch;
    }

    // ========== Object methods ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        ElasticsearchOutputSettings that = (ElasticsearchOutputSettings) o;
        return Objects.equals(when, that.when) &&
                Objects.equals(urls, that.urls) &&
                Objects.equals(apiKey, that.apiKey) &&
                Objects.equals(username, that.username) &&
                Objects.equals(password, that.password) &&
                Objects.equals(sslVerification, that.sslVerification) &&
                Objects.equals(caCertificate, that.caCertificate) &&
                Objects.equals(pathPrefix, that.pathPrefix) &&
                Objects.equals(index, that.index) &&
                Objects.equals(indexFolder, that.indexFolder) &&
                Objects.equals(pipeline, that.pipeline) &&
                Objects.equals(bulkSize, that.bulkSize) &&
                Objects.equals(flushInterval, that.flushInterval) &&
                Objects.equals(byteSize, that.byteSize) &&
                Objects.equals(pushTemplates, that.pushTemplates) &&
                Objects.equals(forcePushTemplates, that.forcePushTemplates) &&
                Objects.equals(semanticSearch, that.semanticSearch);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), when, urls, apiKey, username, password,
                sslVerification, caCertificate, pathPrefix, index, indexFolder, pipeline,
                bulkSize, flushInterval, byteSize, pushTemplates, forcePushTemplates, semanticSearch);
    }

    @Override
    public String toString() {
        return "ElasticsearchOutputSettings{" +
                "version=" + getVersion() +
                ", id='" + getId() + '\'' +
                ", type='" + getType() + '\'' +
                ", urls=" + urls +
                ", index='" + index + '\'' +
                ", bulkSize=" + bulkSize +
                ", pushTemplates=" + pushTemplates +
                '}';
    }
}
