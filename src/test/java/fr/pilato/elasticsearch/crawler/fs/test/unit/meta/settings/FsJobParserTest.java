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

package fr.pilato.elasticsearch.crawler.fs.test.unit.meta.settings;

import fr.pilato.elasticsearch.crawler.fs.meta.job.FsJob;
import fr.pilato.elasticsearch.crawler.fs.meta.job.FsJobParser;
import fr.pilato.elasticsearch.crawler.fs.test.AbstractFSCrawlerTestCase;
import org.junit.Test;

import java.io.IOException;
import java.time.Instant;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class FsJobParserTest extends AbstractFSCrawlerTestCase {

    private static final FsJob JOB_EMPTY = FsJob.builder().build();

    private void jobTester(FsJob source) throws IOException {
        String json = FsJobParser.toJson(source);

        logger.info("-> generated job: [{}]", json);
        FsJob generated = FsJobParser.fromJson(json);
        assertThat(generated, is(source));
    }

    @Test
    public void testParseEmptyJob() throws IOException {
        jobTester(JOB_EMPTY);
    }

    @Test
    public void testParseJob() throws IOException {
        jobTester(
                FsJob.builder()
                        .setName(getCurrentTestName())
                        .setLastrun(Instant.now())
                        .setIndexed(1000)
                        .setDeleted(5)
                        .build()
        );
    }
}
