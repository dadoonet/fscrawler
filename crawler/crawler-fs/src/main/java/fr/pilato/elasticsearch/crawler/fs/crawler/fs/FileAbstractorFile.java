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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.stream.Stream;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.*;

public class FileAbstractorFile extends FileAbstractor<File> {
    private static final Logger logger = LogManager.getLogger();

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

    private static final Comparator<Path> PATH_COMPARATOR = Comparator.comparing(
            file -> getModificationOrCreationTime(file.toFile()));

    @Override
    public FileAbstractModel toFileAbstractModel(String path, File file) {
        final boolean collectAcls = fsSettings.getFs().isAclSupport();
        System.out.println("[ACL DEBUG] Collecting ACLs for file " + file.getAbsolutePath() + " -> " + collectAcls);
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
                getFilePermissions(file),
                collectAcls ? getFileAcls(file) : Collections.emptyList());
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

        final Collection<FileAbstractModel> result = new ArrayList<>();
        try (Stream<Path> paths = Files.list(Paths.get(dir))) {
            paths.filter(p -> fsSettings.getFs().isFollowSymlinks() || !Files.isSymbolicLink(p))
                    // TODO We can add the filter directly here
                    // .filter(s -> s.toString().endsWith(".xml"))
                    .sorted(PATH_COMPARATOR.reversed())
                    .forEach(p -> result.add(toFileAbstractModel(dir, p.toFile())));
        } catch (IOException e) {
            // Logger
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
