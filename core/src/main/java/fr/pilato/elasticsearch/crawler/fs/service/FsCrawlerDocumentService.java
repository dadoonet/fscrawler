/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

package fr.pilato.elasticsearch.crawler.fs.service;

import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

public class FsCrawlerDocumentService implements FsCrawlerService {

    private static final Logger logger = LogManager.getLogger(FsCrawlerDocumentService.class);

    private final ElasticsearchClient client;

    public FsCrawlerDocumentService(ElasticsearchClient client) {
        this.client = client;
    }

    @Override
    public void start() throws IOException {
        client.start();
        logger.debug("Document Service started");
    }

    @Override
    public ElasticsearchClient getClient() {
        return client;
    }

    @Override
    public void close() throws IOException {
        client.close();
        logger.debug("Document Service stopped");
    }
}
