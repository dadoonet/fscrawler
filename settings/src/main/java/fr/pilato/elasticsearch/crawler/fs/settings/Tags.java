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

import org.github.gestalt.config.annotations.Config;

import java.util.Map;
import java.util.Objects;

@SuppressWarnings("SameParameterValue")
public class Tags {

    @Config(defaultVal = Defaults.DEFAULT_META_FILENAME)
    private String metaFilename;
    
    private Map<String, Object> staticTags;

    public String getMetaFilename() {
        return metaFilename;
    }

    public void setMetaFilename(String metaFilename) {
        this.metaFilename = metaFilename;
    }

    public Map<String, Object> getStaticTags() {
        return staticTags;
    }

    public void setStaticTags(Map<String, Object> staticTags) {
        this.staticTags = staticTags;
    }

    @Override
    public String toString() {
        return "Tags{" +
                "metaFilename='" + metaFilename + '\'' +
                ", staticTags=" + staticTags +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Tags tags = (Tags) o;
        return Objects.equals(metaFilename, tags.metaFilename) && Objects.equals(staticTags, tags.staticTags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(metaFilename, staticTags);
    }
}
