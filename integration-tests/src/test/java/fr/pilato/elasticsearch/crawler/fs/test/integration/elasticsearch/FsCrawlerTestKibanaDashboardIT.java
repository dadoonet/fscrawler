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
package fr.pilato.elasticsearch.crawler.fs.test.integration.elasticsearch;

import fr.pilato.elasticsearch.crawler.fs.kibana.KibanaClient;
import fr.pilato.elasticsearch.crawler.fs.kibana.KibanaDashboardBuilder;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.Kibana;
import fr.pilato.elasticsearch.crawler.fs.test.framework.Slow;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Kibana dashboard provisioning against a real Kibana instance.
 *
 * <p>Skipped unless {@code -Dtests.kibana.url=...} is set. CI enables this only for Cloud and Serverless jobs so local
 * TestContainers runs stay light on RAM.
 */
@Slow
@EnabledIfSystemProperty(named = "tests.kibana.url", matches = ".+")
class FsCrawlerTestKibanaDashboardIT extends AbstractFsCrawlerITCase {

    private String dashboardId;

    @AfterEach
    void cleanupDashboard() throws Exception {
        if (dashboardId == null) {
            return;
        }
        FsSettings settings = createKibanaSettings();
        try (KibanaClient kibanaClient = new KibanaClient(settings)) {
            kibanaClient.start();
            kibanaClient.deleteDashboard(dashboardId);
        }
        dashboardId = null;
    }

    @Test
    void createsDefaultDashboardOnStartup() throws Exception {
        FsSettings settings = createKibanaSettings();
        dashboardId = KibanaDashboardBuilder.dashboardIdForJob(settings.getName());

        // Probe Kibana version first: Cloud deployments may still be on 9.4 where the Dashboards
        // API is only experimental / soft-disabled by FSCrawler.
        try (KibanaClient probe = new KibanaClient(settings)) {
            probe.start();
            Assumptions.assumeTrue(
                    KibanaClient.supportsDashboardsApi(probe.getVersion()),
                    () -> "Kibana Dashboards API requires 9.5+; cluster reports " + probe.getVersion());
        }

        startCrawler(settings);

        try (KibanaClient kibanaClient = new KibanaClient(settings)) {
            kibanaClient.start();
            Assertions.assertThat(kibanaClient.isDashboardProvisioningEnabled()).isTrue();
            Assertions.assertThat(kibanaClient.dashboardExists(dashboardId)).isTrue();
        }
    }

    private FsSettings createKibanaSettings() {
        FsSettings settings = createTestSettings();
        Kibana kibana = settings.getKibana() != null ? settings.getKibana() : new Kibana();
        kibana.setUrl(System.getProperty("tests.kibana.url"));
        kibana.setPushDashboard(true);
        if (testApiKey != null) {
            kibana.setApiKey(testApiKey);
        }
        settings.setKibana(kibana);
        return settings;
    }
}
