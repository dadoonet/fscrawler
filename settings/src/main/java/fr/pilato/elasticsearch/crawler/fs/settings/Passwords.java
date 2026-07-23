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

@SuppressWarnings("SameParameterValue")
public class Passwords {

    @Config(defaultVal = "noop")
    private String provider;

    @Config
    @Nullable
    private PasswordProviders providers;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    @Nullable
    public PasswordProviders getProviders() {
        return providers;
    }

    public void setProviders(@Nullable PasswordProviders providers) {
        this.providers = providers;
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
