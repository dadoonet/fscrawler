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
package fr.pilato.elasticsearch.crawler.fs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carrotsearch.randomizedtesting.jupiter.RandomizedTest;
import fr.pilato.elasticsearch.crawler.fs.beans.CrawlerState;
import fr.pilato.elasticsearch.crawler.fs.beans.FileAbstractModel;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientException;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentService;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerManagementService;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import fr.pilato.elasticsearch.crawler.fs.test.framework.Slow;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerExtensionFsProvider;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Processed directories must remain replayable until their writes have succeeded. A fresh parser and document service
 * model losing the previous process's RAM queue.
 */
@Slow
class FsParserCheckpointAcknowledgementTest extends AbstractFSCrawlerTestCase {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failureBeforeAcknowledgementReplaysPreviouslyProcessedDirectory(boolean listingFailure) throws Exception {
        String root = testTmpDir.toString();
        String first = testTmpDir.resolve(randomName()).toString();
        String second = testTmpDir.resolve(randomName()).toString();
        var settings = FsSettingsLoader.load();
        settings.setName(jobName);
        settings.getElasticsearch().setIndex(randomName());
        settings.getElasticsearch().setIndexFolder(randomName());
        settings.getFs().setUrl(root);
        settings.getFs().setJsonSupport(true);
        settings.getFs().setIndexFolders(false);
        settings.getFs().setRemoveDeleted(false);
        var provider = mock(FsCrawlerExtensionFsProvider.class);
        when(provider.exists(root)).thenReturn(true);
        when(provider.getFiles(root)).thenReturn(List.of(model(root, first, false), model(root, second, false)));
        when(provider.getFiles(first)).thenReturn(List.of(model(first, first + "/" + randomName() + ".json", true)));
        doReturn(List.of()).when(provider).getFiles(second);
        when(provider.getInputStream(any()))
                .thenAnswer(
                        invocation -> new ByteArrayInputStream("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var management = mock(FsCrawlerManagementService.class);
        var documents = mock(FsCrawlerDocumentService.class);
        if (listingFailure)
            when(provider.getFiles(second)).thenThrow(new IllegalStateException("simulated lost connection"));
        else
            doThrow(new ElasticsearchClientException("simulated failed acknowledgement"))
                    .when(documents)
                    .flushAndEnsureBulkSucceeded();
        var parser = new FsParser(settings, testTmpDir, management, documents, 1, false, provider);
        parser.closed.set(false);
        parser.run();
        verify(documents).indexRawJson(anyString(), anyString(), anyString(), nullable(String.class));
        var saved = parser.getCheckpointHandler().read(jobName);
        assertThat(saved.getState()).isEqualTo(CrawlerState.ERROR);
        assertThat(saved.getCompletedPaths())
                .as("queued writes do not make a directory durably completed")
                .doesNotContain(first);
        assertThat(saved.hasPendingWork())
                .as("restart must retain a traversal frontier")
                .isTrue();
        doReturn(List.of()).when(provider).getFiles(second);
        var freshDocuments = mock(FsCrawlerDocumentService.class);
        var restarted = new FsParser(settings, testTmpDir, management, freshDocuments, 1, false, provider);
        restarted.closed.set(false);
        restarted.run();
        verify(freshDocuments).indexRawJson(anyString(), anyString(), anyString(), nullable(String.class));
        assertThat(restarted.getCheckpointHandler().read(jobName).getState()).isEqualTo(CrawlerState.COMPLETED);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void pauseOrCloseCannotPersistSpeculativeCompletedDirectories(boolean close) throws Exception {
        String root = testTmpDir.toString();
        String first = testTmpDir.resolve(randomName()).toString();
        String second = testTmpDir.resolve(randomName()).toString();
        var settings = FsSettingsLoader.load();
        settings.setName(jobName);
        settings.getElasticsearch().setIndex(randomName());
        settings.getFs().setUrl(root);
        settings.getFs().setJsonSupport(true);
        settings.getFs().setIndexFolders(false);
        settings.getFs().setRemoveDeleted(false);
        var provider = mock(FsCrawlerExtensionFsProvider.class);
        when(provider.exists(root)).thenReturn(true);
        when(provider.getFiles(root)).thenReturn(List.of(model(root, first, false), model(root, second, false)));
        when(provider.getFiles(first)).thenReturn(List.of(model(first, first + "/" + randomName() + ".json", true)));
        when(provider.getInputStream(any()))
                .thenAnswer(i -> new ByteArrayInputStream("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var parser = new FsParser(
                settings,
                testTmpDir,
                mock(FsCrawlerManagementService.class),
                mock(FsCrawlerDocumentService.class),
                1,
                false,
                provider);
        when(provider.getFiles(second)).thenAnswer(i -> {
            if (close) parser.close();
            else parser.pause();
            var saved = parser.getCheckpointHandler().read(jobName);
            assertThat(saved.getCompletedPaths())
                    .as("lifecycle saves must not publish unacknowledged work")
                    .doesNotContain(first);
            assertThat(saved.hasPendingWork()).isTrue();
            throw new IllegalStateException("end simulated lifecycle operation without waiting for resume");
        });
        parser.closed.set(false);
        parser.run();
    }

    @org.junit.jupiter.api.Test
    void directoryCountBatchesCompletionRatherThanFlushingEveryDirectory() throws Exception {
        String root = testTmpDir.toString();
        var settings = FsSettingsLoader.load();
        settings.setName(jobName);
        settings.getFs().setUrl(root);
        settings.getFs().setJsonSupport(true);
        settings.getFs().setIndexFolders(false);
        settings.getFs().setRemoveDeleted(false);
        var provider = mock(FsCrawlerExtensionFsProvider.class);
        var listed = new java.util.concurrent.atomic.AtomicInteger();
        var children = new java.util.ArrayList<FileAbstractModel>();
        for (int n = 0; n < 100; n++)
            children.add(model(root, testTmpDir.resolve(randomName()).toString(), false));
        when(provider.exists(root)).thenReturn(true);
        when(provider.getFiles(anyString())).thenAnswer(i -> {
            listed.incrementAndGet();
            return i.getArgument(0).equals(root) ? children : List.of();
        });
        var documents = mock(FsCrawlerDocumentService.class);
        var barriers = new java.util.ArrayList<Integer>();
        doAnswer(i -> {
                    barriers.add(listed.get());
                    return null;
                })
                .when(documents)
                .flushAndEnsureBulkSucceeded();
        var parser = new FsParser(
                settings, testTmpDir, mock(FsCrawlerManagementService.class), documents, 1, false, provider);
        parser.closed.set(false);
        parser.run();
        assertThat(barriers)
                .as("100 completed directories form one batch, then commit the remainder")
                .containsExactly(100, 101);
        assertThat(parser.getCheckpointHandler().read(jobName).getState()).isEqualTo(CrawlerState.COMPLETED);
    }

    @org.junit.jupiter.api.Test
    void checkpointInsideLargeDirectoryKeepsItReplayableWithoutAFileCursor() throws Exception {
        String root = testTmpDir.toString();
        var settings = FsSettingsLoader.load();
        settings.setName(jobName);
        settings.getElasticsearch().setIndex(randomName());
        settings.getFs().setUrl(root);
        settings.getFs().setJsonSupport(true);
        settings.getFs().setIndexFolders(false);
        settings.getFs().setRemoveDeleted(false);
        var provider = mock(FsCrawlerExtensionFsProvider.class);
        var files = new java.util.ArrayList<FileAbstractModel>();
        for (int n = 0; n < 1001; n++)
            files.add(model(root, testTmpDir.resolve(randomName() + ".json").toString(), true));
        when(provider.exists(root)).thenReturn(true);
        when(provider.getFiles(root)).thenReturn(files);
        var opened = new java.util.concurrent.atomic.AtomicInteger();
        var parser = new FsParser(
                settings,
                testTmpDir,
                mock(FsCrawlerManagementService.class),
                mock(FsCrawlerDocumentService.class),
                1,
                false,
                provider);
        when(provider.getInputStream(any())).thenAnswer(i -> {
            if (opened.incrementAndGet() == 1001) {
                var saved = parser.getCheckpointHandler().read(jobName);
                assertThat(saved.getPendingPaths()).contains(root);
                assertThat(saved.getCompletedPaths()).doesNotContain(root);
                assertThat(saved.getCurrentPathFilesIndexedCount()).isZero();
                assertThat(saved.getFilesProcessed())
                        .as("the unfinished directory is counted once after replay")
                        .isZero();
            }
            return new ByteArrayInputStream("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        });
        parser.closed.set(false);
        parser.run();
        assertThat(opened.get()).isEqualTo(1001);
        var saved = parser.getCheckpointHandler().read(jobName);
        assertThat(saved.getState()).isEqualTo(CrawlerState.COMPLETED);
        assertThat(saved.getFilesProcessed()).isEqualTo(1001);
    }

    private FileAbstractModel model(String parent, String full, boolean file) {
        String name = java.nio.file.Path.of(full).getFileName().toString();
        return new FileAbstractModel(
                name,
                file,
                Instant.now(),
                null,
                null,
                file ? "json" : null,
                parent,
                full,
                2,
                null,
                null,
                0,
                null,
                null);
    }

    private String randomName() {
        return RandomizedTest.randomAsciiLettersOfLength(randomizedRandomForTests, 12);
    }
}
