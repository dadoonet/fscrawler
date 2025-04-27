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

import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import java.io.IOException;
import java.time.LocalDateTime;

import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.prettyMapper;
import static org.assertj.core.api.Assertions.assertThat;

public class FsJobParserTest extends AbstractFSCrawlerTestCase {
    private static final Logger logger = LogManager.getLogger();

    private void jobTester(FsJob source) throws IOException {
        String json = prettyMapper.writeValueAsString(source);

        logger.info("-> generated job: [{}]", json);
        FsJob generated = prettyMapper.readValue(json, FsJob.class);
        assertThat(generated).isEqualTo(source);
    }

    @Test
    public void parseEmptyJob() throws IOException {
        jobTester(new FsJob());
    }

    @Test
    public void parseJob() throws IOException {
        jobTester(new FsJob(getCurrentTestName(), LocalDateTime.now(), 1000, 5));
    }

    /**
     * We check that the date which is generated on disk does not change when we read it again
     * @throws IOException In case of serialization problem
     */
    @Test
    public void dateTimeSerialization() throws IOException {
        LocalDateTime now = LocalDateTime.now();
        FsJob job = new FsJob(getCurrentTestName(), now, 1000, 5);
        String json = prettyMapper.writeValueAsString(job);
        FsJob generated = prettyMapper.readValue(json, FsJob.class);
        assertThat(generated.getLastrun()).isEqualTo(now);
    }
}
