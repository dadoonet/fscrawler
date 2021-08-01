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

package fr.pilato.elasticsearch.crawler.fs.crawler.fs;

import fr.pilato.elasticsearch.crawler.fs.crawler.FileAbstractModel;
import fr.pilato.elasticsearch.crawler.fs.crawler.FileAbstractor;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.getCreationTime;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.getFileExtension;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.getFilePermissions;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.getGroupName;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.getLastAccessTime;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.getModificationTime;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.getOwnerName;

public class FileAbstractorFile extends FileAbstractor<File> {
    private final Logger logger = LogManager.getLogger(FileAbstractorFile.class);

    public FileAbstractorFile(FsSettings fsSettings) {
        super(fsSettings);
    }

    public static String separator = File.separator;

    private String resolveSeparator(String path) {
        if (separator.equals("/")) {
            return path.replace("\\", "/");
        }
        return path.replace("/", "\\");
    }

    @Override
    public FileAbstractModel toFileAbstractModel(String path, File file) {
        return new FileAbstractModel(
                file.getName(),
                file.isFile(),
                getModificationTime(file),
                getCreationTime(file),
                getLastAccessTime(file),
                getFileExtension(file),
                resolveSeparator(path),
                resolveSeparator(file.getAbsolutePath()),
                file.length(),
                getOwnerName(file),
                getGroupName(file),
                getFilePermissions(file));
    }

    @Override
    public InputStream getInputStream(FileAbstractModel file) throws Exception {
        return new FileInputStream(file.getFullpath());
    }

    @Override
    public void closeInputStream(InputStream inputStream) throws IOException {
        inputStream.close();
    }

    @Override
    public Collection<FileAbstractModel> getFiles(String dir) {
        logger.debug("Listing local files from {}", dir);

        File[] files = new File(dir).listFiles(file -> {
            if (fsSettings.getFs().isFollowSymlinks()) return true;
            return !Files.isSymbolicLink(file.toPath());
        });
        Collection<FileAbstractModel> result;

        if (files != null) {
            result = new ArrayList<>(files.length);

            // Iterate other files
            for (File file : files) {
                result.add(toFileAbstractModel(dir, file));
            }
        } else {
            logger.debug("Symlink on windows gives null for listFiles(). Skipping [{}]", dir);
            result = Collections.emptyList();
        }


        logger.debug("{} local files found", result.size());
        return result;
    }

    @Override
    public boolean exists(String dir) {
        return new File(dir).exists();
    }

    @Override
    public void open() {
        // Do nothing because we don't open resources in the File implementation.
    }

    @Override
    public void close() {
        // Do nothing because we don't open resources in the File implementation.
    }
}
