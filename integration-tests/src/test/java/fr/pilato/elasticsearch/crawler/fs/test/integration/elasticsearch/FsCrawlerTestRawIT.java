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

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchHit;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.Test;

import static com.carrotsearch.randomizedtesting.RandomizedTest.rarely;
import static org.assertj.core.api.Assertions.*;

/**
 * Test json support crawler setting
 */
public class FsCrawlerTestRawIT extends AbstractFsCrawlerITCase {

    /**
     * Test case for issue #439: <a href="https://github.com/dadoonet/fscrawler/issues/439">https://github.com/dadoonet/fscrawler/issues/439</a> : Date Mapping issue in RAW field
     */
    @Test
    public void mapping() throws Exception {
        crawler = startCrawler();

        // We should have one document indexed
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);

        // Let's add manually some documents
        // The 1st document simulates that we are indexing a String field which contains something like a date
        String json1 = "{\n" +
                "  \"meta\": {\n" +
                "    \"raw\": {\n" +
                "      \"fscrawler_date\": \"1971-12-26\"\n" +
                "    }\n" +
                "  }\n" +
                "}\n";

        // The 2nd document index a String to the same field
        String json2 = "{\n" +
                "  \"meta\": {\n" +
                "    \"raw\": {\n" +
                "      \"fscrawler_date\": \"David\"\n" +
                "    }\n" +
                "  }\n" +
                "}\n";

        // This should not raise any exception even if the String is not a Date
        // because of the default mapping we are applying defines all meta raw fields as text
        documentService.indexRawJson(getCrawlerName(), "1", json1, null);
        documentService.flush();
        documentService.indexRawJson(getCrawlerName(), "2", json2, null);
        documentService.flush();

        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 3L, null);
        assertThat(searchResponse.getHits())
                .anySatisfy(hit -> assertThat(hit.getId()).containsAnyOf("1", "2"));
    }

    @Test
    public void disable_raw() throws Exception {
        FsSettings fsSettings = createTestSettings();
        if (rarely()) {
            // Sometimes we explicitly disable it but this is also the default value
            fsSettings.getFs().setRawMetadata(false);
        }
        crawler = startCrawler(fsSettings);
        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
        for (ESSearchHit hit : searchResponse.getHits()) {
            assertThatThrownBy(() -> JsonPath.read(hit.getSource(), "$.meta.raw")).isInstanceOf(PathNotFoundException.class);
        }
    }

    @Test
    public void enable_raw() throws Exception {
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setRawMetadata(true);
        crawler = startCrawler(fsSettings);
        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
        for (ESSearchHit hit : searchResponse.getHits()) {
            assertThat((Object) JsonPath.read(hit.getSource(), "$.meta.raw"))
                    .asInstanceOf(InstanceOfAssertFactories.MAP)
                    .isNotEmpty();
        }
    }
}
