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
package fr.pilato.elasticsearch.crawler.fs.test.integration.elasticsearch;

import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockftpserver.fake.FakeFtpServer;
import org.mockftpserver.fake.UserAccount;
import org.mockftpserver.fake.filesystem.DirectoryEntry;
import org.mockftpserver.fake.filesystem.FileEntry;
import org.mockftpserver.fake.filesystem.FileSystem;
import org.mockftpserver.fake.filesystem.UnixFakeFileSystem;

/** Test crawler with FTP */
class FsCrawlerTestFTPIT extends AbstractFsCrawlerITCase {
    private FakeFtpServer fakeFtpServer;
    private final String hostname = "localhost";
    private final String user = "user";
    private final String pass = "pass";

    @BeforeEach
    void setup() {
        fakeFtpServer = new FakeFtpServer();
        fakeFtpServer.setServerControlPort(0);
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

    @AfterEach
    void shutDown() {
        if (fakeFtpServer != null) {
            fakeFtpServer.stop();
        }
    }

    @Test
    void ftp() throws Exception {
        FsSettings fsSettings = createTestSettings();
        configureFtp(fsSettings, "anonymous", null);
        crawler = startCrawler(fsSettings);

        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName() + FsCrawlerUtil.INDEX_SUFFIX_DOCS), 1L, null);
    }

    @Test
    void ftp_with_user() throws Exception {
        FsSettings fsSettings = createTestSettings();
        configureFtp(fsSettings, user, pass);
        crawler = startCrawler(fsSettings);

        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName() + FsCrawlerUtil.INDEX_SUFFIX_DOCS), 1L, null);
    }

    private void configureFtp(FsSettings fsSettings, String username, String password) {
        fsSettings.getFs().setProvider("ftp");
        fsSettings.getFs().setUrl("/");
        Map<String, Object> ftp = new LinkedHashMap<>();
        ftp.put("hostname", hostname);
        ftp.put("port", fakeFtpServer.getServerControlPort());
        ftp.put("username", username);
        if (password != null) {
            ftp.put("password", password);
        }
        fsSettings.getFs().setProviders(Map.of("ftp", ftp));
    }
}
