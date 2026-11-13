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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carrotsearch.randomizedtesting.jupiter.RandomizedTest;
import fr.pilato.elasticsearch.crawler.fs.beans.CrawlerState;
import fr.pilato.elasticsearch.crawler.fs.beans.FileAbstractModel;
import fr.pilato.elasticsearch.crawler.fs.beans.FsCrawlerCheckpoint;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchHit;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.client.ESTermQuery;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClient;
import fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil;
import fr.pilato.elasticsearch.crawler.fs.framework.SignTool;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentService;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerManagementServiceElasticsearchImpl;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import fr.pilato.elasticsearch.crawler.fs.test.framework.Slow;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerExtensionFsProvider;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Checkpoint acknowledgement includes the real management client's bulk queue and failure state. */
@Slow
class FsParserManagementAcknowledgementTest extends AbstractFSCrawlerTestCase {

    @Test
    void completedScanAcknowledgesBufferedFolderWrites() throws Exception {
        var settings = settings();
        try (var client = new RecordingClient(settings)) {
            var management = management(settings, client);
            var parser = run(settings, management, provider(settings));

            assertThat(saved(parser).getState()).isEqualTo(CrawlerState.COMPLETED);
            assertThat(client.delivered)
                    .as("completion must send the root folder write even below the bulk threshold")
                    .containsExactly(
                            operation("index", settings.getElasticsearch().getIndexFolder(), sign(settings, root())));
        }
    }

    @Test
    void terminalManagementFailurePreventsCompletedCheckpoint() throws Exception {
        var settings = settings();
        settings.getElasticsearch().setBulkSize(1);
        try (var client = new RecordingClient(settings)) {
            client.rejectAll = true;
            var parser = run(settings, management(settings, client), provider(settings));

            assertThat(client.getBulkFailureGeneration()).isPositive();
            assertReplayableError(saved(parser));
        }
    }

    @Test
    void managementFailureAtDirectoryBoundaryDoesNotAcknowledgeTraversal() throws Exception {
        var settings = settings();
        settings.getFs().getCheckpoint().setMaxDirectories(1);
        var provider = provider(settings);
        String child = root() + "/" + randomName();
        when(provider.getFiles(root())).thenReturn(List.of(directory(child)));
        try (var client = new RecordingClient(settings)) {
            client.rejectAll = true;
            var parser = run(settings, management(settings, client), provider);

            assertReplayableError(saved(parser));
            verify(provider, never()).getFiles(child);
        }
    }

