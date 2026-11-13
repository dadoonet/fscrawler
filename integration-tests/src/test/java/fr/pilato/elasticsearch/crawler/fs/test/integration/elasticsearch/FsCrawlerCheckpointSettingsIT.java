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
import fr.pilato.elasticsearch.crawler.fs.framework.ByteSizeValue;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.test.framework.Slow;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import java.nio.file.Files;
import java.time.Duration;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/** A real job-file round trip must reach a live crawler, not just decode a settings DTO. */
@Slow
class FsCrawlerCheckpointSettingsIT extends AbstractFsCrawlerITCase {
    @Test
    void configuredJobFileIndexesAndCommitsWithIndependentLimits() throws Exception {
        var source = Files.createDirectory(currentTestResourceDir.resolve("checkpoint-settings"));
        for (int n = 0; n < 3; n++)
            Files.writeString(
                    source.resolve(RandomizedTest.randomAsciiLettersOfLength(randomizedRandomForTests, 12) + ".json"),
                    "{\"value\":1}");
        var settings = createTestSettings();
        settings.getFs().setUrl(source.toString());
        settings.getFs().setJsonSupport(true);
        settings.getFs().setRemoveDeleted(false);
        settings.getFs().setIndexFolders(false);
        var checkpoint = settings.getFs().getCheckpoint();
        checkpoint.setMaxDirectories(2);
        checkpoint.setMaxOperations(1);
        checkpoint.setMaxBytes(new ByteSizeValue("2mb"));
        checkpoint.setMaxInterval(new TimeValue("10s"));
        settings.getElasticsearch().setBulkSize(1000);
        var loader = new FsSettingsLoader(metadataDir);
        loader.write(settings.getName(), settings);
        var loaded = loader.read(settings.getName());
        Assertions.assertThat(loaded.getFs().getCheckpoint()).isEqualTo(checkpoint);
        crawler = new FsCrawlerImpl(metadataDir, loaded, 1, false);
        try {
            crawler.start();
            Awaitility.await()
                    .atMost(Duration.ofSeconds(20))
                    .until(() -> crawler.getFsParser().isClosed());
            var saved = new FsCrawlerCheckpointFileHandler(metadataDir).read(loaded.getName());
            Assertions.assertThat(saved.getState()).isEqualTo(CrawlerState.COMPLETED);
            Assertions.assertThat(saved.getFilesProcessed()).isEqualTo(3);
            client.refresh(loaded.getElasticsearch().getIndex());
            Assertions.assertThat(client.search(new ESSearchRequest()
                                    .withIndex(loaded.getElasticsearch().getIndex()))
                            .getTotalHits())
                    .isEqualTo(3);
        } finally {
            crawler.close();
            crawler = null;
        }
    }
}
