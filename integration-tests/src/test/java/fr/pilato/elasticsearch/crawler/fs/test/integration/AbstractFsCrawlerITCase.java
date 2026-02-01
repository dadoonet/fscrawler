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
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientException;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;

import java.io.IOException;
import java.time.Duration;

import static fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl.LOOP_INFINITE;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.INDEX_SUFFIX_DOCS;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.INDEX_SUFFIX_FOLDER;

public abstract class AbstractFsCrawlerITCase extends AbstractITCase {
    private static final Logger logger = LogManager.getLogger();

    @Before
    public void cleanExistingIndex() throws ElasticsearchClientException {
        logger.debug("🧹 Removing existing index [{}*]", getCrawlerName());
        client.deleteIndex(getCrawlerName() + INDEX_SUFFIX_DOCS);
        client.deleteIndex(getCrawlerName() + INDEX_SUFFIX_FOLDER);

        // Remove existing templates if any
        String templateName = "fscrawler_" + getCrawlerName() + "_*";
        logger.debug("🧹 Removing existing index and component templates [{}]", templateName);
        removeIndexTemplates(templateName);
        removeComponentTemplates(templateName);

        logger.info("🎬 Starting test [{}] with [{}] as the crawler name", getCurrentTestName(), getCrawlerName());
    }

    @After
    public void cleanUp() throws ElasticsearchClientException {
        if (!TEST_KEEP_DATA) {
            logger.debug("🧹 Removing index [{}*]", getCrawlerName());
            client.deleteIndex(getCrawlerName() + INDEX_SUFFIX_DOCS);
            client.deleteIndex(getCrawlerName() + INDEX_SUFFIX_FOLDER);
            // Remove existing templates if any
            String templateName = "fscrawler_" + getCrawlerName() + "_*";
            logger.debug("🧹 Removing existing index and component templates [{}]", templateName);
            removeIndexTemplates(templateName);
            removeComponentTemplates(templateName);
        }

        logger.info("✅ End of test [{}] with [{}] as the crawler name", getCurrentTestName(), getCrawlerName());
    }

    protected static void removeComponentTemplates(String componentTemplateName) {
        logger.trace("🧹 Removing component templates for [{}]", componentTemplateName);
        try {
            client.performLowLevelRequest("DELETE", "/_component_template/" + componentTemplateName, null);
        } catch (ElasticsearchClientException | NotFoundException e) {
            // We ignore the error
        } catch (BadRequestException e) {
            // We ignore the error
            logger.warn("Failed to remove component templates. Got a [{}] when calling [DELETE /_component_template/{}]",
                    e.getMessage(), componentTemplateName);
        }
    }

    protected static void removeIndexTemplates(String indexTemplateName) {
        logger.trace("🧹 Removing index templates for [{}]", indexTemplateName);
        try {
            client.performLowLevelRequest("DELETE", "/_index_template/" + indexTemplateName, null);
        } catch (ElasticsearchClientException | NotFoundException e) {
            // We ignore the error
        } catch (BadRequestException e) {
            // We ignore the error
            logger.warn("Failed to remove index templates. Got a [{}] when calling [DELETE /_index_template/{}]",
                    e.getMessage(), indexTemplateName);
        }
    }

    @After
    public void shutdownCrawler() throws InterruptedException, IOException {
        if (crawler != null) {
            logger.info("🏁 Stopping crawler");
            crawler.close();
            crawler = null;
        }
    }

    protected FsCrawlerImpl startCrawler() throws Exception {
        return startCrawler(createTestSettings());
    }

    protected FsCrawlerImpl startCrawler(FsSettings fsSettings) throws Exception {
        return startCrawler(fsSettings, MAX_WAIT_FOR_SEARCH);
    }

    protected FsCrawlerImpl startCrawler(final FsSettings fsSettings, Duration duration)
            throws Exception {
        logger.info("🎬 starting crawler [{}]", fsSettings.getName());
        logger.debug("⚙️ with settings [{}]", fsSettings);

        crawler = new FsCrawlerImpl(metadataDir, fsSettings, LOOP_INFINITE, false);
        crawler.start();

        // Wait for the index to be healthy as we might have a race condition
        client.waitForHealthyIndex(fsSettings.getElasticsearch().getIndex());

        // We check that we have at least a few documents
        countTestHelper(new ESSearchRequest().withIndex(fsSettings.getElasticsearch().getIndex()), null, null, duration);

        // Make sure we refresh indexed docs and folders before launching tests
        refresh(fsSettings.getElasticsearch().getIndex());
        refresh(fsSettings.getElasticsearch().getIndexFolder());

        return crawler;
    }
}
