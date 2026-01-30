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

package fr.pilato.elasticsearch.crawler.fs.test.integration.elasticsearch;

import fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.junit.Test;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.INDEX_SUFFIX_DOCS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Test loop crawler settings
 */
public class FsCrawlerTestLoopsIT extends AbstractFsCrawlerITCase {

    /**
     * Test case for #227: <a href="https://github.com/dadoonet/fscrawler/issues/227">https://github.com/dadoonet/fscrawler/issues/227</a> : Add support for run only once
     */
    @Test
    public void single_loop() throws Exception {
        crawler = new FsCrawlerImpl(metadataDir, createTestSettings(), 1, false);
        crawler.start();

        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS), 1L, null);

        // Make sure that we wait enough for the crawler to be closed
        await().atMost(10, SECONDS).until(() -> crawler.getFsParser().isClosed());

        assertThat(crawler.getFsParser().getRunNumber()).isEqualTo(1);
    }

    /**
     * Test case for #227: <a href="https://github.com/dadoonet/fscrawler/issues/227">https://github.com/dadoonet/fscrawler/issues/227</a> : Add support for run only once
     */
    @Test
    public void two_loops() throws Exception {
        crawler = new FsCrawlerImpl(metadataDir, createTestSettings(), 2, false);
        crawler.start();

        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS), 1L, null);

        // Make sure that we wait enough for the crawler to be closed
        await().atMost(10, SECONDS).until(() -> crawler.getFsParser().isClosed());

        assertThat(crawler.getFsParser().getRunNumber()).isEqualTo(2);
    }
}
