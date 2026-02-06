/*
 * Licensed to David Pilato (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
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
 */

package fr.pilato.elasticsearch.crawler.plugins.service;

import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPluginException;
import fr.pilato.elasticsearch.crawler.plugins.AbstractPlugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Abstract base class for service plugins.
 * <p>
 * Service plugins are disabled by default ({@link #isEnabled()} returns false until
 * explicitly set to true via configuration). This ensures that services like REST API
 * or scheduler are only started when the user explicitly enables them.
 */
public abstract class AbstractServicePlugin extends AbstractPlugin implements ServicePlugin {

    /** Tracks whether the service has been started. */
    private volatile boolean running = false;

    public AbstractServicePlugin() {
        super();
        // Service plugins are disabled by default (unlike pipeline plugins)
        this.enabled = false;
    }

    @Override
    protected void configureCommon(Map<String, Object> config) {
        // Service plugins default to disabled; only enable if explicitly set in config
        Boolean enabledVal = getConfigValue(config, "enabled", Boolean.class, Boolean.FALSE);
        this.enabled = enabledVal != null ? enabledVal : false;
        super.configureCommon(config);
    }

    @Override
    protected String getPluginCategoryLabel() {
        return "Service";
    }

    @Override
    public void validateConfiguration() throws FsCrawlerIllegalConfigurationException {
        if (id == null || id.isEmpty()) {
            throw new FsCrawlerIllegalConfigurationException(
                    "Service plugin id is required");
        }
    }

    @Override
    public void start() throws FsCrawlerPluginException {
        if (running) {
            logger.debug("Service plugin [{}] of type [{}] already started", id, getType());
            return;
        }
        logger.debug("Starting service plugin [{}] of type [{}]", id, getType());
        doStart();
        running = true;
    }

    @Override
    public void stop() throws FsCrawlerPluginException {
        if (!running) {
            logger.debug("Service plugin [{}] of type [{}] not running", id, getType());
            return;
        }
        logger.debug("Stopping service plugin [{}] of type [{}]", id, getType());
        doStop();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * Performs the actual start of the service. Subclasses must implement this.
     */
    protected abstract void doStart() throws FsCrawlerPluginException;

    /**
     * Performs the actual stop of the service. Subclasses must implement this.
     */
    protected abstract void doStop() throws FsCrawlerPluginException;

    /**
     * Returns the plugin category for directory organization.
     * Service plugins use the "services" directory.
     */
    @Override
    public String getPluginCategory() {
        return "services";
    }
}
