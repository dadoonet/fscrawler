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
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import java.lang.reflect.Field;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ElasticsearchFailoverTest extends AbstractFSCrawlerTestCase {
    @Test
    void failureRemovesCapturedHostDespiteAnotherRequestSelectingNextHost() throws Exception {
        List<String> hosts = hosts(2);
        List<String> attempted = new ArrayList<>();
        AtomicBoolean interleave = new AtomicBoolean(true);
        try (Fixture fixture = new Fixture(hosts)) {
            fixture.behavior = node -> {
                attempted.add(node);
                if (interleave.getAndSet(false)) {
                    try {
                        fixture.client.httpPost("probe", "{}");
                    } catch (Exception e) {
                        throw new AssertionError(e);
                    }
                    throw refused();
                }
                return "ok";
            };
            Assertions.assertThat(fixture.client.httpPost("_bulk", "{}")).isEqualTo("ok");
            Assertions.assertThat(attempted).containsExactly(hosts.get(0), hosts.get(1), hosts.get(1));
        }
    }

    @Test
    void failoverAddsPathPrefixExactlyOnce() throws Exception {
        List<String> hosts = hosts(2);
        try (Fixture fixture = new Fixture(hosts)) {
            fixture.behavior = node -> {
                if (node.equals(hosts.get(0))) throw refused();
                return "ok";
            };
            Assertions.assertThat(fixture.client.httpPost("_bulk", "{}")).isEqualTo("ok");
            Assertions.assertThat(fixture.paths).containsExactly("proxy/_bulk", "proxy/_bulk");
        }
    }

    @Test
    void hostReloadCannotMakeOneRequestTryForever() throws Exception {
        List<String> hosts = hosts(110);
        try (Fixture fixture = new Fixture(hosts)) {
            fixture.behavior = node -> {
                if (fixture.paths.size() > hosts.size()) throw new AssertionError("Request exceeded its host budget");
                throw refused();
            };
            Assertions.assertThatThrownBy(() -> fixture.client.httpPost("_bulk", "{}"))
                    .isInstanceOf(ElasticsearchClientException.class)
                    .hasMessageContaining("All nodes are failing");
            Assertions.assertThat(fixture.paths).hasSizeLessThanOrEqualTo(hosts.size());
        }
    }

    private List<String> hosts(int count) {
        String name = RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 5, 10)
                .toLowerCase(java.util.Locale.ROOT);
        List<String> values = new ArrayList<>();
        for (int i = 0; i < count; i++) values.add("http://" + name + i + ".invalid");
        return values;
    }

    private static ProcessingException refused() {
        return new ProcessingException(new ConnectException("refused"));
    }

    private static class Fixture implements AutoCloseable {
        final ElasticsearchClient client;
        final List<String> paths = new ArrayList<>();
        Function<String, String> behavior;

        Fixture(List<String> hosts) throws Exception {
            FsSettings settings = FsSettingsLoader.load();
            settings.getElasticsearch().setUrls(hosts);
            settings.getElasticsearch().setPathPrefix("proxy");
            client = new ElasticsearchClient(settings);
            Client transport = Mockito.mock(Client.class);
            Mockito.when(transport.target(Mockito.anyString())).thenAnswer(invocation -> {
                String node = invocation.getArgument(0);
                WebTarget target = Mockito.mock(WebTarget.class);
                Invocation.Builder builder = Mockito.mock(Invocation.Builder.class, Mockito.RETURNS_SELF);
                Mockito.when(target.path(Mockito.anyString())).thenAnswer(path -> {
                    paths.add(path.getArgument(0));
                    return target;
                });
                Mockito.when(target.request(Mockito.anyString())).thenReturn(builder);
                Mockito.when(builder.method(Mockito.anyString(), Mockito.any(Entity.class), Mockito.eq(String.class)))
                        .thenAnswer(call -> behavior.apply(node));
                return target;
            });
            Field field = ElasticsearchClient.class.getDeclaredField("client");
            field.setAccessible(true);
            field.set(client, transport);
        }

        @Override
        public void close() throws Exception {
            client.close();
        }
    }
}
