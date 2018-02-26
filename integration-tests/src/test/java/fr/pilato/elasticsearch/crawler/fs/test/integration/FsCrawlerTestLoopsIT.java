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

package fr.pilato.elasticsearch.crawler.fs.test.integration;

import fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientManager;
import fr.pilato.elasticsearch.crawler.fs.crawler.FsParserAbstract;
import fr.pilato.elasticsearch.crawler.fs.crawler.fs.FsParserLocal;
import fr.pilato.elasticsearch.crawler.fs.settings.Fs;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import org.elasticsearch.action.search.SearchRequest;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Test loop crawler settings
 */
public class FsCrawlerTestLoopsIT extends AbstractFsCrawlerITCase {

    /**
     * Test case for #227: https://github.com/dadoonet/fscrawler/issues/227 : Add support for run only once
     */
    @Test
    public void test_single_loop() throws Exception {
        Fs fs = fsBuilder().build();

        logger.info("  --> starting crawler [{}]", getCrawlerName());

        FsSettings fsSettings = FsSettings.builder(getCrawlerName())
                .setElasticsearch(elasticsearchBuilder()).setFs(fs).build();
        ElasticsearchClientManager esClientManager = new ElasticsearchClientManager(metadataDir, fsSettings);
        FsParserAbstract parser = new FsParserLocal(fsSettings, metadataDir, esClientManager, 1);
        crawler = new FsCrawlerImpl(metadataDir, fsSettings, false, esClientManager, parser);
        crawler.start();

        countTestHelper(new SearchRequest(getCrawlerName()), 1L, null);

        assertThat("Job should stop after one run", crawler.getFsParser().isClosed(), is(true));
        assertThat(crawler.getFsParser().getRunNumber(), is(1));
    }

    /**
     * Test case for #227: https://github.com/dadoonet/fscrawler/issues/227 : Add support for run only once
     */
    @Test
    public void test_two_loops() throws Exception {
        Fs fs = fsBuilder().build();

        logger.info("  --> starting crawler [{}]", getCrawlerName());

        FsSettings fsSettings = FsSettings.builder(getCrawlerName())
                .setElasticsearch(elasticsearchBuilder()).setFs(fs).build();
        ElasticsearchClientManager esClientManager = new ElasticsearchClientManager(metadataDir, fsSettings);
        FsParserAbstract parser = new FsParserLocal(fsSettings, metadataDir, esClientManager, 2);
        crawler = new FsCrawlerImpl(metadataDir, fsSettings, false, esClientManager, parser);
        crawler.start();

        countTestHelper(new SearchRequest(getCrawlerName()), 1L, null);

        assertThat("Job should stop after two runs", awaitBusy(() -> crawler.getFsParser().isClosed()), is(true));
        assertThat(crawler.getFsParser().getRunNumber(), is(2));
    }
}
