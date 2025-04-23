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
package fr.pilato.elasticsearch.crawler.plugins;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.pf4j.Plugin;

/**
 * Defines a FSCrawler plugin as an extension point
 */
public abstract class FsCrawlerPlugin extends Plugin {

    private final Logger logger = LogManager.getLogger();

    protected abstract String getName();

    @Override
    public void start() {
        logger.debug("Starting FsCrawler plugin [{}]", this.getName());
    }

    @Override
    public void stop() {
        logger.debug("Stopping FsCrawler plugin [{}]", this.getName());
    }
}
