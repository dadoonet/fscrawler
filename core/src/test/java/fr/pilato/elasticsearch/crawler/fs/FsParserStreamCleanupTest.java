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

import com.carrotsearch.randomizedtesting.jupiter.RandomizedTest;
import fr.pilato.elasticsearch.crawler.fs.beans.FileAbstractModel;
import fr.pilato.elasticsearch.crawler.fs.beans.ScanStatistic;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerExtensionFsProvider;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPluginException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.IdentityHashMap;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class FsParserStreamCleanupTest extends AbstractFSCrawlerTestCase {
    @Test
    void parseFailureKeepsBothCleanupFailuresSuppressed() throws Exception {
        // Inner-object parsing already propagates on main; raw-helper propagation belongs to #2635.
        Fixture fixture = new Fixture("{", true, false, true);
        Throwable error = failure(fixture);
        Assertions.assertThat(error).isNotInstanceOf(FsCrawlerPluginException.class);
        Assertions.assertThat(error.getSuppressed()).hasSize(2);
        fixture.assertReleasedOnce();
    }

    @Test
    void metadataCloseFailureStillReleasesContent() throws Exception {
        Fixture fixture = new Fixture("{}", true, false);
        Throwable error = failure(fixture);
        Assertions.assertThat(error).isInstanceOf(java.io.IOException.class);
        Assertions.assertThat(error.getSuppressed()).hasSize(1);
        fixture.assertReleasedOnce();
    }

    @Test
    void failedMetadataOpenReleasesAlreadyOpenedContent() throws Exception {
        Fixture fixture = new Fixture("{}", true, true);
        Throwable error = failure(fixture);
        Assertions.assertThat(error).isSameAs(fixture.openFailure);
        Assertions.assertThat(error.getSuppressed()).hasSize(1);
        Assertions.assertThat(fixture.closes.get(fixture.content)).isEqualTo(1);
        Assertions.assertThat(fixture.closes).doesNotContainKey(fixture.metadata);
    }

    @Test
    void rawHelperDoesNotReleaseProviderStreamTwice() throws Exception {
        Fixture fixture = new Fixture("{}", false, false);
        Assertions.assertThat(fixture.invoke()).isEqualTo(0);
        fixture.assertReleasedOnce();
    }

    private Throwable failure(Fixture fixture) throws Exception {
        try {
            fixture.invoke();
            throw new AssertionError("Expected parse or cleanup failure");
        } catch (InvocationTargetException e) {
            return e.getCause();
        }
    }

    private class Fixture {
        final InputStream content;
        final InputStream metadata = new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8));
        final Map<InputStream, Integer> closes = new IdentityHashMap<>();
        final FsCrawlerPluginException openFailure = new FsCrawlerPluginException("metadata open failed");
        final FsParser parser;
        final FileAbstractModel child;
        final FileAbstractModel sidecar;

        Fixture(String json, boolean closeFailure, boolean openFails) {
            this(json, closeFailure, openFails, false);
        }

        Fixture(String json, boolean closeFailure, boolean openFails, boolean innerObject) {
            content = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
            String name = RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 5, 10);
            child = file(name + ".json");
            sidecar = file(name + ".meta.json");
            var provider = (FsCrawlerExtensionFsProvider) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[] {FsCrawlerExtensionFsProvider.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("getInputStream")) {
                            if (args[0] == child) return content;
                            if (openFails) throw openFailure;
                            return metadata;
                        }
                        if (method.getName().equals("closeInputStream")) {
                            InputStream stream = (InputStream) args[0];
                            closes.merge(stream, 1, Integer::sum);
                            if (closeFailure)
                                throw new FsCrawlerPluginException(
                                        "close " + (stream == metadata ? "metadata" : "content"));
                            stream.close();
                            return null;
                        }
                        if (method.getReturnType() == boolean.class) return false;
                        return null;
                    });
            var settings = FsSettingsLoader.load();
            settings.setName(jobName);
            settings.getFs().setJsonSupport(true);
            settings.getFs().setAddAsInnerObject(innerObject);
            settings.getFs().setContinueOnError(false);
            parser = new FsParser(settings, rootTmpDir, null, null, 1, false, provider);
        }

        Object invoke() throws Exception {
            var method = FsParser.class.getDeclaredMethod(
                    "indexFileWithStreams",
                    FileAbstractModel.class,
                    ScanStatistic.class,
                    String.class,
                    FileAbstractModel.class,
                    int.class);
            method.setAccessible(true);
            return method.invoke(
                    parser, child, new ScanStatistic(rootTmpDir.toString()), rootTmpDir.toString(), sidecar, 1);
        }

        void assertReleasedOnce() {
            Assertions.assertThat(closes).hasSize(2);
            Assertions.assertThat(closes.get(content)).isEqualTo(1);
            Assertions.assertThat(closes.get(metadata)).isEqualTo(1);
        }

        FileAbstractModel file(String name) {
            return new FileAbstractModel(
                    name,
                    true,
                    Instant.now(),
                    Instant.now(),
                    Instant.now(),
                    "json",
                    rootTmpDir.toString(),
                    rootTmpDir.resolve(name).toString(),
                    2L,
                    null,
                    null,
                    0,
                    null,
                    null);
        }
    }
}
