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

import com.jayway.jsonpath.JsonPath;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchHit;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil;
import fr.pilato.elasticsearch.crawler.fs.rest.UploadResponse;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.test.framework.Slow;
import fr.pilato.elasticsearch.crawler.fs.test.framework.VerySlow;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractRestITCase;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@SuppressWarnings("ALL")
class FsCrawlerRestFilenameAsIdIT extends AbstractRestITCase {
    private static final Logger logger = LogManager.getLogger();

    public FsSettings getFsSettings() {
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setFilenameAsId(true);
        return fsSettings;
    }

    @Test
    void uploadOneDocument() throws Exception {
        Path from = testDocumentsDir.resolve("test.txt");
        if (Files.notExists(from)) {
            logger.error("file [{}] should exist before we start tests", from);
            throw new RuntimeException(from + " doesn't seem to exist. Check your JUnit tests.");
        }
        UploadResponse uploadResponse = uploadFile(target, from);
        Assertions.assertThat(uploadResponse.isOk()).isTrue();

        // We wait until we have our document
        ESSearchResponse response = countTestHelper(
                new ESSearchRequest().withIndex(getCrawlerName() + FsCrawlerUtil.INDEX_SUFFIX_DOCS), 1L, null);
        for (ESSearchHit hit : response.getHits()) {
            Assertions.assertThat(hit.getId()).isEqualTo((String) JsonPath.read(hit.getSource(), "$.file.filename"));
        }
    }

    @Test
    @VerySlow
    void uploadAllDocuments() throws Exception {
        Path from = testDocumentsDir;
        if (Files.notExists(from)) {
            logger.error("directory [{}] should exist before we start tests", from);
            throw new RuntimeException(from + " doesn't seem to exist. Check your JUnit tests.");
        }
        Files.walk(from).filter(Files::isRegularFile).forEach(path -> {
            UploadResponse response = uploadFile(target, path);
            Assertions.assertThat(response.getFilename())
                    .isEqualTo(path.getFileName().toString());
        });

        // We wait until we have all docs
        ESSearchResponse response = countTestHelper(
                new ESSearchRequest().withIndex(getCrawlerName() + FsCrawlerUtil.INDEX_SUFFIX_DOCS),
                Files.list(from).count(),
                null,
                MAX_WAIT_FOR_SEARCH);
        for (ESSearchHit hit : response.getHits()) {
            Assertions.assertThat(hit.getId()).isEqualTo((String) JsonPath.read(hit.getSource(), "$.file.filename"));
        }
    }
}
