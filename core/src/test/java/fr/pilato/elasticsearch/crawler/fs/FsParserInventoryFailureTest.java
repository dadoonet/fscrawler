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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carrotsearch.randomizedtesting.jupiter.RandomizedTest;
import fr.pilato.elasticsearch.crawler.fs.beans.CrawlerState;
import fr.pilato.elasticsearch.crawler.fs.beans.FsCrawlerCheckpoint;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchHit;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.client.ESTermQuery;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientException;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchIndexNotFoundException;
import fr.pilato.elasticsearch.crawler.fs.client.IElasticsearchClient;
import fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil;
import fr.pilato.elasticsearch.crawler.fs.framework.SignTool;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentService;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerManagementServiceElasticsearchImpl;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import fr.pilato.elasticsearch.crawler.fs.test.framework.Slow;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerExtensionFsProvider;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Query failure must never become an authoritative empty Elasticsearch inventory. */
@Slow
class FsParserInventoryFailureTest extends AbstractFSCrawlerTestCase {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failedInventoryPropagatesOriginalException(boolean folders) throws Exception {
        var settings = settings();
        var client = mock(IElasticsearchClient.class);
        var failure = new ElasticsearchClientException("Retries exhausted: " + randomName());
        when(client.search(any())).thenThrow(failure);
        var service = management(settings, client);
        assertThatThrownBy(() -> inventory(service, folders, settings.getFs().getUrl()))
                .isSameAs(failure);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void explicitlyMissingIndexIsAnEmptyInventory(boolean folders) throws Exception {
        var settings = settings();
        var client = mock(IElasticsearchClient.class);
        when(client.search(any()))
                .thenThrow(new ElasticsearchIndexNotFoundException(
                        folders
                                ? settings.getElasticsearch().getIndexFolder()
                                : settings.getElasticsearch().getIndex()));
        assertThat(inventory(
                        management(settings, client), folders, settings.getFs().getUrl()))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void successfulEmptyInventoryRemainsEmpty(boolean folders) throws Exception {
        var settings = settings();
        var client = mock(IElasticsearchClient.class);
        when(client.search(any())).thenReturn(new ESSearchResponse("{}"));
        assertThat(inventory(
                        management(settings, client), folders, settings.getFs().getUrl()))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void successfulInventoryReturnsItsIndexedEntries(boolean folders) throws Exception {
        var settings = settings();
        var client = mock(IElasticsearchClient.class);
        String entry = randomName();
        var response = new ESSearchResponse("{}");
        response.addHit(folders ? folder(entry) : file(entry));
        when(client.search(any())).thenReturn(response);
        assertThat(inventory(
                        management(settings, client), folders, settings.getFs().getUrl()))
                .containsExactly(entry);
    }

    @ParameterizedTest
    @CsvSource({
        "false, false, false",
        "false, false, true",
        "false, true, false",
        "false, true, true",
        "true, false, false",
        "true, false, true",
        "true, true, false",
        "true, true, true"
    })
    void failedInventoryPreservesCheckpointAndFolderRecordsThenRecovers(
            boolean folders, boolean child, boolean continueOnError) throws Exception {
        var settings = settings();
        settings.getFs().setContinueOnError(continueOnError);
        String root = settings.getFs().getUrl();
        String gone = testTmpDir.resolve(randomName()).toString();
        String filename = randomName() + ".txt";
        String goneId = sign(settings, gone);
        String rootId = sign(settings, root);
        String failedId = child ? goneId : rootId;
        String failedIndex = folders
                ? settings.getElasticsearch().getIndexFolder()
                : settings.getElasticsearch().getIndex();
        var client = mock(IElasticsearchClient.class);
        var unavailable = new AtomicBoolean(true);
        var failure = new ElasticsearchClientException("Inventory query unavailable: " + randomName());
        when(client.search(any())).thenAnswer(call -> {
            ESSearchRequest request = call.getArgument(0);
            String pathId = ((ESTermQuery) request.getESQuery()).getValue();
            if (unavailable.get() && request.getIndex().equals(failedIndex) && pathId.equals(failedId)) {
                throw failure;
            }
            var response = new ESSearchResponse("{}");
            if (request.getIndex().equals(settings.getElasticsearch().getIndexFolder()) && pathId.equals(rootId)) {
                response.addHit(folder(gone));
            } else if (request.getIndex().equals(settings.getElasticsearch().getIndex()) && pathId.equals(goneId)) {
                response.addHit(file(filename));
            }
            return response;
        });
        var provider = mock(FsCrawlerExtensionFsProvider.class);
        when(provider.exists(root)).thenReturn(true);
        when(provider.getFiles(root)).thenReturn(List.of());
        var service = management(settings, client);
        var checkpoint = FsCrawlerCheckpoint.newCheckpoint(root);
        Instant watermark =
                Instant.now().minusSeconds(RandomizedTest.randomIntInRange(randomizedRandomForTests, 60, 600));
        checkpoint.setScanDate(watermark);
        var parser = parser(settings, service, provider);
        parser.getCheckpointHandler().write(jobName, checkpoint);
        parser.closed.set(false);
        parser.run();

        var failed = parser.getCheckpointHandler().read(jobName);
        assertThat(failed.getState()).isEqualTo(CrawlerState.ERROR);
        assertThat(failed.getScanDate()).isEqualTo(watermark);
        assertThat(failed.getScanEndTime()).isNull();
        assertThat(failed.getPendingPaths()).contains(root);
        assertThat(failed.getCompletedPaths()).doesNotContain(root);
        assertThat(failed.getLastError()).contains(failure.getMessage());
        // Document deletions already accepted before a later query fails may be replayed; discovery records stay.
        verify(client, never()).delete(settings.getElasticsearch().getIndexFolder(), goneId);
        verify(client, never()).delete(settings.getElasticsearch().getIndexFolder(), rootId);

        unavailable.set(false);
        var resumed = parser(settings, service, provider);
        resumed.closed.set(false);
        resumed.run();
        var recovered = resumed.getCheckpointHandler().read(jobName);
        assertThat(recovered.getState()).isEqualTo(CrawlerState.COMPLETED);
        assertThat(recovered.getPendingPaths()).isEmpty();
        verify(client, atLeastOnce())
                .delete(
                        settings.getElasticsearch().getIndex(),
                        sign(
                                settings,
                                fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.computeRealPathName(
                                                gone, filename)
                                        .replace(java.io.File.separatorChar, '/')));
        verify(client).delete(settings.getElasticsearch().getIndexFolder(), goneId);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void firstCrawlCanCompleteWhenInventoryIndicesDoNotExistYet(boolean continueOnError) throws Exception {
        var settings = settings();
        settings.getFs().setContinueOnError(continueOnError);
        var client = mock(IElasticsearchClient.class);
        when(client.search(any())).thenAnswer(call -> {
            ESSearchRequest request = call.getArgument(0);
            throw new ElasticsearchIndexNotFoundException(request.getIndex());
        });
        var provider = mock(FsCrawlerExtensionFsProvider.class);
        when(provider.exists(settings.getFs().getUrl())).thenReturn(true);
        when(provider.getFiles(settings.getFs().getUrl())).thenReturn(List.of());
        var parser = parser(settings, management(settings, client), provider);
        parser.closed.set(false);
        parser.run();
        assertThat(parser.getCheckpointHandler().read(jobName).getState()).isEqualTo(CrawlerState.COMPLETED);
        verify(client, never()).delete(anyString(), anyString());
    }

    private FsSettings settings() {
        var settings = FsSettingsLoader.load();
        settings.setName(jobName);
        settings.getFs().setUrl(testTmpDir.toString());
        settings.getFs().setJsonSupport(true);
        settings.getFs().setRemoveDeleted(true);
        settings.getFs().setIndexFolders(true);
        settings.getElasticsearch().setIndex(randomName());
        settings.getElasticsearch().setIndexFolder(randomName());
        return settings;
    }

    private FsParser parser(
            FsSettings settings,
            FsCrawlerManagementServiceElasticsearchImpl service,
            FsCrawlerExtensionFsProvider provider) {
        return new FsParser(settings, testTmpDir, service, mock(FsCrawlerDocumentService.class), 1, false, provider);
    }

    private FsCrawlerManagementServiceElasticsearchImpl management(FsSettings settings, IElasticsearchClient client)
            throws Exception {
        var service = new FsCrawlerManagementServiceElasticsearchImpl(settings);
        var field = FsCrawlerManagementServiceElasticsearchImpl.class.getDeclaredField("client");
        field.setAccessible(true);
        field.set(service, client);
        return service;
    }

    private static java.util.Collection<String> inventory(
            FsCrawlerManagementServiceElasticsearchImpl service, boolean folders, String path) throws Exception {
        return folders ? service.getFolderDirectory(path) : service.getFileDirectory(path);
    }

    private static ESSearchHit file(String filename) {
        var hit = new ESSearchHit();
        hit.setStoredFields(Map.of("file.filename", List.of(filename)));
        return hit;
    }

    private static ESSearchHit folder(String path) {
        var hit = new ESSearchHit();
        hit.setSource(JsonUtil.serialize(Map.of("path", Map.of("real", path))));
        return hit;
    }

    private static String sign(FsSettings settings, String path) throws Exception {
        return SignTool.sign(settings.getFs().getHashAlgorithm(), path);
    }

    private String randomName() {
        return RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 6, 12)
                .toLowerCase(Locale.ROOT);
    }
}
