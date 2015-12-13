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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.http.BindHttpException;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import org.elasticsearch.transport.BindTransportException;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assume.assumeTrue;

public abstract class AbstractMultiNodesITest extends AbstractITest {

    protected static final int NUMBER_OF_NODES = 3;
    protected static List<Node> nodes = new ArrayList<>(NUMBER_OF_NODES);
    protected static final Logger staticLogger = LogManager.getLogger(AbstractMultiNodesITest.class);

    @AfterClass
    public static void closeRunningNodes() {
        for (int i = 0; i < nodes.size(); i++) {
            closeNode(i);
        }
    }

    @BeforeClass
    public static void initNodes() {
        // If we have some remaining nodes running, let's kill them
        closeRunningNodes();
        nodes.clear();
    }

    protected static void startNewNode(int nodeNumber, String prefix) throws IOException {
        File home = folder.newFolder("elasticsearch" + (prefix != null ? prefix : "") + nodeNumber);
        nodes.add(nodeNumber, internalStart(home, nodeNumber));
    }

    protected static void restartNode(int nodeNumber, String prefix) throws IOException {
        staticLogger.info("  --> Restarting elasticsearch test node [{}]", nodeNumber);
        Path path = Paths.get(folder.getRoot().getAbsolutePath(), "elasticsearch" + (prefix != null ? prefix : "") + nodeNumber);
        File home = path.toFile();
        nodes.add(nodeNumber, internalStart(home, nodeNumber));
    }

    private static Node internalStart(File home, int nodeNumber) {
        staticLogger.info("  --> Starting elasticsearch test node [{}] in [{}]", nodeNumber, home.toString());
        Node node = null;
        try {
            node = NodeBuilder.nodeBuilder()
                    .settings(Settings.builder()
                                    .put("path.home", home)
                                    .put("cluster.name", "fscrawler-integration-tests")
                                    .put("transport.tcp.port", TRANSPORT_TEST_PORT + nodeNumber)
                                    .put("http.port", HTTP_TEST_PORT + nodeNumber)
                                    .put("cluster.routing.allocation.disk.threshold_enabled", false)
                                    .putArray("discovery.zen.ping.unicast.hosts",
                                            "127.0.0.1:" + TRANSPORT_TEST_PORT + "-" + TRANSPORT_TEST_PORT + NUMBER_OF_NODES)
                    )
                    .node();
        } catch (BindHttpException|BindTransportException e) {
            staticLogger.warn("  --> Can not start elasticsearch node: {}", e.getMessage());
        }

        assumeTrue(node != null);

        return node;
    }

    protected static void closeNode(int node) {
        staticLogger.info("  --> Stopping elasticsearch test node [{}]", node);
        try {
            nodes.get(node).close();
        } catch (Exception e) {
            staticLogger.info("  --> Failed to stop node [{}]: [{}]", node, e.getMessage());
        }
    }
}
