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
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientManager;
import fr.pilato.elasticsearch.crawler.fs.crawler.FsParserAbstract;
import fr.pilato.elasticsearch.crawler.fs.crawler.fs.FsParserLocal;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import org.elasticsearch.Version;
import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.Test;

import static com.carrotsearch.randomizedtesting.RandomizedTest.randomLongBetween;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.INDEX_SUFFIX_FOLDER;
import static org.junit.Assume.assumeFalse;

/**
 * Test all crawler settings
 */
public class FsCrawlerTestUpgradeVersionIT extends AbstractFsCrawlerITCase {

    @SuppressWarnings("deprecation")
    @Test
    public void test_upgrade_version() throws Exception {
        // We can only run this test if elasticsearch version is >= 2.3 and < 6.0
        Version version = elasticsearchClient.getVersion();
        assumeFalse("We can only run the upgrade process on version between >= 2.3 and < 6.0",
                version.major < 2 || (version.major == 2 && version.minor < 4) || version.major >= 6);

        // Let's create some deprecated indices
        long nbDocs = randomLongBetween(10, 100);
        long nbFolders = randomLongBetween(1, 10);

        elasticsearchClient.createIndex(getCrawlerName(), false, null);

        BulkProcessor.Listener listener = new BulkProcessor.Listener() {
            @Override public void beforeBulk(long executionId, BulkRequest request) { }
            @Override public void afterBulk(long executionId, BulkRequest request, BulkResponse response) { }
            @Override public void afterBulk(long executionId, BulkRequest request, Throwable failure) { }
        };

        BulkProcessor bulkProcessor = BulkProcessor.builder(elasticsearchClient::bulkAsync, listener)
                .setBulkActions(1000)
                .setFlushInterval(org.elasticsearch.common.unit.TimeValue.timeValueSeconds(2))
                .build();

        // Create fake data
        for (int i = 0; i < nbDocs; i++) {
            bulkProcessor.add(new IndexRequest(getCrawlerName(), "doc", "id" + i).source("{\"foo\":\"bar\"}", XContentType.JSON));
        }
        for (int i = 0; i < nbFolders; i++) {
            bulkProcessor.add(new IndexRequest(getCrawlerName(), "folder", "id" + i).source("{\"foo\":\"bar\"}", XContentType.JSON));
        }
        bulkProcessor.close();

        // Let's wait that everything has been indexed
        countTestHelper(new SearchRequest(getCrawlerName()), nbDocs+nbFolders, null);

        // Let's create a crawler instance
        FsSettings fsSettings = FsSettings.builder(getCrawlerName())
                .setElasticsearch(elasticsearchWithSecurity).build();
        fsSettings.getElasticsearch().setIndex(getCrawlerName());
        ElasticsearchClientManager esClientManager = new ElasticsearchClientManager(metadataDir, fsSettings);
        FsParserAbstract parser = new FsParserLocal(fsSettings, metadataDir, esClientManager, 0);
        crawler = new FsCrawlerImpl(metadataDir, fsSettings, false, esClientManager, parser);

        // Call the upgrade process
        crawler.upgrade();

        // Test that we have all needed docs in old index and new indices
        long expectedDocs = nbDocs;
        if (elasticsearchClient.getVersion().major < 5) {
            // If we ran our tests against a 2.x cluster, _delete_by_query is skipped (as it does not exist).
            // Which means that folders are still there
            expectedDocs += nbFolders;
        }
        countTestHelper(new SearchRequest(getCrawlerName()), expectedDocs, null);
        countTestHelper(new SearchRequest(getCrawlerName() + INDEX_SUFFIX_FOLDER), nbFolders, null);
    }
}
