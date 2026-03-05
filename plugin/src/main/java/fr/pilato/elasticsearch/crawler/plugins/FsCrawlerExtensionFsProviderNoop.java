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

import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.beans.FileAbstractModel;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;

import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;

/**
 * No-op filesystem provider used when {@code loop == 0} (REST-only mode).
 * No crawler backend is required; this avoids failing at construction time when
 * a remote provider (e.g. SSH/FTP) is configured but server settings are missing
 * or the plugin would fail to start.
 */
public class FsCrawlerExtensionFsProviderNoop implements FsCrawlerExtensionFsProvider {

    public static final String TYPE = "noop";

    @Override
    public void start(FsSettings fsSettings, String restSettings) {
        // No-op: no connection or config required
    }

    @Override
    public void stop() throws FsCrawlerPluginException {
        // No-op
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public InputStream readFile() throws FsCrawlerPluginException {
        throw new FsCrawlerPluginException("No-op provider cannot be used for REST document uploads");
    }

    @Override
    public Doc createDocument() throws FsCrawlerPluginException {
        throw new FsCrawlerPluginException("No-op provider cannot be used for REST document uploads");
    }

    @Override
    public boolean supportsCrawling() {
        return true;
    }

    @Override
    public void openConnection() throws FsCrawlerPluginException {
        // No-op
    }

    @Override
    public void closeConnection() throws FsCrawlerPluginException {
        // No-op
    }

    @Override
    public boolean exists(String directory) {
        return true;
    }

    @Override
    public Collection<FileAbstractModel> getFiles(String directory) throws FsCrawlerPluginException {
        return Collections.emptyList();
    }

    @Override
    public InputStream getInputStream(FileAbstractModel file) throws FsCrawlerPluginException {
        throw new FsCrawlerPluginException("No-op provider does not read files");
    }

    @Override
    public void closeInputStream(InputStream inputStream) throws FsCrawlerPluginException {
        // No-op
    }

    @Override
    public void close() throws Exception {
        stop();
    }
}
