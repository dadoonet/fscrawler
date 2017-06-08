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

package fr.pilato.elasticsearch.crawler.fs.meta.settings;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Elasticsearch {

    public Elasticsearch() {

    }

    private Elasticsearch(List<Node> nodes, String index, String indexFolder, int bulkSize,
                          TimeValue flushInterval, String username, String password, String pipeline) {
        this.nodes = nodes;
        this.index = index;
        this.indexFolder = indexFolder;
        this.bulkSize = bulkSize;
        this.flushInterval = flushInterval;
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

        public static final Node DEFAULT = Node.builder().setHost("127.0.0.1").setPort(9200).setScheme(Scheme.HTTP).build();

        public Node() {

        }

        private Node(String host, int port, Scheme scheme) {
            this.host = host;
            this.port = port;
            this.active = false;
            this.scheme = scheme;
        }

        private String host;
        private int port;
        private boolean active;
        private Scheme scheme;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public boolean active() {
            return active;
        }

        public void active(boolean active) {
            this.active = active;
        }

        public Scheme getScheme() {
            return scheme;
        }

        public void setScheme(Scheme scheme) {
            this.scheme = scheme;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String host;
            private int port;
            private Scheme scheme = Scheme.HTTP;

            public Builder setHost(String host) {
                this.host = host;
                return this;
            }

            public Builder setPort(int port) {
                this.port = port;
                return this;
            }

            public Builder setScheme(Scheme scheme) {
                this.scheme = scheme;
                return this;
            }

            public Node build() {
                return new Node(host, port, scheme);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Node node = (Node) o;

            if (port != node.port) return false;
            return !(host != null ? !host.equals(node.host) : node.host != null);

        }

        @Override
        public int hashCode() {
            int result = host != null ? host.hashCode() : 0;
            result = 31 * result + port;
            return result;
        }

        @Override
        public String toString() {
            String sb = "Node{" + "active=" + active +
                    ", host='" + host + '\'' +
                    ", port=" + port +
                    ", scheme=" + scheme +
                    '}';
            return sb;
        }
    }

    private List<Node> nodes;
    private String index;
    private String indexFolder;
    private int bulkSize = 100;
    private TimeValue flushInterval = TimeValue.timeValueSeconds(5);
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
            return new Elasticsearch(nodes, index, indexFolder, bulkSize, flushInterval, username, password, pipeline);
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
        result = 31 * result + (password != null ? password.hashCode() : 0);
        result = 31 * result + (pipeline != null ? pipeline.hashCode() : 0);
        result = 31 * result + bulkSize;
        result = 31 * result + (flushInterval != null ? flushInterval.hashCode() : 0);
        return result;
    }
}
