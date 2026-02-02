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
import fr.pilato.elasticsearch.crawler.fs.crawler.FileAbstractModel;
import fr.pilato.elasticsearch.crawler.fs.framework.FileAcl;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerExtensionFsCrawler;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerExtensionFsProviderAbstract;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.pf4j.Extension;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.*;

public class FsLocalPlugin extends FsCrawlerPlugin {
    private static final Logger logger = LogManager.getLogger();

    @Override
    protected String getName() {
        return "fs-local";
    }

    @Extension
    public static class FsCrawlerExtensionFsProviderLocal extends FsCrawlerExtensionFsProviderAbstract
            implements FsCrawlerExtensionFsCrawler {

        private Path path;
        private String url;

        private static final Comparator<Path> PATH_COMPARATOR = Comparator.comparing(
                file -> getModificationOrCreationTime(file.toFile()),
                Comparator.nullsLast(Comparator.naturalOrder()));

        @Override
        public String getType() {
            return "local";
        }

        // ========== FsCrawlerExtensionFsProvider methods (REST API) ==========

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

        // ========== FsCrawlerExtensionFsCrawler methods (Crawling) ==========

        @Override
        public void openConnection() {
            // No connection needed for local filesystem
            logger.debug("Opening local filesystem connection");
        }

        @Override
        public void closeConnection() {
            // No connection to close for local filesystem
            logger.debug("Closing local filesystem connection");
        }

        @Override
        public boolean exists(String directory) {
            return new File(directory).exists();
        }

        @Override
        public Collection<FileAbstractModel> getFiles(String dir) {
            logger.debug("Listing local files from {}", dir);

            final Collection<FileAbstractModel> result = new ArrayList<>();
            try (Stream<Path> paths = Files.list(Paths.get(dir))) {
                paths.filter(p -> fsSettings.getFs().isFollowSymlinks() || !Files.isSymbolicLink(p))
                        .sorted(PATH_COMPARATOR.reversed())
                        .forEach(p -> result.add(toFileAbstractModel(dir, p.toFile())));
            } catch (IOException e) {
                logger.warn("Error listing files in {}: {}", dir, e.getMessage());
            }

            logger.debug("{} local files found", result.size());
            return result;
        }

        @Override
        public InputStream getInputStream(FileAbstractModel file) throws Exception {
            return new FileInputStream(file.getFullpath());
        }

        @Override
        public void closeInputStream(InputStream inputStream) throws Exception {
            inputStream.close();
        }

        /**
         * Convert a File to a FileAbstractModel.
         */
        private FileAbstractModel toFileAbstractModel(String path, File file) {
            List<FileAcl> fileAcls = fsSettings.getFs().isAclSupport() && fsSettings.getFs().isAttributesSupport()
                    ? getFileAcls(file.toPath())
                    : Collections.emptyList();

            String separator = getPathSeparator(fsSettings.getFs().getUrl());

            return new FileAbstractModel(
                    file.getName(),
                    file.isFile(),
                    getModificationTime(file),
                    getCreationTime(file),
                    getLastAccessTime(file),
                    getFileExtension(file),
                    resolveSeparator(path, separator),
                    resolveSeparator(file.getAbsolutePath(), separator),
                    file.length(),
                    getOwnerName(file),
                    getGroupName(file),
                    getFilePermissions(file),
                    fileAcls,
                    computeAclHash(fileAcls));
        }

        private String resolveSeparator(String path, String separator) {
            if (separator.equals("/")) {
                return path.replace("\\", "/");
            }
            return path.replace("/", "\\");
        }
    }
}
