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
import java.util.List;
import java.util.Objects;
import org.github.gestalt.config.annotations.Config;

@SuppressWarnings("SameParameterValue")
public class StaticPasswordProviderSettings {

    @Config
    @Nullable
    private List<String> values;

    @Config
    @Nullable
    private String value;

    @Nullable
    public List<String> getValues() {
        return values;
    }

    public void setValues(@Nullable List<String> values) {
        this.values = values;
    }

    @Nullable
    public String getValue() {
        return value;
    }

    public void setValue(@Nullable String value) {
        this.value = value;
    }

    public List<String> resolvedValues() {
        if (values != null && !values.isEmpty()) {
            return values;
        }

        return value == null ? List.of() : List.of(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StaticPasswordProviderSettings that = (StaticPasswordProviderSettings) o;
        return Objects.equals(values, that.values) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values, value);
    }

    @Override
    public String toString() {
        String valuesState = values == null ? "null" : values.size() + " configured value(s)";
        return "StaticPasswordProviderSettings{" + "values=" + valuesState + ", valuePresent=" + (value != null) + '}';
    }
}
