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
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.settings.Elasticsearch;
import fr.pilato.elasticsearch.crawler.fs.settings.Fs;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.Rest;
import fr.pilato.elasticsearch.crawler.fs.settings.Server;
import org.junit.After;
import org.junit.Before;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl.LOOP_INFINITE;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.INDEX_SUFFIX_FOLDER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public abstract class AbstractFsCrawlerITCase extends AbstractITCase {

    @Before
    public void cleanExistingIndex() throws IOException {
        logger.info(" -> Removing existing index [{}*]", getCrawlerName());
        esClient.deleteIndex(getCrawlerName() + "*");
    }

    @After
    public void shutdownCrawler() throws InterruptedException, IOException {
        stopCrawler();
    }

    Fs.Builder startCrawlerDefinition() {
        return startCrawlerDefinition(currentTestResourceDir.toString(), TimeValue.timeValueSeconds(5));
    }

    Fs.Builder startCrawlerDefinition(TimeValue updateRate) {
        return startCrawlerDefinition(currentTestResourceDir.toString(), updateRate);
    }

    Fs.Builder startCrawlerDefinition(String dir) {
        return startCrawlerDefinition(dir, TimeValue.timeValueSeconds(5));
    }

    private Fs.Builder startCrawlerDefinition(String dir, TimeValue updateRate) {
        logger.info("  --> creating crawler for dir [{}]", dir);
        return Fs
                .builder()
                .setUrl(dir)
                .setUpdateRate(updateRate);
    }

    Elasticsearch endCrawlerDefinition(String indexName) {
        return endCrawlerDefinition(indexName, indexName + INDEX_SUFFIX_FOLDER);
    }

    private Elasticsearch endCrawlerDefinition(String indexDocName, String indexFolderName) {
        return generateElasticsearchConfig(indexDocName, indexFolderName, 1, null, null);
    }

    void startCrawler() throws Exception {
        startCrawler(getCrawlerName());
    }

    private void startCrawler(final String jobName) throws Exception {
        startCrawler(jobName, startCrawlerDefinition().build(), endCrawlerDefinition(jobName), null);
    }

    FsCrawlerImpl startCrawler(final String jobName, Fs fs, Elasticsearch elasticsearch, Server server) throws Exception {
        return startCrawler(jobName, fs, elasticsearch, server, null, TimeValue.timeValueSeconds(10));
    }

    FsCrawlerImpl startCrawler(final String jobName, Fs fs, Elasticsearch elasticsearch, Server server, Rest rest, TimeValue duration)
            throws Exception {
        logger.info("  --> starting crawler [{}]", jobName);

        crawler = new FsCrawlerImpl(
                metadataDir,
                FsSettings.builder(jobName).setElasticsearch(elasticsearch).setFs(fs).setServer(server).setRest(rest).build(),
                LOOP_INFINITE,
                rest != null);
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
            staticLogger.info("  --> Stopping crawler");
            crawler.close();
            crawler = null;
        }
    }
}
