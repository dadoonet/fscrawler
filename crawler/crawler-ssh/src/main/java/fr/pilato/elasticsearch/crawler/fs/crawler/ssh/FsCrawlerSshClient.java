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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;

import java.nio.file.Paths;
import java.security.KeyPair;

public class FsCrawlerSshClient implements AutoCloseable {
    private final Logger logger = LogManager.getLogger(FsCrawlerSshClient.class);

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String pemPath;

    private ClientSession session;
    private SshClient sshClient;
    private SftpClient sftpClient;

    public FsCrawlerSshClient(String username, String password, String pemPath, String host, int port) {
        this.username = username;
        this.password = password;
        this.pemPath = pemPath;
        this.host = host;
        this.port = port;
    }

    public void open() throws Exception {
        sshClient = createSshClient();
        session = openSshSession(sshClient, username, password, pemPath, host, port);
        sftpClient = createSftpClient(session);
    }

    @Override
    public void close() throws Exception {
        logger.debug("Closing FsCrawlerSshClient");
        if (sshClient != null) {
            logger.trace("Closing SSH Client");
            sshClient.close();
        }
    }

    public SftpClient getSftpClient() {
        return sftpClient;
    }

    private SshClient createSshClient() {
        logger.debug("Create and start SSH client");
        SshClient client = SshClient.setUpDefaultClient();
        client.setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE);
        client.start();
        return client;
    }

    private ClientSession openSshSession(SshClient sshClient,
                                         String username, String password,
                                         String pemPath,
                                         String hostname, int port) throws Exception {
        logger.debug("Opening SSH connection to {}@{}", username, password);

        ClientSession session = sshClient.connect(username, hostname, port).verify().getSession();

        if (password != null) {
            session.addPasswordIdentity(password); // for password-based authentication
        }

        if (pemPath != null) {
            // for password-less authentication
            FileKeyPairProvider fileKeyPairProvider = new FileKeyPairProvider(Paths.get(pemPath));
            Iterable<KeyPair> keyPairs = fileKeyPairProvider.loadKeys(null);
            for (KeyPair keyPair : keyPairs) {
                session.addPublicKeyIdentity(keyPair);
            }
        }

        session.auth().verify();

        logger.debug("SSH connection successful");
        return session;
    }

    private SftpClient createSftpClient(ClientSession session) throws Exception {
        return SftpClientFactory.instance().createSftpClient(session);
    }
}
