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

import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClient;
import fr.pilato.elasticsearch.crawler.fs.client.SearchRequest;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.Elasticsearch;
import org.elasticsearch.action.index.IndexRequest;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.fail;

/**
 * Test elasticsearch HTTP client with multiple nodes
 * With failure detection
 */
public class ElasticsearchClientFailureDetectionITest extends AbstractMultiNodesITest {

    private static ElasticsearchClient elasticsearchClient;

    @BeforeClass
    public static void startClient() {
        // We start with no elasticsearch node
        elasticsearchClient = ElasticsearchClient.builder().build();
        for (int i = 0; i < NUMBER_OF_NODES; i++) {
            elasticsearchClient
                    .addNode(Elasticsearch.Node.builder().setHost("127.0.0.1").setPort(HTTP_TEST_PORT + i).build());
        }
    }

    @AfterClass
    public static void stopClient() {
        elasticsearchClient = null;
    }

    @Test
    public void testAndStartNodes() throws IOException, InterruptedException, ExecutionException {
        AtomicInteger iteration = new AtomicInteger();

        // We have 0 running node
        try {
            elasticsearchClient.findNextNode();
            fail("We should get a IOException here as no node is available");
        } catch (IOException e) {
            // We expect it!
            assertThat(e.getMessage(), containsString("no active node found. Start an elasticsearch cluster first!"));
            logger.debug("  --> Expected exception raised: [{}]", e.getMessage());
        }

        // We have 1 running node: 9280
        startNewNode(0, getCurrentTestName());

        // Create a dummy index
        nodes.get(0).client().index(new IndexRequest("dummy", "type").source("foo", "bar").refresh(true)).get();
        Elasticsearch.Node nextNode = findNodeTester(iteration, 9280);

        // We call elasticsearch client so we have a chance to detect a failing node
        elasticsearchClient.search(nextNode, "dummy", "type", SearchRequest.builder().build());

        // We have 2 running nodes: 9280, 9281
        startNewNode(1, getCurrentTestName());
        for (int i = 0; i < 20; i++) {
            findNodeTester(iteration, 9280, 9281);
        }

        // We have 3 running nodes: 9280, 9281, 9282
        startNewNode(2, getCurrentTestName());
        for (int i = 0; i < 20; i++) {
            findNodeTester(iteration, 9280, 9281, 9282);
        }

        // We have 2 running nodes: 9280, 9282
        closeNode(1);

        // We call elasticsearch client so we have a chance to detect a failing node
        try {
            elasticsearchClient.search(elasticsearchClient.getNode(1), "dummy", "type", SearchRequest.builder().build());
            fail("We should get a ConnectException here as the node is down");
        } catch (ConnectException e) {
            // We can expect this
        }

        for (int i = 0; i < 20; i++) {
            findNodeTester(iteration, 9280, 9282);
        }

        // We have 1 running node: 9280
        closeNode(2);

        // We call elasticsearch client so we have a chance to detect a failing node
        try {
            elasticsearchClient.search(elasticsearchClient.getNode(2), "dummy", "type", SearchRequest.builder().build());
            fail("We should get a ConnectException here as the node is down");
        } catch (ConnectException e) {
            // We can expect this
        }

        for (int i = 0; i < 20; i++) {
            findNodeTester(iteration, 9280);
        }

        // We have 2 running nodes: 9280, 9282
        restartNode(2, getCurrentTestName());

        for (int i = 0; i < 20; i++) {
            findNodeTester(iteration, 9280, 9282);
        }
    }

    private Elasticsearch.Node findNodeTester(AtomicInteger iteration, Integer... expectedRunningPorts) throws IOException {
        logger.info("--> iteration {}", iteration.incrementAndGet());
        Elasticsearch.Node nextNode = elasticsearchClient.findNextNode();
        logger.debug("  -> node: [{}]", nextNode);
        assertThat(nextNode.getPort(), isOneOf((Object[])expectedRunningPorts));
        return nextNode;
    }
}
