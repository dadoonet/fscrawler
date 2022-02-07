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

package fr.pilato.elasticsearch.crawler.fs.client;

import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Test elasticsearch HTTP client
 */
public class ElasticsearchClientIT extends AbstractFSCrawlerTestCase {

    protected final Logger logger = LogManager.getLogger(ElasticsearchClientIT.class);
    private ElasticsearchClient esClient;

    @Before
    public void createAndStartClient() throws IOException {
        esClient = new ElasticsearchClientV7(null, FsSettings.builder("foo").build());
        esClient.start();
    }

    @After
    public void stopClient() throws IOException {
        if (esClient != null) {
            esClient.close();
        }
        esClient = null;
    }

    @Test
    public void testDeleteIndex() throws IOException, ElasticsearchClientException {
        esClient.deleteIndex("fscrawler-esclient-test-index");
        esClient.createIndex("fscrawler-esclient-test-index", false, "{}");
        assertThat(esClient.isExistingIndex("fscrawler-esclient-test-index"), is(true));
        esClient.deleteIndex("fscrawler-esclient-test-index");
        assertThat(esClient.isExistingIndex("fscrawler-esclient-test-index"), is(false));
        esClient.deleteIndex("does-not-exist-index");
    }
}
