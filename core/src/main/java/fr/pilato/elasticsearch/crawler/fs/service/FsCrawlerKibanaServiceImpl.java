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
import fr.pilato.elasticsearch.crawler.fs.kibana.KibanaClient;
import fr.pilato.elasticsearch.crawler.fs.kibana.KibanaClientException;
import fr.pilato.elasticsearch.crawler.fs.kibana.KibanaDashboardBuilder;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FsCrawlerKibanaServiceImpl implements FsCrawlerKibanaService {

    private static final Logger logger = LogManager.getLogger();

    private final FsSettings settings;
    private final IKibanaClient client;

    public FsCrawlerKibanaServiceImpl(FsSettings settings) {
        this.settings = settings;
        this.client = new KibanaClient(settings);
    }

    FsCrawlerKibanaServiceImpl(FsSettings settings, IKibanaClient client) {
        this.settings = settings;
        this.client = client;
    }

    @Override
    public void start() throws IOException, KibanaClientException {
        client.start();
        logger.debug("Kibana service started");
    }

    @Override
    public void setupDashboard() throws KibanaClientException {
        if (!client.isDashboardProvisioningEnabled()) {
            logger.debug("Kibana dashboard provisioning is disabled, skipping");
            return;
        }

        String jobName = settings.getName();
        String indexPattern = settings.getElasticsearch().getIndex();
        String dataViewId = KibanaDashboardBuilder.dataViewIdForJob(jobName);
        String dashboardId = KibanaDashboardBuilder.dashboardIdForJob(jobName);

        client.createDataViewIfMissing(dataViewId, indexPattern, KibanaDashboardBuilder.DEFAULT_TIME_FIELD);

        String payload = KibanaDashboardBuilder.buildDefaultDashboardPayload(settings);
        boolean forcePush = settings.getKibana().isForcePushDashboard();

        if (client.dashboardExists(dashboardId)) {
            if (!forcePush) {
                logger.info(
                        "Kibana dashboard [{}] already exists. Skipping. "
                                + "Use kibana.force_push_dashboard: true to override.",
                        dashboardId);
                return;
            }
            String updatedId = client.updateDashboard(dashboardId, payload);
            logger.info(
                    "Updated Kibana dashboard [{}] at {}",
                    updatedId,
                    settings.getKibana().getUrl());
            return;
        }

        String createdId = client.createDashboard(payload);
        logger.info(
                "Created Kibana dashboard [{}] at {}",
                createdId,
                settings.getKibana().getUrl());
    }

    @Override
    public void close() throws IOException {
        client.close();
        logger.debug("Kibana service stopped");
    }
}
