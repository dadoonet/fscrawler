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
import fr.pilato.elasticsearch.crawler.fs.settings.Elasticsearch;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl.LOOP_INFINITE;
import static fr.pilato.elasticsearch.crawler.fs.framework.Await.awaitBusy;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.INDEX_SUFFIX_FOLDER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public abstract class AbstractFsCrawlerITCase extends AbstractITCase {
    private static final Logger logger = LogManager.getLogger();

    @Before
    public void cleanExistingIndex() throws IOException, ElasticsearchClientException {
        logger.info(" -> Removing existing index [{}*]", getCrawlerName());
        managementService.getClient().deleteIndex(getCrawlerName());
        managementService.getClient().deleteIndex(getCrawlerName() + INDEX_SUFFIX_FOLDER);

        logger.info(" -> Removing existing index templates [{}*]", getCrawlerName());
        managementService.getClient().deleteIndexTemplate("fscrawler_docs_" + getCrawlerName());
        managementService.getClient().deleteIndexTemplate("fscrawler_docs_semantic_" + getCrawlerName());
        managementService.getClient().deleteIndexTemplate("fscrawler_folders_" + getCrawlerName());
    }

    @After
    public void shutdownCrawler() throws InterruptedException, IOException {
        stopCrawler();
    }

    protected FsSettings createTestSettings() {
        return createTestSettings(getCrawlerName());
    }

    protected FsSettings createTestSettings(String name) {
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.setName(name);
        fsSettings.getFs().setUpdateRate(TimeValue.timeValueSeconds(1));
        fsSettings.getFs().setUrl(currentTestResourceDir.toString());

        // Clone the elasticsearchConfiguration to avoid modifying the default one
        // We start with a clean configuration
        Elasticsearch elasticsearch = clone(elasticsearchConfiguration);

        fsSettings.setElasticsearch(elasticsearch);
        fsSettings.getElasticsearch().setIndex(name);
        fsSettings.getElasticsearch().setIndexFolder(name + INDEX_SUFFIX_FOLDER);
        // We explicitly set semantic search to false because IT takes too long time
        fsSettings.getElasticsearch().setSemanticSearch(false);
        return fsSettings;
    }

    protected FsCrawlerImpl startCrawler() throws Exception {
        return startCrawler(createTestSettings());
    }

    protected FsCrawlerImpl startCrawler(FsSettings fsSettings) throws Exception {
        return startCrawler(fsSettings, TimeValue.timeValueSeconds(30));
    }

    protected FsCrawlerImpl startCrawler(final FsSettings fsSettings, TimeValue duration)
            throws Exception {
        logger.info("  --> starting crawler [{}]", fsSettings.getName());
        logger.debug("     with settings [{}]", fsSettings);

        crawler = new FsCrawlerImpl(
                metadataDir,
                fsSettings,
                LOOP_INFINITE,
                fsSettings.getRest() != null);
        crawler.start();

        // We wait up to X seconds before considering a failing test
        assertThat("Job meta file should exists in ~/.fscrawler...", awaitBusy(() -> {
            try {
                new FsJobFileHandler(metadataDir).read(fsSettings.getName());
                return true;
            } catch (IOException e) {
                return false;
            }
        }, duration), equalTo(true));

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
