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

package fr.pilato.elasticsearch.crawler.plugins.pipeline;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Context object that travels with a document through the pipeline.
 * Contains metadata used for conditional routing and plugin configuration.
 */
public class PipelineContext {

    /**
     * Tags associated with this document, used for conditional routing.
     * Tags can be added by inputs and used in filter/output conditions.
     */
    private final Set<String> tags = new HashSet<>();

    /**
     * The filename of the document.
     */
    private String filename;

    /**
     * The file extension (without the dot).
     */
    private String extension;

    /**
     * The full path to the document.
     */
    private String path;

    /**
     * The file size in bytes.
     */
    private long size;

    /**
     * The ID of the input that produced this document.
     */
    private String inputId;

    /**
     * The MIME type of the document.
     */
    private String mimeType;

    /**
     * The target index name for Elasticsearch.
     */
    private String index;

    /**
     * Additional metadata that can be used in conditions.
     */
    private final Map<String, Object> metadata = new HashMap<>();

    // ========== Builder-style methods ==========

    /**
     * Adds a tag to this context.
     *
     * @param tag the tag to add
     * @return this context for chaining
     */
    public PipelineContext withTag(String tag) {
        if (tag != null) {
            this.tags.add(tag);
        }
        return this;
    }

    /**
     * Adds multiple tags to this context.
     *
     * @param tags the tags to add
     * @return this context for chaining
     */
    public PipelineContext withTags(Iterable<String> tags) {
        if (tags != null) {
            for (String tag : tags) {
                this.tags.add(tag);
            }
        }
        return this;
    }

    /**
     * Adds metadata to this context.
     *
     * @param key the metadata key
     * @param value the metadata value
     * @return this context for chaining
     */
    public PipelineContext withMetadata(String key, Object value) {
        this.metadata.put(key, value);
        return this;
    }

    public PipelineContext withFilename(String filename) {
        this.filename = filename;
        if (filename != null) {
            int lastDot = filename.lastIndexOf('.');
            if (lastDot > 0) {
                this.extension = filename.substring(lastDot + 1).toLowerCase();
            }
        }
        return this;
    }

    public PipelineContext withPath(String path) {
        this.path = path;
        return this;
    }

    public PipelineContext withSize(long size) {
        this.size = size;
        return this;
    }

    public PipelineContext withInputId(String inputId) {
        this.inputId = inputId;
        return this;
    }

    public PipelineContext withMimeType(String mimeType) {
        this.mimeType = mimeType;
        return this;
    }

    public PipelineContext withIndex(String index) {
        this.index = index;
        return this;
    }

    // ========== Getters ==========

    public Set<String> getTags() {
        return tags;
    }

    public String getFilename() {
        return filename;
    }

    public String getExtension() {
        return extension;
    }

    public String getPath() {
        return path;
    }

    public long getSize() {
        return size;
    }

    public String getInputId() {
        return inputId;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getIndex() {
        return index;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    @Override
    public String toString() {
        return "PipelineContext{" +
                "tags=" + tags +
                ", filename='" + filename + '\'' +
                ", extension='" + extension + '\'' +
                ", path='" + path + '\'' +
                ", size=" + size +
                ", inputId='" + inputId + '\'' +
                ", mimeType='" + mimeType + '\'' +
                ", index='" + index + '\'' +
                '}';
    }
}
