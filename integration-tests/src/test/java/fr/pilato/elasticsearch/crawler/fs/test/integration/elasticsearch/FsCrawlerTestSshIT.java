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

import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.settings.Fs;
import fr.pilato.elasticsearch.crawler.fs.settings.Server;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Test crawler with SSH
 */
public class FsCrawlerTestSshIT extends AbstractFsCrawlerITCase {

    /**
     * You have to adapt this test to your own system (login / password and SSH connexion)
     * So this test is disabled by default
     */
    @Test @Ignore
    public void test_ssh() throws Exception {
        String username = "USERNAME";
        String password = "PASSWORD";
        String hostname = "localhost";

        Fs fs = startCrawlerDefinition().build();
        Server server = Server.builder()
                .setHostname(hostname)
                .setUsername(username)
                .setPassword(password)
                .setProtocol(Server.PROTOCOL.SSH)
                .build();
        crawler = startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), server);

        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 2L, null);
    }

    /**
     * You have to adapt this test to your own system (login / pem file and SSH connexion)
     * So this test is disabled by default
     */
    @Test @Ignore
    public void test_ssh_with_key() throws Exception {
        String username = "USERNAME";
        String path_to_pem_file = "/path/to/private_key.pem";
        String hostname = "localhost";

        Fs fs = startCrawlerDefinition().build();
        Server server = Server.builder()
                .setHostname(hostname)
                .setUsername(username)
                .setPemPath(path_to_pem_file)
                .setProtocol(Server.PROTOCOL.SSH)
                .build();
        crawler = startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), server);

        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
    }
}