    @Test
    void laterScanCanRecoverFromPreviousManagementFailure() throws Exception {
        var settings = settings();
        settings.getElasticsearch().setBulkSize(1);
        try (var client = new RecordingClient(settings)) {
            client.rejectAll = true;
            var management = management(settings, client);
            var first = run(settings, management, provider(settings));
            assertReplayableError(saved(first));

            client.rejectAll = false;
            var second = run(settings, management, provider(settings));

            assertThat(saved(second).getState()).isEqualTo(CrawlerState.COMPLETED);
            assertThat(client.delivered)
                    .contains(operation("index", settings.getElasticsearch().getIndexFolder(), sign(settings, root())));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void recursiveDeletionMustBeAcknowledgedBeforeCompletion(boolean rejectDeletes) throws Exception {
        var settings = settings();
        settings.getFs().setRemoveDeleted(true);
        try (var client = new RecordingClient(settings)) {
            client.removedPath = root() + "/" + randomName();
            client.removedFile = randomName() + ".json";
            client.rejectDeletes = rejectDeletes;
            var parser = run(settings, management(settings, client), provider(settings));

            if (rejectDeletes) {
                assertThat(client.getBulkFailureGeneration()).isPositive();
                assertReplayableError(saved(parser));
            } else {
                assertThat(saved(parser).getState()).isEqualTo(CrawlerState.COMPLETED);
                assertThat(client.delivered)
                        .contains(
                                operation(
                                        "delete",
                                        settings.getElasticsearch().getIndex(),
                                        sign(
                                                settings,
                                                (client.removedPath + "/" + client.removedFile).replace('\\', '/'))),
                                operation(
                                        "delete",
                                        settings.getElasticsearch().getIndexFolder(),
                                        sign(settings, client.removedPath)));
            }
        }
    }

    @Test
    void successfulManagementRetryAllowsCompletion() throws Exception {
        var settings = settings();
        try (var client = new RecordingClient(settings)) {
            client.transientRejections = 1;
            var parser = run(settings, management(settings, client), provider(settings));

            assertThat(saved(parser).getState()).isEqualTo(CrawlerState.COMPLETED);
            assertThat(client.requests).isEqualTo(2);
            assertThat(client.getBulkFailureGeneration()).isZero();
            assertThat(client.delivered)
                    .containsExactly(
                            operation("index", settings.getElasticsearch().getIndexFolder(), sign(settings, root())));
        }
    }

    private FsSettings settings() {
        var settings = FsSettingsLoader.load();
        settings.setName(jobName);
        settings.getFs().setUrl(root());
        settings.getFs().setJsonSupport(true);
        settings.getFs().setIndexFolders(true);
        settings.getFs().setRemoveDeleted(false);
        settings.getFs().setContinueOnError(false);
        settings.getElasticsearch().setIndex(randomName());
        settings.getElasticsearch().setIndexFolder(randomName());
        settings.getElasticsearch().setBulkSize(100);
        settings.getElasticsearch().setFlushInterval(null);
        settings.getElasticsearch().setRetryInitialDelay(TimeValue.timeValueMillis(1));
        settings.getElasticsearch().setRetryMaxDelay(TimeValue.timeValueMillis(2));
        return settings;
    }

    private String randomName() {
        return RandomizedTest.randomAsciiLettersOfLength(randomizedRandomForTests, 12)
                .toLowerCase(Locale.ROOT);
    }

    private String root() {
        return testTmpDir.toString();
    }

    private FsCrawlerExtensionFsProvider provider(FsSettings settings) {
        var provider = mock(FsCrawlerExtensionFsProvider.class);
        when(provider.exists(settings.getFs().getUrl())).thenReturn(true);
        when(provider.getFiles(anyString())).thenReturn(List.of());
        return provider;
    }

    private FileAbstractModel directory(String path) {
        return new FileAbstractModel(
                path.substring(path.lastIndexOf('/') + 1),
                false,
                Instant.now(),
                null,
                null,
                null,
                root(),
                path,
                0,
                null,
                null,
                0,
                List.of(),
                null);
    }

    private FsParser run(
            FsSettings settings,
            FsCrawlerManagementServiceElasticsearchImpl management,
            FsCrawlerExtensionFsProvider provider) {
        var parser = new FsParser(
                settings, testTmpDir, management, mock(FsCrawlerDocumentService.class), 1, false, provider);
        parser.closed.set(false);
        parser.run();
        return parser;
    }

    private FsCrawlerCheckpoint saved(FsParser parser) throws Exception {
        return parser.getCheckpointHandler().read(jobName);
    }

    private void assertReplayableError(FsCrawlerCheckpoint checkpoint) {
        assertThat(checkpoint.getState()).isEqualTo(CrawlerState.ERROR);
        assertThat(checkpoint.getLastError()).contains("management");
        assertThat(checkpoint.getScanEndTime()).isNull();
        assertThat(checkpoint.getCompletedPaths()).doesNotContain(root());
        assertThat(checkpoint.getPendingPaths()).contains(root());
    }

    private FsCrawlerManagementServiceElasticsearchImpl management(FsSettings settings, RecordingClient client)
            throws Exception {
        var service = new FsCrawlerManagementServiceElasticsearchImpl(settings);
        var field = FsCrawlerManagementServiceElasticsearchImpl.class.getDeclaredField("client");
        field.setAccessible(true);
        field.set(service, client);
        return service;
    }

    private static String sign(FsSettings settings, String value) throws Exception {
        return SignTool.sign(settings.getFs().getHashAlgorithm(), value);
    }

    private static String operation(String type, String index, String id) {
        return type + ":" + index + ":" + id;
    }

    /** Real bulk processor/listener with only the transport replaced; no sockets or Elasticsearch are required. */
    private final class RecordingClient extends ElasticsearchClient {
        final FsSettings settings;
        final List<String> delivered = new ArrayList<>();
        boolean rejectAll;
        boolean rejectDeletes;
        int transientRejections;
        int requests;
        String removedPath;
        String removedFile;

        RecordingClient(FsSettings settings) {
            super(settings);
            this.settings = settings;
            initBulkProcessor();
        }

        @Override
        public String bulk(String index, String body) {
            requests++;
            boolean transientFailure = transientRejections > 0;
            if (transientFailure) transientRejections--;
            var items = new ArrayList<Map<String, Object>>();
            String[] lines = body.split("\n");
            for (int i = 0; i < lines.length; i++) {
                var header = JsonUtil.mapper.readTree(lines[i]);
                var entry = header.properties().iterator().next();
                String type = entry.getKey();
                String id = entry.getValue().get("_id").asText();
                String targetIndex =
                        index != null ? index : entry.getValue().get("_index").asText();
                boolean failed = rejectAll || transientFailure || (rejectDeletes && "delete".equals(type));
                var result = new HashMap<String, Object>();
                result.put("_index", targetIndex);
                result.put("_id", id);
                result.put("status", failed ? 400 : 200);
                if (failed) {
                    result.put(
                            "error",
                            Map.of(
                                    "type",
                                    transientFailure ? "es_rejected_execution_exception" : "mapper_parsing_exception",
                                    "reason",
                                    "controlled management failure"));
                } else {
                    delivered.add(operation(type, targetIndex, id));
                }
                items.add(Map.of(type, result));
                if (!"delete".equals(type)) i++;
            }
            return JsonUtil.serialize(Map.of("items", items));
        }

        @Override
        public ESSearchResponse search(ESSearchRequest request) {
            var response = new ESSearchResponse("{}");
            if (removedPath == null) return response;
            String parent = ((ESTermQuery) request.getESQuery()).getValue();
            try {
                var hit = new ESSearchHit();
                if (request.getIndex().equals(settings.getElasticsearch().getIndexFolder())
                        && parent.equals(sign(settings, root()))) {
                    hit.setSource(JsonUtil.serialize(Map.of("path", Map.of("real", removedPath))));
                    response.addHit(hit);
                } else if (request.getIndex().equals(settings.getElasticsearch().getIndex())
                        && parent.equals(sign(settings, removedPath))) {
                    hit.setStoredFields(Map.of("file.filename", List.of(removedFile)));
                    response.addHit(hit);
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return response;
        }
    }
}
