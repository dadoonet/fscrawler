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

package fr.pilato.elasticsearch.crawler.fs.test.integration.elasticsearch;

import fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.rest.RestServer;
import fr.pilato.elasticsearch.crawler.fs.rest.UploadResponse;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.Rest;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import jakarta.ws.rs.client.WebTarget;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static fr.pilato.elasticsearch.crawler.fs.test.integration.elasticsearch.FsCrawlerRestIT.uploadFile;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Test rest crawler settings
 */
public class FsCrawlerTestRestOnlyIT extends AbstractFsCrawlerITCase {

    /**
     * Test case for #251: https://github.com/dadoonet/fscrawler/issues/251 : Add a REST Layer
     */
    @Test
    public void test_with_rest_only() throws Exception {
        logger.info("  --> starting crawler [{}]", getCrawlerName());

        try {
            // TODO do this rarely() createIndex(jobName);
            FsSettings fsSettings = FsSettings.builder(getCrawlerName())
                    .setElasticsearch(endCrawlerDefinition(getCrawlerName()))
                    .setFs(startCrawlerDefinition().build())
                    .setServer(null)
                    .setRest(new Rest("http://127.0.0.1:" + (testRestPort+1) + "/fscrawler")).build();
            crawler = new FsCrawlerImpl(
                    metadataDir,
                    fsSettings,
                    0,
                    true);
            crawler.start();
            RestServer.start(fsSettings, crawler.getManagementService(), crawler.getDocumentService());

            Path from = rootTmpDir.resolve("resources").resolve("documents");
            if (Files.notExists(from)) {
                staticLogger.error("directory [{}] should exist before wa start tests", from);
                throw new RuntimeException(from + " doesn't seem to exist. Check your JUnit tests.");
            }

            WebTarget target = client.target("http://127.0.0.1:" + (testRestPort+1) + "/fscrawler");
            Files.walk(from)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        UploadResponse response = uploadFile(target, path);
                        assertThat(response.getFilename(), is(path.getFileName().toString()));
                    });

            // We wait until we have all docs
            countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), Files.list(from).count(), null, TimeValue.timeValueMinutes(1));
        } finally {
            RestServer.close();
        }
    }
}
