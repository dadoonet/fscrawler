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
package fr.pilato.elasticsearch.crawler.fs.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientException;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchIndexNotFoundException;
import fr.pilato.elasticsearch.crawler.fs.client.IElasticsearchClient;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class FsCrawlerManagementServiceElasticsearchImplTest extends AbstractFSCrawlerTestCase {

    @Test
    void directoryLookupsIgnoreOnlyMissingIndices() throws Exception {
        FsSettings settings = FsSettingsLoader.load();
        IElasticsearchClient client = mock(IElasticsearchClient.class);
        when(client.search(any())).thenThrow(new ElasticsearchIndexNotFoundException("missing"));
        FsCrawlerManagementServiceElasticsearchImpl service =
                new FsCrawlerManagementServiceElasticsearchImpl(settings, client);

        Assertions.assertThat(service.getFileDirectory("/")).isEmpty();
        Assertions.assertThat(service.getFolderDirectory("/")).isEmpty();
    }

    @Test
    void directoryLookupsPropagateOtherClientFailures() throws Exception {
        FsSettings settings = FsSettingsLoader.load();
        IElasticsearchClient client = mock(IElasticsearchClient.class);
        ElasticsearchClientException failure = new ElasticsearchClientException("connection failed");
        when(client.search(any())).thenThrow(failure);
        FsCrawlerManagementServiceElasticsearchImpl service =
                new FsCrawlerManagementServiceElasticsearchImpl(settings, client);

        Assertions.assertThatThrownBy(() -> service.getFileDirectory("/")).isSameAs(failure);
        Assertions.assertThatThrownBy(() -> service.getFolderDirectory("/")).isSameAs(failure);
    }
}
