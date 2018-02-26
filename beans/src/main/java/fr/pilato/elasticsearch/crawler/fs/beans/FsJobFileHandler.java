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

package fr.pilato.elasticsearch.crawler.fs.beans;

import fr.pilato.elasticsearch.crawler.fs.framework.MetaFileHandler;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

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

    /**
     * We clean existing settings in ~/.fscrawler/{job_name}/_status.json
     * @param jobname is the job_name
     * @throws IOException in case of error while removing
     */
    public void clean(String jobname) throws IOException {
        removeFile(jobname, FILENAME);
    }

    @SuppressWarnings("unchecked")
    public LocalDateTime getLastDateFromMeta(String jobName) throws IOException {
        try {
            FsJob fsJob = read(jobName);
            return fsJob.getLastrun();
        } catch (NoSuchFileException e) {
            // The file does not exist yet
        }
        return null;
    }

    /**
     * Update the job metadata
     * @param jobName job name
     * @param scanDate last date we scan the dirs
     * @throws Exception In case of error
     */
    public void updateFsJob(String jobName, LocalDateTime scanDate, ScanStatistic stats) throws Exception {
        // We need to round that latest date to the lower second and
        // remove 2 seconds.
        // See #82: https://github.com/dadoonet/fscrawler/issues/82
        scanDate = scanDate.minus(2, ChronoUnit.SECONDS);
        FsJob fsJob = FsJob.builder()
                .setName(jobName)
                .setLastrun(scanDate)
                .setIndexed(stats.getNbDocScan())
                .setDeleted(stats.getNbDocDeleted())
                .build();
        write(jobName, fsJob);
    }
}
