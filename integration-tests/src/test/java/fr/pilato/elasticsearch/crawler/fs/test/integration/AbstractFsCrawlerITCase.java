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
import fr.pilato.elasticsearch.crawler.fs.settings.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl.LOOP_INFINITE;
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

    protected Fs.Builder startCrawlerDefinition() {
        return startCrawlerDefinition(currentTestResourceDir.toString(), TimeValue.timeValueSeconds(5));
    }

    protected Fs.Builder startCrawlerDefinition(TimeValue updateRate) {
        return startCrawlerDefinition(currentTestResourceDir.toString(), updateRate);
    }

    protected Fs.Builder startCrawlerDefinition(String dir) {
        return startCrawlerDefinition(dir, TimeValue.timeValueSeconds(5));
    }

    private Fs.Builder startCrawlerDefinition(String dir, TimeValue updateRate) {
        logger.info("  --> creating crawler for dir [{}]", dir);
        return Fs
                .builder()
                .setUrl(dir)
                .setUpdateRate(updateRate);
    }

    protected Elasticsearch endCrawlerDefinition(String indexName) {
        return endCrawlerDefinition(indexName, indexName + INDEX_SUFFIX_FOLDER);
    }

    private Elasticsearch endCrawlerDefinition(String indexDocName, String indexFolderName) {
        return generateElasticsearchConfig(indexDocName, indexFolderName, 1, null, null, false, false);
    }

    protected FsCrawlerImpl startCrawler() throws Exception {
        return startCrawler(getCrawlerName());
    }

    private FsCrawlerImpl startCrawler(final String jobName) throws Exception {
        return startCrawler(jobName, startCrawlerDefinition().build(), endCrawlerDefinition(jobName), null, null);
    }

    protected FsCrawlerImpl startCrawler(final String jobName, Fs fs, Elasticsearch elasticsearch, Server server, Tags tags) throws Exception {
        return startCrawler(jobName, fs, elasticsearch, server, null, tags, TimeValue.timeValueSeconds(30));
    }

    protected FsCrawlerImpl startCrawler(final String jobName, Fs fs, Elasticsearch elasticsearch, Server server, Rest rest,
                                         Tags tags, TimeValue duration)
            throws Exception {
        FsSettings.Builder builder = FsSettings.builder(jobName);
        if (elasticsearch != null) builder.setElasticsearch(elasticsearch);
        if (fs != null) builder.setFs(fs);
        if (server != null) builder.setServer(server);
        if (rest != null) builder.setRest(rest);
        if (tags != null) builder.setTags(tags);
        return startCrawler(jobName, builder.build(), duration);
    }

    protected FsCrawlerImpl startCrawler(final String jobName, FsSettings fsSettings, TimeValue duration)
            throws Exception {
        logger.info("  --> starting crawler [{}]", jobName);

        crawler = new FsCrawlerImpl(
                metadataDir,
                fsSettings,
                LOOP_INFINITE,
                fsSettings.getRest() != null);
        crawler.start();

        // We wait up to X seconds before considering a failing test
        assertThat("Job meta file should exists in ~/.fscrawler...", awaitBusy(() -> {
            try {
                new FsJobFileHandler(metadataDir).read(jobName);
                return true;
            } catch (IOException e) {
                return false;
            }
        }, duration.seconds(), TimeUnit.SECONDS), equalTo(true));

        countTestHelper(new ESSearchRequest().withIndex(jobName), null, null);

        // Make sure we refresh indexed docs before launching tests
        refresh();

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
