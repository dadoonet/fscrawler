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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.carrotsearch.randomizedtesting.jupiter.RandomizedTest;
import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.beans.FileAbstractModel;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchBulkRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchEngine;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchIndexOperation;
import fr.pilato.elasticsearch.crawler.fs.client.IElasticsearchClient;
import fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentService;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerManagementService;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import fr.pilato.elasticsearch.crawler.fs.test.framework.Slow;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerExtensionFsProvider;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Slow
class FsParserBulkMetadataTest extends AbstractFSCrawlerTestCase {
    @ParameterizedTest
    @ValueSource(strings = {"\"", "\\n", "\n"})
    void normalTextDocumentWithFilenameIdNeedsMetadataEncoding(String special) throws Exception {
        assertFilenameMetadata(special, true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"", "\\n", "\n"})
    void defaultHashedIdIsNotAffectedBySpecialFilename(String special) throws Exception {
        assertFilenameMetadata(special, false);
    }

    private void assertFilenameMetadata(String special, boolean filenameAsId) throws Exception {
        var settings = FsSettingsLoader.load();
        settings.setName(jobName);
        String root = "/" + randomName();
        settings.getFs().setUrl(root);
        settings.getFs().setFilenameAsId(filenameAsId);
        settings.getFs().setJsonSupport(false);
        settings.getFs().setXmlSupport(false);
        settings.getFs().setIndexFolders(false);
        settings.getFs().setRemoveDeleted(false);
        settings.getFs().setContinueOnError(false);
        settings.getFs().getOcr().setEnabled(false);
        settings.getElasticsearch().setIndex(randomName());
        settings.getElasticsearch().setIndexFolder(randomName());
        String filename = randomName() + special + randomName() + ".txt";
        String content = randomName() + " " + randomName();
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        // Provider models POSIX filenames without trying to create Windows-invalid local names.
        var provider = mock(FsCrawlerExtensionFsProvider.class);
        when(provider.exists(root)).thenReturn(true);
        when(provider.getFiles(root))
                .thenReturn(List.of(new FileAbstractModel(
                        filename,
                        true,
                        Instant.now(),
                        null,
                        null,
                        "txt",
                        root,
                        root + "/" + filename,
                        bytes.length,
                        null,
                        null,
                        0,
                        List.of(),
                        null)));
        when(provider.getInputStream(any())).thenAnswer(call -> new ByteArrayInputStream(bytes));
        doAnswer(call -> {
                    ((InputStream) call.getArgument(0)).close();
                    return null;
                })
                .when(provider)
                .closeInputStream(any());

        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> indexedId = new AtomicReference<>();
        var transport = mock(IElasticsearchClient.class);
        when(transport.bulk(anyString(), anyString())).thenAnswer(call -> {
            body.set(call.getArgument(1));
            return "{\"errors\":false,\"items\":[]}";
        });
        var documents = mock(FsCrawlerDocumentService.class);
        doAnswer(call -> {
                    String id = call.getArgument(1);
                    indexedId.set(id);
                    Doc doc = call.getArgument(2);
                    var request = new ElasticsearchBulkRequest();
                    request.add(new ElasticsearchIndexOperation(
                            call.getArgument(0), id, call.getArgument(3), JsonUtil.serialize(doc)));
                    new ElasticsearchEngine(transport).bulk(request);
                    return null;
                })
                .when(documents)
                .index(anyString(), anyString(), any(Doc.class), nullable(String.class));

        var parser = new FsParser(
                settings, testTmpDir, mock(FsCrawlerManagementService.class), documents, 1, false, provider);
        parser.closed.set(false);
        parser.run();
        assertThat(body.get())
                .as("real Tika text parsing reaches the bulk transport")
                .isNotNull();
        if (filenameAsId) assertThat(indexedId.get()).isEqualTo(filename);
        else assertThat(indexedId.get()).isNotEqualTo(filename);
        String[] lines = body.get().split("\n", -1);
        assertThat(lines).hasSize(3);
        assertThat(JsonUtil.mapper.readTree(lines[0]).path("index").path("_id").asText())
                .isEqualTo(indexedId.get());
        var source = JsonUtil.mapper.readTree(lines[1]);
        assertThat(source.path("file").path("filename").asText()).isEqualTo(filename);
        assertThat(source.path("content").asText()).contains(content);
    }

    private String randomName() {
        return RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 8, 16)
                .toLowerCase(Locale.ROOT);
    }
}
