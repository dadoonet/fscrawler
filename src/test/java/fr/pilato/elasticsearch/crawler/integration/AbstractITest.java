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
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import org.junit.ClassRule;
import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.net.InetAddress;

public abstract class AbstractITest extends AbstractFSCrawlerTest {
    @ClassRule
    public static TemporaryFolder folder = new TemporaryFolder();

    protected final static int TRANSPORT_TEST_PORT = 9380;
    protected final static int HTTP_TEST_PORT = 9280;

    protected static Node node = null;
    protected static Client client = null;

    @ClassRule
    public static ExternalResource elasticsearch = new ExternalResource() {
        @Override
        protected void before() throws Throwable {
            folder.create();
            File home = folder.newFolder("elasticsearch");
            staticLogger.info("  --> Starting elasticsearch test node in [{}]", home.toString());

            node = NodeBuilder.nodeBuilder()
                    .settings(Settings.builder()
                                    .put("path.home", home)
                                    .put("cluster.name", "fscrawler-integration-tests")
                                    .put("transport.tcp.port", TRANSPORT_TEST_PORT)
                                    .put("http.port", HTTP_TEST_PORT)
                    )
                    .node();

            client = TransportClient.builder()
                    .settings(Settings.builder()
                                    .put("path.home", folder.newFolder("client"))
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

            if (node != null) {
                staticLogger.info("  --> Stopping elasticsearch test node");
                node.close();
                node = null;
            }
        }
    };

}
