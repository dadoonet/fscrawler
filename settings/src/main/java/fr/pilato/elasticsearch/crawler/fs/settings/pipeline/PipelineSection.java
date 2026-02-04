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

package fr.pilato.elasticsearch.crawler.fs.settings.pipeline;

import java.util.Map;
import java.util.Objects;

/**
 * Base class for pipeline sections (input, filter, output).
 * Each section has a type that determines which plugin to load,
 * an id for unique identification, and an optional condition (when).
 */
public abstract class PipelineSection {

    /**
     * The type of plugin to use (e.g., "local", "ssh", "tika", "elasticsearch")
     */
    private String type;

    /**
     * Unique identifier for this section in the pipeline
     */
    private String id;

    /**
     * Optional MVEL condition for conditional routing (Phase 2).
     * If null or empty, the section always applies.
     */
    private String when;

    /**
     * Raw configuration map for plugin-specific settings.
     * The key is the plugin type, value is the configuration map.
     */
    private Map<String, Object> rawConfig;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getWhen() {
        return when;
    }

    public void setWhen(String when) {
        this.when = when;
    }

    public Map<String, Object> getRawConfig() {
        return rawConfig;
    }

    public void setRawConfig(Map<String, Object> rawConfig) {
        this.rawConfig = rawConfig;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PipelineSection that = (PipelineSection) o;
        return Objects.equals(type, that.type) &&
                Objects.equals(id, that.id) &&
                Objects.equals(when, that.when) &&
                Objects.equals(rawConfig, that.rawConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, id, when, rawConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "type='" + type + '\'' +
                ", id='" + id + '\'' +
                ", when='" + when + '\'' +
                '}';
    }
}
