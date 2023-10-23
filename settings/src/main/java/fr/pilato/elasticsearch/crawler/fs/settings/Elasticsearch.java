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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Elasticsearch {

    protected static final Logger logger = LogManager.getLogger(Elasticsearch.class);
    public static final ServerUrl NODE_DEFAULT = new ServerUrl("http://127.0.0.1:9200");

    private List<ServerUrl> nodes = Collections.singletonList(NODE_DEFAULT);
    private String index;
    private String indexFolder;
    private int bulkSize = 100;
    private TimeValue flushInterval = TimeValue.timeValueSeconds(5);
    private ByteSizeValue byteSize = new ByteSizeValue(10, ByteSizeUnit.MB);
    private String username;
    @JsonIgnore
    private String password;
    private String pipeline;
    private String pathPrefix;
    private boolean sslVerification = true;

    public Elasticsearch() {

    }

    private Elasticsearch(List<ServerUrl> nodes, String index, String indexFolder, int bulkSize,
                          TimeValue flushInterval, ByteSizeValue byteSize, String username, String password, String pipeline,
                          String pathPrefix, boolean sslVerification) {
        this.nodes = nodes;
        this.index = index;
        this.indexFolder = indexFolder;
        this.bulkSize = bulkSize;
        this.flushInterval = flushInterval;
        this.byteSize = byteSize;
        this.username = username;
        this.password = password;
        this.pipeline = pipeline;
        this.pathPrefix = pathPrefix;
        this.sslVerification = sslVerification;
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

    public TimeValue getFlushInterval() {
        return flushInterval;
    }

    public ByteSizeValue getByteSize() {
        return byteSize;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @JsonIgnore
    public String getPassword() {
        return password;
    }

    public boolean getSslVerification() {
        return sslVerification;
    }

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

    public void setSslVerification(boolean sslVerification) {
        this.sslVerification = sslVerification;
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

        public Builder setUsername(String username) {
            this.username = username;
            return this;
        }

        public Builder setPassword(String password) {
            this.password = password;
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

        public Elasticsearch build() {
            return new Elasticsearch(nodes, index, indexFolder, bulkSize, flushInterval, byteSize, username, password, pipeline, pathPrefix, sslVerification);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Elasticsearch that = (Elasticsearch) o;

        if (bulkSize != that.bulkSize) return false;
        if (!Objects.equals(nodes, that.nodes)) return false;
        if (!Objects.equals(index, that.index)) return false;
        if (!Objects.equals(indexFolder, that.indexFolder)) return false;
        if (!Objects.equals(username, that.username)) return false;
        // We can't really test the password as it may be obfuscated
        if (!Objects.equals(pipeline, that.pipeline)) return false;
        if (!Objects.equals(pathPrefix, that.pathPrefix)) return false;
        if (!Objects.equals(sslVerification, that.sslVerification)) return false;
        return Objects.equals(flushInterval, that.flushInterval);

    }

    @Override
    public int hashCode() {
        int result = nodes != null ? nodes.hashCode() : 0;
        result = 31 * result + (index != null ? index.hashCode() : 0);
        result = 31 * result + (indexFolder != null ? indexFolder.hashCode() : 0);
        result = 31 * result + (username != null ? username.hashCode() : 0);
        result = 31 * result + (pipeline != null ? pipeline.hashCode() : 0);
        result = 31 * result + (pathPrefix != null ? pathPrefix.hashCode() : 0);
        result = 31 * result + bulkSize;
        result = 31 * result + (flushInterval != null ? flushInterval.hashCode() : 0);
        result = 31 * result + (sslVerification? 1: 0);
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
                '}';
    }
}
