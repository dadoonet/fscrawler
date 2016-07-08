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

package fr.pilato.elasticsearch.crawler.fs.meta.job;

import fr.pilato.elasticsearch.crawler.fs.meta.MetaFileHandler;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Provides utility methods to read and write job status files (_status.json)
 */
public class FsJobFileHandler extends MetaFileHandler {

    @Deprecated
    public static final String LEGACY_EXTENSION = "_status.json";
    public static final String FILENAME = "_status.json";

    public FsJobFileHandler(Path root) {
        super(root);
    }

    /**
     * We read settings in ~/.fscrawler/{job_name}/_status.json
     * @param jobname is the job_name
     * @return Status status file
     * @throws IOException in case of error while reading
     */
    public FsJob read(String jobname) throws IOException {
        return FsJobParser.fromJson(readFile(jobname, FILENAME));
    }

    /**
     * We write settings to ~/.fscrawler/{job_name}/_status.json
     * @param jobname is the job_name
     * @param job Status file to write
     * @throws IOException in case of error while reading
     */
    public void write(String jobname, FsJob job) throws IOException {
        writeFile(jobname, FILENAME, FsJobParser.toJson(job));
    }
}
