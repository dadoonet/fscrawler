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

        Map<String, Object> metricDataSource = dataViewSpec(indexPattern);

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

        Map<String, Object> extensionGroupBy = new LinkedHashMap<>();
        extensionGroupBy.put("operation", "terms");
        extensionGroupBy.put("fields", List.of("file.extension"));
        extensionGroupBy.put("limit", 10);

        Map<String, Object> extensionMetric = new LinkedHashMap<>();
        extensionMetric.put("operation", "count");

        Map<String, Object> extensionPanelConfig = new LinkedHashMap<>();
        extensionPanelConfig.put("type", "pie");
        extensionPanelConfig.put("title", "Documents by extension");
        extensionPanelConfig.put("data_source", dataViewSpec(indexPattern));
        extensionPanelConfig.put("metrics", List.of(extensionMetric));
        extensionPanelConfig.put("group_by", List.of(extensionGroupBy));
        extensionPanelConfig.put("styling", Map.of("donut_hole", "m"));

        Map<String, Object> extensionPanel = new LinkedHashMap<>();
        extensionPanel.put("type", "vis");
        extensionPanel.put("grid", grid(0, 8, 24, 12));
        extensionPanel.put("config", extensionPanelConfig);

        Map<String, Object> dashboard = new LinkedHashMap<>();
        // Do not put "id" in the body: POST rejects it; PUT takes the id from the path.
        dashboard.put("title", dashboardTitleForJob(jobName));
        dashboard.put("panels", List.of(metricPanel, extensionPanel));
        dashboard.put("time_range", Map.of("from", "now-30d", "to", "now"));
        return JsonUtil.serialize(dashboard);
    }

    private static Map<String, Object> dataViewSpec(String indexPattern) {
        // data_view_spec has additionalProperties:false — only type/index_pattern/time_field/…
        Map<String, Object> dataSource = new LinkedHashMap<>();
        dataSource.put("type", "data_view_spec");
        dataSource.put("index_pattern", indexPattern);
        dataSource.put("time_field", DEFAULT_TIME_FIELD);
        return dataSource;
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
