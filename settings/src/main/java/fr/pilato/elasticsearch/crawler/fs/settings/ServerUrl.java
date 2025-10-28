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

package fr.pilato.elasticsearch.crawler.fs.settings;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class represents a ServerUrl which is basically just a String.
 * This is used in the Elasticsearch.Node class.
 */
@Deprecated
public class ServerUrl {
    private final Logger logger = LogManager.getLogger();

    private String url;

    public void setUrl(String url) {
        logger.fatal("Setting elasticsearch.nodes.url has been removed in favor of elasticsearch.urls. " +
                "Please update your configuration. See https://fscrawler.readthedocs.io/en/latest/admin/fs/elasticsearch.html#node-settings.");
    }
}
