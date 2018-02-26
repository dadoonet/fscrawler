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
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientManager;
import fr.pilato.elasticsearch.crawler.fs.crawler.FsParserAbstract;
import fr.pilato.elasticsearch.crawler.fs.crawler.Plugins;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.settings.Elasticsearch;
import fr.pilato.elasticsearch.crawler.fs.settings.Fs;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.Rest;
import fr.pilato.elasticsearch.crawler.fs.settings.Server;
import org.elasticsearch.action.search.SearchRequest;
import org.junit.After;
import org.junit.Before;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import static fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl.LOOP_INFINITE;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.INDEX_SUFFIX_FOLDER;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.copyDirs;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public abstract class AbstractFsCrawlerITCase extends AbstractITCase {

    FsCrawlerImpl crawler = null;
    Path currentTestResourceDir;

    private static final Path DEFAULT_RESOURCES = Paths.get(getUrl("samples", "common"));

    /**
     * We suppose that each test has its own set of files. Even if we duplicate them, that will make the code
     * more readable.
     * The temp folder which is used as a root is automatically cleaned after the test so we don't have to worry
     * about it.
     */
    @Before
    public void copyTestResources() throws IOException {
        Path testResourceTarget = rootTmpDir.resolve("resources");
        if (Files.notExists(testResourceTarget)) {
            Files.createDirectory(testResourceTarget);
        }

        String currentTestName = getCurrentTestName();
        // We copy files from the src dir to the temp dir
        staticLogger.info("  --> Launching test [{}]", currentTestName);
        String url = getUrl("samples", currentTestName);
        Path from = Paths.get(url);
        currentTestResourceDir = testResourceTarget.resolve(currentTestName);

        if (Files.exists(from)) {
            staticLogger.debug("  --> Copying test resources from [{}]", from);
        } else {
            staticLogger.debug("  --> Copying test resources from [{}]", DEFAULT_RESOURCES);
            from = DEFAULT_RESOURCES;
        }

        copyDirs(from, currentTestResourceDir);

        staticLogger.debug("  --> Test resources ready in [{}]", currentTestResourceDir);
    }

    @Before
    public void cleanExistingIndex() throws IOException {
        logger.info(" -> Removing existing index [{}*]", getCrawlerName());
        elasticsearchClient.deleteIndex(getCrawlerName() + "*");
    }

    @After
    public void shutdownCrawler() throws InterruptedException {
        stopCrawler();
    }

    Fs.Builder fsBuilder() {
        return fsBuilder(currentTestResourceDir.toString(), TimeValue.timeValueSeconds(5));
    }

    Fs.Builder fsBuilder(String dir, TimeValue updateRate) {
        logger.info("  --> creating crawler for dir [{}]", dir);
        return Fs
                .builder()
                .setUrl(dir)
                .setUpdateRate(updateRate);
    }

    Elasticsearch elasticsearchBuilder() {
        return elasticsearchBuilder(getCrawlerName(), getCrawlerName() + INDEX_SUFFIX_FOLDER, 1, null);
    }

    Elasticsearch elasticsearchBuilder(String indexName) {
        return elasticsearchBuilder(indexName, indexName + INDEX_SUFFIX_FOLDER, 1, null);
    }

    void startCrawler() throws Exception {
        FsSettings fsSettings = FsSettings.builder(getCrawlerName())
                .setElasticsearch(elasticsearchBuilder())
                .setFs(fsBuilder().build())
                .build();
        ElasticsearchClientManager elasticsearchClientManager = createElasticsearchClientManager(fsSettings);
        startCrawler(getCrawlerName(), fsSettings, createParser(fsSettings, elasticsearchClientManager), elasticsearchClientManager, false, TimeValue.timeValueSeconds(10));
    }

    FsCrawlerImpl startCrawler(final String jobName, Fs fs, Elasticsearch elasticsearch) throws Exception {
        FsSettings fsSettings = FsSettings.builder(jobName).setElasticsearch(elasticsearch).setFs(fs).build();
        ElasticsearchClientManager elasticsearchClientManager = createElasticsearchClientManager(fsSettings);
        return startCrawler(jobName, fsSettings, createParser(fsSettings, elasticsearchClientManager), elasticsearchClientManager, false, TimeValue.timeValueSeconds(10));
    }

    ElasticsearchClientManager createElasticsearchClientManager(FsSettings fsSettings) {
        return new ElasticsearchClientManager(metadataDir, fsSettings);
    }

    FsParserAbstract createParser(FsSettings fsSettings, ElasticsearchClientManager elasticsearchClientManager) {
        return Plugins.createParser(fsSettings, metadataDir, elasticsearchClientManager, LOOP_INFINITE);
    }

    FsCrawlerImpl startCrawler(final String jobName, Fs fs, Elasticsearch elasticsearch, Server server, Rest rest, TimeValue duration)
            throws Exception {
        FsSettings fsSettings = FsSettings.builder(jobName).setElasticsearch(elasticsearch).setFs(fs).setServer(server).setRest(rest).build();
        ElasticsearchClientManager elasticsearchClientManager = createElasticsearchClientManager(fsSettings);
        return startCrawler(jobName, fsSettings, createParser(fsSettings, elasticsearchClientManager), elasticsearchClientManager, rest != null, duration);
    }

    FsCrawlerImpl startCrawler(final String jobName, FsSettings fsSettings,
                               FsParserAbstract parser,
                               ElasticsearchClientManager elasticsearchClientManager,
                               boolean rest, TimeValue duration)
            throws Exception {
        logger.info("  --> starting crawler [{}]", jobName);

        crawler = new FsCrawlerImpl(
                metadataDir,
                fsSettings,
                rest,
                elasticsearchClientManager,
                parser);
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

        countTestHelper(new SearchRequest(jobName), null, null);

        // Make sure we refresh indexed docs before launching tests
        refresh();

        return crawler;
    }

    private void stopCrawler() throws InterruptedException {
        if (crawler != null) {
            staticLogger.info("  --> Stopping crawler");
            crawler.close();
            crawler = null;
        }
    }
}
