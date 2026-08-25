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
package fr.pilato.elasticsearch.crawler.fs.kibana;

import com.carrotsearch.randomizedtesting.jupiter.DetectThreadLeaks;
import com.carrotsearch.randomizedtesting.jupiter.RandomizedTest;
import com.carrotsearch.randomizedtesting.jupiter.SystemThreadFilter;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.jayway.jsonpath.JsonPath;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.settings.Kibana;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import fr.pilato.elasticsearch.crawler.fs.test.framework.IntelliJThreadsFilter;
import fr.pilato.elasticsearch.crawler.fs.test.framework.JNACleanerThreadFilter;
import fr.pilato.elasticsearch.crawler.fs.test.framework.JUnitThreadsFilter;
import fr.pilato.elasticsearch.crawler.fs.test.framework.TestContainerThreadFilter;
import fr.pilato.elasticsearch.crawler.fs.test.framework.WindowsSpecificThreadFilter;
import fr.pilato.elasticsearch.crawler.fs.test.framework.WireMockThreadFilter;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@DetectThreadLeaks.ExcludeThreads({
    WireMockThreadFilter.class,
    SystemThreadFilter.class,
    WindowsSpecificThreadFilter.class,
    TestContainerThreadFilter.class,
    JNACleanerThreadFilter.class,
    IntelliJThreadsFilter.class,
    JUnitThreadsFilter.class
})
@Execution(ExecutionMode.SAME_THREAD)
class KibanaClientTest extends AbstractFSCrawlerTestCase {

    private static WireMockServer wireMockServer;

    @BeforeAll
    static void startWireMock() {
        wireMockServer =
                new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    void resetWireMock() {
        wireMockServer.resetAll();
    }

    @Test
    void supportsDashboardsApi_requiresKibana95() {
        Assertions.assertThat(KibanaClient.supportsDashboardsApi("9.5.0")).isTrue();
        Assertions.assertThat(KibanaClient.supportsDashboardsApi("9.5.2")).isTrue();
        Assertions.assertThat(KibanaClient.supportsDashboardsApi("10.0.0")).isTrue();
        Assertions.assertThat(KibanaClient.supportsDashboardsApi("9.4.3")).isFalse();
        Assertions.assertThat(KibanaClient.supportsDashboardsApi("8.19.5")).isFalse();
    }

    @Test
    void start_disablesProvisioningWhenKibanaVersionBelow95() throws Exception {
        stubStatus("8.19.5");

        KibanaClient client = new KibanaClient(settingsForWireMock());
        client.start();

        Assertions.assertThat(client.isDashboardProvisioningEnabled()).isFalse();
        Assertions.assertThat(client.getVersion()).isEqualTo("8.19.5");
        client.close();
    }

    @Test
    void start_keepsProvisioningEnabledWhenKibanaVersionIs95OrHigher() throws Exception {
        stubStatus("9.5.2");

        KibanaClient client = new KibanaClient(settingsForWireMock());
        client.start();

        Assertions.assertThat(client.isDashboardProvisioningEnabled()).isTrue();
        Assertions.assertThat(client.getVersion()).isEqualTo("9.5.2");
        client.close();
    }

    @Test
    void createDashboard_usesPutWithKnownIdAndSendsKbnXsrfHeader() throws Exception {
        String dashboardId = "fscrawler-" + UUID.randomUUID();
        String payload = "{\"title\":\"Test dashboard\",\"panels\":[]}";

        // POST /api/dashboards does not accept an id; create with a stable id via PUT upsert.
        WireMock.stubFor(WireMock.put(WireMock.urlEqualTo("/api/dashboards/" + dashboardId))
                .withHeader("kbn-xsrf", WireMock.equalTo("true"))
                .withHeader("Authorization", WireMock.equalTo("ApiKey test-api-key"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"" + dashboardId + "\"}")));

        stubStatus("9.5.2");

        KibanaClient client = new KibanaClient(settingsForWireMock());
        client.start();

        String createdId = client.createDashboard(dashboardId, payload);

        Assertions.assertThat(createdId).isEqualTo(dashboardId);
        client.close();
    }

    @Test
    void httpCall_wrapsBadRequestAsKibanaClientExceptionWithBody() throws Exception {
        stubStatus("9.5.2");

        WireMock.stubFor(WireMock.put(WireMock.urlEqualTo("/api/dashboards/fscrawler-bad"))
                .willReturn(WireMock.aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"id is not allowed\"}")));

        KibanaClient client = new KibanaClient(settingsForWireMock());
        client.start();

        Assertions.assertThatThrownBy(() -> client.createDashboard("fscrawler-bad", "{\"title\":\"x\",\"panels\":[]}"))
                .isInstanceOf(KibanaClientException.class)
                .hasMessageContaining("HTTP 400")
                .hasMessageContaining("id is not allowed");

        client.close();
    }

