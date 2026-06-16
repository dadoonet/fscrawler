/*
 * Licensed to David Pilato (the "Author") under one
 * or more contributor license agreements. See the NOTICE file
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
 *
 * Made from 🇫🇷🇪🇺 with ❤️ - 2011-2026
 */
package fr.pilato.elasticsearch.crawler.fs.test.integration;

import fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientException;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import java.io.IOException;
import java.time.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public abstract class AbstractFsCrawlerITCase extends AbstractITCase {
    private static final Logger logger = LogManager.getLogger();

    /**
     * Settings used by the last {@link #startCrawler} call, so {@code @AfterEach} cleanup can remove exactly the
     * templates the crawler created (mirroring its create-time options).
     */
    private FsSettings currentSettings;

    @BeforeEach
    protected void cleanExistingIndex() throws ElasticsearchClientException {
        cleanIndexAndTemplates(currentSettings, getCrawlerName());
        logger.info("🎬 Starting test [{}] with [{}] as the crawler name", jobName, getCrawlerName());
    }

    @AfterEach
    protected void cleanUp() throws ElasticsearchClientException {
        if (!TEST_KEEP_DATA) {
            cleanIndexAndTemplates(currentSettings, getCrawlerName());
        }
        logger.info("✅ End of test [{}] with [{}] as the crawler name", jobName, getCrawlerName());
    }

    @AfterEach
    void shutdownCrawler() throws InterruptedException, IOException {
        if (crawler != null) {
            logger.info("🏁 Stopping crawler [{}]", getCrawlerName());
            crawler.close();
            crawler = null;
        }
    }

    /**
     * Builds a minimal settings object carrying the given crawler's index and folder names, used to drive
     * {@link fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClient#removeIndexAndComponentTemplates(FsSettings)}
     * during cleanup (also reused by multi-crawler subclasses for their {@code _1}/{@code _2} crawler names).
     */
    protected static FsSettings cleanupSettings(String crawlerName) {
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.setName(crawlerName);
        fsSettings.getElasticsearch().setIndex(crawlerName + FsCrawlerUtil.INDEX_SUFFIX_DOCS);
        fsSettings.getElasticsearch().setIndexFolder(crawlerName + FsCrawlerUtil.INDEX_SUFFIX_FOLDER);
        return fsSettings;
    }

    protected FsCrawlerImpl startCrawler() throws Exception {
        return startCrawler(createTestSettings());
    }

    protected FsCrawlerImpl startCrawler(FsSettings fsSettings) throws Exception {
        return startCrawler(fsSettings, MAX_WAIT_FOR_SEARCH);
    }

    protected FsCrawlerImpl startCrawler(final FsSettings fsSettings, Duration duration) throws Exception {
        // Remember the settings so @AfterEach cleanup removes exactly the templates we created.
        this.currentSettings = fsSettings;
        logger.info("🎬 starting crawler [{}]", fsSettings.getName());
        logger.debug("⚙️ with settings [{}]", fsSettings);

        crawler = new FsCrawlerImpl(metadataDir, fsSettings, FsCrawlerImpl.LOOP_INFINITE, false);
        crawler.start();

        // Wait for the index to be healthy as we might have a race condition
        client.waitForHealthyIndex(fsSettings.getElasticsearch().getIndex());

        // We check that we have at least a few documents
        countTestHelper(
                new ESSearchRequest().withIndex(fsSettings.getElasticsearch().getIndex()), null, null, duration);

        // Make sure we refresh indexed docs and folders before launching tests
        refresh(fsSettings.getElasticsearch().getIndex());
        refresh(fsSettings.getElasticsearch().getIndexFolder());

        return crawler;
    }
}
