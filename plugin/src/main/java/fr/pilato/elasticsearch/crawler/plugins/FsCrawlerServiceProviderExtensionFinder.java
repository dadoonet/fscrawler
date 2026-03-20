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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.pf4j.AbstractExtensionFinder;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;

/**
 * Extension finder that only reads the FsCrawler FsProvider SPI file ({@value #SERVICE_RESOURCE}), instead of scanning
 * all META-INF/services. This avoids loading unrelated service provider classes (e.g. NetCDF GRIB) that may be on the
 * classpath and cause ClassNotFoundException.
 */
public class FsCrawlerServiceProviderExtensionFinder extends AbstractExtensionFinder {

    private static final Logger log = LogManager.getLogger(FsCrawlerServiceProviderExtensionFinder.class);

    /** Only this SPI file is read (FsCrawler FsProvider implementations). */
    public static final String SERVICE_RESOURCE = "META-INF/services/" + FsCrawlerExtensionFsProvider.class.getName();

    private static final Pattern COMMENT = Pattern.compile("#.*");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    public FsCrawlerServiceProviderExtensionFinder(PluginManager pluginManager) {
        super(pluginManager);
    }

    @Override
    public Map<String, Set<String>> readClasspathStorages() {
        log.debug("Reading FsProvider extensions from classpath ({})", SERVICE_RESOURCE);
        Map<String, Set<String>> result = new LinkedHashMap<>();
        Set<String> bucket = new HashSet<>();
        try {
            ClassLoader classLoader = getClass().getClassLoader();
            Enumeration<URL> urls = classLoader.getResources(SERVICE_RESOURCE);
            if (urls.hasMoreElements()) {
                collectFromUrls(urls, bucket);
            } else {
                log.debug("No resource '{}' found on classpath", SERVICE_RESOURCE);
            }
            debugExtensions(bucket);
            result.put(null, bucket);
        } catch (IOException | URISyntaxException e) {
            log.error("Failed to read classpath storage {}: {}", SERVICE_RESOURCE, e.getMessage(), e);
        }
        return result;
    }

    @Override
    public Map<String, Set<String>> readPluginsStorages() {
        log.debug("Reading FsProvider extensions from plugins ({})", SERVICE_RESOURCE);
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (PluginWrapper plugin : pluginManager.getPlugins()) {
            String pluginId = plugin.getPluginId();
            Set<String> bucket = new HashSet<>();
            try {
                ClassLoader classLoader = plugin.getPluginClassLoader();
                Enumeration<URL> urls = classLoader.getResources(SERVICE_RESOURCE);
                if (urls.hasMoreElements()) {
                    collectFromUrls(urls, bucket);
                } else {
                    log.debug("No resource '{}' in plugin '{}'", SERVICE_RESOURCE, pluginId);
                }
                debugExtensions(bucket);
                result.put(pluginId, bucket);
            } catch (IOException | URISyntaxException e) {
                log.error("Failed to read plugin '{}' storage {}: {}", pluginId, SERVICE_RESOURCE, e.getMessage(), e);
            }
        }
        return result;
    }

    private void collectFromUrls(Enumeration<URL> urls, Set<String> bucket) throws IOException, URISyntaxException {
        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            log.debug("Reading {}", url.getPath());
            readServiceFile(url, bucket);
        }
    }

    private void readServiceFile(URL url, Set<String> bucket) throws IOException {
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = COMMENT.matcher(line).replaceFirst("").trim();
                line = WHITESPACE.matcher(line).replaceAll(" ").trim();
                if (!line.isEmpty()) {
                    bucket.add(line);
                }
            }
        }
    }
}
