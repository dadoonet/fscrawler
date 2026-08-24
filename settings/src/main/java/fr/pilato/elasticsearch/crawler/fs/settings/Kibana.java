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

import jakarta.annotation.Nullable;
import java.util.Objects;
import org.github.gestalt.config.annotations.Config;

public class Kibana {

    @Config(defaultVal = Defaults.KIBANA_URL_DEFAULT)
    @Nullable
    private String url;

    /** When {@code true}, FSCrawler creates or updates a default Kibana dashboard for this job on startup. */
    @Config(defaultVal = "false")
    private boolean pushDashboard;

    /** Optional Kibana space id. When unset, the default space is used. */
    @Config
    @Nullable
    private String space;

    /** Optional API key for Kibana. When unset, {@link Elasticsearch#getApiKey()} is used. */
    @Config
    @Nullable
    private String apiKey;

    @Nullable
    public String getUrl() {
        return url;
    }

    public void setUrl(@Nullable String url) {
        this.url = url;
    }

    public boolean isPushDashboard() {
        return pushDashboard;
    }

    public void setPushDashboard(boolean pushDashboard) {
        this.pushDashboard = pushDashboard;
    }

    @Nullable
    public String getSpace() {
        return space;
    }

    public void setSpace(@Nullable String space) {
        this.space = space;
    }

    @Nullable
    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(@Nullable String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Kibana kibana = (Kibana) o;

        if (pushDashboard != kibana.pushDashboard) return false;
        if (!Objects.equals(url, kibana.url)) return false;
        if (!Objects.equals(space, kibana.space)) return false;
        return Objects.equals(apiKey, kibana.apiKey);
    }

    @Override
    public int hashCode() {
        int result = url != null ? url.hashCode() : 0;
        result = 31 * result + (pushDashboard ? 1 : 0);
        result = 31 * result + (space != null ? space.hashCode() : 0);
        result = 31 * result + (apiKey != null ? apiKey.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Kibana{" + "url='" + url + '\'' + ", pushDashboard=" + pushDashboard + ", space='" + space + '\''
                + ", apiKey='" + apiKey + '\'' + '}';
    }
}
