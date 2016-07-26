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

package fr.pilato.elasticsearch.crawler.fs.fileabstractor;

import fr.pilato.elasticsearch.crawler.fs.meta.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.util.FsCrawlerUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class FileAbstractorFile extends FileAbstractor<File> {
    public FileAbstractorFile(FsSettings fsSettings) {
        super(fsSettings);
    }

    @Override
    public FileAbstractModel toFileAbstractModel(String path, File file) {
        FileAbstractModel model = new FileAbstractModel();
        model.name = file.getName();
        model.file = file.isFile();
        model.directory = !model.file;
        model.lastModifiedDate = Instant.ofEpochMilli(file.lastModified());
        model.creationDate = FsCrawlerUtil.getCreationTime(file);
        model.path = path;
        model.fullpath = file.getAbsolutePath();
        model.size = file.length();
        model.owner = FsCrawlerUtil.getOwnerName(file);
        model.group = FsCrawlerUtil.getGroupName(file);

        return model;
    }

    @Override
    public InputStream getInputStream(FileAbstractModel file) throws Exception {
        return new FileInputStream(new File(file.fullpath));
    }

    @Override
    public Collection<FileAbstractModel> getFiles(String dir) {
        logger.debug("Listing local files from {}", dir);
        File[] files = new File(dir).listFiles();
        Collection<FileAbstractModel> result;

        if (files != null) {
            result = new ArrayList<>(files.length);

            // Iterate other files
            for (File file : files) {
                result.add(toFileAbstractModel(dir, file));
            }
        } else {
            logger.debug("Symlink on windows gives null for listFiles(). Skipping [{}]", dir);
            result = Collections.EMPTY_LIST;
        }


        logger.debug("{} local files found", result.size());
        return result;
    }

    @Override
    public boolean exists(String dir) throws Exception {
        return new File(dir).exists();
    }

    @Override
    public void open() throws Exception {

    }

    @Override
    public void close() throws Exception {

    }
}
