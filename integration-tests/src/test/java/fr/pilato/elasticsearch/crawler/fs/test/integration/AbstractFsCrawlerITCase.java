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
import fr.pilato.elasticsearch.crawler.fs.beans.FsJobFileHandler;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientException;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;

import java.io.IOException;

import static fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl.LOOP_INFINITE;
import static fr.pilato.elasticsearch.crawler.fs.framework.Await.awaitBusy;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.INDEX_SUFFIX_FOLDER;
import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractFsCrawlerITCase extends AbstractITCase {
    private static final Logger logger = LogManager.getLogger();

    @Before
    public void cleanExistingIndex() throws IOException, ElasticsearchClientException {
        logger.debug(" -> Removing existing index [{}*]", getCrawlerName());
        client.deleteIndex(getCrawlerName());
        client.deleteIndex(getCrawlerName() + INDEX_SUFFIX_FOLDER);

        logger.debug(" -> Removing existing index templates [{}*]", getCrawlerName());
        client.deleteIndexTemplate("fscrawler_docs_" + getCrawlerName());
        client.deleteIndexTemplate("fscrawler_docs_semantic_" + getCrawlerName());
        client.deleteIndexTemplate("fscrawler_folders_" + getCrawlerName());
    }

    @After
    public void shutdownCrawler() throws InterruptedException, IOException {
        stopCrawler();
    }

    protected FsCrawlerImpl startCrawler() throws Exception {
        return startCrawler(createTestSettings());
    }

    protected FsCrawlerImpl startCrawler(FsSettings fsSettings) throws Exception {
        return startCrawler(fsSettings, MAX_WAIT_FOR_SEARCH);
    }

    protected FsCrawlerImpl startCrawler(final FsSettings fsSettings, TimeValue duration)
            throws Exception {
        logger.info("  --> starting crawler [{}]", fsSettings.getName());
        logger.debug("     with settings [{}]", fsSettings);

        crawler = new FsCrawlerImpl(metadataDir, fsSettings, LOOP_INFINITE, false);
        crawler.start();

        // We wait up to X seconds before considering a failing test
        assertThat(awaitBusy(() -> {
            try {
                new FsJobFileHandler(metadataDir).read(fsSettings.getName());
                return true;
            } catch (IOException e) {
                return false;
            }
        }, duration))
                .as("Job meta file should exists in ~/.fscrawler...")
                .isTrue();

        countTestHelper(new ESSearchRequest().withIndex(fsSettings.getElasticsearch().getIndex()), null, null, duration);

        // Make sure we refresh indexed docs and folders before launching tests
        refresh(fsSettings.getElasticsearch().getIndex());
        refresh(fsSettings.getElasticsearch().getIndexFolder());

        return crawler;
    }

    private void stopCrawler() throws InterruptedException, IOException {
        if (crawler != null) {
            logger.info("  --> Stopping crawler");
            crawler.close();
            crawler = null;
        }
    }
}
