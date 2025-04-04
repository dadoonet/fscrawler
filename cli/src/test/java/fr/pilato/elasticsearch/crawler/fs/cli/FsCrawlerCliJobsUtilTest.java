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

import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerMetadataTestCase;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.carrotsearch.randomizedtesting.RandomizedTest.randomBoolean;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * We want to test some utilities
 */
public class FsCrawlerCliJobsUtilTest extends AbstractFSCrawlerMetadataTestCase {

    @Test
    public void listExistingJobs() throws IOException {
        String jobNamePrefix = "fscrawler_list_existing_jobs";
        int numJobs = between(1, 30);

        // We generate so fake jobs first in metadata dir
        FsSettingsLoader fsSettingsLoader = new FsSettingsLoader(metadataDir);

        for (int i = 0; i < numJobs; i++) {
            String jobName = jobNamePrefix + "-" + i;
            Path jobDir = metadataDir.resolve(jobName);
            Files.createDirectories(jobDir);
            if (randomBoolean()) {
                // Yaml settings file
                fsSettingsLoader.write(jobName, new FsSettings());
            } else {
                // Json settings (empty dummy)
                Files.createFile(jobDir.resolve(FsSettingsLoader.SETTINGS_JSON));
            }
        }

        // We test that we can actually see the jobs
        List<String> jobs = FsCrawlerJobsUtil.listExistingJobs(metadataDir);
        assertThat(jobs).hasSize(numJobs);
    }
}
