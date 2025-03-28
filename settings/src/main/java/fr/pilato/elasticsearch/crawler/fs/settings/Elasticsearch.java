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

package fr.pilato.elasticsearch.crawler.fs.settings;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import fr.pilato.elasticsearch.crawler.fs.framework.ByteSizeUnit;
import fr.pilato.elasticsearch.crawler.fs.framework.ByteSizeValue;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import jakarta.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Elasticsearch {
    private static final Logger logger = LogManager.getLogger();
    public static final ServerUrl NODE_DEFAULT = new ServerUrl("https://127.0.0.1:9200");

    @Nullable private List<ServerUrl> nodes = Collections.singletonList(NODE_DEFAULT);
    @Nullable private String index;
    @Nullable private String indexFolder;
    @Nullable private Integer bulkSize = 100;
    @Nullable private TimeValue flushInterval = TimeValue.timeValueSeconds(5);
    @Nullable private ByteSizeValue byteSize = new ByteSizeValue(10, ByteSizeUnit.MB);
    @Nullable private String apiKey;

    /**
     * Username
     * @deprecated Use apiKey or accessToken instead
     */
    @Deprecated
    @Nullable private String username;
    /**
     * Password
     * @deprecated Use apiKey or accessToken instead
     */
    @Deprecated
    @JsonIgnore
    @Nullable private String password;
    @Nullable private String caCertificate;
    @Nullable private String pipeline;
    @Nullable private String pathPrefix;
    @Nullable private Boolean sslVerification = true;
    @Nullable private Boolean pushTemplates = true;
    @Nullable private Boolean semanticSearch = true;

    public Elasticsearch() {

    }

    private Elasticsearch(List<ServerUrl> nodes, String index, String indexFolder, int bulkSize,
                          TimeValue flushInterval, ByteSizeValue byteSize, String apiKey,
                          String username, String password, String pipeline,
                          String pathPrefix, boolean sslVerification,
                          String caCertificate,
                          boolean pushTemplates, boolean semanticSearch) {
        this.nodes = nodes;
        this.index = index;
        this.indexFolder = indexFolder;
        this.bulkSize = bulkSize;
        this.flushInterval = flushInterval;
        this.byteSize = byteSize;
        this.apiKey = apiKey;
        this.username = username;
        this.password = password;
        this.pipeline = pipeline;
        this.pathPrefix = pathPrefix;
        this.sslVerification = sslVerification;
        this.caCertificate = caCertificate;
        this.pushTemplates = pushTemplates;
        this.semanticSearch = semanticSearch;
    }

    public static Builder builder() {
        return new Builder();
    }

    // Using here a method instead of a constant as sadly FSCrawlerValidator can modify this object
    // TODO fix that: a validator should not modify the original object but return a modified copy
    public static Elasticsearch DEFAULT() {
        return Elasticsearch.builder().build();
    }

    public List<ServerUrl> getNodes() {
        return nodes;
    }

    public void setNodes(@Nullable List<ServerUrl> nodes) {
        this.nodes = nodes;
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

    public int getBulkSize() {
        return bulkSize;
    }

    public void setBulkSize(@Nullable Integer bulkSize) {
        this.bulkSize = bulkSize;
    }

    public TimeValue getFlushInterval() {
        return flushInterval;
    }

    public void setFlushInterval(@Nullable TimeValue flushInterval) {
        this.flushInterval = flushInterval;
    }

    public ByteSizeValue getByteSize() {
        return byteSize;
    }

    public void setByteSize(@Nullable ByteSizeValue byteSize) {
        this.byteSize = byteSize;
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

    /**
     * Provide the username to connect to Elasticsearch
     * @param username The username
     * @deprecated Use {@link #setApiKey(String)} instead
     */
    @Deprecated
    public void setUsername(String username) {
        this.username = username;
    }

    @JsonIgnore
    public String getPassword() {
        return password;
    }

    /**
     * Provide the password to connect to Elasticsearch
     * @param password The password
     * @deprecated Use {@link #setApiKey(String)} instead
     */
    @Deprecated
    @JsonProperty
    public void setPassword(String password) {
        this.password = password;
    }

    public String getPipeline() {
        return pipeline;
    }

    public void setPipeline(String pipeline) {
        this.pipeline = pipeline;
    }

    public String getPathPrefix() {
        return pathPrefix;
    }

    public void setPathPrefix(String pathPrefix) {
        this.pathPrefix = pathPrefix;
    }

    public boolean isSslVerification() {
        return sslVerification;
    }

    public void setSslVerification(boolean sslVerification) {
        this.sslVerification = sslVerification;
    }

    public boolean isPushTemplates() {
        return pushTemplates;
    }

    public void setPushTemplates(boolean pushTemplates) {
        this.pushTemplates = pushTemplates;
    }

    public String getCaCertificate() {
        return caCertificate;
    }

    public void setCaCertificate(String caCertificate) {
        this.caCertificate = caCertificate;
    }

    public boolean isSemanticSearch() {
        return semanticSearch;
    }

    public void setSemanticSearch(boolean semanticSearch) {
        this.semanticSearch = semanticSearch;
    }

    @SuppressWarnings("UnusedReturnValue")
    public static class Builder {
        private List<ServerUrl> nodes = Collections.singletonList(NODE_DEFAULT);
        private String index;
        private String indexFolder;
        private int bulkSize = 100;
        private TimeValue flushInterval = TimeValue.timeValueSeconds(5);
        private ByteSizeValue byteSize = new ByteSizeValue(10, ByteSizeUnit.MB);
        private String username = null;
        private String password = null;
        private String pipeline = null;
        private String pathPrefix = null;
        private boolean sslVerification = true;
        private String caCertificate;
        private boolean pushTemplates = true;
        private String apiKey = null;
        private boolean semanticSearch = true;

        public Builder setNodes(List<ServerUrl> nodes) {
            this.nodes = nodes;
            return this;
        }

        public Builder setIndex(String index) {
            this.index = index;
            return this;
        }

        public Builder setIndexFolder(String indexFolder) {
            this.indexFolder = indexFolder;
            return this;
        }

        public Builder setBulkSize(int bulkSize) {
            this.bulkSize = bulkSize;
            return this;
        }

        public Builder setFlushInterval(TimeValue flushInterval) {
            this.flushInterval = flushInterval;
            return this;
        }

        public Builder setByteSize(ByteSizeValue byteSize) {
            this.byteSize = byteSize;
            return this;
        }

        public Builder setApiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Set the username (for tests only)
         * @param username  Username
         * @return the current builder
         * @deprecated Use {@link #setApiKey(String)} instead
         */
        @Deprecated
        public Builder setUsername(String username) {
            logger.warn("username is deprecated. Use apiKey instead.");
            this.username = username;
            return this;
        }

        /**
         * Set the password (for tests only)
         * @param password  Password
         * @return the current builder
         * @deprecated Use {@link #setApiKey(String)} instead
         */
        @Deprecated
        public Builder setPassword(String password) {
            logger.warn("password is deprecated. Use apiKey instead.");
            this.password = password;
            return this;
        }

        /**
         * Set the credentials depending on what is available.
         * This is a helper method to set either apiKey or username/password.
         * @param apiKey        API Key (omitted if null)
         * @param username      Username (omitted if null)
         * @param password      Password (omitted if null)
         * @return the current builder
         */
        public Builder setCredentials(String apiKey, String username, String password) {
            if (apiKey != null) {
                logger.trace("using api key [{}]", apiKey);
                this.setApiKey(apiKey);
            } else if (username != null && password != null) {
                logger.trace("using login/password [{}]/[{}]", username, password);
                this.setUsername(username);
                this.setPassword(password);
            }

            return this;
        }

        public Builder setPipeline(String pipeline) {
            this.pipeline = pipeline;
            return this;
        }

        public Builder setPathPrefix(String pathPrefix) {
            this.pathPrefix = pathPrefix;
            return this;
        }

        public Builder setSslVerification(boolean sslVerification) {
            this.sslVerification = sslVerification;
            return this;
        }

        public Builder setCaCertificate(String caCertificate) {
            this.caCertificate = caCertificate;
            return this;
        }

        public Builder setPushTemplates(boolean pushTemplates) {
            this.pushTemplates = pushTemplates;
            return this;
        }

        public Builder setSemanticSearch(boolean semanticSearch) {
            this.semanticSearch = semanticSearch;
            return this;
        }

        public Elasticsearch build() {
            return new Elasticsearch(nodes, index, indexFolder, bulkSize, flushInterval, byteSize, apiKey,
                    username, password,
                    pipeline, pathPrefix,
                    sslVerification, caCertificate,
                    pushTemplates, semanticSearch);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Elasticsearch that = (Elasticsearch) o;

        if (!Objects.equals(bulkSize, that.bulkSize)) return false;
        if (!Objects.equals(nodes, that.nodes)) return false;
        if (!Objects.equals(index, that.index)) return false;
        if (!Objects.equals(indexFolder, that.indexFolder)) return false;
        if (!Objects.equals(apiKey, that.apiKey)) return false;
        if (!Objects.equals(username, that.username)) return false;
        // We can't really test the password as it may be obfuscated
        if (!Objects.equals(pipeline, that.pipeline)) return false;
        if (!Objects.equals(pathPrefix, that.pathPrefix)) return false;
        if (!Objects.equals(sslVerification, that.sslVerification)) return false;
        if (!Objects.equals(caCertificate, that.caCertificate)) return false;
        if (!Objects.equals(pushTemplates, that.pushTemplates)) return false;
        return Objects.equals(flushInterval, that.flushInterval);

    }

    @Override
    public int hashCode() {
        int result = nodes != null ? nodes.hashCode() : 0;
        result = 31 * result + (index != null ? index.hashCode() : 0);
        result = 31 * result + (indexFolder != null ? indexFolder.hashCode() : 0);
        result = 31 * result + (username != null ? username.hashCode() : 0);
        result = 31 * result + (apiKey != null ? apiKey.hashCode() : 0);
        result = 31 * result + (pipeline != null ? pipeline.hashCode() : 0);
        result = 31 * result + (pathPrefix != null ? pathPrefix.hashCode() : 0);
        result = 31 * result + bulkSize;
        result = 31 * result + (flushInterval != null ? flushInterval.hashCode() : 0);
        result = 31 * result + (caCertificate != null ? caCertificate.hashCode() : 0);
        result = 31 * result + (sslVerification? 1: 0);
        result = 31 * result + (pushTemplates? 1: 0);
        return result;
    }

    @Override
    public String toString() {
        return "Elasticsearch{" + "nodes=" + nodes +
                ", index='" + index + '\'' +
                ", indexFolder='" + indexFolder + '\'' +
                ", bulkSize=" + bulkSize +
                ", flushInterval=" + flushInterval +
                ", byteSize=" + byteSize +
                ", username='" + username + '\'' +
                ", pipeline='" + pipeline + '\'' +
                ", pathPrefix='" + pathPrefix + '\'' +
                ", sslVerification='" + sslVerification + '\'' +
                ", caCertificatePath='" + caCertificate + '\'' +
                ", pushTemplates='" + pushTemplates + '\'' +
                '}';
    }
}
