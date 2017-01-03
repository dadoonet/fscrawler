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

import java.util.Locale;

import static fr.pilato.elasticsearch.crawler.fs.util.FsCrawlerUtil.buildUrl;

public class Rest {

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

    public static final Rest DEFAULT = Rest.builder().build();

    public Rest() {

    }

    private Rest(Scheme scheme, String host, int port, String endpoint) {
        this.scheme = scheme;
        this.host = host;
        this.port = port;
        this.endpoint = endpoint;
    }

    private Scheme scheme;
    private String host;
    private int port;
    private String endpoint;

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

    public Scheme getScheme() {
        return scheme;
    }

    public void setScheme(Scheme scheme) {
        this.scheme = scheme;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    /**
     * Get the server URL: Scheme://host:port/endpoint
     * @return the server URL
     */
    public String url() {
        return buildUrl(scheme.toLowerCase(), host, port) + "/" + endpoint;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Scheme scheme = Scheme.HTTP;
        private String host = "127.0.0.1";
        private int port = 8080;
        private String endpoint = "fscrawler";

        public Builder setScheme(Scheme scheme) {
            this.scheme = scheme;
            return this;
        }

        public Builder setHost(String host) {
            this.host = host;
            return this;
        }

        public Builder setPort(int port) {
            this.port = port;
            return this;
        }

        public Builder setEndpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public Rest build() {
            return new Rest(scheme, host, port, endpoint);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Rest node = (Rest) o;

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
        return url();
    }
}
