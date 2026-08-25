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
import fr.pilato.elasticsearch.crawler.fs.framework.Version;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import java.util.ArrayList;
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

    private static final String DOCS_URL = "https://fscrawler.readthedocs.io/en/latest/admin/fs/kibana.html";

    private static final String KEY_TITLE = "title";
    private static final String KEY_TYPE = "type";
    private static final String KEY_OPERATION = "operation";
    private static final String KEY_DATA_SOURCE = "data_source";
    private static final String KEY_METRICS = "metrics";
    private static final String KEY_CONFIG = "config";
    private static final String KEY_FIELD = "field";
    private static final String KEY_FIELDS = "fields";
    private static final String KEY_LAYERS = "layers";
    private static final String KEY_PANELS = "panels";
    private static final String KEY_GRID = "grid";
    private static final String OPERATION_COUNT = "count";
    private static final String OPERATION_TERMS = "terms";
    private static final String TYPE_VIS = "vis";
    private static final String TYPE_XY = "xy";
    private static final String TYPE_PRIMARY = "primary";

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
        dataView.put(KEY_TITLE, indexPattern);
        dataView.put("name", dataViewTitleForPattern(indexPattern));
        dataView.put("timeFieldName", timeField);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("data_view", dataView);
        return JsonUtil.serialize(payload);
    }

    public static String buildDefaultDashboardPayload(FsSettings settings) {
        String jobName = settings.getName();
        String indexPattern = settings.getElasticsearch().getIndex();

        List<Map<String, Object>> panels = new ArrayList<>();

        // Intro markdown
        panels.add(markdownPanel(0, 0, 48, 5, sampleDashboardMarkdown()));

        // Metric row
        panels.add(metricPanel(
                0, 5, 16, 6, "Indexed documents", indexPattern, primaryMetric(OPERATION_COUNT, null, null)));
        panels.add(metricPanel(
                16,
                5,
                16,
                6,
                "Total size",
                indexPattern,
                primaryMetric("sum", "file.filesize", Map.of(KEY_TYPE, "bytes", "decimals", 1))));
        panels.add(metricPanel(
                32,
                5,
                16,
                6,
                "Unique extensions",
                indexPattern,
                primaryMetric("unique_count", "file.extension", null)));

        // Overview (expanded)
        panels.add(section(
                "Overview",
                false,
                11,
                List.of(
                        piePanel(0, 0, 24, 12, "Documents by extension", indexPattern, "file.extension", 10),
                        horizontalBarPanel(
                                24, 0, 24, 12, "Top content types", indexPattern, "file.content_type", 10))));

        // Timeline (expanded)
        panels.add(section(
                "Timeline",
                false,
                24,
                List.of(dateHistogramPanel(
                        0, 0, 48, 12, "Indexing activity over time", indexPattern, DEFAULT_TIME_FIELD, "area"))));

        // File dates (collapsed)
        panels.add(section(
                "File dates",
                true,
                37,
                List.of(
                        dateHistogramPanel(0, 0, 16, 10, "Created", indexPattern, "file.created", "line"),
                        dateHistogramPanel(16, 0, 16, 10, "Last modified", indexPattern, "file.last_modified", "line"),
                        dateHistogramPanel(
                                32, 0, 16, 10, "Last accessed", indexPattern, "file.last_accessed", "line"))));

        // Document metadata (collapsed)
        panels.add(section(
                "Document metadata",
                true,
                40,
                List.of(
                        piePanel(0, 0, 24, 12, "Documents by language", indexPattern, "meta.language", 10),
                        horizontalBarPanel(24, 0, 24, 12, "Top authors", indexPattern, "meta.author", 10))));

        Map<String, Object> dashboard = new LinkedHashMap<>();
        // Do not put "id" in the body: POST rejects it; PUT takes the id from the path.
        dashboard.put(KEY_TITLE, dashboardTitleForJob(jobName));
        dashboard.put(KEY_PANELS, panels);
        dashboard.put("time_range", Map.of("from", "now-30d", "to", "now"));
        return JsonUtil.serialize(dashboard);
    }

    static String sampleDashboardMarkdown() {
        return "# Sample FSCrawler Dashboard\n\n"
                + "Feel free to copy and edit this dashboard as you need to.\n"
                + "It's provided as default one by FSCrawler. If you want to disable\n"
                + "the automatic creation, set `kibana.push_dashboard` to `false` in your job settings.\n\n"
                + "See [Doc](" + DOCS_URL + ") for more information.\n"
                + "Created by FSCrawler v" + Version.getVersion();
    }

    private static Map<String, Object> markdownPanel(int x, int y, int w, int h, String content) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("content", content);

        Map<String, Object> panel = new LinkedHashMap<>();
        panel.put(KEY_TYPE, "markdown");
        panel.put(KEY_GRID, grid(x, y, w, h));
        panel.put(KEY_CONFIG, config);
        return panel;
    }

    private static Map<String, Object> metricPanel(
            int x, int y, int w, int h, String title, String indexPattern, Map<String, Object> metric) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put(KEY_TYPE, "metric");
        config.put(KEY_TITLE, title);
        config.put(KEY_DATA_SOURCE, dataViewSpec(indexPattern));
        config.put(KEY_METRICS, List.of(metric));
        return visPanel(x, y, w, h, config);
    }

    private static Map<String, Object> primaryMetric(String operation, String field, Map<String, Object> format) {
        Map<String, Object> metric = new LinkedHashMap<>();
        metric.put(KEY_TYPE, TYPE_PRIMARY);
        metric.put(KEY_OPERATION, operation);
        if (field != null) {
            metric.put(KEY_FIELD, field);
        }
        if (format != null) {
            metric.put("format", format);
        }
        return metric;
    }

    private static Map<String, Object> piePanel(
            int x, int y, int w, int h, String title, String indexPattern, String groupByField, int limit) {
        Map<String, Object> groupBy = new LinkedHashMap<>();
        groupBy.put(KEY_OPERATION, OPERATION_TERMS);
        groupBy.put(KEY_FIELDS, List.of(groupByField));
        groupBy.put("limit", limit);

        Map<String, Object> config = new LinkedHashMap<>();
        config.put(KEY_TYPE, "pie");
        config.put(KEY_TITLE, title);
        config.put(KEY_DATA_SOURCE, dataViewSpec(indexPattern));
        config.put(KEY_METRICS, List.of(Map.of(KEY_OPERATION, OPERATION_COUNT)));
        config.put("group_by", List.of(groupBy));
        config.put("styling", Map.of("donut_hole", "m"));
        return visPanel(x, y, w, h, config);
    }

    private static Map<String, Object> horizontalBarPanel(
            int x, int y, int w, int h, String title, String indexPattern, String termsField, int limit) {
        Map<String, Object> xAxis = new LinkedHashMap<>();
        xAxis.put(KEY_OPERATION, OPERATION_TERMS);
        xAxis.put(KEY_FIELDS, List.of(termsField));
        xAxis.put("limit", limit);

        Map<String, Object> layer = new LinkedHashMap<>();
        layer.put(KEY_TYPE, "bar_horizontal");
        layer.put(KEY_DATA_SOURCE, dataViewSpec(indexPattern));
        layer.put("x", xAxis);
        layer.put("y", List.of(Map.of(KEY_OPERATION, OPERATION_COUNT)));

        Map<String, Object> config = new LinkedHashMap<>();
        config.put(KEY_TYPE, TYPE_XY);
        config.put(KEY_TITLE, title);
        config.put(KEY_LAYERS, List.of(layer));
        return visPanel(x, y, w, h, config);
    }

    private static Map<String, Object> dateHistogramPanel(
            int x, int y, int w, int h, String title, String indexPattern, String dateField, String layerType) {
        Map<String, Object> xAxis = new LinkedHashMap<>();
        xAxis.put(KEY_OPERATION, "date_histogram");
        xAxis.put(KEY_FIELD, dateField);

        Map<String, Object> layer = new LinkedHashMap<>();
        layer.put(KEY_TYPE, layerType);
        layer.put(KEY_DATA_SOURCE, dataViewSpec(indexPattern));
        layer.put("x", xAxis);
        layer.put("y", List.of(Map.of(KEY_OPERATION, OPERATION_COUNT)));

        Map<String, Object> axisX = new LinkedHashMap<>();
        axisX.put("scale", "temporal");
        axisX.put("domain", Map.of(KEY_TYPE, "fit", "rounding", false));

        Map<String, Object> config = new LinkedHashMap<>();
        config.put(KEY_TYPE, TYPE_XY);
        config.put(KEY_TITLE, title);
        config.put(KEY_LAYERS, List.of(layer));
        config.put("axis", Map.of("x", axisX));
        return visPanel(x, y, w, h, config);
    }

    private static Map<String, Object> section(
            String title, boolean collapsed, int y, List<Map<String, Object>> nestedPanels) {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put(KEY_TITLE, title);
        section.put("collapsed", collapsed);
        section.put(KEY_GRID, Map.of("y", y));
        section.put(KEY_PANELS, nestedPanels);
        return section;
    }

    private static Map<String, Object> visPanel(int x, int y, int w, int h, Map<String, Object> config) {
        Map<String, Object> panel = new LinkedHashMap<>();
        panel.put(KEY_TYPE, TYPE_VIS);
        panel.put(KEY_GRID, grid(x, y, w, h));
        panel.put(KEY_CONFIG, config);
        return panel;
    }

    private static Map<String, Object> dataViewSpec(String indexPattern) {
        // data_view_spec has additionalProperties:false — only type/index_pattern/time_field/…
        Map<String, Object> dataSource = new LinkedHashMap<>();
        dataSource.put(KEY_TYPE, "data_view_spec");
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
