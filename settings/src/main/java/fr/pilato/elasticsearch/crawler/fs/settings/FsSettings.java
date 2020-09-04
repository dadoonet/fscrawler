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

import java.util.Objects;

@SuppressWarnings("SameParameterValue")
public class FsSettings {

    private String name;
    private Fs fs;
    private Server server;
    private Elasticsearch elasticsearch;
    private Rest rest;
    private Pipeline pipeline;

    public FsSettings() {

    }

    private FsSettings(String name, Fs fs, Server server, Elasticsearch elasticsearch, Rest rest) {
        this.name = name;
        this.fs = fs;
        this.server = server;
        this.elasticsearch = elasticsearch;
        this.rest = rest;
    }

    public static Builder builder(String name) {
        return new Builder().setName(name);
    }

    public Pipeline getPipeline() {
        return pipeline;
    }

    public static class Builder {
        private String name;
        private Fs fs = Fs.DEFAULT;
        private Server server = null;
        private Elasticsearch elasticsearch = Elasticsearch.DEFAULT();
        private Rest rest = null;
        private Pipeline pipeline = Pipeline.DEFAULT();

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

        public Builder setRest(Rest rest) {
            this.rest = rest;
            return this;
        }

        public FsSettings build() {
            return new FsSettings(name, fs, server, elasticsearch, rest);
        }

        public Pipeline getPipeline() {
            return pipeline;
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

    public Rest getRest() {
        return rest;
    }

    public void setRest(Rest rest) {
        this.rest = rest;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FsSettings that = (FsSettings) o;

        if (!Objects.equals(name, that.name)) return false;
        if (!Objects.equals(fs, that.fs)) return false;
        if (!Objects.equals(server, that.server)) return false;
        if (!Objects.equals(rest, that.rest)) return false;
        if (!Objects.equals(pipeline, that.pipeline)) return false;
        return Objects.equals(elasticsearch, that.elasticsearch);

    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (fs != null ? fs.hashCode() : 0);
        result = 31 * result + (server != null ? server.hashCode() : 0);
        result = 31 * result + (rest != null ? rest.hashCode() : 0);
        result = 31 * result + (elasticsearch != null ? elasticsearch.hashCode() : 0);
        result = 31 * result + (pipeline != null ? pipeline.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "FsSettings{" + "name='" + name + '\'' +
                ", fs=" + fs +
                ", server=" + server +
                ", elasticsearch=" + elasticsearch +
                ", rest=" + rest +
                ", pipeline=" + pipeline +
                '}';
    }
}
