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

import java.util.List;
import java.util.Objects;

/**
 * Configuration section for input plugins (data sources).
 * Supports local filesystem, SSH, FTP, S3, HTTP, etc.
 */
public class InputSection extends PipelineSection {

    /**
     * How often to check for updates (e.g., "15m", "1h")
     */
    private String updateRate;

    /**
     * File patterns to include (e.g., ["*.pdf", "*.doc"])
     */
    private List<String> includes;

    /**
     * File patterns to exclude (e.g., ["*~", "*.tmp"])
     */
    private List<String> excludes;

    /**
     * Tags to add to documents from this input.
     * Used for conditional routing in filters/outputs.
     */
    private List<String> tags;

    public String getUpdateRate() {
        return updateRate;
    }

    public void setUpdateRate(String updateRate) {
        this.updateRate = updateRate;
    }

    public List<String> getIncludes() {
        return includes;
    }

    public void setIncludes(List<String> includes) {
        this.includes = includes;
    }

    public List<String> getExcludes() {
        return excludes;
    }

    public void setExcludes(List<String> excludes) {
        this.excludes = excludes;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        InputSection that = (InputSection) o;
        return Objects.equals(updateRate, that.updateRate) &&
                Objects.equals(includes, that.includes) &&
                Objects.equals(excludes, that.excludes) &&
                Objects.equals(tags, that.tags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), updateRate, includes, excludes, tags);
    }

    @Override
    public String toString() {
        return "InputSection{" +
                "type='" + getType() + '\'' +
                ", id='" + getId() + '\'' +
                ", updateRate='" + updateRate + '\'' +
                ", includes=" + includes +
                ", excludes=" + excludes +
                ", tags=" + tags +
                ", when='" + getWhen() + '\'' +
                '}';
    }
}
