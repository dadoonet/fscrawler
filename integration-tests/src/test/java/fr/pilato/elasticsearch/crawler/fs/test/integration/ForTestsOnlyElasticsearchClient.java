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

import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClient;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientException;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;

/**
 * Elasticsearch client used by the integration-test suite for assertions and cleanup.
 *
 * <p>Unlike the production {@link ElasticsearchClient}, it does <b>not</b> create the bulk processor and its background
 * flush scheduler. The integration-test client is shared across the whole suite and closed only at suite teardown, so a
 * lingering flush thread would (correctly) be reported as a leak by the per-class {@code @DetectThreadLeaks} check.
 *
 * <p>Tests only ever write a handful of documents, so every write is routed to the direct, synchronous API
 * ({@link #indexSingle}/{@link #deleteSingle}) instead of the asynchronous bulk path. This removes the background
 * thread entirely and makes test writes deterministic. The crawler under test keeps using the real
 * {@link ElasticsearchClient} (with its bulk processor), which is closed per test — so it is unaffected.
 */
class ForTestsOnlyElasticsearchClient extends ElasticsearchClient {

    public ForTestsOnlyElasticsearchClient(FsSettings settings) {
        super(settings);
    }

    /** No async bulk flusher in tests — see class javadoc. */
    @Override
    protected void initBulkProcessor() {
        // Intentionally empty: the test client writes directly and synchronously.
    }

    /** {@code index(...)} delegates to this method, so both go through the direct path. */
    @Override
    public void indexRawJson(String index, String id, String json, String pipeline) {
        try {
            indexSingle(index, id, json, pipeline);
        } catch (ElasticsearchClientException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void delete(String index, String id) {
        try {
            deleteSingle(index, id);
        } catch (ElasticsearchClientException e) {
            throw new RuntimeException(e);
        }
    }

    /** Nothing to flush — writes are already synchronous. */
    @Override
    public void flush() {
        // no-op
    }
}
