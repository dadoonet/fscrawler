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
package fr.pilato.elasticsearch.crawler.fs.client;

import com.carrotsearch.randomizedtesting.jupiter.RandomizedTest;
import fr.pilato.elasticsearch.crawler.fs.framework.bulk.FsCrawlerBulkProcessor;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import jakarta.ws.rs.client.Client;
import java.io.IOException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

class ElasticsearchLifecycleCleanupTest extends AbstractFSCrawlerTestCase {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failedStartRestoresHostCandidatesForRetry(boolean checkedFailure) throws Exception {
        var settings = FsSettingsLoader.load();
        settings.getElasticsearch().setSemanticSearch(false);
        String hostPrefix = RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 6, 12)
                .toLowerCase(java.util.Locale.ROOT);
        settings.getElasticsearch()
                .setUrls(java.util.List.of(
                        "http://" + hostPrefix + "-first.invalid", "http://" + hostPrefix + "-second.invalid"));
        int[] attempts = {0};
        try (ElasticsearchClient client = new ElasticsearchClient(settings) {
            @Override
            public String getVersion() throws ElasticsearchClientException {
                if (++attempts[0] == 1) {
                    try {
                        // Model the host state left by failed connection attempts during startup.
                        ((java.util.List<?>) field("hosts").get(this)).clear();
                    } catch (Exception e) {
                        throw new AssertionError(e);
                    }
                    if (checkedFailure) throw new ElasticsearchClientException("all startup hosts failed");
                    throw new IllegalStateException("all startup hosts failed");
                }
                return "9.5.5";
            }

            @Override
            protected void initBulkProcessor() {}
        }) {
            Assertions.assertThatThrownBy(client::start)
                    .isInstanceOf(checkedFailure ? ElasticsearchClientException.class : IllegalStateException.class);
            Assertions.assertThat((java.util.List<?>) field("hosts").get(client))
                    .isEqualTo(settings.getElasticsearch().getUrls());
            client.start();
            Assertions.assertThat(attempts[0]).isEqualTo(2);
        }
    }

    @Test
    void bulkCloseFailureStillClosesHttpClientAndSuppressesCleanupFailure() throws Exception {
        ElasticsearchClient client = new ElasticsearchClient(FsSettingsLoader.load());
        FsCrawlerBulkProcessor<?, ?, ?> bulk = Mockito.mock(FsCrawlerBulkProcessor.class);
        Client transport = Mockito.mock(Client.class);
        IOException failure = new IOException("bulk close failed");
        IllegalStateException cleanup = new IllegalStateException("HTTP close failed");
        Mockito.doThrow(failure).when(bulk).close();
        Mockito.doThrow(cleanup).when(transport).close();
        field("bulkProcessor").set(client, bulk);
        field("client").set(client, transport);
        Assertions.assertThatThrownBy(client::close).isSameAs(failure);
        Mockito.verify(transport).close();
        Assertions.assertThat(failure.getSuppressed()).containsExactly(cleanup);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failedStartReleasesHttpClientAndCanActuallyRetryInitialization(boolean checkedFailure) throws Exception {
        var settings = FsSettingsLoader.load();
        settings.getElasticsearch().setSemanticSearch(false);
        int[] attempts = {0};
        Client[] transports = {null};
        try (ElasticsearchClient client = new ElasticsearchClient(settings) {
            @Override
            public String getVersion() throws ElasticsearchClientException {
                try {
                    transports[0] = (Client) field("client").get(this);
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
                if (++attempts[0] == 1) {
                    if (checkedFailure) throw new ElasticsearchClientException("version lookup failed");
                    throw new IllegalStateException("version lookup failed");
                }
                return "9.5.5";
            }

            @Override
            protected void initBulkProcessor() {}
        }) {
            Assertions.assertThatThrownBy(client::start)
                    .isInstanceOf(checkedFailure ? ElasticsearchClientException.class : IllegalStateException.class);
            Assertions.assertThat(field("client").get(client)).isNull();
            Assertions.assertThatThrownBy(() -> transports[0].target("http://localhost"))
                    .isInstanceOf(IllegalStateException.class);
            client.start();
            Assertions.assertThat(attempts[0]).isEqualTo(2);
        }
    }

    private static java.lang.reflect.Field field(String name) throws Exception {
        var field = ElasticsearchClient.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
