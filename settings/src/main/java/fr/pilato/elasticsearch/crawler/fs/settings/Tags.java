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

@SuppressWarnings("SameParameterValue")
public class Tags {
    public static final String DEFAULT_META_FILENAME = ".meta.yml";
    public static final Tags DEFAULT = Tags.builder().build();

    private String metaFilename;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String metaFilename = DEFAULT_META_FILENAME;

        public Builder setMetaFilename(String metaFilename) {
            this.metaFilename = metaFilename;
            return this;
        }

        public Tags build() {
            return new Tags(metaFilename);
        }
    }

    public Tags() {

    }

    public Tags(String metaFilename) {
        this.metaFilename = metaFilename;
    }

    public String getMetaFilename() {
        return metaFilename;
    }

    public void setMetaFilename(String metaFilename) {
        this.metaFilename = metaFilename;
    }

    @Override
    public String toString() {
        return "Tags{" +
                "metaFilename='" + metaFilename + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Tags tags = (Tags) o;
        return Objects.equals(metaFilename, tags.metaFilename);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(metaFilename);
    }
}
