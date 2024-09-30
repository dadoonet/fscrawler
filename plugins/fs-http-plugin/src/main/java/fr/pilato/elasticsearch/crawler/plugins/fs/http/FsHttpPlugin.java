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
package fr.pilato.elasticsearch.crawler.plugins.fs.http;

import com.jayway.jsonpath.PathNotFoundException;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerExtensionFsProviderAbstract;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPlugin;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.pf4j.Extension;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

public class FsHttpPlugin extends FsCrawlerPlugin {
    private static final Logger logger = LogManager.getLogger(FsHttpPlugin.class);

    @Override
    protected String getName() {
        return "fs-http";
    }

    @Extension
    public static class FsCrawlerExtensionFsProviderHttp extends FsCrawlerExtensionFsProviderAbstract {
        private URL url;

        @Override
        public void start() {

        }

        @Override
        public void stop() {

        }

        @Override
        public String getType() {
            return "http";
        }

        @Override
        public InputStream readFile() throws IOException {
            return url.openStream();
        }

        @Override
        public String getFilename() {
            return FilenameUtils.getName(url.getPath());
        }

        @Override
        public long getFilesize() throws IOException {
            return url.openConnection().getContentLengthLong();
        }

        @Override
        protected void parseSettings() throws PathNotFoundException {
            String urlFromJson = document.read("$.http.url");
            logger.debug("Reading http file from [{}]", urlFromJson);
            try {
                url = new URL(urlFromJson);
            } catch (MalformedURLException e) {
                throw new FsCrawlerIllegalConfigurationException("Invalid url [" + url + "]");
            }
        }
    }
}
