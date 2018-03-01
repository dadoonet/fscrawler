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

import fr.pilato.elasticsearch.crawler.fs.beans.FileModel;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientManager;
import fr.pilato.elasticsearch.crawler.fs.crawler.FsParserAbstract;
import fr.pilato.elasticsearch.crawler.fs.crawler.FsParserPlugin;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.getCreationTime;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.getFileExtension;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.getGroupName;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.getOwnerName;

@FsParserPlugin(name = FsParserLocal.NAME)
public class FsParserLocal extends FsParserAbstract {

    public static final String NAME = "local";
    private static final Logger logger = LogManager.getLogger(FsParserLocal.class);

    public FsParserLocal(FsSettings fsSettings,
                         Path config,
                         ElasticsearchClientManager esClientManager,
                         Integer loop) {
        super(fsSettings, config, esClientManager, loop);
    }

    @Override
    public void validate() {
        // Check that the Local directory we want to crawl exists
        if (!new File(fsSettings.getFs().getUrl()).exists()) {
            throw new RuntimeException(fsSettings.getFs().getUrl() + " doesn't exists.");
        }
    }

    @Override
    public InputStream getInputStream(FileModel file) throws Exception {
        return new FileInputStream(new File(file.fullpath));
    }

    @Override
    public Collection<FileModel> getFiles(String dir) {
        logger.debug("Listing local files from {}", dir);
        File[] files = new File(dir).listFiles();
        Collection<FileModel> result;

        if (files != null) {
            result = new ArrayList<>(files.length);

            // Iterate other files
            for (File file : files) {
                FileModel model = new FileModel();
                model.name = file.getName();
                model.file = file.isFile();
                model.directory = !model.file;
                model.lastModifiedDate = LocalDateTime.ofInstant(Instant.ofEpochMilli(file.lastModified()), ZoneId.systemDefault());
                model.creationDate = getCreationTime(file);
                model.extension = getFileExtension(file);
                model.path = dir;
                model.fullpath = file.getAbsolutePath();
                model.size = file.length();
                model.owner = getOwnerName(file);
                model.group = getGroupName(file);
                result.add(model);
            }
        } else {
            logger.debug("Symlink on windows gives null for listFiles(). Skipping [{}]", dir);
            result = Collections.emptyList();
        }


        logger.debug("{} local files found", result.size());
        return result;
    }
}
