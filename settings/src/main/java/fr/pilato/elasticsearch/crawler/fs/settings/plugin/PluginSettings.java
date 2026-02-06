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

package fr.pilato.elasticsearch.crawler.fs.settings.plugin;

import java.util.Objects;

/**
 * Base class for all plugin settings.
 * Each plugin defines its own settings class extending this base.
 * <p>
 * Example configuration file:
 * <pre>
 * version: 1
 * id: my-plugin
 * type: local
 * # ... plugin-specific settings
 * </pre>
 */
public abstract class PluginSettings {

    /**
     * Settings format version for this plugin.
     * Used to track schema changes and enable migrations.
     */
    private Integer version;

    /**
     * Unique identifier for this plugin instance within the pipeline.
     */
    private String id;

    /**
     * Plugin type (e.g., "local", "ssh", "tika", "elasticsearch").
     * Used to validate that the config file matches the expected plugin.
     */
    private String type;

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    /**
     * Returns the expected type for this settings class.
     * Used to validate that the loaded config matches the plugin.
     */
    public abstract String getExpectedType();

    /**
     * Returns the current settings version supported by this plugin.
     * Used to determine if migration is needed.
     */
    public abstract int getCurrentVersion();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PluginSettings that = (PluginSettings) o;
        return Objects.equals(version, that.version) &&
                Objects.equals(id, that.id) &&
                Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, id, type);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "version=" + version +
                ", id='" + id + '\'' +
                ", type='" + type + '\'' +
                '}';
    }
}
