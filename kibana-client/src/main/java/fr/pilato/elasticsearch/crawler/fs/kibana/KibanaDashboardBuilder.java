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

import fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the default FSCrawler dashboard payload for the Kibana Dashboards API.
 *
 * <p>See <a href="https://elastic.github.io/dashboards-api-spec/dashboards">Kibana Dashboards API</a>.
 */
public final class KibanaDashboardBuilder {

    public static final String DASHBOARD_ID_PREFIX = "fscrawler-";
    public static final String DATA_VIEW_ID_PREFIX = "fscrawler-data-view-";
    public static final String DEFAULT_TIME_FIELD = "file.indexing_date";

    private KibanaDashboardBuilder() {}

    public static String dashboardIdForJob(String jobName) {
        return DASHBOARD_ID_PREFIX + jobName;
    }

    public static String dataViewIdForJob(String jobName) {
        return DATA_VIEW_ID_PREFIX + jobName;
    }

    public static String dashboardTitleForJob(String jobName) {
        return "FSCrawler - " + jobName;
    }

    public static String dataViewTitleForJob(String jobName) {
        return "FSCrawler " + jobName;
    }

    public static String buildDataViewPayload(String dataViewId, String indexPattern, String timeField) {
        Map<String, Object> dataView = new LinkedHashMap<>();
        dataView.put("id", dataViewId);
        dataView.put("title", indexPattern);
        dataView.put("name", dataViewTitleForPattern(indexPattern));
        dataView.put("timeFieldName", timeField);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("data_view", dataView);
        return JsonUtil.serialize(payload);
    }

    public static String buildDefaultDashboardPayload(FsSettings settings) {
        String jobName = settings.getName();
        String indexPattern = settings.getElasticsearch().getIndex();
        String dataViewId = dataViewIdForJob(jobName);

        Map<String, Object> metricDataSource = new LinkedHashMap<>();
        metricDataSource.put("type", "data_view_spec");
        metricDataSource.put("index_pattern", indexPattern);
        metricDataSource.put("time_field", DEFAULT_TIME_FIELD);
        metricDataSource.put("data_view_id", dataViewId);

        Map<String, Object> metric = new LinkedHashMap<>();
        metric.put("type", "primary");
        metric.put("operation", "count");

        Map<String, Object> metricPanelConfig = new LinkedHashMap<>();
        metricPanelConfig.put("type", "metric");
        metricPanelConfig.put("title", "Indexed documents");
        metricPanelConfig.put("data_source", metricDataSource);
        metricPanelConfig.put("metrics", List.of(metric));

        Map<String, Object> metricPanel = new LinkedHashMap<>();
        metricPanel.put("type", "vis");
        metricPanel.put("grid", grid(0, 0, 24, 8));
        metricPanel.put("config", metricPanelConfig);

        Map<String, Object> extensionDataSource = new LinkedHashMap<>();
        extensionDataSource.put("type", "data_view_spec");
        extensionDataSource.put("index_pattern", indexPattern);
        extensionDataSource.put("time_field", DEFAULT_TIME_FIELD);
        extensionDataSource.put("data_view_id", dataViewId);

        Map<String, Object> extensionLayer = new LinkedHashMap<>();
        extensionLayer.put("type", "donut");
        extensionLayer.put("data_source", extensionDataSource);
        extensionLayer.put("breakdown", Map.of("field", "file.extension", "limit", 10));

        Map<String, Object> extensionPanelConfig = new LinkedHashMap<>();
        extensionPanelConfig.put("type", "partition");
        extensionPanelConfig.put("title", "Documents by extension");
        extensionPanelConfig.put("layers", List.of(extensionLayer));

        Map<String, Object> extensionPanel = new LinkedHashMap<>();
        extensionPanel.put("type", "vis");
        extensionPanel.put("grid", grid(0, 8, 24, 12));
        extensionPanel.put("config", extensionPanelConfig);

        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("id", dashboardIdForJob(jobName));
        dashboard.put("title", dashboardTitleForJob(jobName));
        dashboard.put("panels", List.of(metricPanel, extensionPanel));
        return JsonUtil.serialize(dashboard);
    }

    private static Map<String, Integer> grid(int x, int y, int w, int h) {
        Map<String, Integer> grid = new LinkedHashMap<>();
        grid.put("x", x);
        grid.put("y", y);
        grid.put("w", w);
        grid.put("h", h);
        return grid;
    }

    private static String dataViewTitleForPattern(String indexPattern) {
        return "FSCrawler " + indexPattern;
    }
}
