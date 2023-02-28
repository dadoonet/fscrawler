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

import fr.pilato.elasticsearch.crawler.fs.client.ESPrefixQuery;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESTermQuery;
import fr.pilato.elasticsearch.crawler.fs.settings.Elasticsearch;
import fr.pilato.elasticsearch.crawler.fs.settings.Fs;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Test json transform settings
 */
public class FsCrawlerTestJsonTransformIT extends AbstractFsCrawlerITCase {

    @Test
    public void test_json_transform() throws Exception {
        Fs fs = startCrawlerDefinition()
                .setIndexContent(true)
                .build();

        Elasticsearch es = endCrawlerDefinition(getCrawlerName());
        es.setJsonTransform(". | del(.content)");

        crawler = startCrawler(getCrawlerName(), fs, es, null);

        // We expect to have one file
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);

        // We expect to find zero documents with any content and we know "text" is part
        // of a keyword in content
        countTestHelper(
                new ESSearchRequest().withIndex(getCrawlerName()).withESQuery(new ESPrefixQuery("content", "text")),
                0L, null);

        // We expect to still have a content_type as we did content indexing and only
        // deleted the content field afterwards
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName())
                .withESQuery(new ESPrefixQuery("file.content_type", "text")), 1L, null);

    }
}
