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

import fr.pilato.elasticsearch.crawler.fs.util.FsCrawlerUtil;

import java.util.ArrayList;
import java.util.List;

public class Elasticsearch {

    private List<Node> nodes;
    private String index;
    private String type;
    private int bulkSize;
    private TimeValue flushInterval;
    private int waitSeconds;
    private int waitInterval;

    public Elasticsearch() {

    }

    private Elasticsearch(List<Node> nodes, String index, String type, int bulkSize, TimeValue flushInterval, int waitSeconds, int waitInterval) {
        this.nodes = nodes;
        this.index = index;
        this.type = type;
        this.bulkSize = bulkSize;
        this.flushInterval = flushInterval;
        this.waitSeconds = waitSeconds;
        this.waitInterval = waitInterval;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final Elasticsearch DEFAULT = Elasticsearch.builder()
            .addNode(Elasticsearch.Node.builder().setHost("127.0.0.1").setPort(9200).build())
            .build();


    public static class Node {

        public Node() {

        }

        private Node(String host, int port) {
            this.host = host;
            this.port = port;
            this.active = false;
        }

        private String host;
        private int port;
        private boolean active;

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

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String host;
            private int port;

            public Builder setHost(String host) {
                this.host = host;
                return this;
            }

            public Builder setPort(int port) {
                this.port = port;
                return this;
            }

            public Node build() {
                return new Node(host, port);
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
            final StringBuffer sb = new StringBuffer("Node{");
            sb.append("active=").append(active);
            sb.append(", host='").append(host).append('\'');
            sb.append(", port=").append(port);
            sb.append('}');
            return sb.toString();
        }
    }


    public List<Node> getNodes() {
        return nodes;
    }

    public void setNodes(List<Node> nodes) {
        this.nodes = nodes;
    }

    public String getIndex() {
        return index;
    }

    public void setIndex(String index) {
        this.index = index;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getBulkSize() {
        return bulkSize;
    }

    public void setBulkSize(int bulkSize) {
        this.bulkSize = bulkSize;
    }

    public TimeValue getFlushInterval() {
        return flushInterval;
    }

    public void setFlushInterval(TimeValue flushInterval) {
        this.flushInterval = flushInterval;
    }

    public int getWaitSeconds() {
        return waitSeconds;
    }

    public void setWaitSeconds(int waitSeconds) {
        this.waitSeconds = waitSeconds;
    }

    public int getWaitInterval() {
        return waitInterval;
    }

    public void setWaitInterval(int waitInterval) {
        this.waitInterval = waitInterval;
    }



    public static class Builder {
        private List<Node> nodes;
        private String index;
        private String type = FsCrawlerUtil.INDEX_TYPE_DOC;
        private int bulkSize = 100;
        private TimeValue flushInterval = TimeValue.timeValueSeconds(5);
        private int waitSeconds = 1;
        private int waitInterval = 1;

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

        public Builder setType(String type) {
            this.type = type;
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

        public Builder setWaitSeconds(int waitSeconds){
            this.waitSeconds = waitSeconds;
            return this;
        }

        public Builder setWaitInterval(int waitInterval){
            this.waitInterval = waitInterval;
            return this;
        }

        public Elasticsearch build() {
            return new Elasticsearch(nodes, index, type, bulkSize, flushInterval, waitSeconds, waitInterval);
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
        if (type != null ? !type.equals(that.type) : that.type != null) return false;
        return !(flushInterval != null ? !flushInterval.equals(that.flushInterval) : that.flushInterval != null);

    }

    @Override
    public int hashCode() {
        int result = nodes != null ? nodes.hashCode() : 0;
        result = 31 * result + (index != null ? index.hashCode() : 0);
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + bulkSize;
        result = 31 * result + (flushInterval != null ? flushInterval.hashCode() : 0);
        return result;
    }
}
