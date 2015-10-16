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

import fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl.PROTOCOL;

public class Server {

    public Server() {

    }

    private Server(String hostname, int port, String username, String password, String protocol, String pemPath) {
        this.hostname = hostname;
        this.port = port;
        this.username = username;
        this.password = password;
        this.protocol = protocol;
        this.pemPath = pemPath;
    }

    private String hostname;
    private int port;
    private String username;
    private String password;
    private String protocol;
    private String pemPath;

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
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

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getPemPath() {
        return pemPath;
    }

    public void setPemPath(String pemPath) {
        this.pemPath = pemPath;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String hostname = null;
        private int port = PROTOCOL.SSH_PORT;
        private String username = null;
        private String password = null;
        private String protocol = PROTOCOL.LOCAL;
        private String pemPath = null;

        public Builder setHostname(String hostname) {
            this.hostname = hostname;
            return this;
        }

        public Builder setPort(int port) {
            this.port = port;
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

        public Builder setProtocol(String protocol) {
            this.protocol = protocol;
            return this;
        }

        public Builder setPemPath(String pemPath) {
            this.pemPath = pemPath;
            return this;
        }

        public Server build() {
            return new Server(hostname, port, username, password, protocol, pemPath);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Server server = (Server) o;

        if (port != server.port) return false;
        if (hostname != null ? !hostname.equals(server.hostname) : server.hostname != null) return false;
        if (username != null ? !username.equals(server.username) : server.username != null) return false;
        if (password != null ? !password.equals(server.password) : server.password != null) return false;
        if (protocol != null ? !protocol.equals(server.protocol) : server.protocol != null) return false;
        return !(pemPath != null ? !pemPath.equals(server.pemPath) : server.pemPath != null);

    }

    @Override
    public int hashCode() {
        int result = hostname != null ? hostname.hashCode() : 0;
        result = 31 * result + port;
        result = 31 * result + (username != null ? username.hashCode() : 0);
        result = 31 * result + (password != null ? password.hashCode() : 0);
        result = 31 * result + (protocol != null ? protocol.hashCode() : 0);
        result = 31 * result + (pemPath != null ? pemPath.hashCode() : 0);
        return result;
    }
}
