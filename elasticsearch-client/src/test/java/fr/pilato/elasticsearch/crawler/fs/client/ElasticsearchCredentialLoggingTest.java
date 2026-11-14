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
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ElasticsearchCredentialLoggingTest extends AbstractFSCrawlerTestCase {
    @Test
    void diagnosticSettingsNeverExposeConfiguredApiCredential() {
        String secret = RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 30, 50);
        var settings = FsSettingsLoader.load();
        settings.getElasticsearch().setApiKey(secret);
        // Failed client startup logs this object at WARN, including keys generated earlier.
        Assertions.assertThat(settings.getElasticsearch().toString())
                .doesNotContain(secret)
                .contains("[REDACTED]");
    }

    @Test
    void generatedCredentialsNeverReachDebugOrTraceLogs() throws Exception {
        String secret = RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 30, 50);
        String id = RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 8, 16);
        String name = RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 8, 16);
        String response = "{\"id\":\"" + id + "\",\"encoded\":\"" + secret + "\",\"api_key\":\"" + secret + "\"}";
        Client transport = Mockito.mock(Client.class);
        WebTarget target = Mockito.mock(WebTarget.class);
        Invocation.Builder builder = Mockito.mock(Invocation.Builder.class, Mockito.RETURNS_SELF);
        Mockito.when(transport.target(Mockito.anyString())).thenReturn(target);
        Mockito.when(target.path(Mockito.anyString())).thenReturn(target);
        Mockito.when(target.request(Mockito.anyString())).thenReturn(builder);
        Mockito.when(builder.method(Mockito.anyString(), Mockito.any(Entity.class), Mockito.eq(String.class)))
                .thenReturn(response);
        Mockito.when(builder.method(Mockito.anyString(), Mockito.eq(String.class)))
                .thenReturn(response);
        ElasticsearchClient client = new ElasticsearchClient(FsSettingsLoader.load());
        Field field = ElasticsearchClient.class.getDeclaredField("client");
        field.setAccessible(true);
        field.set(client, transport);
        Logger logger = (Logger) LogManager.getLogger(ElasticsearchClient.class);
        Level previous = logger.getLevel();
        boolean additive = logger.isAdditive();
        List<String> messages = new ArrayList<>();
        AbstractAppender capture = new AbstractAppender(name, null, null, false, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                messages.add(event.getMessage().getFormattedMessage());
            }
        };
        capture.start();
        logger.addAppender(capture);
        logger.setAdditive(false);
        logger.setLevel(Level.TRACE);
        try {
            Assertions.assertThat(client.generateApiKey(name)).isEqualTo(secret);
            // Also cover the response path without a request body.
            client.httpGet("_security/api_key");
            Assertions.assertThat(String.join("\n", messages))
                    .doesNotContain(secret)
                    .contains(id, name);
        } finally {
            logger.removeAppender(capture);
            logger.setLevel(previous);
            logger.setAdditive(additive);
            capture.stop();
            client.close();
        }
    }
}
