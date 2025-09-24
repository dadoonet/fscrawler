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
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.time.Duration;
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

    // regex that
    //   matches 8.3 JSON logging with started message and some follow up content within the message field
    //   matches 8.0 JSON logging with no whitespace between message field and content
    //   matches 7.x JSON logging with whitespace between message field and content
    //   matches 6.x text logging with node name in brackets and just a 'started' message till the end of the line
    private static final String WAIT_LOGS_REGEX = ".*(\"message\":\\s?\"started[\\s?|\"].*|] started\n$)";
    private static final WaitStrategy ELASTICSEARCH_WAIT_STRATEGY = Wait
            .forLogMessage(WAIT_LOGS_REGEX, 1)
            .withStartupTimeout(Duration.ofMinutes(5));
    private final String elasticsearchVersion;

    private ElasticsearchContainer elasticsearch;
    private byte[] certAsBytes;
    private final AtomicBoolean starting = new AtomicBoolean(false);
    private boolean started = false;

    public TestContainerHelper() {
        try {
            Properties props = new Properties();
            props.load(TestContainerHelper.class.getResourceAsStream("/elasticsearch.version.properties"));
            elasticsearchVersion = props.getProperty("version");
        } catch (IOException e) {
            log.error("Cannot load /elasticsearch.version.properties from the classpath");
            throw new RuntimeException("Cannot load /elasticsearch.version.properties from the classpath", e);
        }
    }

    /**
     * Start the container. If the container was already started, it will be reused.
     * @param  keepData keep the cluster running after the test and reuse it if possible
     */
    public synchronized String startElasticsearch(boolean keepData) {
        if (starting.compareAndSet(false, true)) {
            if (!started) {
                // Start the container. This step might take some time...
                log.info("Starting{} testcontainers with Elasticsearch [{}].",
                        keepData ? " or reusing" : "",
                        elasticsearchVersion);

                elasticsearch = new ElasticsearchContainer(
                        DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch")
                                .withTag(elasticsearchVersion))
                        // As for 7.x clusters, there's no https, api keys are disabled by default. We force it.
                        .withEnv("xpack.security.authc.api_key.enabled", "true")
                        // For 6.x clusters and for semantic search, we need to activate a trial
                        .withEnv("xpack.license.self_generated.type", "trial")
                        .withReuse(keepData)
                        .withPassword(DEFAULT_PASSWORD)
                        .waitingFor(ELASTICSEARCH_WAIT_STRATEGY);
                elasticsearch.start();

                String url = String.format(HTTPS_URL, elasticsearch.getHttpHostAddress());

                // Try to get the https certificate if exists
                try {
                    certAsBytes = elasticsearch.copyFileFromContainer(
                            "/usr/share/elasticsearch/config/certs/http_ca.crt",
                            IOUtils::toByteArray);
                    log.debug("Found an https elasticsearch cert for version [{}].", elasticsearchVersion);
                } catch (Exception e) {
                    log.debug("We did not find the https elasticsearch cert for version [{}]. We switch to http instead.", elasticsearchVersion);
                    url = String.format(HTTP_URL, elasticsearch.getHttpHostAddress());
                }

                log.info("Elasticsearch container is now running at {}", url);

                starting.set(false);
                started = true;

                waitForReadiness();
                return url;
            } else {
                log.info("Testcontainers with Elasticsearch [{}] was previously started", elasticsearchVersion);
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

    public String getElasticsearchVersion() {
        return elasticsearchVersion;
    }

    private synchronized void waitForReadiness() {
        if (Integer.parseInt(elasticsearchVersion.split("\\.")[0]) > 8) {
            log.warn("From 9.0.0, we need to wait a bit before all security indices are allocated");
            try {
                wait(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
