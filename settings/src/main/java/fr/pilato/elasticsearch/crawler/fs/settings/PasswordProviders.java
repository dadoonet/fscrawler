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
public class PasswordProviders {

    @Config
    @Nullable
    private DiskPasswordProviderSettings disk;

    @Config(path = "static")
    @Nullable
    private StaticPasswordProviderSettings staticSettings;

    @Config
    @Nullable
    private ChainedPasswordProviderSettings chained;

    @Nullable
    public DiskPasswordProviderSettings getDisk() {
        return disk;
    }

    public void setDisk(@Nullable DiskPasswordProviderSettings disk) {
        this.disk = disk;
    }

    @Nullable
    public StaticPasswordProviderSettings getStaticSettings() {
        return staticSettings;
    }

    public void setStaticSettings(@Nullable StaticPasswordProviderSettings staticSettings) {
        this.staticSettings = staticSettings;
    }

    @Nullable
    public StaticPasswordProviderSettings getStatic() {
        return staticSettings;
    }

    public void setStatic(@Nullable StaticPasswordProviderSettings staticSettings) {
        this.staticSettings = staticSettings;
    }

    @Nullable
    public ChainedPasswordProviderSettings getChained() {
        return chained;
    }

    public void setChained(@Nullable ChainedPasswordProviderSettings chained) {
        this.chained = chained;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PasswordProviders that = (PasswordProviders) o;
        return Objects.equals(disk, that.disk)
                && Objects.equals(staticSettings, that.staticSettings)
                && Objects.equals(chained, that.chained);
    }

    @Override
    public int hashCode() {
        return Objects.hash(disk, staticSettings, chained);
    }

    @Override
    public String toString() {
        return "PasswordProviders{"
                + "disk="
                + disk
                + ", staticSettings="
                + staticSettings
                + ", chained="
                + chained
                + '}';
    }
}
