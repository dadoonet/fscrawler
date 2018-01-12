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

package fr.pilato.elasticsearch.crawler.fs.meta.settings;

import fr.pilato.elasticsearch.crawler.fs.meta.MetaFileHandler;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Provides utility methods to read and write settings files
 */
public class FsSettingsFileHandler extends MetaFileHandler {

    @Deprecated
    public static final String LEGACY_EXTENSION = ".json";
    public static final String FILENAME = "_settings.json";

    public FsSettingsFileHandler(Path root) {
        super(root);
    }

    /**
     * We read settings in ~/.fscrawler/{job_name}/_settings.json
     * @param jobname is the job_name
     * @return Settings settings
     * @throws IOException in case of error while reading
     */
    public FsSettings read(String jobname) throws IOException {
        return FsSettingsParser.fromJson(readFile(jobname, FILENAME));
    }

    /**
     * We write settings to ~/.fscrawler/{job_name}/_settings.json
     * @param settings Settings to write (settings.getName() contains the job name)
     * @throws IOException in case of error while reading
     */
    public void write(FsSettings settings) throws IOException {
        writeFile(settings.getName(), FILENAME, FsSettingsParser.toJson(settings));
    }
}
