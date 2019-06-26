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
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import org.junit.Test;

import static com.carrotsearch.randomizedtesting.RandomizedTest.randomLongBetween;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.INDEX_SUFFIX_FOLDER;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.extractMajorVersion;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.extractMinorVersion;
import static org.junit.Assume.assumeFalse;

/**
 * Test all crawler settings
 */
public class FsCrawlerTestUpgradeVersionIT extends AbstractFsCrawlerITCase {

    @SuppressWarnings("deprecation")
    @Test
    public void test_upgrade_version() throws Exception {
        // We can only run this test if elasticsearch version is >= 2.3 and < 6.0
        String version = esClient.getVersion();
        int major = Integer.parseInt(extractMajorVersion(version));
        int minor = Integer.parseInt(extractMinorVersion(version));
        assumeFalse("We can only run the upgrade process on version between >= 2.3 and < 6.0",
                major < 2 || (major == 2 && minor < 4) || major >= 6);

        // Let's create some deprecated indices
        long nbDocs = randomLongBetween(10, 100);
        long nbFolders = randomLongBetween(1, 10);

        esClient.createIndex(getCrawlerName(), false, null);

        // Create fake data
        for (int i = 0; i < nbDocs; i++) {
            esClient.performLowLevelRequest("PUT", "/" + getCrawlerName() + "/doc/id" + i, "{\"foo\":\"bar\"}");
        }
        for (int i = 0; i < nbFolders; i++) {
            esClient.performLowLevelRequest("PUT", "/" + getCrawlerName() + "/folder/id" + i, "{\"foo\":\"bar\"}");
        }
        esClient.flush();

        // Let's wait that everything has been indexed
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), nbDocs+nbFolders, null);

        // Let's create a crawler instance
        FsSettings fsSettings = FsSettings.builder(getCrawlerName())
                .setElasticsearch(elasticsearchWithSecurity).build();
        fsSettings.getElasticsearch().setIndex(getCrawlerName());
        crawler = new FsCrawlerImpl(metadataDir, fsSettings, 0, false);

        // Call the upgrade process
        crawler.upgrade();

        // Test that we have all needed docs in old index and new indices
        long expectedDocs = nbDocs;
        if (Integer.parseInt(extractMajorVersion(esClient.getVersion())) < 5) {
            // If we ran our tests against a 2.x cluster, _delete_by_query is skipped (as it does not exist).
            // Which means that folders are still there
            expectedDocs += nbFolders;
        }
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), expectedDocs, null);
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_FOLDER), nbFolders, null);
    }
}
