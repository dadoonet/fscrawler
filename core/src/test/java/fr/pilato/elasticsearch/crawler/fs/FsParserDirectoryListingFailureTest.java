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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carrotsearch.randomizedtesting.jupiter.RandomizedTest;
import fr.pilato.elasticsearch.crawler.fs.beans.CrawlerState;
import fr.pilato.elasticsearch.crawler.fs.beans.FileAbstractModel;
import fr.pilato.elasticsearch.crawler.fs.beans.FsCrawlerCheckpoint;
import fr.pilato.elasticsearch.crawler.fs.beans.FsCrawlerCheckpointFileHandler;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentService;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerManagementService;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import fr.pilato.elasticsearch.crawler.fs.test.framework.Slow;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerExtensionFsProvider;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPluginException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AccessDeniedException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@Slow
class FsParserDirectoryListingFailureTest extends AbstractFSCrawlerTestCase {
    @ParameterizedTest
    @CsvSource({"false, denied", "true, denied", "false, interrupted", "true, interrupted", "false, null", "true, null"
    })
    void failedListingPreservesIndexAndPendingWorkThenRecovers(boolean continueOnError, String failureMode)
            throws Exception {
        FsSettings settings = FsSettingsLoader.load();
        settings.setName(jobName);
        String root = testTmpDir.toString();
        String failed = testTmpDir.resolve(randomName()).toString();
        String healthy = testTmpDir.resolve(randomName()).toString();
        settings.getFs().setUrl(root);
        settings.getFs().setJsonSupport(true);
        settings.getFs().setContinueOnError(continueOnError);
        settings.getFs().setRemoveDeleted(true);
        settings.getFs().setIndexFolders(true);
        settings.getElasticsearch().setIndex(randomName());
        settings.getElasticsearch().setIndexFolder(randomName());

        FsCrawlerExtensionFsProvider provider = mock(FsCrawlerExtensionFsProvider.class);
        when(provider.exists(root)).thenReturn(true);
        FileAbstractModel failedDirectory = mock(FileAbstractModel.class);
        when(failedDirectory.getName())
                .thenReturn(java.nio.file.Path.of(failed).getFileName().toString());
        when(failedDirectory.getFullpath()).thenReturn(failed);
        when(failedDirectory.isDirectory()).thenReturn(true);
        FileAbstractModel healthyDirectory = mock(FileAbstractModel.class);
        when(healthyDirectory.getName())
                .thenReturn(java.nio.file.Path.of(healthy).getFileName().toString());
        when(healthyDirectory.getFullpath()).thenReturn(healthy);
        when(healthyDirectory.isDirectory()).thenReturn(true);
        when(provider.getFiles(root)).thenReturn(List.of(failedDirectory, healthyDirectory));
        if (failureMode.equals("null")) {
            when(provider.getFiles(failed)).thenReturn(null).thenReturn(List.of());
        } else {
            RuntimeException failure = failureMode.equals("denied")
                    ? new FsCrawlerPluginException("Cannot list " + failed, new AccessDeniedException(failed))
                    : new UncheckedIOException(new IOException("Network enumeration interrupted"));
            when(provider.getFiles(failed)).thenThrow(failure).thenReturn(List.of());
        }
        String childName = randomName();
        String healthyChild = java.nio.file.Path.of(healthy).resolve(childName).toString();
        FileAbstractModel child = mock(FileAbstractModel.class);
        when(child.getName()).thenReturn(childName);
        when(child.getFullpath()).thenReturn(healthyChild);
        when(child.isDirectory()).thenReturn(true);
        AtomicBoolean firstScan = new AtomicBoolean(true);
        when(provider.getFiles(healthy)).thenAnswer(invocation -> {
            if (continueOnError && firstScan.get()) {
                // Failure must already be persisted while other work is still running.
                FsCrawlerCheckpoint saved = new FsCrawlerCheckpointFileHandler(testTmpDir).read(jobName);
                assertThat(saved.getPendingPaths()).contains(failed);
                assertThat(saved.getCompletedPaths()).doesNotContain(failed);
            }
            return List.of(child);
        });
        when(provider.getFiles(healthyChild)).thenReturn(List.of());
        FsCrawlerManagementService management = mock(FsCrawlerManagementService.class);
        FsCrawlerDocumentService documents = mock(FsCrawlerDocumentService.class);
        String deletedFilename = randomName() + ".txt";
        String deletedFolder = testTmpDir.resolve(randomName()).toString();
        when(management.getFileDirectory(failed)).thenReturn(List.of(deletedFilename));
        when(management.getFolderDirectory(failed)).thenReturn(List.of(deletedFolder));
        when(management.getFileDirectory(deletedFolder)).thenReturn(List.of(randomName() + ".txt"));
        when(management.getFolderDirectory(deletedFolder)).thenReturn(List.of());
        when(management.getFileDirectory(healthy)).thenReturn(List.of());
        when(management.getFolderDirectory(healthy)).thenReturn(List.of());

        FsCrawlerCheckpoint checkpoint = FsCrawlerCheckpoint.newCheckpoint(root);
        Instant watermark =
                Instant.now().minusSeconds(RandomizedTest.randomIntInRange(randomizedRandomForTests, 60, 600));
        checkpoint.setScanDate(watermark);
        checkpoint.clearPendingPaths();
        checkpoint.addPath(failed);
        checkpoint.addPath(healthy);
        checkpoint.markCompleted(root);
        FsParser parser = new FsParser(settings, testTmpDir, management, documents, 1, false, provider);
        parser.getCheckpointHandler().write(jobName, checkpoint);
        parser.closed.set(false);
        parser.run();

        FsCrawlerCheckpoint interrupted = parser.getCheckpointHandler().read(jobName);
        assertThat(interrupted.getState()).isEqualTo(CrawlerState.ERROR);
        assertThat(interrupted.getScanDate()).isEqualTo(watermark);
        assertThat(interrupted.getPendingPaths()).contains(failed);
        assertThat(interrupted.getCompletedPaths()).doesNotContain(failed);
        assertThat(interrupted.getLastError()).contains(failed);
        verify(management, never()).getFileDirectory(failed);
        verify(management, never()).getFolderDirectory(failed);
        verify(documents, never()).delete(anyString(), anyString());
        verify(management, never()).delete(anyString(), anyString());
        if (continueOnError) {
            verify(provider).getFiles(healthy);
            verify(provider).getFiles(healthyChild);
            assertThat(interrupted.getCompletedPaths()).contains(healthy, healthyChild);
            assertThat(interrupted.getPendingPaths()).containsExactly(failed);
        } else {
            verify(provider, never()).getFiles(healthy);
            assertThat(interrupted.getPendingPaths()).contains(healthy);
        }
        verify(provider, times(1)).getFiles(failed);

        // A fresh process resumes the saved checkpoint. A successful empty listing
        // must still reconcile genuinely deleted files and folders.
        firstScan.set(false);
        FsParser resumed = new FsParser(settings, testTmpDir, management, documents, 1, false, provider);
        resumed.closed.set(false);
        resumed.run();
        FsCrawlerCheckpoint completed = resumed.getCheckpointHandler().read(jobName);
        assertThat(completed.getState()).isEqualTo(CrawlerState.COMPLETED);
        assertThat(completed.getPendingPaths()).isEmpty();
        verify(documents).delete(eq(settings.getElasticsearch().getIndex()), anyString());
        verify(management).delete(eq(settings.getElasticsearch().getIndexFolder()), anyString());
    }

    private String randomName() {
        return RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 6, 12)
                .toLowerCase(Locale.ROOT);
    }
}
