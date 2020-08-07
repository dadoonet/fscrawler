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

import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;

public class WorkplaceSearch {

    protected static final Logger logger = LogManager.getLogger(WorkplaceSearch.class);
    public static final ServerUrl DEFAULT_SERVER = new ServerUrl("http://127.0.0.1:3002");
    public static final String DEFAULT_URL_PREFIX = "http://127.0.0.1";

    private ServerUrl server = DEFAULT_SERVER;
    private String key;
    private String accessToken;
    private String urlPrefix = DEFAULT_URL_PREFIX;
    private int bulkSize = 100;
    private TimeValue flushInterval = TimeValue.timeValueSeconds(5);

    public WorkplaceSearch() {

    }

    public WorkplaceSearch(ServerUrl server, String key, String accessToken, String urlPrefix,
                           int bulkSize, TimeValue flushInterval) {
        this.server = server;
        this.key = key;
        this.accessToken = accessToken;
        this.urlPrefix = urlPrefix;
        this.bulkSize = bulkSize;
        this.flushInterval = flushInterval;
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

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getUrlPrefix() {
        return urlPrefix;
    }

    public void setUrlPrefix(String urlPrefix) {
        this.urlPrefix = urlPrefix;
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

    public static class Builder {
        private ServerUrl server = DEFAULT_SERVER;
        private String key;
        private String accessToken;
        private String urlPrefix = DEFAULT_URL_PREFIX;
        private int bulkSize = 100;
        private TimeValue flushInterval = TimeValue.timeValueSeconds(5);

        public Builder setServer(ServerUrl server) {
            this.server = server;
            return this;
        }

        public Builder setKey(String key) {
            this.key = key;
            return this;
        }

        public Builder setAccessToken(String accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        public Builder setUrlPrefix(String urlPrefix) {
            this.urlPrefix = urlPrefix;
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

        public WorkplaceSearch build() {
            return new WorkplaceSearch(server, key, accessToken, urlPrefix, bulkSize, flushInterval);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkplaceSearch that = (WorkplaceSearch) o;
        return Objects.equals(server, that.server) &&
                Objects.equals(key, that.key) &&
                Objects.equals(accessToken, that.accessToken) &&
                Objects.equals(urlPrefix, that.urlPrefix);
    }

    @Override
    public int hashCode() {
        return Objects.hash(server, key, accessToken, urlPrefix);
    }

    @Override
    public String toString() {
        return "WorkplaceSearch{" + "server=" + server +
                ", key='" + key + '\'' +
                ", accessToken='" + accessToken + '\'' +
                ", urlPrefix='" + urlPrefix + '\'' +
                '}';
    }
}
