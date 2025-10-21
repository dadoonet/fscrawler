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
import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerExtensionFsProviderAbstract;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.pf4j.Extension;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.computeVirtualPathName;

public class FsLocalPlugin extends FsCrawlerPlugin {
    private static final Logger logger = LogManager.getLogger();

    @Override
    protected String getName() {
        return "fs-local";
    }

    @Extension
    public static class FsCrawlerExtensionFsProviderLocal extends FsCrawlerExtensionFsProviderAbstract {
        private Path path;
        private String url;

        @Override
        public String getType() {
            return "local";
        }

        @Override
        public InputStream readFile() throws IOException {
            return Files.newInputStream(path);
        }

        private String getFilename() {
            return path.getFileName().toString();
        }

        private long getFilesize() throws IOException {
            return Files.size(path);
        }

        @Override
        public Doc createDocument() throws IOException {
            logger.debug("Creating document from {}", getFilename());
            String filename = getFilename();

            Doc doc = new Doc();
            // The file name without the path
            doc.getFile().setFilename(filename);
            doc.getFile().setFilesize(getFilesize());
            // The virtual URL (not including the initial root dir)
            doc.getPath().setVirtual(computeVirtualPathName(fsSettings.getFs().getUrl(), filename));
            // The real URL on the filesystem
            doc.getPath().setReal(path.toAbsolutePath().toString());
            return doc;
        }

        @Override
        protected void parseSettings() throws PathNotFoundException {
            url = document.read("$.local.url");
        }

        @Override
        protected void validateSettings() throws IOException {
            Path rootPath = Path.of(fsSettings.getFs().getUrl()).toAbsolutePath().normalize();
            logger.debug("Reading file {} from {}", url, rootPath);

            path = rootPath.resolve(url).normalize();
            if (Files.notExists(path)) {
                throw new IOException("File " + path.toAbsolutePath() + " does not exist");
            }

            // Check that the url is under the rootPath
            if (!path.startsWith(rootPath)) {
                throw new IOException("File " + path.toAbsolutePath() + " is not within " + rootPath);
            }
        }
    }
}
