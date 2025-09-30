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
package fr.pilato.elasticsearch.crawler.plugins.fs.local;

import com.jayway.jsonpath.PathNotFoundException;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerExtensionFsProviderAbstract;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.pf4j.Extension;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class FsLocalPlugin extends FsCrawlerPlugin {
    private static final Logger logger = LogManager.getLogger();

    @Override
    protected String getName() {
        return "fs-local";
    }

    @Extension
    public static class FsCrawlerExtensionFsProviderLocal extends FsCrawlerExtensionFsProviderAbstract {
        private Path path;
        private String root;

        @Override
        public void start() {

        }

        @Override
        public void stop() {

        }

        @Override
        public String getType() {
            return "local";
        }

        @Override
        public InputStream readFile() throws IOException {
            return Files.newInputStream(path);
        }

        @Override
        public String getFilename() {
            // If root is provided, compute virtual path by removing the root
            if (root != null && !root.isEmpty()) {
                String fullPath = path.toString();
                if (fullPath.startsWith(root)) {
                    String virtualPath = fullPath.substring(root.length());
                    // Ensure the virtual path starts with a separator if it's not empty
                    if (!virtualPath.isEmpty() && !virtualPath.startsWith("/") && !virtualPath.startsWith("\\")) {
                        String separator = root.contains("/") ? "/" : "\\";
                        virtualPath = separator + virtualPath;
                    }
                    logger.debug("Computed virtual path [{}] from full path [{}] and root [{}]", virtualPath, fullPath, root);
                    return virtualPath;
                }
            }
            // Fallback to just the filename if no root or path doesn't start with root
            return path.getFileName().toString();
        }

        @Override
        public long getFilesize() throws IOException {
            return Files.size(path);
        }

        @Override
        protected void parseSettings() throws PathNotFoundException {
            String url = document.read("$.local.url");
            logger.debug("Reading local file from [{}]", url);
            path = Path.of(url);
            
            // Try to read optional root parameter
            try {
                root = document.read("$.local.root");
                logger.debug("Root path configured as [{}]", root);
            } catch (PathNotFoundException e) {
                logger.debug("No root path configured, will use filename only");
                root = null;
            }
        }
    }
}
