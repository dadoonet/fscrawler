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

public class FsSettings {

    private String name;
    private Fs fs;
    private Server server;
    private Elasticsearch elasticsearch;

    public FsSettings() {

    }

    private FsSettings(String name, Fs fs, Server server, Elasticsearch elasticsearch) {
        this.name = name;
        this.fs = fs;
        this.server = server;
        this.elasticsearch = elasticsearch;
    }

    public static Builder builder(String name) {
        return new Builder().setName(name);
    }

    public static class Builder {
        private String name;
        private Fs fs = Fs.DEFAULT;
        private Server server = null;
        private Elasticsearch elasticsearch = Elasticsearch.DEFAULT;

        private Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setFs(Fs fs) {
            this.fs = fs;
            return this;
        }

        public Builder setServer(Server server) {
            this.server = server;
            return this;
        }

        public Builder setElasticsearch(Elasticsearch elasticsearch) {
            this.elasticsearch = elasticsearch;
            return this;
        }

        public FsSettings build() {
            return new FsSettings(name, fs, server, elasticsearch);
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Fs getFs() {
        return fs;
    }

    public void setFs(Fs fs) {
        this.fs = fs;
    }

    public Server getServer() {
        return server;
    }

    public void setServer(Server server) {
        this.server = server;
    }

    public Elasticsearch getElasticsearch() {
        return elasticsearch;
    }

    public void setElasticsearch(Elasticsearch elasticsearch) {
        this.elasticsearch = elasticsearch;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FsSettings that = (FsSettings) o;

        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        if (fs != null ? !fs.equals(that.fs) : that.fs != null) return false;
        if (server != null ? !server.equals(that.server) : that.server != null) return false;
        return !(elasticsearch != null ? !elasticsearch.equals(that.elasticsearch) : that.elasticsearch != null);

    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (fs != null ? fs.hashCode() : 0);
        result = 31 * result + (server != null ? server.hashCode() : 0);
        result = 31 * result + (elasticsearch != null ? elasticsearch.hashCode() : 0);
        return result;
    }
}
