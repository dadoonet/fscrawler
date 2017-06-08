/*
 * Licensed to David Pilato (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
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
 */

package fr.pilato.elasticsearch.crawler.fs.test.integration;

import fr.pilato.elasticsearch.crawler.fs.FsCrawlerValidator;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientManager;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.rest.RestServer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

public class AbstractRestITCase extends AbstractITCase {

    private static ElasticsearchClientManager esClientManager;

    static final String REST_INDEX = "fscrawler_rest_tests";

    @BeforeClass
    public static void startRestServer() throws Exception {
        FsSettings fsSettings = FsSettings.builder(REST_INDEX)
                .setRest(rest)
                .setElasticsearch(elasticsearchWithSecurity)
                .build();
        FsCrawlerValidator.validateSettings(staticLogger, fsSettings, true);
        esClientManager = new ElasticsearchClientManager(metadataDir, fsSettings);
        esClientManager.start();
        RestServer.start(fsSettings, esClientManager);
    }

    @AfterClass
    public static void stopRestServer() throws InterruptedException {
        RestServer.close();
        if (esClientManager != null) {
            esClientManager.close();
            esClientManager = null;
        }
    }

    @Before
    public void createIndexAndMappings() throws Exception {
        FsSettings fsSettings = FsSettings.builder(getCrawlerName()).setRest(rest).build();
        FsCrawlerValidator.validateSettings(logger, fsSettings, true);
        esClientManager.createIndices(fsSettings);
    }
}
