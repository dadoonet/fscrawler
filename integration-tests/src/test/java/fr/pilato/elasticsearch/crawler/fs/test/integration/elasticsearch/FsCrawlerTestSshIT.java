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
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyPairResourceWriter;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.config.keys.AuthorizedKeysAuthenticator;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.server.SftpFileSystemAccessor;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.apache.sshd.sftp.server.SftpSubsystemProxy;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.*;
import java.util.Collections;

/**
 * Test crawler with SSH
 */
public class FsCrawlerTestSshIT extends AbstractFsCrawlerITCase {

    private final static String SSH_USERNAME = "USERNAME";
    private final static String SSH_PASSWORD = "PASSWORD";

    private SshServer sshd = null;

    @Before
    public void setup() throws IOException, NoSuchAlgorithmException {
        SftpSubsystemFactory factory = new SftpSubsystemFactory.Builder()
                .withFileSystemAccessor(new SftpFileSystemAccessor() {
                    @Override
                    public Path resolveLocalFilePath(SftpSubsystemProxy subsystem, Path rootDir, String remotePath) throws InvalidPathException {
                        String path = remotePath;
                        if (remotePath.startsWith("/")) {
                            path = remotePath.substring(1);
                        }
                        return currentTestResourceDir.resolve(path);
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
    public void test_ssh() throws Exception {
        Fs fs = startCrawlerDefinition("/").build();
        Server server = Server.builder()
                .setHostname(sshd.getHost())
                .setPort(sshd.getPort())
                .setUsername(SSH_USERNAME)
                .setPassword(SSH_PASSWORD)
                .setProtocol(Server.PROTOCOL.SSH)
                .build();
        crawler = startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), server);

        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 2L, null);
    }

    @Test
    public void test_ssh_with_key() throws Exception {
        Fs fs = startCrawlerDefinition("/").build();
        Server server = Server.builder()
                .setHostname(sshd.getHost())
                .setPort(sshd.getPort())
                .setUsername(SSH_USERNAME)
                .setPemPath(rootTmpDir.resolve("private.key").toFile().getAbsolutePath())
                .setProtocol(Server.PROTOCOL.SSH)
                .build();
        crawler = startCrawler(getCrawlerName(), fs, endCrawlerDefinition(getCrawlerName()), server);

        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
    }
}
