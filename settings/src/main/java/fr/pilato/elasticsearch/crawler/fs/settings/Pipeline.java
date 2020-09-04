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
    private String className = "fr.pilato.elasticsearch.crawler.fs.DefaultProcessingPipeline";

    public Pipeline() { }

    private Pipeline(String className) {
        this.className = className;
    }

    public static Pipeline DEFAULT() {
        return Pipeline.builder()
                .addClass("fr.pilato.elasticsearch.crawler.fs.DefaultProcessingPipeline")
                .build();
    }

    private static Builder builder() {
        return new Builder();
    }

    public String getClassName() {
        return className;
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

        public Builder addClass(String className) {
            this.className = className;
            return this;
        }

        public Pipeline build() {
            return new Pipeline(className);
        }
    }
}
