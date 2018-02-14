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

package fr.pilato.elasticsearch.crawler.fs.rest;

import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;

public class ServerStatusResponse extends RestResponse {

    private String version;
    private String elasticsearch;
    private FsSettings settings;

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getElasticsearch() {
        return elasticsearch;
    }

    public void setElasticsearch(String elasticsearch) {
        this.elasticsearch = elasticsearch;
    }

    public FsSettings getSettings() {
        return settings;
    }

    public void setSettings(FsSettings settings) {
        this.settings = settings;
    }
}
