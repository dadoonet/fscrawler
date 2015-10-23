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

package fr.pilato.elasticsearch.crawler.integration;

import fr.pilato.elasticsearch.crawler.fs.client.BulkProcessor;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClient;
import fr.pilato.elasticsearch.crawler.fs.client.IndexRequest;
import fr.pilato.elasticsearch.crawler.fs.client.SearchResponse;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.Elasticsearch;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.TimeValue;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.fail;

/**
 * Test all crawler settings
 */
public class ElasticsearchClientITest extends AbstractITest {

    private static ElasticsearchClient elasticsearchClient;

    @BeforeClass
    public static void startClient() {
        elasticsearchClient = ElasticsearchClient.builder().build();
        elasticsearchClient.addNode(Elasticsearch.Node.builder().setHost("127.0.0.1").setPort(HTTP_TEST_PORT).build());
    }

    @AfterClass
    public static void stopClient() {
        elasticsearchClient = null;
    }

    @Test
    public void testCreateIndex() throws IOException {
        elasticsearchClient.createIndex(getCurrentTestName());
        boolean exists = client.admin().indices().prepareExists(getCurrentTestName()).get().isExists();
        assertThat(exists, is(true));
    }

    @Test
    public void testCreateIndexAlreadyExists() throws IOException {
        client.admin().indices().prepareCreate(getCurrentTestName()).get();
        client.admin().cluster().prepareHealth(getCurrentTestName()).setWaitForYellowStatus().get();
        try {
            elasticsearchClient.createIndex(getCurrentTestName());
            fail("we should reject creation of an already existing index");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), is("index already exists"));
        }
    }

    @Test
    public void testIsExistingTypeWithNoIndex() throws IOException {
        boolean existingType = elasticsearchClient.isExistingType(getCurrentTestName(), "doc");
        assertThat(existingType, is(false));
    }

    @Test
    public void testIsExistingTypeWithIndexNoType() throws IOException {
        client.admin().indices().prepareCreate(getCurrentTestName()).get();
        client.admin().cluster().prepareHealth(getCurrentTestName()).setWaitForYellowStatus().get();
        boolean existingType = elasticsearchClient.isExistingType(getCurrentTestName(), "doc");
        assertThat(existingType, is(false));
    }

    @Test
    public void testIsExistingTypeWithIndexAndType() throws IOException {
        client.admin().indices().prepareCreate(getCurrentTestName()).get();
        client.admin().cluster().prepareHealth(getCurrentTestName()).setWaitForYellowStatus().get();
        client.admin().indices().preparePutMapping(getCurrentTestName()).setType("doc").setSource("{\"doc\":{}}").get();
        boolean existingType = elasticsearchClient.isExistingType(getCurrentTestName(), "doc");
        assertThat(existingType, is(true));
    }

    @Test
    public void testSearch() throws IOException {
        client.admin().indices().prepareCreate(getCurrentTestName()).get();
        client.admin().cluster().prepareHealth(getCurrentTestName()).setWaitForYellowStatus().get();
        client.prepareIndex(getCurrentTestName(), "doc", "1").setSource("foo", "bar").setRefresh(true).get();
        client.prepareIndex(getCurrentTestName(), "doc", "2").setSource("foo", "baz").setRefresh(true).get();

        // match_all
        SearchResponse response = elasticsearchClient.search(getCurrentTestName(), "doc", (String) null);
        assertThat(response.getHits().getTotal(), is(2L));

        // term
        response = elasticsearchClient.search(getCurrentTestName(), "doc", "foo:bar");
        assertThat(response.getHits().getTotal(), is(1L));
    }

    @Test
    public void testBulkWithTime() throws IOException, InterruptedException {
        // Create the index first
        client.admin().indices().prepareCreate(getCurrentTestName()).get();
        client.admin().cluster().prepareHealth(getCurrentTestName()).setWaitForYellowStatus().get();

        BulkProcessor bulkProcessor = BulkProcessor.simpleBulkProcessor(elasticsearchClient, 100, TimeValue.timeValueSeconds(2));
        for (int i = 0; i < 10; i++) {
            bulkProcessor.add(new IndexRequest(getCurrentTestName(), "doc", "id" + i).source("{\"foo\":\"bar\"}"));
        }

        client.admin().indices().prepareRefresh(getCurrentTestName()).get();

        // If we search just after sending the requests, we won't have all data
        SearchResponse response = elasticsearchClient.search(getCurrentTestName(), "doc", (String) null);
        assertThat(response.getHits().getTotal(), lessThan(10L));

        // We wait for 3 seconds (2 should be enough)
        Thread.sleep(3000L);

        client.admin().indices().prepareRefresh(getCurrentTestName()).get();

        // We should have now our docs
        response = elasticsearchClient.search(getCurrentTestName(), "doc", (String) null);
        assertThat(response.getHits().getTotal(), is(10L));
    }

    @Test
    public void testBulkWithoutTime() throws IOException, InterruptedException {
        // Create the index first
        client.admin().indices().prepareCreate(getCurrentTestName()).get();
        client.admin().cluster().prepareHealth(getCurrentTestName()).setWaitForYellowStatus().get();

        BulkProcessor bulkProcessor = BulkProcessor.simpleBulkProcessor(elasticsearchClient, 10, null);
        for (int i = 0; i < 9; i++) {
            bulkProcessor.add(new IndexRequest(getCurrentTestName(), "doc", "id" + i).source("{\"foo\":\"bar\"}"));
        }

        client.admin().indices().prepareRefresh(getCurrentTestName()).get();

        // If we search just after sending the requests, we won't have all data
        SearchResponse response = elasticsearchClient.search(getCurrentTestName(), "doc", (String) null);
        assertThat(response.getHits().getTotal(), is(0L));

        bulkProcessor.add(new IndexRequest(getCurrentTestName(), "doc", "id" + 9).source("{\"foo\":\"bar\"}"));

        client.admin().indices().prepareRefresh(getCurrentTestName()).get();

        // We should have now our docs
        response = elasticsearchClient.search(getCurrentTestName(), "doc", (String) null);
        assertThat(response.getHits().getTotal(), is(10L));
    }

}
