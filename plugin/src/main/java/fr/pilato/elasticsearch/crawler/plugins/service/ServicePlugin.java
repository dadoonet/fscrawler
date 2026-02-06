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

import fr.pilato.elasticsearch.crawler.plugins.pipeline.ConfigurablePlugin;

/**
 * Plugin interface for transversal services that are not part of the document pipeline.
 * <p>
 * Service plugins (REST API, scheduler, metrics, APM, etc.) are discovered and loaded
 * like input/filter/output plugins, but only started when {@link #isEnabled()} is true.
 * By default, service plugins are disabled ({@code enabled: false}) and must be explicitly
 * enabled in configuration.
 */
public interface ServicePlugin extends ConfigurablePlugin {

    /**
     * Starts this service.
     * Implementations should be idempotent: calling start() when already running is a no-op.
     *
     * @throws fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPluginException if the service fails to start
     */
    void start() throws fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPluginException;

    /**
     * Stops this service.
     * Calling stop() when not running is a no-op.
     *
     * @throws fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPluginException if the service fails to stop
     */
    void stop() throws fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPluginException;

    /**
     * Returns whether this service is currently running.
     *
     * @return true if the service has been started and not yet stopped
     */
    boolean isRunning();
}
