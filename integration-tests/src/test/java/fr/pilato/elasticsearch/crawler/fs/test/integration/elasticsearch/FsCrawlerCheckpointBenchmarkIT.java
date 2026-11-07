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

import fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl;
import fr.pilato.elasticsearch.crawler.fs.beans.CrawlerState;
import fr.pilato.elasticsearch.crawler.fs.beans.FsCrawlerCheckpointFileHandler;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.test.framework.VerySlow;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Locale;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Opt-in healthy-workload benchmark. Fixture generation, validation and cleanup are outside the timed interval. The
 * same source runs on baseline and fixed code.
 */
@VerySlow
@EnabledIfSystemProperty(named = "tests.checkpoint.benchmark", matches = "true")
class FsCrawlerCheckpointBenchmarkIT extends AbstractFsCrawlerITCase {
    @Test
    @org.junit.jupiter.api.Timeout(value = 20, unit = java.util.concurrent.TimeUnit.MINUTES)
    void healthyWorkloads() throws Exception {
        int repeats = Integer.getInteger("tests.checkpoint.repeats", 5);
        for (String workload : new String[] {"small-directories", "large-directory", "wide-directory"}) {
            var source = Files.createDirectories(currentTestResourceDir.resolve(workload));
            int expected = 0;
            if (workload.equals("small-directories")) {
                for (int d = 0; d < 100; d++) {
                    var dir = Files.createDirectories(source.resolve("d" + d));
                    for (int f = 0; f < 10; f++) Files.writeString(dir.resolve("f" + f + ".json"), "{\"value\":1}");
                }
                expected = 1000;
            } else if (workload.equals("large-directory")) {
                for (int f = 0; f < 10050; f++) Files.writeString(source.resolve("f" + f + ".json"), "{\"value\":1}");
                expected = 10050;
            } else {
                for (int d = 0; d < 10050; d++) Files.createDirectories(source.resolve("d" + d));
            }
            for (int iteration = 0; iteration <= repeats; iteration++) {
                var settings = createTestSettings();
                settings.getFs().setUrl(source.toString());
                settings.getFs().setProvider("checkpoint-benchmark");
                settings.getFs().setJsonSupport(true);
                settings.getFs().setIndexFolders(false);
                settings.getFs().setRemoveDeleted(false);
                settings.getElasticsearch().setBulkSize(100);
                settings.getElasticsearch().setFlushInterval(TimeValue.timeValueSeconds(5));
                cleanIndexAndTemplates(settings, settings.getName());
                long begin = System.nanoTime();
                crawler = new FsCrawlerImpl(metadataDir, settings, 1, false);
                try {
                    crawler.start();
                    Awaitility.await()
                            .pollInterval(Duration.ofMillis(10))
                            .atMost(Duration.ofMinutes(5))
                            .until(() -> crawler.getFsParser().isClosed());
                    long elapsed = System.nanoTime() - begin;
                    var saved = new FsCrawlerCheckpointFileHandler(metadataDir).read(settings.getName());
                    Assertions.assertThat(saved.getState()).isEqualTo(CrawlerState.COMPLETED);
                    if (expected > 0
                            || client.isExistingIndex(
                                    settings.getElasticsearch().getIndex())) {
                        client.refresh(settings.getElasticsearch().getIndex());
                        Assertions.assertThat(client.search(new ESSearchRequest()
                                                .withIndex(settings.getElasticsearch()
                                                        .getIndex()))
                                        .getTotalHits())
                                .isEqualTo(expected);
                    } // With no document operations, lazy index creation legitimately leaves no document index.
                    System.out.printf(
                            Locale.ROOT,
                            "CHECKPOINT_BENCH workload=%s iteration=%d warmup=%s elapsed_ms=%.3f documents=%d%n",
                            workload,
                            iteration,
                            iteration == 0,
                            elapsed / 1000000.0,
                            expected);
                } finally {
                    crawler.close();
                    crawler = null;
                    cleanIndexAndTemplates(settings, settings.getName());
                    var handler = new FsCrawlerCheckpointFileHandler(metadataDir);
                    handler.clean(settings.getName());
                }
            }
        }
    }
}
