/*
 * Licensed to David Pilato (the "Author") under one
 * or more contributor license agreements. See the NOTICE file
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
 *
 * Made from 🇫🇷🇪🇺 with ❤️ - 2011-2026
 */
package fr.pilato.elasticsearch.crawler.fs.settings;

import java.util.Objects;
import org.github.gestalt.config.annotations.Config;

public class Rest {

    @Config(defaultVal = Defaults.REST_URL_DEFAULT)
    private String url;

    @Config(defaultVal = "false")
    private boolean enableCors;

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    public void setEnableCors(boolean enableCors) {
        this.enableCors = enableCors;
    }

    public boolean isEnableCors() {
        return enableCors;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Rest rest = (Rest) o;
        return enableCors == rest.enableCors && Objects.equals(url, rest.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enableCors, url);
    }

    @Override
    public String toString() {
        return "Rest{" + "url='" + url + '\'' + ", enableCors=" + enableCors + '}';
    }
}
