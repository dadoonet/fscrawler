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
import fr.pilato.elasticsearch.crawler.fs.tika.TikaInstance;
import java.io.File;
import java.io.InputStream;
import org.apache.commons.io.FileUtils;
import org.junit.Test;

/**
 * Test tika config path crawler setting
 */
public class FsCrawlerTestTikaConfigPathIT extends AbstractFsCrawlerITCase {

  @Test
  public void test_tika_config_path() throws Exception {
    TikaInstance.reloadTika();
    InputStream tikaConfigIS = getClass().getResourceAsStream("/documents/tikaConfig.xml");
    File tikaConfigFile = File.createTempFile("tikaConfigTestFile", ".xml");
    FileUtils.copyInputStreamToFile(tikaConfigIS, tikaConfigFile);

    Fs fs = startCrawlerDefinition()
        .setTikaConfigPath(tikaConfigFile.getPath())
        .build();
    startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

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

    tikaConfigFile.delete();
  }

  @Test
  public void test_tika_config_bad_path() throws Exception {
    TikaInstance.reloadTika();
    Fs fs = startCrawlerDefinition()
        .setTikaConfigPath("/bad/path.xml")
        .build();
    startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), null);

    countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 2L, null);
    countTestHelper(new ESSearchRequest()
        .withIndex(getCrawlerName())
        .withESQuery(new ESMatchQuery("content", "example")), 2L, null);
    // Both XHTML and HTML parsed as HTML will put <head> information in meta, not in content
    countTestHelper(new ESSearchRequest()
        .withIndex(getCrawlerName())
        .withESQuery(new ESMatchQuery("content", "Tika")), 0L, null);
    countTestHelper(new ESSearchRequest()
        .withIndex(getCrawlerName())
        .withESQuery(new ESMatchQuery("meta.title", "Test Tika title")), 2L, null);
  }
}
