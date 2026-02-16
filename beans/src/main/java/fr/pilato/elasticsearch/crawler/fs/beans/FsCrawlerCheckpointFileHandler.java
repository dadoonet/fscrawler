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

import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.prettyMapper;

/**
 * Provides utility methods to read and write checkpoint files (_checkpoint.json).
 * The checkpoint file allows the crawler to resume from where it left off after
 * an interruption (crash, manual stop, network error, etc.).
 */
public class FsCrawlerCheckpointFileHandler extends MetaFileHandler {

    public static final String FILENAME = "_checkpoint.json";

    public FsCrawlerCheckpointFileHandler(Path root) {
        super(root);
    }

    /**
     * Read checkpoint from ~/.fscrawler/{job_name}/_checkpoint.json
     * @param jobname the job name
     * @return the checkpoint or null if no checkpoint exists
     * @throws IOException in case of error while reading (other than file not found)
     */
    public FsCrawlerCheckpoint read(String jobname) throws IOException {
        try {
            return prettyMapper.readValue(readFile(jobname, FILENAME), FsCrawlerCheckpoint.class);
        } catch (NoSuchFileException e) {
            // No checkpoint file exists, return null
            return null;
        }
    }

    /**
     * Check if a checkpoint exists for the given job
     * @param jobname the job name
     * @return true if a checkpoint file exists
     */
    public boolean exists(String jobname) {
        try {
            return read(jobname) != null;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Write checkpoint to ~/.fscrawler/{job_name}/_checkpoint.json
     * @param jobname the job name
     * @param checkpoint the checkpoint to write
     * @throws IOException in case of error while writing
     */
    public void write(String jobname, FsCrawlerCheckpoint checkpoint) throws IOException {
        writeFile(jobname, FILENAME, prettyMapper.writeValueAsString(checkpoint));
    }

    /**
     * Remove checkpoint file from ~/.fscrawler/{job_name}/_checkpoint.json
     * @param jobname the job name
     * @throws IOException in case of error while removing
     */
    public void clean(String jobname) throws IOException {
        removeFile(jobname, FILENAME);
    }
}
