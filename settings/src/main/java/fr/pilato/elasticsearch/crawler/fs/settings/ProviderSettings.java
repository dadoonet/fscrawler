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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Resolves one {@code fs.providers.<type>} field at a time.
 *
 * <p>Order: optional overlay (caller-supplied, e.g. REST), then job {@code fs.providers.<type>.<field>}, then an
 * optional deprecated {@code server.<field>} value supplied by the caller. This class does not know SSH, FTP, or REST
 * JSON.
 */
public final class ProviderSettings {
    private final String type;
    private final Map<String, Object> overlay;
    private final Map<String, Object> jobConfig;
    private final List<String> warnings = new ArrayList<>();
    private static final String DEPRECATED_PLEASE_USE =
            " is deprecated and will be removed in a future version. Please use ";

    private ProviderSettings(String type, Map<String, Object> overlay, Map<String, Object> jobConfig) {
        this.type = type;
        this.overlay = overlay;
        this.jobConfig = jobConfig;
    }

    /**
     * @param type provider type key under {@code fs.providers}
     * @param fsSettings job settings
     */
    public static ProviderSettings of(String type, FsSettings fsSettings) {
        return of(type, fsSettings, Map.of());
    }

    /**
     * @param type provider type key under {@code fs.providers}
     * @param fsSettings job settings
     * @param overlay values that win over the job map (e.g. a REST {@code type} block)
     */
    public static ProviderSettings of(String type, FsSettings fsSettings, Map<String, Object> overlay) {
        Map<String, Object> jobConfig = fsSettings != null && fsSettings.getFs() != null
                ? fsSettings.getFs().getProviderConfig(type)
                : null;
        return new ProviderSettings(type, overlay == null ? Map.of() : overlay, jobConfig);
    }

    /** Overlay, then job, then {@code deprecatedServerValue}. */
    public String string(String field) {
        return string(field, null);
    }

    /** Overlay, then job, then {@code deprecatedServerValue}. */
    public String string(String field, String deprecatedServerValue) {
        return resolveString(field, deprecatedServerValue, true);
    }

    /** Overlay, then job, then {@code deprecatedServerValue}, then {@code defaultValue}. */
    public String string(String field, String deprecatedServerValue, String defaultValue) {
        String value = string(field, deprecatedServerValue);
        return value != null ? value : defaultValue;
    }

    /** Same as {@link #string(String, String)} but the deprecation warning never echoes the value. */
    public String secret(String field, String deprecatedServerValue) {
        return resolveString(field, deprecatedServerValue, false);
    }

    /** Overlay, then job, then {@code defaultValue}. Values {@code <= 0} are treated as missing. */
    public int integer(String field, int defaultValue) {
        return integer(field, defaultValue, null);
    }

    /**
     * Overlay, then job, then {@code deprecatedServerValue}, then {@code defaultValue}. Values {@code <= 0} are treated
     * as missing.
     */
    public int integer(String field, int defaultValue, Integer deprecatedServerValue) {
        Integer overlayValue = readMapInt(overlay, field);
        Integer jobValue = readMapInt(jobConfig, field);
        Integer chosen = firstPositive(overlayValue, jobValue);
        Integer serverValue = positive(deprecatedServerValue);
        String serverAsString = serverValue != null ? Integer.toString(serverValue) : null;
        maybeWarn(field, serverAsString, chosen != null ? Integer.toString(chosen) : null, true, false);
        if (chosen != null) {
            return chosen;
        }
        return serverValue != null ? serverValue : defaultValue;
    }

    /**
     * Overlay-only field (e.g. a REST single-file {@code path}). Does not read job settings or emit {@code server.*}
     * warnings.
     */
    public String overlayString(String field) {
        return readMapString(overlay, field);
    }

    public List<String> deprecationWarnings() {
        return List.copyOf(warnings);
    }

    private String resolveString(String field, String deprecatedServerValue, boolean includeValue) {
        String overlayValue = readMapString(overlay, field);
        String jobValue = readMapString(jobConfig, field);
        String chosen = firstNonBlank(overlayValue, jobValue);
        String serverValue = blankToNull(deprecatedServerValue);
        maybeWarn(field, serverValue, chosen, includeValue, true);
        if (chosen != null) {
            return chosen;
        }
        return serverValue;
    }

    private void maybeWarn(String field, String serverValue, String newValue, boolean includeValue, boolean quote) {
        if (blankToNull(serverValue) == null) {
            return;
        }
        String oldKey = "server." + field;
        String newKey = "fs.providers." + type + "." + field;
        String prefix = "Setting " + oldKey + DEPRECATED_PLEASE_USE;
        if (newValue != null) {
            warnings.add(prefix + newKey + " instead. The value from " + oldKey + " is ignored because " + newKey
                    + " is set.");
            return;
        }
        if (includeValue) {
            String displayed = quote ? "\"" + serverValue + "\"" : serverValue;
            warnings.add(prefix + newKey + ": " + displayed + " instead.");
            return;
        }
        warnings.add(prefix + newKey + " instead.");
    }

    private static String readMapString(Map<String, Object> map, String field) {
        if (map == null) {
            return null;
        }
        Object value = map.get(field);
        return value == null ? null : String.valueOf(value);
    }

    private static Integer readMapInt(Map<String, Object> map, String field) {
        if (map == null) {
            return null;
        }
        return toInt(map.get(field));
    }

    private static Integer firstPositive(Integer first, Integer second) {
        Integer positiveFirst = positive(first);
        if (positiveFirst != null) {
            return positiveFirst;
        }
        return positive(second);
    }

    private static Integer positive(Integer value) {
        return value != null && value > 0 ? value : null;
    }

    private static String firstNonBlank(String first, String second) {
        if (blankToNull(first) != null) {
            return first;
        }
        return blankToNull(second);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private static Integer toInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
