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

package fr.pilato.elasticsearch.crawler.fs.crawler.ssh;

import fr.pilato.elasticsearch.crawler.fs.crawler.FileAbstractModel;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.Server;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyPairResourceWriter;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.config.keys.AuthorizedKeysAuthenticator;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.server.SftpFileSystemAccessor;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.apache.sshd.sftp.server.SftpSubsystemProxy;
import org.hamcrest.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class FileAbstractorSSHTest extends AbstractFSCrawlerTestCase {
    private static final Logger logger = LogManager.getLogger();
    private final static String SSH_USERNAME = "USERNAME";
    private final static String SSH_PASSWORD = "PASSWORD";
    private SshServer sshd = null;
    Path testDir = rootTmpDir.resolve("test-ssh");

    @Before
    public void setup() throws IOException, NoSuchAlgorithmException {
        if (Files.notExists(testDir)) {
            Files.createDirectory(testDir);
        }
        // Add some fake files to the rootTmpDir
        Path testFile = testDir.resolve("testfile.txt");
        if (Files.notExists(testFile)) {
            Files.writeString(testFile, "I'm a test file");
        }
        Path testResourceTarget = testDir.resolve("subdir");
        if (Files.notExists(testResourceTarget)) {
            Files.createDirectory(testResourceTarget);
        }
        Path subFile = testResourceTarget.resolve("subfile.txt");
        if (Files.notExists(subFile)) {
            Files.writeString(subFile, "I'm a sub file");
        }

        SftpSubsystemFactory factory = new SftpSubsystemFactory.Builder()
                .withFileSystemAccessor(new SftpFileSystemAccessor() {
                    @Override
                    public Path resolveLocalFilePath(SftpSubsystemProxy subsystem, Path rootDir, String remotePath) throws InvalidPathException {
                        String path = remotePath;
                        if (remotePath.startsWith("/")) {
                            path = remotePath.substring(1);
                        }
                        return rootTmpDir.resolve(path);
                    }
                })
                .build();

        // Generate the key files for our SSH tests
        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        saveKeyPair(rootTmpDir, keyPair);

        sshd = SshServer.setUpDefaultServer();
        sshd.setHost("localhost");
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(rootTmpDir.resolve("host.ser")));
        sshd.setPasswordAuthenticator((username, password, session) ->
                SSH_USERNAME.equals(username) && SSH_PASSWORD.equals(password));
        sshd.setPublickeyAuthenticator(new AuthorizedKeysAuthenticator(rootTmpDir.resolve("public.key")));

        sshd.setSubsystemFactories(Collections.singletonList(factory));
        sshd.start();

        logger.info(" -> Started fake SSHD service on {}:{}", sshd.getHost(), sshd.getPort());
    }

    private void saveKeyPair(Path path, KeyPair keyPair) {
        OpenSSHKeyPairResourceWriter writer = new OpenSSHKeyPairResourceWriter();

        // Store Public Key.
        try (FileOutputStream fos = new FileOutputStream(path.resolve("public.key").toFile())) {
            writer.writePublicKey(keyPair.getPublic(), "Public Key for tests", fos);
        } catch (GeneralSecurityException | IOException e) {
            logger.error("Failed to save public key", e);
        }

        // Store Private Key.
        try (FileOutputStream fos = new FileOutputStream(path.resolve("private.key").toFile())) {
            writer.writePrivateKey(keyPair, "Private Key for tests", null, fos);
        } catch (GeneralSecurityException | IOException e) {
            logger.error("Failed to save public key", e);
        }
    }

    @After
    public void shutDown() throws IOException {
        if (sshd != null) {
            sshd.stop(true);
            sshd.close();
            logger.info(" -> Stopped fake SSHD service on {}:{}", sshd.getHost(), sshd.getPort());
        }
    }

    @Test
    public void testSshClient() throws Exception {
        String path = testDir.getFileName().toString();

        // Test with login / password
        try (FsCrawlerSshClient client = new FsCrawlerSshClient(SSH_USERNAME, SSH_PASSWORD, null,
                sshd.getHost(),sshd.getPort())) {
            client.open();
            SftpClient.Attributes stat = client.getSftpClient().stat(path);
            assertThat(stat.isDirectory(), equalTo(true));
        }

        // Test with PEM file
        try (FsCrawlerSshClient client = new FsCrawlerSshClient(SSH_USERNAME, null,
                rootTmpDir.resolve("private.key").toString(),
                sshd.getHost(),sshd.getPort())) {
            client.open();
            SftpClient.Attributes stat = client.getSftpClient().stat(path);
            assertThat(stat.isDirectory(), equalTo(true));
        }
    }

    @Test
    public void testFileAbstractorSSH() throws Exception {
        String path = testDir.getFileName().toString();
        FsSettings fsSettings = FsSettings.builder("foo")
                .setServer(
                        Server.builder()
                                .setHostname(sshd.getHost())
                                .setPort(sshd.getPort())
                                .setUsername(SSH_USERNAME)
                                .setPassword(SSH_PASSWORD)
                                .build()
                )
                .build();
        try (FileAbstractorSSH ssh = new FileAbstractorSSH(fsSettings)) {
            ssh.open();
            assertThat(ssh.exists("/ThisPathDoesNotExist"), is(false));
            assertThat(ssh.exists(path), is(true));
            Collection<FileAbstractModel> files = ssh.getFiles(path);
            assertThat(files, iterableWithSize(2));

            FileAbstractModel expected1 = new FileAbstractModel("subdir", false,
                    null, null, null,
                    "", "test-ssh", "test-ssh/subdir", 0,
                    null, null, 0);
            FileAbstractModel expected2 = new FileAbstractModel("testfile.txt", true,
                    null, null, null,
                    "txt", "test-ssh", "test-ssh/testfile.txt", 15,
                    null, null, 15);

            assertThat(files, containsInAnyOrder(new FileAbstractModelMatcher(expected1), new FileAbstractModelMatcher(expected2)));
        }
    }

    static class FileAbstractModelMatcher extends BaseMatcher<FileAbstractModel> {

        FileAbstractModel bean;

        public FileAbstractModelMatcher(FileAbstractModel object) {
            bean = object;
        }

        @Override
        public boolean matches(Object arg) {
            if (arg instanceof FileAbstractModel) {
                FileAbstractModel file = (FileAbstractModel) arg;
                return file.isFile() == bean.isFile() &&
                        file.getName().equals(bean.getName()) &&
                        file.getExtension().equals(bean.getExtension()) &&
                        file.getPath().equals(bean.getPath()) &&
                        file.getFullpath().equals(bean.getFullpath()) &&
                        file.getSize() == bean.getSize();
            }
            return false;
        }

        @Override
        public void describeTo(Description description) {
            description
                    .appendText("Instance of FileAbstractModel(")
                    .appendValue(bean.getName())
                    .appendText(")");
        }
    }
}
