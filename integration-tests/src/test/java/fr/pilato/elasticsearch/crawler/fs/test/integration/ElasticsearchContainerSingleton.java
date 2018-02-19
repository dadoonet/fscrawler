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

package fr.pilato.elasticsearch.crawler.fs.test.integration;

import fr.pilato.elasticsearch.containers.ElasticsearchContainer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testcontainers.containers.wait.HttpWaitStrategy;

import java.time.Duration;

public class ElasticsearchContainerSingleton {

    protected static final Logger logger = LogManager.getLogger(ElasticsearchContainerSingleton.class);
    private static ElasticsearchContainer container = null;

    static ElasticsearchContainer getInstance(String version, String user, String password) {
        if (container == null) {
            logger.debug("No local node running. We need to start a Docker instance.");
            container = new ElasticsearchContainer().withVersion(version);
            container.withEnv("ELASTIC_PASSWORD", password);
            container.setWaitStrategy(
                    new HttpWaitStrategy()
                            .forStatusCode(200)
                            .withBasicCredentials(user, password)
                            .withStartupTimeout(Duration.ofSeconds(90)));
            container.start();
        }

        return container;
    }

}
