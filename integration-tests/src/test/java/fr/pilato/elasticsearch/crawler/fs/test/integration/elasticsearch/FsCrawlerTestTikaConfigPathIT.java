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

import fr.pilato.elasticsearch.crawler.fs.client.ESMatchQuery;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.settings.Fs;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.junit.Test;

import static com.carrotsearch.randomizedtesting.RandomizedTest.assumeTrue;

/**
 * Test tika config path crawler setting
 */
public class FsCrawlerTestTikaConfigPathIT extends AbstractFsCrawlerITCase {

  @Test
  public void test_tika_config_path() throws Exception {
    assumeTrue("We are skipping this test. See discussion at https://github.com/dadoonet/fscrawler/pull/1403#issuecomment-1077912549", false);
    Fs fs = startCrawlerDefinition()
        .setTikaConfigPath(currentTestResourceDir.resolve("config/tikaConfig.xml").toString())
        .addExclude("/config/*")
        .build();
    crawler = startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

    countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 2L, null);
    countTestHelper(new ESSearchRequest()
            .withIndex(getCrawlerName())
            .withESQuery(new ESMatchQuery("content", "Tika")), 2L, null);
    // HTML parsed as TXT will contain all tags in content
    // XHTML parsed as XML will remove tags from content
    countTestHelper(new ESSearchRequest()
            .withIndex(getCrawlerName())
            .withESQuery(new ESMatchQuery("content", "div")), 1L, null);
    countTestHelper(new ESSearchRequest()
        .withIndex(getCrawlerName())
        .withESQuery(new ESMatchQuery("meta.title", "Test Tika title")), 0L, null);
  }

}
