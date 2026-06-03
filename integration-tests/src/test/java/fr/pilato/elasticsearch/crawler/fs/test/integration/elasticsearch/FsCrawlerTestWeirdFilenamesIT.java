/*
 * Licensed to David Pilato (the "Author") under one
 * or more contributor license agreements. See the NOTICE file
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
 *
 * Made from 🇫🇷🇪🇺 with ❤️ - 2011-2026
 */
package fr.pilato.elasticsearch.crawler.fs.test.integration.elasticsearch;

import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import java.nio.file.Files;
import java.nio.file.Path;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** Test weird filenames */
class FsCrawlerTestWeirdFilenamesIT extends AbstractFsCrawlerITCase {
    /**
     * Test for #1952: <a
     * href="https://github.com/dadoonet/fscrawler/issues/1952">https://github.com/dadoonet/fscrawler/issues/1952</a>:
     * When a directory has a space at the end, files inside are not indexed
     */
    @Test
    @DisabledOnOs(OS.WINDOWS) // Windows does not allow to have a space at the end of a directory name
    void dir_with_space_at_the_end() throws Exception {
        // We need to do a small hack here and rename the test directory as this could not work on Windows
        Path dirWithSpace = currentTestResourceDir.resolve("with_space ");
        Files.move(currentTestResourceDir.resolve("with_space"), dirWithSpace);
        FsSettings fsSettings = createTestSettings();
        crawler = startCrawler(fsSettings);
        ESSearchResponse response = countTestHelper(
                new ESSearchRequest().withIndex(getCrawlerName() + FsCrawlerUtil.INDEX_SUFFIX_DOCS), 3L, null);
        Assertions.assertThat(response.getTotalHits()).isEqualTo(3L);
    }
}
