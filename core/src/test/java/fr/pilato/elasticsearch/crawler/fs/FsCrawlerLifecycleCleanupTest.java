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

import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentService;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerManagementService;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerExtensionPasswordProvider;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPluginsManager;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

class FsCrawlerLifecycleCleanupTest extends AbstractFSCrawlerTestCase {
    @Test
    void pluginStartupFailureRollsBackManagerAndPreservesCause() {
        try (Manager manager = new Manager()) {
            FsSettings settings = settings();
            manager.failStart = true;
            Assertions.assertThatThrownBy(() -> new FsCrawlerImpl(rootTmpDir, settings, 0, false))
                    .isSameAs(manager.failure);
            Assertions.assertThat(manager.closes).isEqualTo(1);
        }
    }

    @Test
    void invalidSettingsAreRejectedBeforeStartingPlugins() {
        try (Manager manager = new Manager()) {
            FsSettings settings = settings();
            settings.getFs().setJsonSupport(true);
            settings.getFs().setXmlSupport(true);
            Assertions.assertThatThrownBy(() -> new FsCrawlerImpl(rootTmpDir, settings, 0, false))
                    .isInstanceOf(
                            fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException.class);
            Assertions.assertThat(manager.starts).isZero();
        }
    }

    @Test
    void oneServiceCloseFailureDoesNotSkipRemainingServicesOrPlugins() throws Exception {
        try (Manager manager = new Manager()) {
            FsCrawlerImpl crawler = new FsCrawlerImpl(rootTmpDir, settings(), 0, false);
            List<String> events = new ArrayList<>();
            IOException first = new IOException("management close failed");
            IOException second = new IOException("document close failed");
            replace(
                    crawler,
                    "managementService",
                    service(FsCrawlerManagementService.class, events, "management", first, false));
            replace(
                    crawler,
                    "documentService",
                    service(FsCrawlerDocumentService.class, events, "document", second, false));
            Assertions.assertThatThrownBy(crawler::close).isSameAs(first);
            Assertions.assertThat(first.getSuppressed()).containsExactly(second);
            Assertions.assertThat(events).containsExactly("management close", "document close");
            Assertions.assertThat(manager.closes).isEqualTo(1);
        }
    }

    @Test
    void serviceStartupFailureRollsBackAlreadyOwnedResources() throws Exception {
        try (Manager manager = new Manager()) {
            FsCrawlerImpl crawler = new FsCrawlerImpl(rootTmpDir, settings(), 0, false);
            List<String> events = new ArrayList<>();
            IOException failure = new IOException("document start failed");
            replace(
                    crawler,
                    "managementService",
                    service(FsCrawlerManagementService.class, events, "management", null, false));
            replace(
                    crawler,
                    "documentService",
                    service(FsCrawlerDocumentService.class, events, "document", failure, true));
            Assertions.assertThatThrownBy(crawler::start).isSameAs(failure);
            Assertions.assertThat(events).contains("management close", "document close");
            Assertions.assertThat(manager.closes).isEqualTo(1);
        }
    }

    private FsSettings settings() {
        FsSettings settings = FsSettingsLoader.load();
        settings.setName(jobName);
        return settings;
    }

    private static void replace(Object target, String name, Object value) throws Exception {
        var field = FsCrawlerImpl.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static <T> T service(
            Class<T> type, List<String> events, String name, IOException failure, boolean failStart) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
            if (method.getName().equals("start")) {
                events.add(name + " start");
                if (failStart) throw failure;
            }
            if (method.getName().equals("close")) {
                events.add(name + " close");
                if (!failStart && failure != null) throw failure;
            }
            if (method.getReturnType() == long.class) return 0L;
            if (method.getReturnType() == boolean.class) return false;
            return null;
        }));
    }

    private static class Manager implements AutoCloseable {
        int starts, closes;
        boolean failStart;
        final IllegalStateException failure = new IllegalStateException("plugin startup failed");
        final MockedConstruction<FsCrawlerPluginsManager> construction;

        Manager() {
            construction = Mockito.mockConstruction(FsCrawlerPluginsManager.class, (manager, context) -> {
                Mockito.doAnswer(invocation -> {
                            starts++;
                            if (failStart) throw failure;
                            return null;
                        })
                        .when(manager)
                        .startPlugins();
                Mockito.doAnswer(invocation -> {
                            closes++;
                            return null;
                        })
                        .when(manager)
                        .close();
                Mockito.when(manager.findPasswordProvider(Mockito.anyString()))
                        .thenReturn(Mockito.mock(FsCrawlerExtensionPasswordProvider.class));
            });
        }

        @Override
        public void close() {
            construction.close();
        }
    }
}
