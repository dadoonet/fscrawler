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
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/** A fresh client must replay missing writes; the original process's queue cannot rescue recovery. */
@Slow
class FsCrawlerCheckpointReplayIT extends AbstractFsCrawlerITCase {
    @Test
    void correctedPipelineRecoversRejectedFileFromAcknowledgedCheckpoint() throws Exception {
        var source = Files.createDirectory(currentTestResourceDir.resolve("checkpoint-replay"));
        String good = RandomizedTest.randomAsciiLettersOfLength(randomizedRandomForTests, 12) + ".txt";
        String bad = RandomizedTest.randomAsciiLettersOfLength(randomizedRandomForTests, 12) + "-rejected.txt";
        Files.writeString(source.resolve(good), "accepted document");
        Files.writeString(source.resolve(bad), "rejected document");
        var settings = createTestSettings();
        settings.getFs().setUrl(source.toString());
        settings.getFs().setIndexFolders(false);
        settings.getFs().setFilenameAsId(true);
        settings.getElasticsearch().setFlushInterval(null);
        settings.getElasticsearch().setBulkSize(100);
        settings.getElasticsearch().setPipeline(getCrawlerName());
        client.createPipeline(getCrawlerName(), """
                {"processors":[{"fail":{"if":"ctx.file.filename == '%s'","message":"checkpoint replay rejection"}}]}
                """.formatted(bad));
        try {
            crawler = new FsCrawlerImpl(metadataDir, settings, 1, false);
            crawler.start();
            Awaitility.await()
                    .atMost(Duration.ofSeconds(20))
                    .until(() -> crawler.getFsParser().isClosed());
            var handler = new FsCrawlerCheckpointFileHandler(metadataDir);
            Assertions.assertThat(handler.read(settings.getName()).getState()).isEqualTo(CrawlerState.ERROR);
            client.refresh(settings.getElasticsearch().getIndex());
            Assertions.assertThat(client.search(new ESSearchRequest()
                                    .withIndex(settings.getElasticsearch().getIndex()))
                            .getTotalHits())
                    .isEqualTo(1);
            crawler.close();
            crawler = null;
            client.createPipeline(getCrawlerName(), """
                {"processors":[{"set":{"field":"recovered","value":true}}]}
                """);
            crawler = new FsCrawlerImpl(metadataDir, settings, 1, false);
            crawler.start();
            Awaitility.await()
                    .atMost(Duration.ofSeconds(20))
                    .until(() -> crawler.getFsParser().isClosed());
            Assertions.assertThat(handler.read(settings.getName()).getState()).isEqualTo(CrawlerState.COMPLETED);
            client.refresh(settings.getElasticsearch().getIndex());
            Assertions.assertThat(client.search(new ESSearchRequest()
                                    .withIndex(settings.getElasticsearch().getIndex()))
                            .getTotalHits())
                    .as("restart must recover the rejected document, not skip its completed directory")
                    .isEqualTo(2);
            Assertions.assertThat(client.get(settings.getElasticsearch().getIndex(), good)
                            .getVersion())
                    .isEqualTo(2);
            Assertions.assertThat(client.get(settings.getElasticsearch().getIndex(), bad)
                            .getVersion())
                    .isEqualTo(1);
        } finally {
            if (crawler != null) {
                crawler.close();
                crawler = null;
            }
            client.deletePipeline(getCrawlerName());
        }
    }
}
