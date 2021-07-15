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
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;

public class WorkplaceSearch {

    protected static final Logger logger = LogManager.getLogger(WorkplaceSearch.class);
    public static final ServerUrl DEFAULT_SERVER = new ServerUrl("http://127.0.0.1:3002");
    public static final String DEFAULT_URL_PREFIX = "http://127.0.0.1";

    private ServerUrl server = DEFAULT_SERVER;
    private String id;
    private String username;
    @JsonIgnore
    private String password;
    private String urlPrefix = DEFAULT_URL_PREFIX;
    private int bulkSize = 100;
    private TimeValue flushInterval = TimeValue.timeValueSeconds(5);
    private String name;

    public WorkplaceSearch() {

    }

    public WorkplaceSearch(ServerUrl server, String id, String name, String username, String password, String urlPrefix,
                           int bulkSize, TimeValue flushInterval) {
        this.server = server;
        this.id = id;
        this.name = name;
        this.username = username;
        this.password = password;
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

    /**
     * The id of the source. If not provided, the id will be automatically set
     * by fetching the first custom source which name is equal to the fscrawler job name.
     * If no custom source is found, a new one will be automatically created
     * @return the id of the source if known
     */
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public static class Builder {
        private ServerUrl server = DEFAULT_SERVER;
        private String id;
        private String name;
        private String username;
        private String password;
        private String urlPrefix = DEFAULT_URL_PREFIX;
        private int bulkSize = 100;
        private TimeValue flushInterval = TimeValue.timeValueSeconds(5);

        public Builder setServer(ServerUrl server) {
            this.server = server;
            return this;
        }

        /**
         * The id of the source. If not provided, the id will be automatically set
         * by fetching the first custom source which name is equal to source name.
         * If no custom source is found, a new one will be automatically created
         * @param id id of the source if known
         * @return the builder
         */
        public Builder setId(String id) {
            this.id = id;
            return this;
        }

        /**
         * Provide the custom source name. It will be used if the id is not provided.
         * It defaults to "Local files from DIR" where DIR is the fs.url property.
         * @param name The custom source name
         * @return the builder
         */
        public Builder setName(String name) {
            this.name = name;
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
            return new WorkplaceSearch(server, id, name, username, password, urlPrefix, bulkSize, flushInterval);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkplaceSearch that = (WorkplaceSearch) o;
        return Objects.equals(server, that.server) &&
                Objects.equals(id, that.id) &&
                Objects.equals(username, that.username) &&
                Objects.equals(urlPrefix, that.urlPrefix);
    }

    @Override
    public int hashCode() {
        return Objects.hash(server, id, username, urlPrefix);
    }

    @Override
    public String toString() {
        return "WorkplaceSearch{" + "server=" + server +
                ", id='" + id + '\'' +
                ", username='" + username + '\'' +
                ", urlPrefix='" + urlPrefix + '\'' +
                '}';
    }
}
