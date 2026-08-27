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
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class FsCrawlerExtensionFsProviderAbstract implements FsCrawlerExtensionFsProvider {
    private static final Logger logger = LogManager.getLogger();
    protected DocumentContext document;
    protected FsSettings fsSettings;

    protected abstract void parseSettings() throws PathNotFoundException, IOException;

    protected abstract void validateSettings() throws PathNotFoundException, IOException;

    @Override
    public void start(FsSettings fsSettings, String restSettings) {
        this.fsSettings = fsSettings;

        if (hasRestSettings(restSettings)) {
            logger.trace("with rest settings {}", restSettings);
            document = JsonUtil.parseJsonAsDocumentContext(restSettings);
        } else {
            logger.trace("No REST settings provided");
        }

        try {
            parseSettings();
            validateSettings();
        } catch (PathNotFoundException | IOException e) {
            throw new FsCrawlerIllegalConfigurationException(e.getMessage(), e);
        }
    }

    private static boolean hasRestSettings(String restSettings) {
        return restSettings != null && !restSettings.isEmpty() && !"{}".equals(restSettings);
    }

    @Override
    public void stop() throws FsCrawlerPluginException {}

    @Override
    public void close() throws Exception {
        logger.debug("Closing FsCrawlerExtensionFsProviderAbstract");
        stop();
    }

    /** REST JSON block for this provider type ({@code $.<type>}), or an empty map when none is present. */
    protected Map<String, Object> restTypeSettings() {
        if (document == null) {
            return Map.of();
        }
        try {
            Object section = document.read("$." + getType());
            if (section instanceof Map<?, ?> map) {
                Map<String, Object> copy = new LinkedHashMap<>();
                map.forEach((key, value) -> copy.put(String.valueOf(key), value));
                return copy;
            }
            return Map.of();
        } catch (PathNotFoundException e) {
            return Map.of();
        }
    }
}
