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

import com.carrotsearch.randomizedtesting.jupiter.RandomizedTest;
import fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl;
import fr.pilato.elasticsearch.crawler.fs.beans.CrawlerState;
import fr.pilato.elasticsearch.crawler.fs.beans.FsCrawlerCheckpointFileHandler;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.test.framework.Slow;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

@Slow
class FsCrawlerBulkItemFailureIT extends AbstractFsCrawlerITCase {
    @Test
    void rejectedDocumentMakesFilesystemCrawlFailWithoutRest() throws Exception {
        var source = Files.createDirectory(currentTestResourceDir.resolve("bug04-source"));
        String acceptedName = RandomizedTest.randomAsciiLettersOfLength(randomizedRandomForTests, 12) + ".txt";
        String rejectedName = RandomizedTest.randomAsciiLettersOfLength(randomizedRandomForTests, 12) + "-rejected.txt";
        Files.writeString(source.resolve(acceptedName), "accepted document");
        Files.writeString(source.resolve(rejectedName), "rejected document");
        String reason = "Rejected by regression test "
                + RandomizedTest.randomAsciiLettersOfLength(randomizedRandomForTests, 12);
        String pipeline = getCrawlerName();
        client.createPipeline(pipeline, """
                {"processors":[{"fail":{"if":"ctx.file.filename == '%s'","message":"%s"}}]}
                """.formatted(rejectedName, reason));
        var settings = createTestSettings();
        settings.getFs().setUrl(source.toString());
        settings.getFs().setIndexFolders(false);
        settings.getFs().setFilenameAsId(true);
        settings.getElasticsearch().setPipeline(pipeline);
        settings.getElasticsearch().setBulkSize(100);
        settings.getElasticsearch().setFlushInterval(null);
        try {
            // One real filesystem scan; the REST gateway is explicitly disabled.
            crawler = new FsCrawlerImpl(metadataDir, settings, 1, false);
            crawler.start();
            Awaitility.await()
                    .atMost(Duration.ofSeconds(30))
                    .until(() -> crawler.getFsParser().isClosed());
            countTestHelper(
                    new ESSearchRequest().withIndex(settings.getElasticsearch().getIndex()), 1L, source);
            Assertions.assertThat(client.get(settings.getElasticsearch().getIndex(), acceptedName)
                            .getVersion())
                    .isEqualTo(1);
            var checkpoint = new FsCrawlerCheckpointFileHandler(metadataDir).read(settings.getName());
            Assertions.assertThat(checkpoint.getState()).isEqualTo(CrawlerState.ERROR);
            Assertions.assertThat(checkpoint.getLastError()).contains("Bulk indexing failed", reason, rejectedName);
            Assertions.assertThat(checkpoint.getScanDate()).isIn(null, Instant.EPOCH);
            Assertions.assertThat(checkpoint.getScanEndTime()).isNull();
        } finally {
            if (crawler != null) {
                crawler.close();
                crawler = null;
            }
            client.deletePipeline(pipeline);
        }
    }
}
