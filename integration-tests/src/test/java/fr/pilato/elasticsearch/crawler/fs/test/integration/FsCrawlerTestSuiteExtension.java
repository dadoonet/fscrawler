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
package fr.pilato.elasticsearch.crawler.fs.test.integration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import static org.junit.jupiter.api.extension.ExtensionContext.Namespace.GLOBAL;

/**
 * Ensures shared IT suite resources (Elasticsearch client, plugin manager)
 * are shut down exactly once after all tests complete, even in parallel runs.
 *
 * <p>Initialization remains in {@code @BeforeAll startServices()} which is made
 * idempotent via {@code synchronized} + a null-check guard. This extension only
 * registers a {@link ExtensionContext.Store.CloseableResource} in the GLOBAL store
 * so that {@link #close()} fires at true suite teardown time — after every class's
 * {@code @AfterAll} methods have finished, not at per-class teardown time.
 */
class FsCrawlerTestSuiteExtension
        implements BeforeAllCallback, ExtensionContext.Store.CloseableResource {

    private static final Logger logger = LogManager.getLogger(FsCrawlerTestSuiteExtension.class);
    private static final String STORE_KEY = FsCrawlerTestSuiteExtension.class.getName();

    @Override
    public void beforeAll(ExtensionContext context) {
        context.getRoot().getStore(GLOBAL)
               .getOrComputeIfAbsent(STORE_KEY, k -> this, FsCrawlerTestSuiteExtension.class);
    }

    @Override
    public void close() throws Exception {
        logger.info("🏁 Shutting down FSCrawler IT suite shared resources (once per JVM)");
        AbstractITCase.shutdownSuiteResources();
    }
}
