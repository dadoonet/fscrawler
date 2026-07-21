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

import fr.pilato.elasticsearch.crawler.fs.client.ESSearchHit;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.framework.Digests;
import fr.pilato.elasticsearch.crawler.fs.framework.ExponentialBackoffPollInterval;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil;
import fr.pilato.elasticsearch.crawler.fs.settings.BulkOperation;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * End-to-end coverage for content-based {@code _id} deduplication (tips docs), including
 * {@code elasticsearch.bulk_operation: create} first-writer-wins behaviour from <a
 * href="https://github.com/dadoonet/fscrawler/issues/2462">#2462</a>.
 */
class FsCrawlerTestDeduplicateIT extends AbstractFsCrawlerITCase {

    /**
     * Two identical files under different paths share one document when the ingest pipeline sets {@code _id} from
     * {@code file.checksum} and FSCrawler uses bulk {@code create}. The surviving document stays at version {@code 1}
     * (first writer wins).
     */
    @Test
    void deduplicate_with_create() throws Exception {
        String expectedChecksumId = sha256OfSample();

        String pipelineName = getCrawlerName();
        createChecksumIdPipeline(pipelineName);

        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setIndexContent(true);
        fsSettings.getFs().setChecksum("SHA-256");
        fsSettings.getElasticsearch().setPipeline(pipelineName);
        fsSettings.getElasticsearch().setBulkOperation(BulkOperation.CREATE);

        crawler = startCrawler(fsSettings);

        String docsIndex = getCrawlerName() + FsCrawlerUtil.INDEX_SUFFIX_DOCS;
        String folderIndex = getCrawlerName() + FsCrawlerUtil.INDEX_SUFFIX_FOLDER;

        // Root + copy_a + copy_b: wait until the first scan has walked both duplicate paths.
        countTestHelper(new ESSearchRequest().withIndex(folderIndex), 3L, currentTestResourceDir);
        countTestHelper(new ESSearchRequest().withIndex(docsIndex), 1L, currentTestResourceDir);

        // Give any in-flight create for the second duplicate time to complete, then assert
        // first-writer-wins (still one doc at version 1).
        Awaitility.await()
                .pollDelay(Duration.ofSeconds(2))
                .atMost(MAX_WAIT_FOR_SEARCH)
                .pollInterval(ExponentialBackoffPollInterval.exponential(Duration.ofMillis(500), Duration.ofSeconds(2)))
                .untilAsserted(() -> assertSingleDocument(docsIndex, expectedChecksumId, 1L));
    }

    /**
     * Same content-based {@code _id} setup with the default bulk {@code index} action: the second duplicate overwrites
     * the first document, so the version is greater than {@code 1}.
     */
    @Test
    void deduplicate_with_index() throws Exception {
        String expectedChecksumId = sha256OfSample();

        String pipelineName = getCrawlerName();
        createChecksumIdPipeline(pipelineName);

        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setIndexContent(true);
        fsSettings.getFs().setChecksum("SHA-256");
        fsSettings.getElasticsearch().setPipeline(pipelineName);
        fsSettings.getElasticsearch().setBulkOperation(BulkOperation.INDEX);

        crawler = startCrawler(fsSettings);

        String docsIndex = getCrawlerName() + FsCrawlerUtil.INDEX_SUFFIX_DOCS;
        String folderIndex = getCrawlerName() + FsCrawlerUtil.INDEX_SUFFIX_FOLDER;

        countTestHelper(new ESSearchRequest().withIndex(folderIndex), 3L, currentTestResourceDir);

        Awaitility.await()
                .atMost(MAX_WAIT_FOR_SEARCH)
                .pollInterval(ExponentialBackoffPollInterval.exponential(Duration.ofMillis(500), Duration.ofSeconds(2)))
                .untilAsserted(() -> assertSingleDocument(docsIndex, expectedChecksumId, 2L));
    }

    /**
     * Hash the fixture bytes as present on disk. Windows checkouts may use CRLF, so the value must not be hardcoded
     * (same reason {@link FsCrawlerTestChecksumIT} uses platform-specific expectations).
     */
    private String sha256OfSample() throws Exception {
        Path copyA = currentTestResourceDir.resolve("copy_a").resolve("identical.txt");
        Path copyB = currentTestResourceDir.resolve("copy_b").resolve("identical.txt");
        MessageDigest digest = Digests.get("SHA-256");
        String checksumA = Digests.toHex(digest.digest(Files.readAllBytes(copyA)));
        digest.reset();
        String checksumB = Digests.toHex(digest.digest(Files.readAllBytes(copyB)));
        Assertions.assertThat(checksumA)
                .as("duplicate fixtures must be binary-identical for this test")
                .isEqualTo(checksumB);
        return checksumA;
    }

    private void assertSingleDocument(String docsIndex, String expectedChecksumId, long minVersion) throws Exception {
        refresh(docsIndex);
        ESSearchResponse response = client.search(new ESSearchRequest().withIndex(docsIndex));
        Assertions.assertThat(response.getTotalHits()).isEqualTo(1L);
        Assertions.assertThat(response.getHits()).hasSize(1);

        ESSearchHit hit = response.getHits().get(0);
        ESSearchHit getHit = client.get(hit.getIndex(), hit.getId());
        Assertions.assertThat(getHit.getId())
                .as("document _id must be the SHA-256 checksum from the ingest pipeline")
                .isEqualTo(expectedChecksumId);
        if (minVersion <= 1L) {
            Assertions.assertThat(getHit.getVersion())
                    .as("create mode must keep the first indexed copy (version stays at 1)")
                    .isEqualTo(1L);
        } else {
            Assertions.assertThat(getHit.getVersion())
                    .as("index mode overwrites the shared _id, so version must advance")
                    .isGreaterThanOrEqualTo(minVersion);
        }
    }

    private void createChecksumIdPipeline(String pipelineName) throws Exception {
        String pipeline = """
                {
                  "description": "Set the _id from file.checksum",
                  "processors": [
                    {
                      "set": {
                        "field": "_id",
                        "value": "{{{file.checksum}}}"
                      }
                    }
                  ]
                }
                """;
        client.createPipeline(pipelineName, pipeline);
    }
}
