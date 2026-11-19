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

import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ElasticsearchTransportTimeoutTest extends AbstractFSCrawlerTestCase {
    @ParameterizedTest
    @ValueSource(strings = {"0ms", "-1ms", "2147483648ms"})
    void rejectsTimeoutsThatWouldDisableOrOverflowTransportBounds(String duration) throws Exception {
        for (String setting : new String[] {"connect_timeout", "read_timeout", "connection_request_timeout"}) {
            Path config = rootTmpDir.resolve("invalid.yaml");
            Files.writeString(config, "elasticsearch:\n  " + setting + ": " + duration + "\n");
            Assertions.assertThatThrownBy(() -> {
                        FsSettings settings = FsSettingsLoader.load(config);
                        try (ElasticsearchClient client = new ElasticsearchClient(settings)) {
                            client.start();
                        }
                    })
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    void exhaustedConnectionPoolHonorsAcquisitionTimeout() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try (ServerSocket server = new ServerSocket(0)) {
            Path config = rootTmpDir.resolve("pool.yaml");
            Files.writeString(
                    config,
                    "elasticsearch:\n  urls: [\"http://127.0.0.1:" + server.getLocalPort()
                            + "\"]\n  semantic_search: false\n  read_timeout: 5s\n  connection_request_timeout: 200ms\n");
            FsSettings settings = FsSettingsLoader.load(config);
            try (ElasticsearchClient client = new ElasticsearchClient(settings) {
                @Override
                public String getVersion() {
                    return "9.5.5";
                }

                @Override
                protected void initBulkProcessor() {}
            }) {
                client.start();
                var field = ElasticsearchClient.class.getDeclaredField("client");
                field.setAccessible(true);
                var transport = (jakarta.ws.rs.client.Client) field.get(client);
                var manager = org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder.create()
                        .setMaxConnTotal(1)
                        .setMaxConnPerRoute(1)
                        .build();
                transport.property(
                        org.glassfish.jersey.apache5.connector.Apache5ClientProperties.CONNECTION_MANAGER, manager);
                var first = executor.submit(() -> client.httpPost("_refresh", "{}"));
                server.setSoTimeout(2000);
                try (Socket accepted = server.accept()) {
                    var second = executor.submit(() -> client.httpPost("_refresh", "{}"));
                    Assertions.assertThatThrownBy(() -> second.get(2, TimeUnit.SECONDS))
                            .isInstanceOf(java.util.concurrent.ExecutionException.class)
                            .hasCauseInstanceOf(ElasticsearchClientException.class);
                } finally {
                    Assertions.assertThatThrownBy(() -> first.get(2, TimeUnit.SECONDS))
                            .isInstanceOf(java.util.concurrent.ExecutionException.class);
                }
            }
        } finally {
            executor.shutdownNow();
            Assertions.assertThat(executor.awaitTermination(2, TimeUnit.SECONDS))
                    .isTrue();
        }
    }

    @Test
    void stalledResponseIsBoundedByConfiguredReadTimeout() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        try (ServerSocket server = new ServerSocket(0)) {
            Path config = rootTmpDir.resolve("timeouts.yaml");
            Files.writeString(
                    config,
                    "elasticsearch:\n  urls: [\"http://127.0.0.1:" + server.getLocalPort()
                            + "\"]\n  semantic_search: false\n  connect_timeout: 200ms\n  read_timeout: 200ms\n  connection_request_timeout: 200ms\n");
            FsSettings settings = FsSettingsLoader.load(config);
            try (ElasticsearchClient client = new ElasticsearchClient(settings) {
                @Override
                public String getVersion() {
                    return "9.5.5";
                }

                @Override
                protected void initBulkProcessor() {}
            }) {
                client.start();
                var request = executor.submit(() -> client.httpPost("_refresh", "{}"));
                server.setSoTimeout(2000);
                try (Socket accepted = server.accept()) {
                    // This peer accepts the request and never sends an HTTP response.
                    Assertions.assertThatThrownBy(() -> request.get(2, TimeUnit.SECONDS))
                            .isInstanceOf(java.util.concurrent.ExecutionException.class)
                            .hasCauseInstanceOf(ElasticsearchClientException.class);
                }
            }
        } finally {
            executor.shutdownNow();
            Assertions.assertThat(executor.awaitTermination(2, TimeUnit.SECONDS))
                    .isTrue();
        }
    }
}
