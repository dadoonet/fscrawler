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

/**
 * Makes it possible to provide your own pipeline
 */
public class Pipeline {
    public static final Pipeline DEFAULT = Pipeline.builder()
            .addClass("fr.pilato.elasticsearch.crawler.fs.DefaultProcessingPipeline")
            .build();

    private String className = "fr.pilato.elasticsearch.crawler.fs.DefaultProcessingPipeline";

    public Pipeline() { }

    private Pipeline(String className, String postTransform) {
        this.className = className;
        this.postTransform = postTransform;
    }

    private static Builder builder() {
        return new Builder();
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getClassName() {
        return className;
    }

    // Custom Serializer
    private String postTransform = null;

    public void setPostTransform(String postTransform) {
        this.postTransform = postTransform;
    }

    public String getPostTransform() {
        return postTransform;
    }
 

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Pipeline pipeline = (Pipeline) o;

        return className != null ? className.equals(pipeline.className) : pipeline.className == null;
    }

    @Override
    public int hashCode() {
        return className != null ? className.hashCode() : 0;
    }

    public static class Builder {
        private String className;
        private String postTransform;

        public Builder addClass(String className) {
            this.className = className;
            return this;
        }

        public Builder addTransform(String postTransform) {
            this.postTransform = postTransform;
            return this;
        }

        public Pipeline build() {
            return new Pipeline(className, postTransform);
        }
    }
}