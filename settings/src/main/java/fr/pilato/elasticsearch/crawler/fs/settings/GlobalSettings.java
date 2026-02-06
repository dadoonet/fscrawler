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

/**
 * Global settings shared across all plugins.
 * Loaded from _settings/01-global.yaml
 * <p>
 * Example configuration:
 * <pre>
 * name: "my-crawler"
 * tags:
 *   meta_filename: ".meta.yml"
 * rest:
 *   url: "http://127.0.0.1:8080"
 * </pre>
 */
public class GlobalSettings {

    public static final String SETTINGS_DIR = "_settings";
    public static final String GLOBAL_SETTINGS_YAML = "01-global.yaml";
    public static final String GLOBAL_SETTINGS_JSON = "01-global.json";
    public static final String INPUTS_DIR = "inputs";
    public static final String FILTERS_DIR = "filters";
    public static final String OUTPUTS_DIR = "outputs";
    public static final String SERVICES_DIR = "services";

    public static final String EXAMPLE_SETTINGS = "/fr/pilato/elasticsearch/crawler/fs/settings/v2/global-default.yaml";
    public static final String DEFAULT_PROPERTIES = "/fr/pilato/elasticsearch/crawler/fs/settings/v2/global-default.properties";

    /**
     * Job name, used as prefix for Elasticsearch indices.
     */
    private String name;

    /**
     * Tags configuration for metadata.
     */
    private Tags tags;

    /**
     * REST server configuration.
     */
    private Rest rest;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Tags getTags() {
        return tags;
    }

    public void setTags(Tags tags) {
        this.tags = tags;
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
        GlobalSettings that = (GlobalSettings) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(tags, that.tags) &&
                Objects.equals(rest, that.rest);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, tags, rest);
    }

    @Override
    public String toString() {
        return "GlobalSettings{" +
                "name='" + name + '\'' +
                ", tags=" + tags +
                ", rest=" + rest +
                '}';
    }

    /**
     * Tags configuration for metadata files.
     */
    public static class Tags {
        private String metaFilename;
        private String staticMetaFilename;

        public String getMetaFilename() {
            return metaFilename;
        }

        public void setMetaFilename(String metaFilename) {
            this.metaFilename = metaFilename;
        }

        public String getStaticMetaFilename() {
            return staticMetaFilename;
        }

        public void setStaticMetaFilename(String staticMetaFilename) {
            this.staticMetaFilename = staticMetaFilename;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Tags tags = (Tags) o;
            return Objects.equals(metaFilename, tags.metaFilename) &&
                    Objects.equals(staticMetaFilename, tags.staticMetaFilename);
        }

        @Override
        public int hashCode() {
            return Objects.hash(metaFilename, staticMetaFilename);
        }

        @Override
        public String toString() {
            return "Tags{" +
                    "metaFilename='" + metaFilename + '\'' +
                    ", staticMetaFilename='" + staticMetaFilename + '\'' +
                    '}';
        }
    }

    /**
     * REST server configuration.
     */
    public static class Rest {
        private String url;
        private Boolean enableCors;

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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Rest rest = (Rest) o;
            return Objects.equals(url, rest.url) &&
                    Objects.equals(enableCors, rest.enableCors);
        }

        @Override
        public int hashCode() {
            return Objects.hash(url, enableCors);
        }

        @Override
        public String toString() {
            return "Rest{" +
                    "url='" + url + '\'' +
                    ", enableCors=" + enableCors +
                    '}';
        }
    }
}
