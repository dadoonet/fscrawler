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
package fr.pilato.elasticsearch.crawler.plugins.fs.ssh;

import com.jayway.jsonpath.PathNotFoundException;
import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.crawler.FileAbstractModel;
import fr.pilato.elasticsearch.crawler.fs.settings.Server;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerExtensionFsCrawler;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerExtensionFsProviderAbstract;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPlugin;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.apache.sshd.sftp.common.SftpConstants;
import org.apache.sshd.sftp.common.SftpException;
import org.pf4j.Extension;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

public class FsSshPlugin extends FsCrawlerPlugin {
    private static final Logger logger = LogManager.getLogger();

    @Override
    protected String getName() {
        return "fs-ssh";
    }

    @Extension
    public static class FsCrawlerExtensionFsProviderSsh extends FsCrawlerExtensionFsProviderAbstract
            implements FsCrawlerExtensionFsCrawler {

        private static final Predicate<SftpClient.DirEntry> IS_DOT = file ->
                !".".equals(file.getFilename()) &&
                !"..".equals(file.getFilename());

        private static final Comparator<SftpClient.DirEntry> SFTP_FILE_COMPARATOR = Comparator.comparing(
                file -> {
                    var attributes = file.getAttributes();
                    var modifyTime = attributes != null ? attributes.getModifyTime() : null;
                    return modifyTime != null ? LocalDateTime.ofInstant(modifyTime.toInstant(), ZoneId.systemDefault()) : null;
                },
                Comparator.nullsLast(Comparator.naturalOrder()));

        private SshClient sshClient;
        private SftpClient sftpClient;

        @Override
        public String getType() {
            return "ssh";
        }

        // ========== FsCrawlerExtensionFsProvider methods (REST API) ==========

        @Override
        public InputStream readFile() throws IOException {
            // For REST API, we would need to implement single file reading
            throw new UnsupportedOperationException("Single file reading via REST API not yet implemented for SSH");
        }

        @Override
        public Doc createDocument() throws IOException {
            // For REST API, we would create a document from the current file
            throw new UnsupportedOperationException("Document creation via REST API not yet implemented for SSH");
        }

        @Override
        protected void parseSettings() throws PathNotFoundException {
            // For REST API usage, parse settings from JSON
        }

        @Override
        protected void validateSettings() throws IOException {
            // For REST API usage, validate settings
        }

        // ========== FsCrawlerExtensionFsCrawler methods (Crawling) ==========

        @Override
        public void openConnection() throws Exception {
            logger.debug("Opening SSH connection");
            Server server = fsSettings.getServer();

            sshClient = createSshClient();
            sftpClient = createSftpClient(openSshSession(
                    sshClient,
                    server.getUsername(),
                    server.getPassword(),
                    server.getPemPath(),
                    server.getHostname(),
                    server.getPort()));
        }

        @Override
        public void closeConnection() throws Exception {
            logger.debug("Closing SSH connection");
            if (sshClient != null) {
                logger.trace("Closing SSH Client");
                sshClient.close();
            }
        }

        @Override
        public boolean exists(String directory) {
            logger.trace("Checking if ssh file/dir [{}] exists", directory);
            try {
                sftpClient.stat(directory);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public Collection<FileAbstractModel> getFiles(String dir) throws Exception {
            logger.debug("Listing files from [{}]", dir);

            try {
                Iterable<SftpClient.DirEntry> ls = sftpClient.readDir(dir);
                Collection<FileAbstractModel> result = StreamSupport.stream(ls.spliterator(), false)
                        .filter(IS_DOT)
                        .sorted(SFTP_FILE_COMPARATOR.reversed())
                        .map(file -> toFileAbstractModel(dir, file))
                        .toList();

                logger.trace("{} files found", result.size());
                return result;
            } catch (Exception e) {
                if (e.getCause() instanceof SftpException cause) {
                    if (cause.getStatus() == SftpConstants.SSH_FX_NO_SUCH_FILE) {
                        logger.debug("Directory [{}] does not exist. Returning an empty list.", dir);
                        return Collections.emptyList();
                    } else {
                        logger.warn("Failed to list files from [{}] : status [{}]", dir, cause.getStatus());
                    }
                }
                logger.warn("Failed to list files from [{}]", dir, e);
                throw e;
            }
        }

        @Override
        public InputStream getInputStream(FileAbstractModel file) throws Exception {
            logger.trace("Getting input stream for [{}]", file.getFullpath());
            return sftpClient.read(file.getFullpath());
        }

        @Override
        public void closeInputStream(InputStream inputStream) throws Exception {
            logger.trace("Closing input stream");
            inputStream.close();
        }

        /**
         * Convert an SFTP DirEntry to a FileAbstractModel.
         */
        private FileAbstractModel toFileAbstractModel(String path, SftpClient.DirEntry file) {
            logger.trace("Transform ssh file/dir [{}/{}] to a FileAbstractModel", path, file.getFilename());
            return new FileAbstractModel(
                    file.getFilename(),
                    !file.getAttributes().isDirectory(),
                    // Using local TimeZone as reference
                    LocalDateTime.ofInstant(file.getAttributes().getModifyTime().toInstant(), ZoneId.systemDefault()),
                    // Creation date not available
                    null,
                    // Using local TimeZone as reference
                    LocalDateTime.ofInstant(file.getAttributes().getAccessTime().toInstant(), ZoneId.systemDefault()),
                    FilenameUtils.getExtension(file.getFilename()),
                    path,
                    path.equals("/") ? path.concat(file.getFilename()) : path.concat("/").concat(file.getFilename()),
                    file.getAttributes().getSize(),
                    Integer.toString(file.getAttributes().getUserId()),
                    Integer.toString(file.getAttributes().getGroupId()),
                    file.getAttributes().getPermissions(),
                    Collections.emptyList(),
                    null);
        }

        // ========== SSH Client Helper Methods ==========

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
            logger.debug("Opening SSH connection to {}@{}", username, hostname);

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
}
