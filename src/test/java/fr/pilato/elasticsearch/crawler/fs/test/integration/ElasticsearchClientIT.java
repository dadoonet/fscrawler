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

import org.elasticsearch.Version;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.iterableWithSize;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeThat;

/**
 * Test elasticsearch HTTP client
 */
public class ElasticsearchClientIT extends AbstractITCase {

    @Before
    public void cleanExistingIndex() throws IOException {
        logger.info(" -> Removing existing index [{}*]", getCrawlerName());
        elasticsearchClient.deleteIndex(getCrawlerName() + "*");
    }

    @Test
    public void testCreateIndex() throws IOException {
        elasticsearchClient.createIndex(getCrawlerName(), false, null);
        boolean exists = elasticsearchClient.isExistingIndex(getCrawlerName());
        assertThat(exists, is(true));
    }

    @Test
    public void testCreateIndexWithSettings() throws IOException {
        elasticsearchClient.createIndex(getCrawlerName(), false, "{\n" +
                "  \"settings\": {\n" +
                "    \"number_of_shards\": 1,\n" +
                "    \"number_of_replicas\": 1\n" +
                "  }\n" +
                "}");
        boolean exists = elasticsearchClient.isExistingIndex(getCrawlerName());
        assertThat(exists, is(true));
    }

    @Test
    public void testRefresh() throws IOException {
        elasticsearchClient.createIndex(getCrawlerName(), false, null);
        refresh();
    }

    @Test
    public void testCreateIndexAlreadyExists() throws IOException {
        elasticsearchClient.createIndex(getCrawlerName(), false, null);
        elasticsearchClient.waitForHealthyIndex(getCrawlerName());
        try {
            elasticsearchClient.createIndex(getCrawlerName(), false, null);
            fail("we should reject creation of an already existing index");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), is("index already exists"));
        }
    }

    @Test
    public void testSearch() throws IOException {
        // Depending on the version we are using, we need to adapt the test settings (mapping)
        assumeThat("We only test our internal search stuff for version < 5.0", elasticsearchClient.info().getVersion().onOrAfter(Version.V_5_0_0_alpha1), is(false));

        String settings = "{\n" +
                "  \"mappings\": {\n" +
                "    \"doc\": {\n" +
                "      \"properties\": {\n" +
                "        \"foo\": {\n" +
                "          \"properties\": {\n" +
                "            \"bar\": {\n" +
                "              \"type\": \"string\",\n" +
                "              \"store\": true\n" +
                "            }\n" +
                "          }\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}\n";

        elasticsearchClient.createIndex(getCrawlerName(), false, settings);
        elasticsearchClient.waitForHealthyIndex(getCrawlerName());

        elasticsearchClient.index(new IndexRequest(getCrawlerName(), "doc", "1").source("{ \"foo\": { \"bar\": \"bar\" } }", XContentType.JSON));
        elasticsearchClient.index(new IndexRequest(getCrawlerName(), "doc", "2").source("{ \"foo\": { \"bar\": \"baz\" } }", XContentType.JSON));

        elasticsearchClient.refresh(getCrawlerName());

        // match_all
        SearchResponse response = elasticsearchClient.search(new SearchRequest(getCrawlerName()));
        assertThat(response.getHits().getTotalHits(), is(2L));

        // term
        response = elasticsearchClient.search(new SearchRequest(getCrawlerName()).source(new SearchSourceBuilder()
                .query(QueryBuilders.termQuery("foo.bar", "bar"))));
        assertThat(response.getHits().getTotalHits(), is(1L));

        // using fields
        Collection<String> fields = elasticsearchClient.getFromStoredFieldsV2(
                getCrawlerName(), 10, "foo.bar", "foo", "bar", "/a/path", QueryBuilders.termQuery("foo.bar", "bar"));
        assertThat(fields, iterableWithSize(1));
        assertThat(fields.iterator().next(), is("bar"));
    }

    @Test
    public void testFindVersion() throws IOException {
        Version version = elasticsearchClient.info().getVersion();
        logger.info("Current elasticsearch version: [{}]", version);

        // TODO if we store in a property file the elasticsearch version we are running tests against we can add some assertions
    }
}
