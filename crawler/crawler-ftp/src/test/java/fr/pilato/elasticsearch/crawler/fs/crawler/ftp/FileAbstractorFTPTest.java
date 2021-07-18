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

package fr.pilato.elasticsearch.crawler.fs.crawler.ftp;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import fr.pilato.elasticsearch.crawler.fs.crawler.FileAbstractModel;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.Server;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

import org.apache.commons.io.IOUtils;
import org.apache.commons.net.ftp.FTPClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockftpserver.fake.FakeFtpServer;
import org.mockftpserver.fake.UserAccount;
import org.mockftpserver.fake.filesystem.DirectoryEntry;
import org.mockftpserver.fake.filesystem.FileEntry;
import org.mockftpserver.fake.filesystem.FileSystem;
import org.mockftpserver.fake.filesystem.UnixFakeFileSystem;

public class FileAbstractorFTPTest extends AbstractFSCrawlerTestCase {
    private FakeFtpServer fakeFtpServer;
    private final String path = "/data";
    private final String user = "user";
    private final String pass = "password";

    @Before
    public void setup() {
        // it doesn't seem to support utf-8
        fakeFtpServer = new FakeFtpServer();
        fakeFtpServer.setServerControlPort(5968);
        fakeFtpServer.addUserAccount(new UserAccount(user, pass, path));
        FileSystem fileSystem = new UnixFakeFileSystem();

        fileSystem.add(new DirectoryEntry("/data"));
        fileSystem.add(new FileEntry("/data/foo.txt", "foo"));
        fileSystem.add(new FileEntry("/data/bar.txt", "bar"));

        fileSystem.add(new DirectoryEntry("/data/buzz"));
        fileSystem.add(new FileEntry("/data/buzz/hello.txt", "hello"));
        fileSystem.add(new FileEntry("/data/buzz/world.txt", "world"));

        fakeFtpServer.setFileSystem(fileSystem);
        fakeFtpServer.start();
    }

    @After
    public void teardown() {
        fakeFtpServer.stop();
    }

    @Test
    public void testConnectToFakeFTPServer() throws Exception {
        int port = fakeFtpServer.getServerControlPort();
        FsSettings fsSettings = FsSettings.builder("fake")
                .setServer(
                        Server.builder()
                                .setHostname("localhost")
                                .setUsername(user)
                                .setPassword(pass)
                                .setPort(port)
                                .build()
                )
                .build();

        FileAbstractorFTP ftp = new FileAbstractorFTP(fsSettings);
        ftp.open();
        boolean exists = ftp.exists(path);
        assertThat(exists, is(true));
        Collection<FileAbstractModel> files = ftp.getFiles(path);
        assertThat(files.size(), is(3));

        for (FileAbstractModel file : files) {
            if (file.isDirectory()) {
                assertThat(file.getName(), is("buzz"));
                Collection<FileAbstractModel> subDirFiles = ftp.getFiles(file.getFullpath());
                assertThat(subDirFiles.size(), is(2));
                logger.debug("Found {} files in sub dir", subDirFiles.size());
                for (FileAbstractModel subDirFile : subDirFiles) {
                    try (InputStream inputStream = ftp.getInputStream(subDirFile)) {
                        String content = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
                        logger.debug("[sub dir] - {}: {}", subDirFile.getName(), content);
                    }
                }
            } else {
                try (InputStream inputStream = ftp.getInputStream(file)) {
                    String content = IOUtils.toString(inputStream, FTPClient.DEFAULT_CONTROL_ENCODING);
                    logger.debug(" - {}: {}", file.getName(), content);
                }
            }
        }

        ftp.close();
    }

    @Test @Ignore
    public void testConnectToFTPServer() throws Exception {
        String path = "/";
        FsSettings fsSettings = FsSettings.builder("local_test")
            .setServer(
                Server.builder()
                    .setHostname("192.168.18.207")
                    .setUsername("helsonxiao")
                    .setPassword("123456")
                    .setPort(21)
                    .build()
            )
            .build();

        FileAbstractorFTP ftp = new FileAbstractorFTP(fsSettings);
        ftp.open();
        boolean exists = ftp.exists(path);
        assertThat(exists, is(true));
        Collection<FileAbstractModel> files = ftp.getFiles(path);
        logger.debug("Found {} files", files.size());

        for (FileAbstractModel file : files) {
            if (file.isDirectory()) {
                Collection<FileAbstractModel> subDirFiles = ftp.getFiles(file.getFullpath());
                logger.debug("Found {} files in sub dir", subDirFiles.size());
                for (FileAbstractModel subDirFile : subDirFiles) {
                    if (subDirFile.isFile()) {
                        try (InputStream inputStream = ftp.getInputStream(subDirFile)) {
                            String content = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
                            logger.debug("[sub dir] - {}: {}", subDirFile.getName(), content);
                        }
                    }
                }
            } else {
                try (InputStream inputStream = ftp.getInputStream(file)) {
                    String content = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
                    logger.debug(" - {}: {}", file.getName(), content);
                }
            }
        }

        ftp.close();
    }
}
