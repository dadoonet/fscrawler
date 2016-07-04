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

package fr.pilato.elasticsearch.crawler.integration;

import fr.pilato.elasticsearch.crawler.fs.AbstractFSCrawlerTest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.junit.ClassRule;
import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.net.InetAddress;

public abstract class AbstractITest extends AbstractFSCrawlerTest {

    protected final static int TRANSPORT_TEST_PORT = 9380;
    protected final static int HTTP_TEST_PORT = 9280;
    protected static final Logger staticLogger = LogManager.getLogger(AbstractMultiNodesITest.class);

    protected static Client client = null;

    @ClassRule
    public static ExternalResource elasticsearchClient = new ExternalResource() {
        @Override
        protected void before() throws Throwable {
            File home = folder.newFolder("client");
            staticLogger.info("  --> Starting elasticsearch client in [{}]", home.toString());

            client = TransportClient.builder()
                    .settings(Settings.builder()
                                    .put("path.home", home)
                                    .put("cluster.name", "fscrawler-integration-tests")
                                    .build()
                    )
                    .build()
                    .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName("127.0.0.1"), TRANSPORT_TEST_PORT));
        }

        @Override
        protected void after() {
            if (client != null) {
                staticLogger.info("  --> Stopping elasticsearch client");
                client.close();
                client = null;
            }
        }
    };

}