    @Test
    void createDataViewIfMissing_createsOnlyWhenAbsent() throws Exception {
        String dataViewId = "fscrawler-data-view-" + UUID.randomUUID();

        stubStatus("9.5.2");

        WireMock.stubFor(WireMock.get(WireMock.urlEqualTo("/api/data_views/data_view/" + dataViewId))
                .inScenario("data-view")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(WireMock.aResponse().withStatus(404))
                .willSetStateTo("created"));

        WireMock.stubFor(WireMock.get(WireMock.urlEqualTo("/api/data_views/data_view/" + dataViewId))
                .inScenario("data-view")
                .whenScenarioStateIs("created")
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"data_view\":{\"id\":\"" + dataViewId + "\"}}")));

        WireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/api/data_views/data_view"))
                .withHeader("kbn-xsrf", WireMock.equalTo("true"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"data_view\":{\"id\":\"" + dataViewId + "\"}}")));

        KibanaClient client = new KibanaClient(settingsForWireMock());
        client.start();

        Assertions.assertThat(client.createDataViewIfMissing(
                        dataViewId, "job_docs", KibanaDashboardBuilder.DEFAULT_TIME_FIELD))
                .isTrue();
        Assertions.assertThat(client.createDataViewIfMissing(
                        dataViewId, "job_docs", KibanaDashboardBuilder.DEFAULT_TIME_FIELD))
                .isFalse();

        client.close();
    }

    @Test
    void buildDefaultDashboardPayload_containsJobPanels() {
        String jobName = RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 6, 12)
                .toLowerCase();
        String rootUrl = "/data/" + RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 4, 10);
        FsSettings settings = FsSettingsLoader.load();
        settings.setName(jobName);
        settings.getFs().setUrl(rootUrl);
        settings.getElasticsearch().setIndex(jobName + "_docs");

        String payload = KibanaDashboardBuilder.buildDefaultDashboardPayload(settings);
        String version = fr.pilato.elasticsearch.crawler.fs.framework.Version.getVersion();

        // POST/PUT body must not include id (id is taken from the URL path on PUT).
        Assertions.assertThatThrownBy(() -> JsonPath.read(payload, "$.id"))
                .isInstanceOf(com.jayway.jsonpath.PathNotFoundException.class);
        Assertions.assertThat((String) JsonPath.read(payload, "$.title")).isEqualTo("FSCrawler - " + jobName);

        // Top-level: intro + tech markdown + 3 metrics + 6 sections
        Assertions.assertThat((List<?>) JsonPath.read(payload, "$.panels")).hasSize(11);

        Assertions.assertThat((String) JsonPath.read(payload, "$.panels[0].type"))
                .isEqualTo("markdown");
        Assertions.assertThat((String) JsonPath.read(payload, "$.panels[0].config.content"))
                .contains("# Sample FSCrawler Dashboard")
                .contains("`kibana.push_dashboard`")
                .contains("https://fscrawler.readthedocs.io/en/latest/admin/fs/kibana.html")
                .contains("Created by [FSCrawler **v" + version + "**](https://fscrawler.readthedocs.io/)")
                .contains("**Root (`fs.url`):** `" + rootUrl + "`");

        Assertions.assertThat((String) JsonPath.read(payload, "$.panels[1].type"))
                .isEqualTo("markdown");
        Assertions.assertThat((String) JsonPath.read(payload, "$.panels[1].config.content"))
                .contains("## FSCrawler runtime")
                .contains("**OS:**")
                .contains("**Java:**")
                .contains("**Heap max:**")
                .doesNotContain("**Heap:**")
                .contains("**Job:** `" + jobName + "`");

        Assertions.assertThat((String) JsonPath.read(payload, "$.panels[2].config.type"))
                .isEqualTo("metric");
        Assertions.assertThat((String) JsonPath.read(payload, "$.panels[2].config.title"))
                .isEqualTo("Indexed documents");
        Assertions.assertThat((String) JsonPath.read(payload, "$.panels[3].config.title"))
                .isEqualTo("Total size");
        Assertions.assertThat((String) JsonPath.read(payload, "$.panels[3].config.metrics[0].operation"))
                .isEqualTo("sum");
        Assertions.assertThat((String) JsonPath.read(payload, "$.panels[3].config.metrics[0].field"))
                .isEqualTo("file.filesize");
        Assertions.assertThat((String) JsonPath.read(payload, "$.panels[4].config.title"))
                .isEqualTo("Unique extensions");
        Assertions.assertThat((String) JsonPath.read(payload, "$.panels[4].config.metrics[0].operation"))
                .isEqualTo("unique_count");

        Assertions.assertThat((String) JsonPath.read(payload, "$.panels[5].title"))
                .isEqualTo("Overview");
        Assertions.assertThat((Boolean) JsonPath.read(payload, "$.panels[5].collapsed"))
                .isFalse();
        Assertions.assertThat((String) JsonPath.read(payload, "$.panels[5].panels[0].config.type"))
                .isEqualTo("tag_cloud");
        Assertions.assertThat((String) JsonPath.read(payload, "$.panels[5].panels[0].config.title"))
                .isEqualTo("Documents by extension");
        Assertions.assertThat((String) JsonPath.read(payload, "$.panels[5].panels[0].config.tag_by.fields[0]"))
                .isEqualTo("file.extension");
        Assertions.assertThat((String) JsonPath.read(payload, "$.panels[5].panels[0].config.metric.operation"))
                .isEqualTo("count");
        Assertions.assertThat((String) JsonPath.read(payload, "$.panels[5].panels[1].config.type"))
                .isEqualTo("xy");
        Assertions.assertThat((String) JsonPath.read(payload, "$.panels[5].panels[1].config.layers[0].x.fields[0]"))
                .isEqualTo("file.content_type");

        Assertions.assertThat((String) JsonPath.read(payload, "$.panels[6].title"))
                .isEqualTo("Directories");
        Assertions.assertThat((Boolean) JsonPath.read(payload, "$.panels[6].collapsed"))
                .isFalse();
        Assertions.assertThat((String) JsonPath.read(payload, "$.panels[6].panels[0].config.type"))
                .isEqualTo("treemap");
        Assertions.assertThat((String) JsonPath.read(payload, "$.panels[6].panels[0].config.title"))
                .isEqualTo("Directories by file count");
        Assertions.assertThat((String) JsonPath.read(payload, "$.panels[6].panels[0].config.group_by[0].fields[0]"))
                .isEqualTo("path.virtual.tree");
        Assertions.assertThat((String) JsonPath.read(payload, "$.panels[6].panels[0].config.metrics[0].operation"))
                .isEqualTo("count");
        Assertions.assertThat((String) JsonPath.read(payload, "$.panels[6].panels[0].config.drilldowns[0].type"))
                .isEqualTo("discover_drilldown");
        Assertions.assertThat((String) JsonPath.read(payload, "$.panels[6].panels[1].config.type"))
                .isEqualTo("treemap");
        Assertions.assertThat((String) JsonPath.read(payload, "$.panels[6].panels[1].config.title"))
                .isEqualTo("Directories by size");
        Assertions.assertThat((String) JsonPath.read(payload, "$.panels[6].panels[1].config.group_by[0].fields[0]"))
                .isEqualTo("path.virtual.tree");
        Assertions.assertThat((String) JsonPath.read(payload, "$.panels[6].panels[1].config.metrics[0].operation"))
                .isEqualTo("sum");
        Assertions.assertThat((String) JsonPath.read(payload, "$.panels[6].panels[1].config.metrics[0].field"))
                .isEqualTo("file.filesize");

        Assertions.assertThat((String) JsonPath.read(payload, "$.panels[7].title"))
                .isEqualTo("Timeline");
        Assertions.assertThat((Boolean) JsonPath.read(payload, "$.panels[7].collapsed"))
                .isFalse();
        Assertions.assertThat((String) JsonPath.read(payload, "$.panels[7].panels[0].config.layers[0].x.field"))
                .isEqualTo("file.indexing_date");

        Assertions.assertThat((String) JsonPath.read(payload, "$.panels[8].title"))
                .isEqualTo("Documents");
        Assertions.assertThat((Boolean) JsonPath.read(payload, "$.panels[8].collapsed"))
                .isFalse();
        Assertions.assertThat((String) JsonPath.read(payload, "$.panels[8].panels[0].type"))
                .isEqualTo("discover_session");
        Assertions.assertThat((String) JsonPath.read(payload, "$.panels[8].panels[0].config.title"))
                .isEqualTo("Indexed documents");
        @SuppressWarnings("unchecked")
        List<String> discoverColumns =
                (List<String>) JsonPath.read(payload, "$.panels[8].panels[0].config.tabs[0].column_order");
        Assertions.assertThat(discoverColumns)
                .containsExactly(
                        "file.filename",
                        "path.virtual",
                        "file.extension",
                        "file.content_type",
                        "file.filesize",
                        "file.indexing_date",
                        "meta.title",
                        "meta.author");
        Assertions.assertThat((String)
                        JsonPath.read(payload, "$.panels[8].panels[0].config.tabs[0].data_source.index_pattern"))
                .isEqualTo(jobName + "_docs");

        Assertions.assertThat((String) JsonPath.read(payload, "$.panels[9].title"))
                .isEqualTo("File dates");
        Assertions.assertThat((Boolean) JsonPath.read(payload, "$.panels[9].collapsed"))
                .isTrue();
        Assertions.assertThat((Boolean)
                        JsonPath.read(payload, "$.panels[9].panels[0].config.layers[0].ignore_global_filters"))
                .isTrue();
        Assertions.assertThat((String) JsonPath.read(payload, "$.panels[9].panels[0].config.layers[0].x.field"))
                .isEqualTo("file.created");
        Assertions.assertThat((Boolean)
                        JsonPath.read(payload, "$.panels[9].panels[1].config.layers[0].ignore_global_filters"))
                .isTrue();
        Assertions.assertThat((String) JsonPath.read(payload, "$.panels[9].panels[1].config.layers[0].x.field"))
                .isEqualTo("file.last_modified");
        Assertions.assertThat((Boolean)
                        JsonPath.read(payload, "$.panels[9].panels[2].config.layers[0].ignore_global_filters"))
                .isTrue();
        Assertions.assertThat((String) JsonPath.read(payload, "$.panels[9].panels[2].config.layers[0].x.field"))
                .isEqualTo("file.last_accessed");
        Assertions.assertThat((String) JsonPath.read(payload, "$.panels[10].title"))
                .isEqualTo("Document metadata");
        Assertions.assertThat((Boolean) JsonPath.read(payload, "$.panels[10].collapsed"))
                .isTrue();
        Assertions.assertThat((String) JsonPath.read(payload, "$.panels[10].panels[0].config.group_by[0].fields[0]"))
                .isEqualTo("meta.language");
        Assertions.assertThat((String) JsonPath.read(payload, "$.panels[10].panels[1].config.layers[0].x.fields[0]"))
                .isEqualTo("meta.creator_tool");
        Assertions.assertThat((String) JsonPath.read(payload, "$.panels[10].panels[1].config.title"))
                .isEqualTo("Top creator tools");
    }

    private void stubStatus(String version) {
        WireMock.stubFor(WireMock.get(WireMock.urlEqualTo("/api/status"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"version\":{\"number\":\"" + version
                                + "\"},\"status\":{\"overall\":{\"level\":\"available\"}}}")));
    }

    private FsSettings settingsForWireMock() {
        FsSettings settings = FsSettingsLoader.load();
        settings.setName("wiremock");
        settings.getElasticsearch().setApiKey("test-api-key");
        settings.getElasticsearch().setSslVerification(false);

        Kibana kibana = new Kibana();
        kibana.setUrl("http://localhost:" + wireMockServer.port());
        kibana.setPushDashboard(true);
        settings.setKibana(kibana);
        return settings;
    }
}
