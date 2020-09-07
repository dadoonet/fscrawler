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

package fr.pilato.elasticsearch.crawler.fs.cli;

import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsFileHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class FsCrawlerJobsUtil {

    private static final Logger logger = LogManager.getLogger(FsCrawlerJobsUtil.class);

    public static List<String> listExistingJobs(Path configDir) {
        List<String> files = new ArrayList<>();
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(configDir)) {
            for (Path path : directoryStream) {
                // This is a directory. Let's see if we have the _settings.json file in it
                if (Files.isDirectory(path)) {
                    String jobName = path.getFileName().toString();
                    if (Files.exists(path.resolve(FsSettingsFileHandler.SETTINGS_YAML))
                            || Files.exists(path.resolve(FsSettingsFileHandler.FILENAME_JSON))) {
                        files.add(jobName);
                        logger.debug("Adding job [{}]", jobName);
                    } else {
                        logger.debug("Ignoring [{}] dir as no settings file has been found", jobName);
                    }
                }
            }
        } catch (IOException ignored) {}

        return files;
    }
}
