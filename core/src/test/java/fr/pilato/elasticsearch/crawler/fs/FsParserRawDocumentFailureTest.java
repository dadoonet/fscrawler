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
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carrotsearch.randomizedtesting.jupiter.RandomizedTest;
import fr.pilato.elasticsearch.crawler.fs.beans.CrawlerState;
import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.beans.FileAbstractModel;
import fr.pilato.elasticsearch.crawler.fs.beans.FsCrawlerCheckpoint;
import fr.pilato.elasticsearch.crawler.fs.beans.FsCrawlerCheckpointFileHandler;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientException;
import fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil;
import fr.pilato.elasticsearch.crawler.fs.framework.SignTool;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentService;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerManagementService;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import fr.pilato.elasticsearch.crawler.fs.test.framework.Slow;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerExtensionFsProvider;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Real JSON/XML source files, parsing and checkpoint persistence; only provider discovery and submission are
 * controlled.
 */
@Slow
class FsParserRawDocumentFailureTest extends AbstractFSCrawlerTestCase {
    enum Failure {
        PARSE,
        SUBMIT,
        NONE
    }

    @ParameterizedTest
    @CsvSource({"false, PARSE", "true, PARSE", "false, SUBMIT", "true, SUBMIT"})
    void rawFailureStopsAtSafeCheckpointAndFreshCrawlerRecovers(boolean xml, Failure failure) throws Exception {
        var fixture = new Fixture(xml, false, false, failure, true);
        fixture.seedCheckpoint();
        var failed = fixture.run();
        assertThat(failed.getState())
                .as("a rejected or malformed source file must fail this crawl")
                .isEqualTo(CrawlerState.ERROR);
        assertThat(failed.getFilesProcessed()).isZero();
        assertThat(failed.getScanDate()).isEqualTo(fixture.watermark);
        assertThat(failed.getScanEndTime()).isNull();
        assertThat(failed.getPendingPaths()).contains(fixture.root.toString());
        assertThat(failed.getCompletedPaths()).doesNotContain(fixture.root.toString());
        assertThat(failed.getLastError()).isNotBlank();
        assertThat(fixture.accepted).isEmpty();
        if (failure == Failure.SUBMIT) assertThat(failed.getLastError()).contains(fixture.rejectionReason);

        fixture.repair();
        var recovered = fixture.run();
        assertThat(recovered.getState()).isEqualTo(CrawlerState.COMPLETED);
        assertThat(recovered.getPendingPaths()).isEmpty();
        assertThat(recovered.getFilesProcessed()).isEqualTo(2);
        assertThat(fixture.accepted)
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(fixture.badId, fixture.badValue, fixture.goodId, fixture.goodValue));
    }

    @ParameterizedTest
    @CsvSource({"false, PARSE", "true, PARSE", "false, SUBMIT", "true, SUBMIT"})
    void continueOnErrorSkipsOnlyFailedRawFileAndCountsOnlySuccessfulFile(boolean xml, Failure failure)
            throws Exception {
        var fixture = new Fixture(xml, false, true, failure, true);
        var completed = fixture.run();
        assertThat(fixture.accepted).containsExactlyEntriesOf(Map.of(fixture.goodId, fixture.goodValue));
        assertThat(completed.getState()).isEqualTo(CrawlerState.COMPLETED);
        assertThat(completed.getFilesProcessed())
                .as("skipped files must not be counted as indexed")
                .isEqualTo(1);
        assertThat(completed.getPendingPaths()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void validRawSourceFileIsParsedSubmittedAndCounted(boolean xml) throws Exception {
        var fixture = new Fixture(xml, false, false, Failure.NONE, false);
        var completed = fixture.run();
        assertThat(completed.getState()).isEqualTo(CrawlerState.COMPLETED);
        assertThat(completed.getFilesProcessed()).isEqualTo(1);
        assertThat(fixture.accepted).containsExactlyEntriesOf(Map.of(fixture.badId, fixture.badValue));
        assertThat(fixture.rawPayloads).containsKey(fixture.badId);
        assertThat(JsonUtil.parseJsonAsDocumentContext(fixture.rawPayloads.get(fixture.badId))
                        .<String>read("$.value"))
                .isEqualTo(fixture.badValue);
    }

    @ParameterizedTest
    @CsvSource({"false, false", "true, false", "false, true", "true, true"})
    void innerObjectModeAlreadyHonorsMalformedInputPolicy(boolean xml, boolean continueOnError) throws Exception {
        var fixture = new Fixture(xml, true, continueOnError, Failure.PARSE, true);
        var checkpoint = fixture.run();
        assertThat(fixture.rawPayloads).isEmpty();
        assertThat(checkpoint.getState()).isEqualTo(continueOnError ? CrawlerState.COMPLETED : CrawlerState.ERROR);
        assertThat(checkpoint.getFilesProcessed()).isEqualTo(continueOnError ? 1 : 0);
        if (continueOnError)
            assertThat(fixture.accepted).containsExactlyEntriesOf(Map.of(fixture.goodId, fixture.goodValue));
        else assertThat(fixture.accepted).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void terminalBulkFailureStillStopsCrawlEvenWhenContinueOnErrorIsEnabled(boolean xml) throws Exception {
        var fixture = new Fixture(xml, false, true, Failure.NONE, false);
        doThrow(new ElasticsearchClientException("Terminal bulk failure " + randomName()))
                .when(fixture.documents)
                .flushAndEnsureBulkSucceeded();
        var checkpoint = fixture.run();
        assertThat(fixture.accepted).containsExactlyEntriesOf(Map.of(fixture.badId, fixture.badValue));
        assertThat(checkpoint.getState()).isEqualTo(CrawlerState.ERROR);
        // This control checks fatal flush reporting; raw-file rejection/recovery is asserted above.
        assertThat(checkpoint.getScanEndTime()).isNull();
    }

    private final class Fixture {
        final FsSettings settings = FsSettingsLoader.load();
        final FsCrawlerExtensionFsProvider provider = mock(FsCrawlerExtensionFsProvider.class);
        final FsCrawlerDocumentService documents = mock(FsCrawlerDocumentService.class);
        final FsCrawlerManagementService management = mock(FsCrawlerManagementService.class);
        final Map<String, String> accepted = new LinkedHashMap<>();
        final Map<String, String> rawPayloads = new LinkedHashMap<>();
        final List<InputStream> opened = new ArrayList<>();
        final AtomicBoolean reject = new AtomicBoolean(true);
        final String badValue = randomName();
        final String goodValue = randomName();
        final String rejectionReason = "Controlled submission rejection " + randomName();
        final Instant watermark =
                Instant.now().minusSeconds(RandomizedTest.randomIntInRange(randomizedRandomForTests, 60, 600));
        final Path root;
        final Path bad;
        final Path good;
        final String badId;
        final String goodId;
        final boolean xml;

        Fixture(boolean xml, boolean inner, boolean continueOnError, Failure failure, boolean withGood)
                throws Exception {
            this.xml = xml;
            root = Files.createDirectory(testTmpDir.resolve(randomName()));
            String extension = xml ? "xml" : "json";
            bad = root.resolve(randomName() + "." + extension);
            good = root.resolve(randomName() + "." + extension);
            Files.writeString(bad, failure == Failure.PARSE ? malformed() : valid(badValue));
            if (withGood) Files.writeString(good, valid(goodValue));
            settings.setName(jobName);
            settings.getFs().setUrl(root.toString());
            settings.getFs().setJsonSupport(!xml);
            settings.getFs().setXmlSupport(xml);
            settings.getFs().setAddAsInnerObject(inner);
            settings.getFs().setContinueOnError(continueOnError);
            settings.getFs().setIndexFolders(false);
            settings.getFs().setRemoveDeleted(false);
            settings.getElasticsearch().setIndex(randomName());
            settings.getElasticsearch().setIndexFolder(randomName());
            badId = id(bad);
            goodId = id(good);
            when(provider.exists(root.toString())).thenReturn(true);
            when(provider.getFiles(root.toString()))
                    .thenAnswer(call -> withGood
                            ? List.of(model(bad, extension), model(good, extension))
                            : List.of(model(bad, extension)));
            when(provider.getInputStream(any())).thenAnswer(call -> {
                FileAbstractModel file = call.getArgument(0);
                InputStream stream = Files.newInputStream(Path.of(file.getFullpath()));
                opened.add(stream);
                return stream;
            });
            doAnswer(call -> {
                        ((InputStream) call.getArgument(0)).close();
                        return null;
                    })
                    .when(provider)
                    .closeInputStream(any());
            doAnswer(call -> {
                        String id = call.getArgument(1);
                        if (failure == Failure.SUBMIT && reject.get() && id.equals(badId)) {
                            throw new RejectedExecutionException(rejectionReason);
                        }
                        String payload = call.getArgument(2);
                        rawPayloads.put(id, payload);
                        accepted.put(
                                id, JsonUtil.parseJsonAsDocumentContext(payload).read("$.value"));
                        return null;
                    })
                    .when(documents)
                    .indexRawJson(anyString(), anyString(), anyString(), nullable(String.class));
            doAnswer(call -> {
                        Doc doc = call.getArgument(2);
                        accepted.put(
                                call.getArgument(1), (String) doc.getObject().get("value"));
                        return null;
                    })
                    .when(documents)
                    .index(anyString(), anyString(), any(Doc.class), nullable(String.class));
        }

        void seedCheckpoint() throws Exception {
            var checkpoint = FsCrawlerCheckpoint.newCheckpoint(root.toString());
            checkpoint.setScanDate(watermark);
            new FsCrawlerCheckpointFileHandler(testTmpDir).write(jobName, checkpoint);
        }

        FsCrawlerCheckpoint run() throws Exception {
            var parser = new FsParser(settings, testTmpDir, management, documents, 1, false, provider);
            parser.closed.set(false);
            parser.run();
            try {
                assertThat(opened).isNotEmpty();
                for (InputStream stream : opened) {
                    verify(provider, atLeastOnce()).closeInputStream(stream);
                    assertThatThrownBy(stream::read)
                            .as("source stream is closed after parsing/submission")
                            .isInstanceOf(IOException.class);
                }
            } finally {
                // Ensure a failed resource assertion cannot leak a handle from the test itself.
                for (InputStream stream : opened) stream.close();
                opened.clear();
            }
            return parser.getCheckpointHandler().read(jobName);
        }

        void repair() throws Exception {
            Files.writeString(bad, valid(badValue));
            reject.set(false);
        }

        private FileAbstractModel model(Path file, String extension) throws Exception {
            return new FileAbstractModel(
                    file.getFileName().toString(),
                    true,
                    Files.getLastModifiedTime(file).toInstant(),
                    null,
                    null,
                    extension,
                    root.toString(),
                    file.toString(),
                    Files.size(file),
                    null,
                    null,
                    0,
                    List.of(),
                    null);
        }

        private String id(Path file) throws Exception {
            return SignTool.sign(
                    settings.getFs().getHashAlgorithm(), file.toString().replace('\\', '/'));
        }

        private String malformed() {
            return xml ? "<record><value>" : "{\"value\":";
        }

        private String valid(String value) {
            return xml ? "<record><value>" + value + "</value></record>" : JsonUtil.serialize(Map.of("value", value));
        }
    }

    private String randomName() {
        return RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 8, 16)
                .toLowerCase(Locale.ROOT);
    }
}
