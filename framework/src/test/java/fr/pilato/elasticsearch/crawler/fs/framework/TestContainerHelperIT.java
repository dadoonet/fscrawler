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
package fr.pilato.elasticsearch.crawler.fs.framework;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import fr.pilato.elasticsearch.crawler.fs.test.framework.TestContainerHelper;
import org.junit.Test;
import org.testcontainers.DockerClientFactory;

public class TestContainerHelperIT {

    private static final String DEFAULT_TEST_CLUSTER_URL = "https://127.0.0.1:9200";

    @Test
    public void testStartingTestcontainer() {
        // Don't run it if an external cluster for Elasticsearch is set
        boolean isExternalClusterSet = System.getProperty("tests.cluster.url") != null
                && !DEFAULT_TEST_CLUSTER_URL.equals(System.getProperty("tests.cluster.url"));
        assumeFalse("External Elasticsearch cluster is set, skipping TestContainerHelperIT.", isExternalClusterSet);

        // Check if Docker is available on this OS
        assumeTrue(
                "Docker is not available on this machine.",
                DockerClientFactory.instance().isDockerAvailable());

        TestContainerHelper helper = new TestContainerHelper();
        assertThat(helper.isStarted()).isFalse();
        assertThat(helper.getElasticsearchVersion()).isNotBlank();
        String url = helper.startElasticsearch(false);
        assertThat(url).isNotBlank();
        assertThat(helper.isStarted()).isTrue();
    }
}
