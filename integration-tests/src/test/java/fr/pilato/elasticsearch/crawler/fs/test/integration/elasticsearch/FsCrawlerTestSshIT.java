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
import fr.pilato.elasticsearch.crawler.fs.framework.OsValidator;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.Server;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeFalse;

/**
 * Test crawler with SSH
 */
public class FsCrawlerTestSshIT extends AbstractFsCrawlerITCase {
    private static final Logger logger = LogManager.getLogger();
    private static final String SSH_USERNAME = "USERNAME";
    private static final String SSH_PASSWORD = "PASSWORD";

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
    public void ssh() throws Exception {
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setUrl("/");
        fsSettings.getFs().setUpdateRate(TimeValue.timeValueMinutes(1));
        fsSettings.getServer().setHostname(sshd.getHost());
        fsSettings.getServer().setPort(sshd.getPort());
        fsSettings.getServer().setUsername(SSH_USERNAME);
        fsSettings.getServer().setPassword(SSH_PASSWORD);
        fsSettings.getServer().setProtocol(Server.PROTOCOL.SSH);
        crawler = startCrawler(fsSettings);

        ESSearchResponse response = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 2L, null);
        assertThat(response.getTotalHits()).isEqualTo(2L);
    }

    @Test
    public void ssh_with_key() throws Exception {
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setUrl("/");
        fsSettings.getFs().setUpdateRate(TimeValue.timeValueMinutes(1));
        fsSettings.getServer().setHostname(sshd.getHost());
        fsSettings.getServer().setPort(sshd.getPort());
        fsSettings.getServer().setUsername(SSH_USERNAME);
        fsSettings.getServer().setPassword(SSH_PASSWORD);
        fsSettings.getServer().setPemPath(rootTmpDir.resolve("private.key").toFile().getAbsolutePath());
        fsSettings.getServer().setProtocol(Server.PROTOCOL.SSH);
        crawler = startCrawler(fsSettings);

        ESSearchResponse response = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
        assertThat(response.getTotalHits()).isEqualTo(1L);
    }

    /**
     * Test for #1952: <a href="https://github.com/dadoonet/fscrawler/issues/1952">https://github.com/dadoonet/fscrawler/issues/1952</a>:
     * When a directory has a space at the end, files inside are not indexed
     */
    @Test
    public void dir_with_space_at_the_end() throws Exception {
        // We need to do a small hack here and rename the test directory as this could not work on Windows
        Path dirWithSpace = currentTestResourceDir.resolve("with_space ");
        try {
            Files.move(currentTestResourceDir.resolve("with_space"), dirWithSpace);
        } catch (InvalidPathException e) {
            logger.warn("Cannot rename directory to have a space at the end on Windows. Ignoring the test.", e);
            assumeFalse("We can not run this test on Windows", OsValidator.WINDOWS);
        }

        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setUrl("/");
        fsSettings.getFs().setUpdateRate(TimeValue.timeValueMinutes(1));
        fsSettings.getServer().setHostname(sshd.getHost());
        fsSettings.getServer().setPort(sshd.getPort());
        fsSettings.getServer().setUsername(SSH_USERNAME);
        fsSettings.getServer().setPassword(SSH_PASSWORD);
        fsSettings.getServer().setProtocol(Server.PROTOCOL.SSH);
        crawler = startCrawler(fsSettings);
        ESSearchResponse response = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 3L, null);
        assertThat(response.getTotalHits()).isEqualTo(3L);
    }
}
