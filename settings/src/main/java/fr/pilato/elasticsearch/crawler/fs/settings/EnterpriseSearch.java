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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;

public class EnterpriseSearch {

    protected static final Logger logger = LogManager.getLogger(EnterpriseSearch.class);
    public static final ServerUrl SERVER_DEFAULT = new ServerUrl("http://localhost:3002");

    private ServerUrl server = SERVER_DEFAULT;
    private String contentSourceKey;
    private String accessToken;

    public EnterpriseSearch() {

    }

    public EnterpriseSearch(ServerUrl server, String contentSourceKey, String accessToken) {
        this.server = server;
        this.contentSourceKey = contentSourceKey;
        this.accessToken = accessToken;
    }

    public static Builder builder() {
        return new Builder();
    }

    public ServerUrl getServer() {
        return server;
    }

    public void setServer(ServerUrl server) {
        this.server = server;
    }

    public String getContentSourceKey() {
        return contentSourceKey;
    }

    public void setContentSourceKey(String contentSourceKey) {
        this.contentSourceKey = contentSourceKey;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public static class Builder {
        private ServerUrl server = SERVER_DEFAULT;
        private String contentSourceKey;
        private String accessToken;

        public Builder setServer(ServerUrl server) {
            this.server = server;
            return this;
        }

        public Builder setContentSourceKey(String contentSourceKey) {
            this.contentSourceKey = contentSourceKey;
            return this;
        }

        public Builder setAccessToken(String accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        public EnterpriseSearch build() {
            return new EnterpriseSearch(server, contentSourceKey, accessToken);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EnterpriseSearch that = (EnterpriseSearch) o;
        return Objects.equals(server, that.server) &&
                Objects.equals(contentSourceKey, that.contentSourceKey) &&
                Objects.equals(accessToken, that.accessToken);
    }

    @Override
    public int hashCode() {
        return Objects.hash(server, contentSourceKey, accessToken);
    }

    @Override
    public String toString() {
        return "EnterpriseSearch{" + "server=" + server +
                ", contentSourceKey='" + contentSourceKey + '\'' +
                ", accessToken='" + accessToken + '\'' +
                '}';
    }
}
