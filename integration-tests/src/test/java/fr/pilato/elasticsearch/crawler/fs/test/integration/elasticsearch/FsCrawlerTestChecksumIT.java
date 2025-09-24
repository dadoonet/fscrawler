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
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchHit;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.framework.OsValidator;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.junit.Test;

import java.security.MessageDigest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThatCode;

/**
 * Test checksum crawler settings
 */
public class FsCrawlerTestChecksumIT extends AbstractFsCrawlerITCase {

    @Test
    public void checksum_md5() throws Exception {
        assumeThatCode(() -> MessageDigest.getInstance("MD5")).doesNotThrowAnyException();

        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setChecksum("MD5");
        crawler = startCrawler(fsSettings);
        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
        String expectedChecksum = OsValidator.WINDOWS ? "89421a18ae16e3bfe7c04a4946b7250a" : "caa71e1914ecbcf5ae4f46cf85de8648";
        for (ESSearchHit hit : searchResponse.getHits()) {
            assertThat((String) JsonPath.read(hit.getSource(), "$.file.checksum")).isEqualTo(expectedChecksum);
        }
    }

    @Test
    public void checksum_sha1() throws Exception {
        assumeThatCode(() -> MessageDigest.getInstance("SHA-1")).doesNotThrowAnyException();

        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setChecksum("SHA-1");
        crawler = startCrawler(fsSettings);
        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
        String expectedChecksum = OsValidator.WINDOWS ? "5916fb8f87be8d54c4323981af7c27f243c82b96" : "81bf7dba781a1efbea6d9f2ad638ffe772ba4eab";
        for (ESSearchHit hit : searchResponse.getHits()) {
            assertThat((String) JsonPath.read(hit.getSource(), "$.file.checksum")).isEqualTo(expectedChecksum);
        }
    }
}
