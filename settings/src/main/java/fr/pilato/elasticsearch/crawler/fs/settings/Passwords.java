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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Job-level password provider selection.
 *
 * <p>Provider-specific options live under {@code providers} as an opaque map keyed by provider type ({@code disk},
 * {@code static}, {@code chained}, …). Each password plugin parses and validates its own section; the settings module
 * does not model those shapes.
 */
@SuppressWarnings("SameParameterValue")
public class Passwords {

    private String provider = "noop";

    /**
     * Opaque per-provider configuration ({@code passwords.providers.<type>}). Loaded by {@link FsSettingsLoader} from
     * job YAML/JSON files.
     */
    @Nullable
    private Map<String, Object> providers;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    @Nullable
    public Map<String, Object> getProviders() {
        return providers;
    }

    public void setProviders(@Nullable Map<String, Object> providers) {
        this.providers = providers;
    }

    /**
     * Return the configuration map for one provider type, or {@code null} when absent.
     *
     * @param type provider type key under {@code passwords.providers}
     * @return mutable copy of that section, or {@code null}
     */
    @Nullable
    public Map<String, Object> getProviderConfig(String type) {
        if (providers == null || type == null) {
            return null;
        }

        Object section = providers.get(type);
        if (section == null) {
            return null;
        }
        if (!(section instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("passwords.providers." + type + " must be a map, got "
                    + section.getClass().getName());
        }

        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            copy.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return copy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Passwords passwords = (Passwords) o;
        return Objects.equals(provider, passwords.provider) && Objects.equals(providers, passwords.providers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider, providers);
    }

    @Override
    public String toString() {
        return "Passwords{" + "provider='" + provider + '\'' + ", providers=" + providers + '}';
    }
}
