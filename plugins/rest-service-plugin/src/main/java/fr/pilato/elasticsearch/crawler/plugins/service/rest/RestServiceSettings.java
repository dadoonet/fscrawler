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

package fr.pilato.elasticsearch.crawler.plugins.service.rest;

import fr.pilato.elasticsearch.crawler.fs.settings.plugin.PluginSettings;

import java.util.Objects;

/**
 * Settings for the REST service plugin.
 * <p>
 * Example configuration file (01-rest.yaml):
 * <pre>
 * version: 1
 * id: rest-service
 * type: rest
 * url: "http://127.0.0.1:8080/fscrawler"
 * enableCors: false
 * enabled: true
 * </pre>
 */
public class RestServiceSettings extends PluginSettings {

    public static final int CURRENT_VERSION = 1;

    /**
     * URL to bind the REST API (e.g. http://127.0.0.1:8080/fscrawler).
     */
    private String url;

    /**
     * Whether to enable CORS.
     */
    private Boolean enableCors;

    /**
     * Whether this service is enabled (service plugins default to false).
     */
    private Boolean enabled;

    @Override
    public String getExpectedType() {
        return "rest";
    }

    @Override
    public int getCurrentVersion() {
        return CURRENT_VERSION;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Boolean getEnableCors() {
        return enableCors;
    }

    public void setEnableCors(Boolean enableCors) {
        this.enableCors = enableCors;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        RestServiceSettings that = (RestServiceSettings) o;
        return Objects.equals(url, that.url) &&
                Objects.equals(enableCors, that.enableCors) &&
                Objects.equals(enabled, that.enabled);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), url, enableCors, enabled);
    }
}
