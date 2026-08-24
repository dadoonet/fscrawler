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

import fr.pilato.elasticsearch.crawler.fs.kibana.IKibanaClient;
import fr.pilato.elasticsearch.crawler.fs.kibana.KibanaDashboardBuilder;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.settings.Kibana;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class FsCrawlerKibanaServiceImplTest extends AbstractFSCrawlerTestCase {

    @Test
    void setupDashboard_createsDataViewAndDashboardWhenMissing() throws Exception {
        FsSettings settings = settingsWithKibana("demo");
        IKibanaClient client = Mockito.mock(IKibanaClient.class);
        Mockito.when(client.createDataViewIfMissing(
                        KibanaDashboardBuilder.dataViewIdForJob("demo"),
                        "demo_docs",
                        KibanaDashboardBuilder.DEFAULT_TIME_FIELD))
                .thenReturn(true);
        Mockito.when(client.dashboardExists(KibanaDashboardBuilder.dashboardIdForJob("demo")))
                .thenReturn(false);
        Mockito.when(client.createDashboard(Mockito.anyString())).thenReturn("fscrawler-demo");

        FsCrawlerKibanaServiceImpl service = new FsCrawlerKibanaServiceImpl(settings, client);
        service.setupDashboard();

        Mockito.verify(client)
                .createDataViewIfMissing(
                        KibanaDashboardBuilder.dataViewIdForJob("demo"),
                        "demo_docs",
                        KibanaDashboardBuilder.DEFAULT_TIME_FIELD);
        Mockito.verify(client).createDashboard(Mockito.anyString());
    }

    @Test
    void setupDashboard_skipsDashboardCreationWhenAlreadyPresent() throws Exception {
        FsSettings settings = settingsWithKibana("demo");
        IKibanaClient client = Mockito.mock(IKibanaClient.class);
        Mockito.when(client.createDataViewIfMissing(
                        KibanaDashboardBuilder.dataViewIdForJob("demo"),
                        "demo_docs",
                        KibanaDashboardBuilder.DEFAULT_TIME_FIELD))
                .thenReturn(false);
        Mockito.when(client.dashboardExists(KibanaDashboardBuilder.dashboardIdForJob("demo")))
                .thenReturn(true);

        new FsCrawlerKibanaServiceImpl(settings, client).setupDashboard();

        Mockito.verify(client, Mockito.never()).createDashboard(Mockito.anyString());
    }

    private FsSettings settingsWithKibana(String jobName) {
        FsSettings settings = FsSettingsLoader.load();
        settings.setName(jobName);
        settings.getElasticsearch().setIndex(jobName + "_docs");
        Kibana kibana = new Kibana();
        kibana.setUrl("http://127.0.0.1:5601");
        kibana.setPushDashboard(true);
        settings.setKibana(kibana);
        return settings;
    }
}
