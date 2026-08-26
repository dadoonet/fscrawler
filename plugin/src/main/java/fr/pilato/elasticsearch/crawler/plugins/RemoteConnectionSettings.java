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
import fr.pilato.elasticsearch.crawler.fs.settings.Server;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Connection settings for a remote filesystem provider (SSH, FTP).
 *
 * <p>Resolution order: REST JSON {@code type} block, then job {@code fs.<type>}, then deprecated {@code server.*}.
 */
@SuppressWarnings("removal")
public record RemoteConnectionSettings(
        String hostname,
        int port,
        String username,
        String password,
        String pemPath,
        String remotePath,
        List<String> deprecationWarnings) {

    public static final int DEFAULT_SSH_PORT = 22;
    public static final int DEFAULT_FTP_PORT = 21;
    public static final String DEFAULT_FTP_USERNAME = "anonymous";

    /**
     * Resolve remote connection settings.
     *
     * @param type provider type ({@code ssh}, {@code ftp})
     * @param restJson REST payload, or {@code null} for crawler/job-only resolution
     * @param fsSettings job settings
     * @param defaultPort port used when none is configured
     * @param defaultUsername username used when none is configured (e.g. FTP {@code anonymous})
     * @return resolved settings plus deprecation warnings for {@code server.*}
     */
    public static RemoteConnectionSettings resolve(
            String type, DocumentContext restJson, FsSettings fsSettings, int defaultPort, String defaultUsername) {
        Map<String, Object> jobConfig = fsSettings != null && fsSettings.getFs() != null
                ? fsSettings.getFs().getProviderConfig(type)
                : null;
        Server server = fsSettings != null ? fsSettings.getServer() : null;
        List<String> warnings = new ArrayList<>();

        String hostname = resolveString(type, "hostname", restJson, jobConfig, serverHostname(server), warnings);
        Integer port = resolvePort(type, restJson, jobConfig, server, warnings);
        String username = resolveString(type, "username", restJson, jobConfig, serverUsername(server), warnings);
        String password = resolvePassword(type, restJson, jobConfig, server, warnings);
        String pemPath = "ssh".equals(type)
                ? resolveString(type, "pem_path", restJson, jobConfig, serverPemPath(server), warnings)
                : readRestString(restJson, type, "pem_path");
        String remotePath = readRestString(restJson, type, "path");

        int effectivePort = port != null && port > 0 ? port : defaultPort;
        String effectiveUsername = username != null ? username : defaultUsername;

        return new RemoteConnectionSettings(
                hostname, effectivePort, effectiveUsername, password, pemPath, remotePath, List.copyOf(warnings));
    }

    private static String resolveString(
            String type,
            String field,
            DocumentContext restJson,
            Map<String, Object> jobConfig,
            String serverValue,
            List<String> warnings) {
        String restValue = readRestString(restJson, type, field);
        String jobValue = readJobString(jobConfig, field);
        String chosen = firstNonBlank(restValue, jobValue);
        maybeWarn(warnings, type, field, serverValue, chosen, formatQuoted(serverValue), true);
        return chosen != null ? chosen : blankToNull(serverValue);
    }

    private static String resolvePassword(
            String type,
            DocumentContext restJson,
            Map<String, Object> jobConfig,
            Server server,
            List<String> warnings) {
        String restValue = readRestString(restJson, type, "password");
        String jobValue = readJobString(jobConfig, "password");
        String serverValue = server != null ? server.getPassword() : null;
        String chosen = firstNonBlank(restValue, jobValue);
        maybeWarn(warnings, type, "password", serverValue, chosen, null, false);
        return chosen != null ? chosen : serverValue;
    }

    private static Integer resolvePort(
            String type,
            DocumentContext restJson,
            Map<String, Object> jobConfig,
            Server server,
            List<String> warnings) {
        Integer restValue = readRestInt(restJson, type, "port");
        Integer jobValue = readJobInt(jobConfig, "port");
        Integer serverValue = server != null && server.getPort() > 0 ? server.getPort() : null;
        Integer chosen =
                restValue != null && restValue > 0 ? restValue : (jobValue != null && jobValue > 0 ? jobValue : null);
        String serverAsString = serverValue != null ? Integer.toString(serverValue) : null;
        maybeWarn(
                warnings,
                type,
                "port",
                serverAsString,
                chosen != null ? Integer.toString(chosen) : null,
                serverAsString,
                true);
        return chosen != null ? chosen : serverValue;
    }

    private static void maybeWarn(
            List<String> warnings,
            String type,
            String field,
            String serverValue,
            String newValue,
            String serverValueForMessage,
            boolean includeValue) {
        if (blankToNull(serverValue) == null) {
            return;
        }
        String oldKey = "server." + field;
        String newKey = "fs." + type + "." + field;
        if (newValue != null) {
            warnings.add("Setting " + oldKey + " is deprecated and will be removed in a future version. Please use "
                    + newKey + " instead. The value from " + oldKey + " is ignored because " + newKey + " is set.");
            return;
        }
        if (includeValue && serverValueForMessage != null) {
            warnings.add("Setting " + oldKey + " is deprecated and will be removed in a future version. Please use "
                    + newKey + ": " + serverValueForMessage + " instead.");
            return;
        }
        warnings.add("Setting " + oldKey + " is deprecated and will be removed in a future version. Please use "
                + newKey + " instead.");
    }

    private static String formatQuoted(String value) {
        return value == null ? null : "\"" + value + "\"";
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

    private static String readRestString(DocumentContext restJson, String type, String field) {
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

    private static Integer readRestInt(DocumentContext restJson, String type, String field) {
        if (restJson == null) {
            return null;
        }
        try {
            Object value = restJson.read("$." + type + "." + field);
            return toInt(value);
        } catch (PathNotFoundException e) {
            return null;
        }
    }

    private static String readJobString(Map<String, Object> jobConfig, String field) {
        if (jobConfig == null) {
            return null;
        }
        Object value = jobConfig.get(field);
        return value == null ? null : String.valueOf(value);
    }

    private static Integer readJobInt(Map<String, Object> jobConfig, String field) {
        if (jobConfig == null) {
            return null;
        }
        return toInt(jobConfig.get(field));
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

    private static String serverHostname(Server server) {
        return server == null ? null : server.getHostname();
    }

    private static String serverUsername(Server server) {
        return server == null ? null : server.getUsername();
    }

    private static String serverPemPath(Server server) {
        return server == null ? null : server.getPemPath();
    }
}
