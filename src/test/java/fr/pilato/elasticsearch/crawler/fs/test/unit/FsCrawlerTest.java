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

package fr.pilato.elasticsearch.crawler.fs.test.unit;

import fr.pilato.elasticsearch.crawler.fs.FsCrawler;
import fr.pilato.elasticsearch.crawler.fs.meta.job.FsJob;
import fr.pilato.elasticsearch.crawler.fs.meta.job.FsJobFileHandler;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.FsSettingsFileHandler;
import fr.pilato.elasticsearch.crawler.fs.test.AbstractFSCrawlerTestCase;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static com.carrotsearch.randomizedtesting.RandomizedTest.randomInt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

/**
 * We want to test FSCrawler main app
 */
public class FsCrawlerTest extends AbstractFSCrawlerTestCase {

    @Test
    public void testListExistingJobs() throws IOException {
        String jobNamePrefix = "fscrawler_list_existing_jobs";
        int numJobs = randomInt(30);

        // We generate so fake jobs first in metadata dir
        FsSettingsFileHandler fsSettingsFileHandler = new FsSettingsFileHandler(metadataDir);

        for (int i = 0; i < numJobs; i++) {
            String jobName = jobNamePrefix + "-" + i;
            Path jobDir = metadataDir.resolve(jobName);
            Files.createDirectories(jobDir);
            fsSettingsFileHandler.write(FsSettings.builder(jobName).build());
        }

        // We test that we can actually see the jobs
        List<String> jobs = FsCrawler.listExistingJobs(metadataDir);
        assertThat(jobs, hasSize(numJobs));
    }

    @Test
    public void testRestartCommand() throws Exception {
        String jobName = "fscrawler_restart_command";

        // We generate a fake status first in metadata dir
        FsSettingsFileHandler fsSettingsFileHandler = new FsSettingsFileHandler(metadataDir);
        FsJobFileHandler fsJobFileHandler = new FsJobFileHandler(metadataDir);

        Path jobDir = metadataDir.resolve(jobName);
        Files.createDirectories(jobDir);


        fsSettingsFileHandler.write(FsSettings.builder(jobName).build());
        fsJobFileHandler.write(jobName, FsJob.builder().build());

        assertThat(Files.exists(jobDir.resolve(FsJobFileHandler.FILENAME)), is(true));

        String[] args = { "--config_dir", metadataDir.toString(), "--loop", "0", "--restart", jobName };

        FsCrawler.main(args);

        assertThat(Files.exists(jobDir.resolve(FsJobFileHandler.FILENAME)), is(false));
    }

}
