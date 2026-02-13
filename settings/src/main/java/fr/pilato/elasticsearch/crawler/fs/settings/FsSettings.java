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

import fr.pilato.elasticsearch.crawler.fs.settings.pipeline.FilterSection;
import fr.pilato.elasticsearch.crawler.fs.settings.pipeline.InputSection;
import fr.pilato.elasticsearch.crawler.fs.settings.pipeline.OutputSection;

import java.util.List;
import java.util.Objects;

@SuppressWarnings("SameParameterValue")
public class FsSettings {

    /**
     * Settings format version.
     * Version 1: Legacy format with fs/server/elasticsearch at root
     * Version 2: Pipeline format with inputs/filters/outputs
     */
    private Integer version;

    private String name;
    
    // Legacy v1 settings (kept for backward compatibility)
    private Fs fs;
    private Server server;
    private Elasticsearch elasticsearch;
    private Rest rest;
    private Tags tags;

    // Pipeline v2 settings
    private List<InputSection> inputs;
    private List<FilterSection> filters;
    private List<OutputSection> outputs;

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

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public List<InputSection> getInputs() {
        return inputs;
    }

    public void setInputs(List<InputSection> inputs) {
        this.inputs = inputs;
    }

    public List<FilterSection> getFilters() {
        return filters;
    }

    public void setFilters(List<FilterSection> filters) {
        this.filters = filters;
    }

    public List<OutputSection> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<OutputSection> outputs) {
        this.outputs = outputs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FsSettings that = (FsSettings) o;

        if (!Objects.equals(version, that.version)) return false;
        if (!Objects.equals(name, that.name)) return false;
        if (!Objects.equals(fs, that.fs)) return false;
        if (!Objects.equals(server, that.server)) return false;
        if (!Objects.equals(rest, that.rest)) return false;
        if (!Objects.equals(tags, that.tags)) return false;
        if (!Objects.equals(elasticsearch, that.elasticsearch)) return false;
        if (!Objects.equals(inputs, that.inputs)) return false;
        if (!Objects.equals(filters, that.filters)) return false;
        return Objects.equals(outputs, that.outputs);
    }

    @Override
    public int hashCode() {
        int result = version != null ? version.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (fs != null ? fs.hashCode() : 0);
        result = 31 * result + (server != null ? server.hashCode() : 0);
        result = 31 * result + (rest != null ? rest.hashCode() : 0);
        result = 31 * result + (tags != null ? tags.hashCode() : 0);
        result = 31 * result + (elasticsearch != null ? elasticsearch.hashCode() : 0);
        result = 31 * result + (inputs != null ? inputs.hashCode() : 0);
        result = 31 * result + (filters != null ? filters.hashCode() : 0);
        result = 31 * result + (outputs != null ? outputs.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "FsSettings{" +
                "version=" + version +
                ", name='" + name + '\'' +
                ", fs=" + fs +
                ", server=" + server +
                ", elasticsearch=" + elasticsearch +
                ", rest=" + rest +
                ", tags=" + tags +
                ", inputs=" + inputs +
                ", filters=" + filters +
                ", outputs=" + outputs +
                '}';
    }
}
