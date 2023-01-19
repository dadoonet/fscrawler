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

package fr.pilato.elasticsearch.crawler.fs;

import fr.pilato.elasticsearch.crawler.fs.beans.FsJob;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClient;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientException;
import fr.pilato.elasticsearch.crawler.fs.client.IElasticsearchClient;
import fr.pilato.elasticsearch.crawler.fs.framework.MetaFileHandler;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.prettyMapper;
import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.serialize;
import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.deserialize;

/**
 * Provides utility methods to read and write job status files (_status.json)
 */
public class FsJobFileHandler extends MetaFileHandler {
    private static final Logger logger = LogManager.getLogger(FsParserAbstract.class);

    public static final String FILENAME = "_status.json";
    private final IElasticsearchClient client;

    private boolean useEs = false;
    private String statusIndex = null;

    public FsJobFileHandler(Path root, FsSettings settings) {
        super(root);

        this.client = new ElasticsearchClient(root, settings);

        this.statusIndex = settings.getElasticsearch().getIndexStatus();
        this.useEs = this.statusIndex != null;

    }

    private String getStatusIndex(String jobname) {
        return (statusIndex == null) ? jobname + "_status" : statusIndex;
    }

    /**
     * We read settings in ~/.fscrawler/{job_name}/_status.json
     * 
     * @param jobname is the job_name
     * @return Status status file
     * @throws IOException in case of error while reading
     */
    public FsJob read(String jobname) throws IOException {
        if (useEs) {
            try {
                return readFromEs(jobname);
            } catch (Exception e) {
                return prettyMapper.readValue(readFile(jobname, FILENAME), FsJob.class);
            }
        } else {
            return prettyMapper.readValue(readFile(jobname, FILENAME), FsJob.class);
        }
    }

    private FsJob readFromEs(String jobname) throws IOException {
        var index = getStatusIndex(jobname);

        try {
            client.start();
            client.createIndex(index, true, null);
            var isThere = client.exists(index, jobname);

            if (isThere) {
                var result = client.get(index, jobname);
                return deserialize(result.getSource(), FsJob.class);
            } else {
                throw new NoSuchFileException("not found");
            }

        } catch (ElasticsearchClientException e) {
            logger.info(e.getLocalizedMessage());
            throw new NoSuchFileException(e.getLocalizedMessage());
        }
    }

    /**
     * We write settings to ~/.fscrawler/{job_name}/_status.json
     * 
     * @param jobname is the job_name
     * @param job     Status file to write
     * @throws IOException in case of error while reading
     */
    public void write(String jobname, FsJob job) throws IOException {
        writeFile(jobname, FILENAME, prettyMapper.writeValueAsString(job));

        if (useEs) {
            writeToEs(jobname, job);
        }
    }

    private void writeToEs(String jobname, FsJob job) throws IOException {
        var index = getStatusIndex(jobname);

        try {
            client.start();
            client.createIndex(index, true, null);
            client.indexRawJson(index, jobname, serialize(job), null);
        } catch (ElasticsearchClientException e) {
            throw new IOException(e.getMessage());
        }
    }

    /**
     * We clean existing settings in ~/.fscrawler/{job_name}/_status.json
     * 
     * @param jobname is the job_name
     * @throws IOException in case of error while removing
     */
    public void clean(String jobname) throws IOException {
        // removeFile(jobname, FILENAME);
        cleanEs(jobname);

        if (useEs) {
            cleanEs(jobname);
        }
    }

    private void cleanEs(String jobname) throws IOException {
        client.delete(getStatusIndex(jobname), jobname);
    }
}
