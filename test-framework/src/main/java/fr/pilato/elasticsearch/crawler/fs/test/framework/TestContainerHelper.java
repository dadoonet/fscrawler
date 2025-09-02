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

package fr.pilato.elasticsearch.crawler.fs.test.framework;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This creates a TestContainer Elasticsearch instance
 */
public class TestContainerHelper {

    private static final Logger log = LogManager.getLogger(TestContainerHelper.class);

    public static final String DEFAULT_PASSWORD = "changeme";
    private static final String HTTPS_URL = "https://%s";
    private static final String HTTP_URL = "http://%s";

    private ElasticsearchContainer elasticsearch;
    private byte[] certAsBytes;
    private final AtomicBoolean starting = new AtomicBoolean(false);
    private boolean started = false;

    /**
     * Start the container. If the container was already started, it will be reused.
     * @param  keepData keep the cluster running after the test and reuse it if possible
     * @throws IOException in case of error
     */
    public synchronized String startElasticsearch(boolean keepData) throws IOException {
        if (starting.compareAndSet(false, true)) {
            Properties props = new Properties();
            props.load(TestContainerHelper.class.getResourceAsStream("/elasticsearch.version.properties"));
            String version = props.getProperty("version");
            if (!started) {
                // Start the container. This step might take some time...
                log.info("Starting{} testcontainers with Elasticsearch [{}].",
                        keepData ? " or reusing" : "",
                        version);

                elasticsearch = new ElasticsearchContainer(
                        DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch")
                                .withTag(version))
                        // As for 7.x clusters, there's no https, api keys are disabled by default. We force it.
                        .withEnv("xpack.security.authc.api_key.enabled", "true")
                        // For 6.x clusters and for semantic search, we need to activate a trial
                        .withEnv("xpack.license.self_generated.type", "trial")
                        .withReuse(keepData)
                        .withPassword(DEFAULT_PASSWORD);
                // TODO Change the WaitStrategy to allow more than the default 60s

                elasticsearch.start();

                String url = String.format(HTTPS_URL, elasticsearch.getHttpHostAddress());

                // Try to get the https certificate if exists
                try {
                    certAsBytes = elasticsearch.copyFileFromContainer(
                            "/usr/share/elasticsearch/config/certs/http_ca.crt",
                            IOUtils::toByteArray);
                    log.debug("Found an https elasticsearch cert for version [{}].", version);
                } catch (Exception e) {
                    log.debug("We did not find the https elasticsearch cert for version [{}]. We switch to http instead.", version);
                    url = String.format(HTTP_URL, elasticsearch.getHttpHostAddress());
                }

                log.info("Elasticsearch container is now running at {}", url);

                starting.set(false);
                started = true;

                waitForReadiness(version);
                return url;
            } else {
                log.info("Testcontainers with Elasticsearch [{}] was previously started", version);
                return String.format(HTTPS_URL, elasticsearch.getHttpHostAddress());
            }
        } else {
            log.info("Elasticsearch container is already starting. Skipping starting it again.");

            // Let's wait for it to be started
            while (!started) {
                try {
                    wait(1000);
                    log.info("Checking if started...");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            log.info("Testcontainers with Elasticsearch is now available");
            return String.format(HTTPS_URL, elasticsearch.getHttpHostAddress());
        }
    }

    public byte[] getCertAsBytes() {
        return certAsBytes;
    }

    public boolean isStarted() {
        return started;
    }

    private synchronized void waitForReadiness(String version) {
        if (Integer.parseInt(version.split("\\.")[0]) > 8) {
            log.warn("From 9.0.0, we need to wait a bit before all security indices are allocated");
            try {
                wait(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
