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
package fr.pilato.elasticsearch.crawler.plugins;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.PathNotFoundException;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Resolves one {@code fs.providers.<type>} field at a time.
 *
 * <p>Order: REST JSON {@code $.<type>.<field>}, then job {@code fs.providers.<type>.<field>}, then an optional
 * deprecated {@code server.<field>} value supplied by the caller. The helper does not know SSH or FTP.
 */
public final class ProviderSettings {
    private final String type;
    private final DocumentContext restJson;
    private final Map<String, Object> jobConfig;
    private final List<String> warnings = new ArrayList<>();

    private ProviderSettings(String type, DocumentContext restJson, Map<String, Object> jobConfig) {
        this.type = type;
        this.restJson = restJson;
        this.jobConfig = jobConfig;
    }

    /**
     * @param type provider type key under {@code fs.providers}
     * @param restJson REST payload, or {@code null} for crawler/job-only resolution
     * @param fsSettings job settings
     */
    public static ProviderSettings of(String type, DocumentContext restJson, FsSettings fsSettings) {
        Map<String, Object> jobConfig = fsSettings != null && fsSettings.getFs() != null
                ? fsSettings.getFs().getProviderConfig(type)
                : null;
        return new ProviderSettings(type, restJson, jobConfig);
    }

    /** REST, then job, then {@code deprecatedServerValue}. */
    public String string(String field) {
        return string(field, null);
    }

    /** REST, then job, then {@code deprecatedServerValue}. */
    public String string(String field, String deprecatedServerValue) {
        return resolveString(field, deprecatedServerValue, true);
    }

    /** REST, then job, then {@code deprecatedServerValue}, then {@code defaultValue}. */
    public String string(String field, String deprecatedServerValue, String defaultValue) {
        String value = string(field, deprecatedServerValue);
        return value != null ? value : defaultValue;
    }

    /** Same as {@link #string(String, String)} but the deprecation warning never echoes the value. */
    public String secret(String field, String deprecatedServerValue) {
        return resolveString(field, deprecatedServerValue, false);
    }

    /** REST, then job, then {@code defaultValue}. Values {@code <= 0} are treated as missing. */
    public int integer(String field, int defaultValue) {
        return integer(field, defaultValue, null);
    }

    /**
     * REST, then job, then {@code deprecatedServerValue}, then {@code defaultValue}. Values {@code <= 0} are treated as
     * missing.
     */
    public int integer(String field, int defaultValue, Integer deprecatedServerValue) {
        Integer restValue = readRestInt(field);
        Integer jobValue = readJobInt(field);
        Integer chosen = firstPositive(restValue, jobValue);
        Integer serverValue = positive(deprecatedServerValue);
        String serverAsString = serverValue != null ? Integer.toString(serverValue) : null;
        maybeWarn(field, serverAsString, chosen != null ? Integer.toString(chosen) : null, true, false);
        if (chosen != null) {
            return chosen;
        }
        return serverValue != null ? serverValue : defaultValue;
    }

    /**
     * REST-only field (e.g. a single-file {@code path}). Does not read job settings or emit {@code server.*} warnings.
     */
    public String restString(String field) {
        return readRestString(field);
    }

    public List<String> deprecationWarnings() {
        return List.copyOf(warnings);
    }

    private String resolveString(String field, String deprecatedServerValue, boolean includeValue) {
        String restValue = readRestString(field);
        String jobValue = readJobString(field);
        String chosen = firstNonBlank(restValue, jobValue);
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
        if (newValue != null) {
            warnings.add("Setting " + oldKey + " is deprecated and will be removed in a future version. Please use "
                    + newKey + " instead. The value from " + oldKey + " is ignored because " + newKey + " is set.");
            return;
        }
        if (includeValue) {
            String displayed = quote ? "\"" + serverValue + "\"" : serverValue;
            warnings.add("Setting " + oldKey + " is deprecated and will be removed in a future version. Please use "
                    + newKey + ": " + displayed + " instead.");
            return;
        }
        warnings.add("Setting " + oldKey + " is deprecated and will be removed in a future version. Please use "
                + newKey + " instead.");
    }

    private String readRestString(String field) {
        if (restJson == null) {
            return null;
        }
        try {
            Object value = restJson.read("$." + type + "." + field);
            return value == null ? null : String.valueOf(value);
        } catch (PathNotFoundException e) {
            return null;
        }
    }

    private Integer readRestInt(String field) {
        if (restJson == null) {
            return null;
        }
        try {
            return toInt(restJson.read("$." + type + "." + field));
        } catch (PathNotFoundException e) {
            return null;
        }
    }

    private String readJobString(String field) {
        if (jobConfig == null) {
            return null;
        }
        Object value = jobConfig.get(field);
        return value == null ? null : String.valueOf(value);
    }

    private Integer readJobInt(String field) {
        if (jobConfig == null) {
            return null;
        }
        return toInt(jobConfig.get(field));
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
