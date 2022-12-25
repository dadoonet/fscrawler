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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockftpserver.fake.FakeFtpServer;
import org.mockftpserver.fake.UserAccount;
import org.mockftpserver.fake.filesystem.DirectoryEntry;
import org.mockftpserver.fake.filesystem.FileEntry;
import org.mockftpserver.fake.filesystem.FileSystem;
import org.mockftpserver.fake.filesystem.UnixFakeFileSystem;

/**
 * Test crawler with FTP
 */
public class FsCrawlerTestFTPIT extends AbstractFsCrawlerITCase {
    private FakeFtpServer fakeFtpServer;
    private final int port = 5968;
    private final String hostname = "localhost";
    private final String user = "user";
    private final String pass = "pass";

    @Before
    public void setup() {
        fakeFtpServer = new FakeFtpServer();
        fakeFtpServer.setServerControlPort(port);
        UserAccount anonymous = new UserAccount("anonymous", "", "/");
        anonymous.setPasswordRequiredForLogin(false);
        fakeFtpServer.addUserAccount(anonymous);
        fakeFtpServer.addUserAccount(new UserAccount(user, pass, "/"));
        FileSystem fileSystem = new UnixFakeFileSystem();

        fileSystem.add(new DirectoryEntry("/"));
        fileSystem.add(new FileEntry("/foo.txt", "bar"));

        fakeFtpServer.setFileSystem(fileSystem);
        fakeFtpServer.start();
    }

    @After
    public void shutDown() {
        if (fakeFtpServer != null) {
            fakeFtpServer.stop();
        }
    }

    @Test
    public void test_ftp() throws Exception {
        Fs fs = startCrawlerDefinition().setUrl("/").build();
        Server server = Server.builder()
                .setHostname(hostname)
                .setUsername("anonymous")
                .setProtocol(Server.PROTOCOL.FTP)
                .setPort(port)
                .build();
        crawler = startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), server);

        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
    }

    @Test
    public void test_ftp_with_user() throws Exception {
        Fs fs = startCrawlerDefinition().setUrl("/").build();
        Server server = Server.builder()
                .setHostname(hostname)
                .setUsername(user)
                .setPassword(pass)
                .setProtocol(Server.PROTOCOL.FTP)
                .setPort(port)
                .build();
        crawler = startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), server);

        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
    }
}
