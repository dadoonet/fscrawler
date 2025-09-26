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

import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientException;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import java.nio.file.Files;

import static fr.pilato.elasticsearch.crawler.fs.framework.Await.awaitBusy;
import static fr.pilato.elasticsearch.crawler.fs.framework.TimeValue.MAX_WAIT_FOR_SEARCH;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test filename_as_id crawler setting
 */
public class FsCrawlerTestFilenameAsIdIT extends AbstractFsCrawlerITCase {
    private static final Logger logger = LogManager.getLogger();

    /**
     * Test case for issue #7: <a href="https://github.com/dadoonet/fscrawler/issues/7">https://github.com/dadoonet/fscrawler/issues/7</a> : Use filename as ID
     */
    @Test
    public void filename_as_id() throws Exception {
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setFilenameAsId(true);
        crawler = startCrawler(fsSettings);

        assertThat(awaitBusy(() -> {
            try {
                return client.exists(getCrawlerName(), "roottxtfile.txt");
            } catch (ElasticsearchClientException e) {
                return false;
            }
        }, MAX_WAIT_FOR_SEARCH))
                .as("Document should exists with [roottxtfile.txt] id...")
                .isTrue();
    }

    /**
     * Test case for #336: <a href="https://github.com/dadoonet/fscrawler/issues/336">https://github.com/dadoonet/fscrawler/issues/336</a>
     */
    @Test
    public void remove_deleted_with_filename_as_id() throws Exception {
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setRemoveDeleted(true);
        fsSettings.getFs().setFilenameAsId(true);
        crawler = startCrawler(fsSettings);

        // We should have two docs first
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 2L, currentTestResourceDir);

        assertThat(awaitBusy(() -> {
            try {
                return client.exists(getCrawlerName(), "id1.txt");
            } catch (ElasticsearchClientException e) {
                return false;
            }
        }))
                .as("Document should exists with [id1.txt] id...")
                .isTrue();
        assertThat(awaitBusy(() -> {
            try {
                return client.exists(getCrawlerName(), "id2.txt");
            } catch (ElasticsearchClientException e) {
                return false;
            }
        }))
                .as("Document should exists with [id2.txt] id...")
                .isTrue();

        // We remove a file
        logger.info("  ---> Removing file id2.txt");
        Files.delete(currentTestResourceDir.resolve("id2.txt"));

        // We expect to have two files
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, currentTestResourceDir);
    }
}
