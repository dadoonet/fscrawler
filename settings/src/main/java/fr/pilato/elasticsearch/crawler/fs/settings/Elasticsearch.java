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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class Elasticsearch {

    protected static final Logger logger = LogManager.getLogger(Elasticsearch.class);

    public Elasticsearch() {

    }

    private Elasticsearch(List<Node> nodes, String index, String indexFolder, int bulkSize,
                          TimeValue flushInterval, ByteSizeValue byteSize, String username, String password, String pipeline) {
        this.nodes = nodes;
        this.index = index;
        this.indexFolder = indexFolder;
        this.bulkSize = bulkSize;
        this.flushInterval = flushInterval;
        this.byteSize = byteSize;
        this.username = username;
        this.password = password;
        this.pipeline = pipeline;
    }

    public static Builder builder() {
        return new Builder();
    }

    // Using here a method instead of a constant as sadly FSCrawlerValidator can modify this object
    // TODO fix that: a validator should not modify the original object but return a modified copy
    public static Elasticsearch DEFAULT() {
        return Elasticsearch.builder()
                .addNode(Node.DEFAULT)
                .build();
    }

    public static class Node {

        public enum Scheme {
            HTTP,
            HTTPS;

            public static Scheme parse(String value) {
                return valueOf(value.toUpperCase(Locale.ROOT));
            }

            public String toLowerCase() {
                return this.toString().toLowerCase(Locale.ROOT);
            }
        }

        public static final Node DEFAULT = new Node("http://127.0.0.1:9200");

        public Node() {

        }

        @Deprecated
        public Node(String host, int port, Scheme scheme) {
            this.host = host;
            this.port = port;
            this.scheme = scheme;
        }

        public Node(String urlOrCloudId) {
            // We check if the String starts with https:// or http://
            // In which case this is a URL, otherwise its a cloud id
            String asLowerCase = urlOrCloudId.toLowerCase();
            if (asLowerCase.startsWith("http://") || asLowerCase.startsWith("https://")) {
                this.url = urlOrCloudId;
            } else {
                this.cloudId = urlOrCloudId;
            }
        }

        private String url;
        private String cloudId;

        @Deprecated
        private String host;
        @Deprecated
        private Integer port;
        @Deprecated
        private Scheme scheme = Scheme.HTTP;

        public void setHost(String host) {
            this.host = host;
        }

        public void setPort(Integer port) {
            this.port = port;
        }

        public void setScheme(Scheme scheme) {
            this.scheme = scheme;
        }

        public String getCloudId() {
            return cloudId;
        }

        public void setCloudId(String cloudId) {
            this.cloudId = cloudId;
        }

        public String getUrl() {
            // If we are using deprecated settings, let's warn the user to move to url param
            if (host != null || port != null) {
                String tmpUrl = scheme.toLowerCase() + "://" + host + ":" + port;
                logger.warn("elasticsearch.nodes.[scheme, host, port] has been deprecated and will be removed in a coming version. " +
                        "Use elasticsearch.nodes: [ { \"url\": \"{}\" } ] instead", tmpUrl);
                return tmpUrl;
            }
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Node node = (Node) o;
            return Objects.equals(cloudId, node.cloudId) &&
                    Objects.equals(url, node.url);
        }

        @Override
        public int hashCode() {
            return Objects.hash(cloudId, url);
        }

        @Override
        public String toString() {
            return "Node{" + "cloudId='" + cloudId + '\'' +
                    ", url=" + url +
                    '}';
        }
    }

    private List<Node> nodes;
    private String index;
    private String indexFolder;
    private int bulkSize = 100;
    private TimeValue flushInterval = TimeValue.timeValueSeconds(5);
    private ByteSizeValue byteSize = new ByteSizeValue(10, ByteSizeUnit.MB);
    private String username;
    @JsonIgnore
    private String password;
    private String pipeline;

    public List<Node> getNodes() {
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

    public static class Builder {
        private List<Node> nodes;
        private String index;
        private String indexFolder;
        private int bulkSize = 100;
        private TimeValue flushInterval = TimeValue.timeValueSeconds(5);
        private ByteSizeValue byteSize = new ByteSizeValue(10, ByteSizeUnit.MB);
        private String username = null;
        private String password = null;
        private String pipeline = null;

        public Builder setNodes(List<Node> nodes) {
            this.nodes = nodes;
            return this;
        }

        public Builder addNode(Node node) {
            if (this.nodes == null) {
                this.nodes = new ArrayList<>();
            }
            this.nodes.add(node);
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

        public Elasticsearch build() {
            return new Elasticsearch(nodes, index, indexFolder, bulkSize, flushInterval, byteSize, username, password, pipeline);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Elasticsearch that = (Elasticsearch) o;

        if (bulkSize != that.bulkSize) return false;
        if (nodes != null ? !nodes.equals(that.nodes) : that.nodes != null) return false;
        if (index != null ? !index.equals(that.index) : that.index != null) return false;
        if (indexFolder != null ? !indexFolder.equals(that.indexFolder) : that.indexFolder != null) return false;
        if (username != null ? !username.equals(that.username) : that.username != null) return false;
        // We can't really test the password as it may be obfuscated
        if (pipeline != null ? !pipeline.equals(that.pipeline) : that.pipeline != null) return false;
        return !(flushInterval != null ? !flushInterval.equals(that.flushInterval) : that.flushInterval != null);

    }

    @Override
    public int hashCode() {
        int result = nodes != null ? nodes.hashCode() : 0;
        result = 31 * result + (index != null ? index.hashCode() : 0);
        result = 31 * result + (indexFolder != null ? indexFolder.hashCode() : 0);
        result = 31 * result + (username != null ? username.hashCode() : 0);
        result = 31 * result + (pipeline != null ? pipeline.hashCode() : 0);
        result = 31 * result + bulkSize;
        result = 31 * result + (flushInterval != null ? flushInterval.hashCode() : 0);
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
                '}';
    }
}
