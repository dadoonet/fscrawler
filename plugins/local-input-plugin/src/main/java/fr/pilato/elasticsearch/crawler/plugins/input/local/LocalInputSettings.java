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

package fr.pilato.elasticsearch.crawler.plugins.input.local;

import fr.pilato.elasticsearch.crawler.fs.settings.plugin.PluginSettings;

import java.util.List;
import java.util.Objects;

/**
 * Settings for the local input plugin.
 * <p>
 * Example configuration file (01-local.yaml):
 * <pre>
 * version: 1
 * id: my-local-input
 * type: local
 * path: /data/documents
 * update_rate: 15m
 * includes:
 *   - "*.pdf"
 *   - "*.docx"
 * excludes:
 *   - "*~"
 * follow_symlinks: false
 * acl_support: false
 * attributes_support: false
 * </pre>
 */
public class LocalInputSettings extends PluginSettings {

    public static final int CURRENT_VERSION = 1;

    // ========== Required settings ==========

    /**
     * Path to the directory to crawl.
     */
    private String path;

    // ========== Common input settings ==========

    /**
     * How often to check for new/modified files.
     * Format: "15m", "1h", "30s"
     */
    private String updateRate;

    /**
     * Patterns for files to include (glob patterns).
     */
    private List<String> includes;

    /**
     * Patterns for files to exclude (glob patterns).
     */
    private List<String> excludes;

    /**
     * Tags to add to documents from this input.
     */
    private List<String> tags;

    // ========== Local-specific settings ==========

    /**
     * Whether to follow symbolic links when crawling.
     */
    private Boolean followSymlinks;

    /**
     * Whether to extract ACL (Access Control List) information.
     */
    private Boolean aclSupport;

    /**
     * Whether to extract file attributes.
     */
    private Boolean attributesSupport;

    // ========== PluginSettings implementation ==========

    @Override
    public String getExpectedType() {
        return LocalInputPlugin.TYPE;
    }

    @Override
    public int getCurrentVersion() {
        return CURRENT_VERSION;
    }

    // ========== Getters and Setters ==========

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

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

    public Boolean getFollowSymlinks() {
        return followSymlinks;
    }

    public void setFollowSymlinks(Boolean followSymlinks) {
        this.followSymlinks = followSymlinks;
    }

    public Boolean getAclSupport() {
        return aclSupport;
    }

    public void setAclSupport(Boolean aclSupport) {
        this.aclSupport = aclSupport;
    }

    public Boolean getAttributesSupport() {
        return attributesSupport;
    }

    public void setAttributesSupport(Boolean attributesSupport) {
        this.attributesSupport = attributesSupport;
    }

    // ========== Object methods ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        LocalInputSettings that = (LocalInputSettings) o;
        return Objects.equals(path, that.path) &&
                Objects.equals(updateRate, that.updateRate) &&
                Objects.equals(includes, that.includes) &&
                Objects.equals(excludes, that.excludes) &&
                Objects.equals(tags, that.tags) &&
                Objects.equals(followSymlinks, that.followSymlinks) &&
                Objects.equals(aclSupport, that.aclSupport) &&
                Objects.equals(attributesSupport, that.attributesSupport);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), path, updateRate, includes, excludes, 
                tags, followSymlinks, aclSupport, attributesSupport);
    }

    @Override
    public String toString() {
        return "LocalInputSettings{" +
                "version=" + getVersion() +
                ", id='" + getId() + '\'' +
                ", type='" + getType() + '\'' +
                ", path='" + path + '\'' +
                ", updateRate='" + updateRate + '\'' +
                ", includes=" + includes +
                ", excludes=" + excludes +
                ", tags=" + tags +
                ", followSymlinks=" + followSymlinks +
                ", aclSupport=" + aclSupport +
                ", attributesSupport=" + attributesSupport +
                '}';
    }
}
