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

package fr.pilato.elasticsearch.crawler.plugins.pipeline.input;

import fr.pilato.elasticsearch.crawler.fs.beans.FileAbstractModel;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPluginException;
import fr.pilato.elasticsearch.crawler.plugins.pipeline.AbstractPlugin;
import fr.pilato.elasticsearch.crawler.plugins.pipeline.PipelineContext;

import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Abstract base class for input plugins.
 * Provides common functionality and default implementations.
 */
public abstract class AbstractInputPlugin extends AbstractPlugin implements InputPlugin {

    protected String updateRate;
    protected List<String> includes;
    protected List<String> excludes;
    protected List<String> tags;

    @Override
    protected String getPluginCategory() {
        return "Input";
    }

    @Override
    public List<String> getTags() {
        return tags;
    }

    @Override
    public String getUpdateRate() {
        return updateRate;
    }

    @Override
    protected void configureCommon(Map<String, Object> config) {
        super.configureCommon(config);
        
        this.updateRate = getConfigValue(config, "update_rate", String.class, null);
        this.includes = getConfigList(config, "includes");
        this.excludes = getConfigList(config, "excludes");
        this.tags = getConfigList(config, "tags");

        // Fallback to global settings for backward compatibility
        if (updateRate == null && globalSettings.getFs() != null && globalSettings.getFs().getUpdateRate() != null) {
            this.updateRate = globalSettings.getFs().getUpdateRate().toString();
        }
        if (includes == null && globalSettings.getFs() != null) {
            this.includes = globalSettings.getFs().getIncludes();
        }
        if (excludes == null && globalSettings.getFs() != null) {
            this.excludes = globalSettings.getFs().getExcludes();
        }
    }

    @Override
    public void start() throws FsCrawlerPluginException {
        logger.debug("Starting input plugin [{}] of type [{}]", id, getType());
    }

    @Override
    public void stop() throws FsCrawlerPluginException {
        logger.debug("Stopping input plugin [{}] of type [{}]", id, getType());
    }

    // ========== Default implementations for optional methods ==========

    @Override
    public boolean supportsCrawling() {
        return false;
    }

    @Override
    public void openConnection() throws FsCrawlerPluginException {
        throw new FsCrawlerPluginException("Crawling not supported by " + getType() + " input plugin");
    }

    @Override
    public void closeConnection() throws FsCrawlerPluginException {
        throw new FsCrawlerPluginException("Crawling not supported by " + getType() + " input plugin");
    }

    @Override
    public boolean exists(String directory) throws FsCrawlerPluginException {
        throw new FsCrawlerPluginException("Crawling not supported by " + getType() + " input plugin");
    }

    @Override
    public Collection<FileAbstractModel> getFiles(String directory) throws FsCrawlerPluginException {
        throw new FsCrawlerPluginException("Crawling not supported by " + getType() + " input plugin");
    }

    @Override
    public InputStream getInputStream(FileAbstractModel file) throws FsCrawlerPluginException {
        throw new FsCrawlerPluginException("Crawling not supported by " + getType() + " input plugin");
    }

    @Override
    public void closeInputStream(InputStream inputStream) throws FsCrawlerPluginException {
        throw new FsCrawlerPluginException("Crawling not supported by " + getType() + " input plugin");
    }

    @Override
    public InputStream readFile() throws FsCrawlerPluginException {
        throw new FsCrawlerPluginException("REST API not supported by " + getType() + " input plugin");
    }

    @Override
    public PipelineContext createContext() throws FsCrawlerPluginException {
        throw new FsCrawlerPluginException("REST API not supported by " + getType() + " input plugin");
    }
}
