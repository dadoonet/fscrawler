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

import static org.assertj.core.api.Assertions.assertThat;

import com.carrotsearch.randomizedtesting.jupiter.RandomizedTest;
import fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl;
import fr.pilato.elasticsearch.crawler.fs.beans.CrawlerState;
import fr.pilato.elasticsearch.crawler.fs.beans.FsCrawlerCheckpointFileHandler;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.test.framework.VerySlow;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Real UTF-8 text, Tika and Elasticsearch exercise recovery across 40 directories, eight branches five levels deep. A
 * provider hook stops traversal at a known file, after an acknowledged mid-directory checkpoint. A new crawler,
 * provider and bulk client then recover exclusively from disk. No sleeps, drive letters or path separators are used.
 */
@VerySlow
@org.junit.jupiter.api.parallel.Execution(org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD)
class FsCrawlerCheckpointNestedReplayIT extends AbstractFsCrawlerITCase {
    /**
     * The second case deliberately augments a genuine saved frontier: replay the root and insert an already completed
     * directory in its queue. This exercises defensive skip/deduplication paths that a normal queue may not encounter.
     */
    @ParameterizedTest(name = "nested recovery, replay parent and completed queue entry = {0}")
    @ValueSource(booleans = {false, true})
    void nestedTextTreeResumesFromCheckpoint(boolean replayParent) throws Exception {
        Path source = Files.createDirectory(currentTestResourceDir.resolve("nested-recovery"));
        String token = RandomizedTest.randomAsciiLettersOfLength(randomizedRandomForTests, 8);
        Map<Path, String> contents = new LinkedHashMap<>();
        List<Path> directories = new ArrayList<>();
        for (int branch = 0; branch < 8; branch++) {
            Path parent = source;
            for (int depth = 1; depth <= 5; depth++) {
                Path directory = Files.createDirectory(parent.resolve("a" + branch + "d" + depth + token));
                directories.add(directory);
                for (int file = 0; file < 25; file++) {
                    String name = "z" + branch + "d" + depth + "f" + String.format(java.util.Locale.ROOT, "%02d", file)
                            + token + ".txt";
                    String text = "Recovery document " + name + " caf\u00e9\n";
                    Path path = directory.resolve(name);
                    Files.writeString(path, text, StandardCharsets.UTF_8);
                    contents.put(path, text);
                }
                parent = directory;
            }
        }
        assertThat(directories).hasSize(40);
        assertThat(contents).hasSize(1000);
        assertThat(directories.stream()
                        .mapToInt(p -> source.relativize(p).getNameCount())
                        .max()
                        .orElseThrow())
                .isEqualTo(5);

        FsSettings settings = createTestSettings(getCrawlerName() + "_" + token.toLowerCase(java.util.Locale.ROOT));
        settings.getFs().setUrl(source.toString());
        settings.getFs().setProvider("checkpoint-recovery");
        settings.getFs().setFilenameAsId(true);
        settings.getFs().setIndexFolders(true);
        settings.getFs().setRemoveDeleted(false);
        settings.getFs().setContinueOnError(false);
        settings.getFs().getCheckpoint().setMaxDirectories(1);
        settings.getFs().getCheckpoint().setMaxOperations(10);
        settings.getElasticsearch().setBulkSize(10);
        settings.getElasticsearch().setFlushInterval(null);
        var handler = new FsCrawlerCheckpointFileHandler(metadataDir);
        // Natural recovery stops at depth five, after earlier whole branches have completed.
        // Root replay stops at depth one so the root has both completed and already-pending children.
        Path interrupted = directories.get(3 * 5 + (replayParent ? 0 : 4));
        List<Path> interruptedFiles = contents.keySet().stream()
                .filter(p -> p.getParent().equals(interrupted))
                .toList();
        Path stopAt = interruptedFiles.get(12);
        try {
            crawler = new FsCrawlerImpl(metadataDir, settings, 1, false);
            var originalProvider = provider();
            originalProvider.beforeRead = path -> {
                if (path.equals(stopAt.toString())) crawler.getFsParser().close();
            };
            crawler.start();
            awaitStopped();
            crawler.close(); // Join the old worker and close its clients before constructing the replacement.
            crawler = null;

            var saved = handler.read(settings.getName());
            assertThat(originalProvider.reads).containsKey(stopAt.toString());
            assertThat(saved.getState()).isNotEqualTo(CrawlerState.COMPLETED);
            assertThat(saved.getCurrentPath()).isEqualTo(interrupted.toString());
            assertThat(saved.getPendingPaths()).contains(interrupted.toString());
            assertThat(saved.getCompletedPaths())
                    .doesNotContain(interrupted.toString())
                    .isNotEmpty();
            assertThat(saved.getCurrentPathFilesIndexedCount()).isZero();
            assertThat(saved.getPendingPaths().size()).isGreaterThan(1);
            var completed = new HashSet<>(saved.getCompletedPaths());
            if (!replayParent) {
                assertThat(source.relativize(interrupted).getNameCount()).isEqualTo(5);
                assertThat(completed)
                        .as("the first five-level branch is fully acknowledged before interruption")
                        .containsAll(directories.subList(0, 5).stream()
                                .map(Path::toString)
                                .toList());
            }
            long completedFileCount = contents.keySet().stream()
                    .filter(p -> completed.contains(p.getParent().toString()))
                    .count();
            assertThat(saved.getFilesProcessed()).isEqualTo(completedFileCount);
            client.refresh(settings.getElasticsearch().getIndex());
            Path replayedFile = interruptedFiles.get(0);
            long previousVersion = client.get(
                            settings.getElasticsearch().getIndex(),
                            replayedFile.getFileName().toString())
                    .getVersion();
            assertThat(previousVersion).isPositive();

            if (replayParent) {
                completed.remove(source.toString());
                saved.getCompletedPaths().remove(source.toString());
                saved.setCurrentPath(source.toString());
                saved.addPathFirst(completed.iterator().next());
                saved.addPathFirst(source.toString());
                handler.write(settings.getName(), saved);
            }
            crawler = new FsCrawlerImpl(metadataDir, settings, 1, false);
            var recoveredProvider = provider();
            assertThat(recoveredProvider).isNotSameAs(originalProvider);
            assertThat(recoveredProvider.listings).isEmpty();
            var observedQueues = new java.util.concurrent.ConcurrentLinkedQueue<List<String>>();
            if (replayParent) {
                recoveredProvider.beforeListing = path -> {
                    if (!path.equals(source.toString())) {
                        observedQueues.add(List.copyOf(
                                crawler.getFsParser().getCheckpoint().getPendingPaths()));
                    }
                };
            }
            crawler.start();
            awaitStopped();
            crawler.close();
            crawler = null;

            if (replayParent) {
                assertThat(observedQueues).isNotEmpty();
                for (var queue : observedQueues) {
                    assertThat(queue)
                            .as("parent replay must not queue completed children or duplicate pending children")
                            .doesNotHaveDuplicates()
                            .doesNotContainAnyElementsOf(completed);
                }
            }
            var recovered = handler.read(settings.getName());
            assertThat(recovered.getState()).isEqualTo(CrawlerState.COMPLETED);
            assertThat(recovered.getFilesProcessed()).isEqualTo(1000);
            assertThat(recovered.getFilesDeleted()).isZero();
            assertThat(recovered.getPendingPaths()).isEmpty();
            assertThat(recovered.getCompletedPaths()).isEmpty();
            assertThat(recovered.getCurrentPath()).isNull();
            var expectedListings =
                    new HashSet<>(directories.stream().map(Path::toString).toList());
            expectedListings.add(source.toString());
            expectedListings.removeAll(completed);
            assertThat(recoveredProvider.listings.keySet()).containsExactlyInAnyOrderElementsOf(expectedListings);
            assertThat(recoveredProvider.listings.values()).allMatch(count -> count == 1);
            var expectedReads = contents.keySet().stream()
                    .filter(p -> !completed.contains(p.getParent().toString()))
                    .map(Path::toString)
                    .toList();
            assertThat(recoveredProvider.reads.keySet()).containsExactlyInAnyOrderElementsOf(expectedReads);
            // All 25 files in the interrupted directory were opened again, including files already acknowledged.
            assertThat(recoveredProvider.reads.keySet())
                    .containsAll(interruptedFiles.stream().map(Path::toString).toList());
            client.refresh(settings.getElasticsearch().getIndex());
            var result = client.search(new ESSearchRequest()
                    .withIndex(settings.getElasticsearch().getIndex())
                    .withSize(1000));
            assertThat(result.getTotalHits()).isEqualTo(1000);
            Map<String, String> expectedContent = new LinkedHashMap<>();
            contents.forEach(
                    (path, text) -> expectedContent.put(path.getFileName().toString(), text.strip()));
            Map<String, String> actualContent = new LinkedHashMap<>();
            for (var hit : result.getHits()) {
                var document = JsonUtil.prettyMapper.readTree(hit.getSource());
                actualContent.put(
                        document.path("file").path("filename").asText(),
                        document.path("content").asText().strip());
            }
            assertThat(actualContent).containsExactlyInAnyOrderEntriesOf(expectedContent);
            assertThat(client.get(
                                    settings.getElasticsearch().getIndex(),
                                    replayedFile.getFileName().toString())
                            .getVersion())
                    .isEqualTo(previousVersion + 1);
            Path completedFile = contents.keySet().stream()
                    .filter(p -> completed.contains(p.getParent().toString()))
                    .findFirst()
                    .orElseThrow();
            assertThat(client.get(
                                    settings.getElasticsearch().getIndex(),
                                    completedFile.getFileName().toString())
                            .getVersion())
                    .isEqualTo(1);
            client.refresh(settings.getElasticsearch().getIndexFolder());
            assertThat(client.search(new ESSearchRequest()
                                    .withIndex(settings.getElasticsearch().getIndexFolder()))
                            .getTotalHits())
                    .isEqualTo(41);
        } finally {
            if (crawler != null) {
                crawler.close();
                crawler = null;
            }
            cleanIndexAndTemplates(settings, settings.getName());
        }
    }

    private CheckpointRecoveryFsProvider provider() {
        return (CheckpointRecoveryFsProvider) crawler.getPluginsManager().findFsProvider("checkpoint-recovery");
    }

    private void awaitStopped() {
        Awaitility.await()
                .atMost(Duration.ofMinutes(2))
                .until(() -> crawler.getFsParser().isClosed());
    }
}
