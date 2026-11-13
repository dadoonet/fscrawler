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
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Two persistent JVMs take turns under the external coordinator. Both prepare fixtures before either starts timing;
 * crawler close, index cleanup and checkpoint cleanup finish before the next turn. Identical sources run on old and
 * fixed production code. This opt-in benchmark deliberately uses stable test-only filesystem ordering.
 */
@VerySlow
@EnabledIfSystemProperty(named = "tests.checkpoint.paired.variant", matches = "baseline|fixed")
class FsCrawlerCheckpointPairedBenchmarkIT extends AbstractFsCrawlerITCase {
    private Path control;

    @Test
    @org.junit.jupiter.api.Timeout(value = 120, unit = java.util.concurrent.TimeUnit.MINUTES)
    void alternatingHealthyWorkloads() throws Exception {
        String variant = System.getProperty("tests.checkpoint.paired.variant");
        control = Path.of(System.getProperty("tests.checkpoint.paired.control"));
        int warmups = Integer.getInteger("tests.checkpoint.paired.warmups", 2);
        int repeats = Integer.getInteger("tests.checkpoint.paired.repeats", 10);
        int filesPerDataset = Integer.getInteger("tests.checkpoint.paired.files", 50000);
        int directories = Integer.getInteger("tests.checkpoint.paired.directories", 500);
        Assertions.assertThat(filesPerDataset % directories).isZero();
        for (String workload : java.util.List.of("single-50000", "many-50000")) {
            // A phase gate prevents preparing the next fixture while the partner's preceding crawl is timed.
            awaitSignal("prepare-" + workload + ".start");
            var source = Files.createDirectories(currentTestResourceDir.resolve(workload));
            for (int f = 0; f < filesPerDataset; f++) {
                Path directory = workload.equals("single-50000")
                        ? source
                        : Files.createDirectories(source.resolve("d" + (f / (filesPerDataset / directories))));
                Files.writeString(directory.resolve("f" + f + ".json"), "{\"value\":1}");
            }
            Files.writeString(control.resolve(variant + "-" + workload + ".ready"), "ready");
            for (int run = 0; run < warmups + repeats; run++) {
                String turn = variant + "-" + workload + "-" + run;
                awaitSignal(turn + ".start");
                var settings = createTestSettings(getCrawlerName() + "_" + variant);
                settings.getFs().setUrl(source.toString());
                settings.getFs().setProvider("checkpoint-benchmark");
                settings.getFs().setJsonSupport(true);
                settings.getFs().setIndexFolders(false);
                settings.getFs().setRemoveDeleted(false);
                settings.getElasticsearch().setBulkSize(100);
                settings.getElasticsearch().setFlushInterval(TimeValue.timeValueSeconds(5));
                cleanIndexAndTemplates(settings, settings.getName());
                long elapsed;
                long begin = System.nanoTime();
                crawler = new FsCrawlerImpl(metadataDir, settings, 1, false);
                try {
                    crawler.start();
                    Awaitility.await()
                            .pollInterval(Duration.ofMillis(10))
                            .atMost(Duration.ofMinutes(10))
                            .until(() -> crawler.getFsParser().isClosed());
                    elapsed = System.nanoTime() - begin;
                    var saved = new FsCrawlerCheckpointFileHandler(metadataDir).read(settings.getName());
                    Assertions.assertThat(saved.getState()).isEqualTo(CrawlerState.COMPLETED);
                    client.refresh(settings.getElasticsearch().getIndex());
                    Assertions.assertThat(client.search(new ESSearchRequest()
                                            .withIndex(
                                                    settings.getElasticsearch().getIndex()))
                                    .getTotalHits())
                            .isEqualTo(filesPerDataset);
                    System.out.printf(
                            Locale.ROOT,
                            "CHECKPOINT_PAIRED variant=%s workload=%s run=%d warmup=%s elapsed_ms=%.3f documents=%d%n",
                            variant,
                            workload,
                            run,
                            run < warmups,
                            elapsed / 1000000.0,
                            filesPerDataset);
                } finally {
                    crawler.close();
                    crawler = null;
                    cleanIndexAndTemplates(settings, settings.getName());
                    new FsCrawlerCheckpointFileHandler(metadataDir).clean(settings.getName());
                }
                // Publish only after validation and cleanup. Atomic publication prevents partially read samples.
                String sample = String.format(
                        Locale.ROOT,
                        "{\"variant\":\"%s\",\"workload\":\"%s\",\"run\":%d,\"warmup\":%s,\"elapsed_ms\":%.3f,\"documents\":%d,\"state\":\"COMPLETED\"}",
                        variant,
                        workload,
                        run,
                        run < warmups,
                        elapsed / 1000000.0,
                        filesPerDataset);
                Path temporary = control.resolve(turn + ".tmp");
                Files.writeString(temporary, sample);
                Files.move(temporary, control.resolve(turn + ".done"), StandardCopyOption.ATOMIC_MOVE);
            }
        }
        // Keep final fixture teardown from overlapping the other JVM's last measured crawl.
        awaitSignal("finish-" + variant + ".start");
    }

    private void awaitSignal(String filename) {
        Awaitility.await()
                .pollInterval(Duration.ofMillis(100))
                .atMost(Duration.ofMinutes(15))
                .until(() -> {
                    if (Files.exists(control.resolve("stop"))) {
                        throw new IllegalStateException("Paired benchmark stopped by coordinator");
                    }
                    return Files.exists(control.resolve(filename));
                });
    }
}
