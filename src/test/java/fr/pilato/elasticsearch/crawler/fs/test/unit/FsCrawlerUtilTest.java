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

import fr.pilato.elasticsearch.crawler.fs.ScanStatistic;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.FsSettingsFileHandler;
import fr.pilato.elasticsearch.crawler.fs.test.AbstractFSCrawlerTestCase;
import fr.pilato.elasticsearch.crawler.fs.util.FsCrawlerUtil;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static fr.pilato.elasticsearch.crawler.fs.util.FsCrawlerUtil.extractMajorVersionNumber;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

/**
 * We want to test some utilities
 */
public class FsCrawlerUtilTest extends AbstractFSCrawlerTestCase {

    @Test
    public void testComputePathLinux() {
        testHelper("/tmp", "/tmp/myfile.txt", "/myfile.txt");
        testHelper("/tmp", "/tmp/dir/myfile.txt", "/dir/myfile.txt");
    }

    @Test
    public void testComputePathWindows() {
        testHelper("C:\\tmp", "C:\\tmp\\myfile.txt", "/myfile.txt");
        testHelper("C:\\tmp", "C:\\tmp\\dir\\myfile.txt", "/dir/myfile.txt");
    }

    private void testHelper(String rootPath, String realPath, String expectedPath) {
        assertThat(FsCrawlerUtil.computeVirtualPathName(new ScanStatistic(rootPath), realPath), is(expectedPath));
    }

    @Test
    public void testExtractingVersion() {
        assertThat(extractMajorVersionNumber("1.2.3"), is("1"));
        assertThat(extractMajorVersionNumber("2.3.1"), is("2"));
        assertThat(extractMajorVersionNumber("5.0.0-SNAPSHOT"), is("5"));
        assertThat(extractMajorVersionNumber("5.0.0.beta4-SNAPSHOT"), is("5"));
        assertThat(extractMajorVersionNumber("1"), is("1"));
    }

    @Test
    public void testListExistingJobs() throws IOException {
        String jobNamePrefix = "fscrawler_list_existing_jobs";
        int numJobs = between(1, 30);

        // We generate so fake jobs first in metadata dir
        FsSettingsFileHandler fsSettingsFileHandler = new FsSettingsFileHandler(metadataDir);

        for (int i = 0; i < numJobs; i++) {
            String jobName = jobNamePrefix + "-" + i;
            Path jobDir = metadataDir.resolve(jobName);
            Files.createDirectories(jobDir);
            fsSettingsFileHandler.write(FsSettings.builder(jobName).build());
        }

        // We test that we can actually see the jobs
        List<String> jobs = FsCrawlerUtil.listExistingJobs(metadataDir);
        assertThat(jobs, hasSize(numJobs));
    }
}
