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

import com.jcraft.jsch.JSchException;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.settings.Fs;
import fr.pilato.elasticsearch.crawler.fs.settings.Server;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.apache.sshd.server.SshServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;

/**
 * Test crawler with SSH
 */
public class FsCrawlerTestSshIT extends AbstractFsCrawlerITCase {

    private SshServer sshd = null;

    @Before
    public void setup() throws IOException, JSchException {
        sshd = startSshServer();
    }

    @After
    public void shutDown() throws IOException {
        if (sshd != null) {
            sshd.stop(true);
            logger.info(" -> Stopped fake SSHD service on {}:{}", sshd.getHost(), sshd.getPort());
        }
    }

    @Test
    public void test_ssh() throws Exception {
        Fs fs = startCrawlerDefinition("/").build();
        Server server = Server.builder()
                .setHostname(sshd.getHost())
                .setPort(sshd.getPort())
                .setUsername(SSH_USERNAME)
                .setPassword(SSH_PASSWORD)
                .setProtocol(Server.PROTOCOL.SSH)
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), server);

        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 2L, null);
    }

    // TODO fix the key pair generation problem
    @Test @Ignore
    public void test_ssh_with_key() throws Exception {
        Fs fs = startCrawlerDefinition("/").build();
        Server server = Server.builder()
                .setHostname(sshd.getHost())
                .setPort(sshd.getPort())
                .setUsername(SSH_USERNAME)
                .setPemPath(rootTmpDir.resolve("private.key").toFile().getAbsolutePath())
                .setProtocol(Server.PROTOCOL.SSH)
                .build();
        startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), server);

        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
    }
}
