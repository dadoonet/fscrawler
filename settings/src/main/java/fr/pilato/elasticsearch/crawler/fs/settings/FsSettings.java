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

import java.util.Map;
import java.util.Objects;

@SuppressWarnings("SameParameterValue")
public class FsSettings {

    private String name;
    private Fs fs;
    private Server server;
    private Elasticsearch elasticsearch;
    private Rest rest;
    private Tags tags;
    private Map<String, Object> staticTags;

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

    public Tags getTags() {
        return tags;
    }

    public void setTags(Tags tags) {
        this.tags = tags;
    }

    public Map<String, Object> getStaticTags() {
        return staticTags;
    }

    public void setStaticTags(Map<String, Object> staticTags) {
        this.staticTags = staticTags;
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
        if (!Objects.equals(tags, that.tags)) return false;
        if (!Objects.equals(staticTags, that.staticTags)) return false;
        return Objects.equals(elasticsearch, that.elasticsearch);

    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (fs != null ? fs.hashCode() : 0);
        result = 31 * result + (server != null ? server.hashCode() : 0);
        result = 31 * result + (rest != null ? rest.hashCode() : 0);
        result = 31 * result + (tags != null ? tags.hashCode() : 0);
        result = 31 * result + (staticTags != null ? staticTags.hashCode() : 0);
        result = 31 * result + (elasticsearch != null ? elasticsearch.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "FsSettings{" + "name='" + name + '\'' +
                ", fs=" + fs +
                ", server=" + server +
                ", elasticsearch=" + elasticsearch +
                ", rest=" + rest +
                ", tags=" + tags +
                '}';
    }
}
