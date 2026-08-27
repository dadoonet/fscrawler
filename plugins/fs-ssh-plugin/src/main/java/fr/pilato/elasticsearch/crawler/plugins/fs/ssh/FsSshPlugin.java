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
package fr.pilato.elasticsearch.crawler.plugins.fs.ssh;

import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.beans.FileAbstractModel;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerExtensionFsProviderAbstract;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPlugin;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPluginException;
import fr.pilato.elasticsearch.crawler.plugins.RemoteConnectionSettings;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;
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

public class FsSshPlugin extends FsCrawlerPlugin {
    private static final Logger logger = LogManager.getLogger();

    @Override
    protected String getName() {
        return "fs-ssh";
    }

    @Extension
    public static class FsCrawlerExtensionFsProviderSsh extends FsCrawlerExtensionFsProviderAbstract {
        /** Default SFTP port when {@code fs.providers.ssh.port} is omitted. */
        public static final int DEFAULT_PORT = 22;

        private static final Predicate<SftpClient.DirEntry> IS_DOT =
                file -> !".".equals(file.getFilename()) && !"..".equals(file.getFilename());

        private static final Comparator<SftpClient.DirEntry> SFTP_FILE_COMPARATOR = Comparator.comparing(
                file -> {
                    var attributes = file.getAttributes();
                    var modifyTime = attributes != null ? attributes.getModifyTime() : null;
                    return modifyTime != null ? modifyTime.toInstant() : null;
                },
                Comparator.nullsLast(Comparator.naturalOrder()));

        private SshClient sshClient;
        private SftpClient sftpClient;
        private SftpClient.Attributes fileAttributes;

        private String remotePath;
        private String hostname;
        private int port;
        private String username;
        private String password;
        private String pemPath;

        @Override
        public String getType() {
            return "ssh";
        }

        @Override
        public boolean supportsCrawling() {
            return true;
        }

        @Override
        protected void parseSettings() {
            RemoteConnectionSettings resolved =
                    RemoteConnectionSettings.resolve(getType(), document, fsSettings, DEFAULT_PORT, null);
            remotePath = resolved.remotePath();
            hostname = resolved.hostname();
            port = resolved.port();
            username = resolved.username();
            password = resolved.password();
            pemPath = resolved.pemPath();
            resolved.deprecationWarnings().forEach(logger::warn);
        }

        @Override
        protected void validateSettings() throws IOException {
            if (hostname == null || hostname.isBlank()) {
                throw new IOException("Provider [" + getType() + "] requires fs.providers." + getType() + ".hostname");
            }
            if (username == null || username.isBlank()) {
                throw new IOException("Provider [" + getType() + "] requires fs.providers." + getType() + ".username");
            }

            if (remotePath == null || remotePath.isEmpty()) {
                if (document != null) {
                    throw new IOException(getType() + " path is missing");
                }
                return;
            }

            remotePath = normalizeRemotePath(remotePath);

            boolean success = false;
            try {
                openConnection();
                validateRemoteFile();
                success = true;
            } catch (FsCrawlerPluginException e) {
                throw e;
            } catch (Exception e) {
                throw new FsCrawlerPluginException(
                        "Failed to connect to " + getType().toUpperCase() + " server: " + e.getMessage(), e);
            } finally {
                if (!success) {
                    try {
                        closeConnection();
                    } catch (Exception e) {
                        logger.warn(
                                "Error closing {} connection after validation failure: {}",
                                getType().toUpperCase(),
                                e.getMessage());
                    }
                }
            }
        }

        @Override
        public Doc createDocument() {
            String filename = FilenameUtils.getName(remotePath);
            logger.debug("Creating document from {} for file {}", getType(), filename);

            Doc doc = new Doc();
            doc.getFile().setFilename(filename);
            doc.getFile().setFilesize(fileAttributes != null ? fileAttributes.getSize() : 0);

            String rootUrl = (fsSettings.getFs() != null && fsSettings.getFs().getUrl() != null)
                    ? fsSettings.getFs().getUrl()
                    : "/";
            doc.getPath().setVirtual(FsCrawlerUtil.computeVirtualPathName(rootUrl, remotePath));
            doc.getPath().setReal(remotePath);
            return doc;
        }

        @Override
        public void stop() throws FsCrawlerPluginException {
            closeConnection();
        }

        @Override
        public String toFileUrl(String fullPath) {
            return String.format("sftp://%s:%d%s", hostname, port, fullPath);
        }

        private String normalizeRemotePath(String path) throws IOException {
            if (path == null) {
                return null;
            }
            if (!path.startsWith("/")) {
                String rootPath =
                        fsSettings.getFs() != null ? fsSettings.getFs().getUrl() : null;
                if (rootPath == null || rootPath.isEmpty()) {
                    throw new IOException("Cannot resolve relative path [" + path + "]: fs.url is not configured. "
                            + "Please use an absolute path starting with '/' or configure fs.url in the job settings.");
                }
                return rootPath.endsWith("/") ? rootPath + path : rootPath + "/" + path;
            }
            return path;
        }

        private void validateRemoteFile() throws FsCrawlerPluginException {
            try {
                // Check if file exists and get its attributes
                fileAttributes = sftpClient.stat(remotePath);
                if (fileAttributes.isDirectory()) {
                    throw new FsCrawlerPluginException("Path [" + remotePath + "] is a directory, not a file");
                }
            } catch (SftpException e) {
                if (e.getStatus() == SftpConstants.SSH_FX_NO_SUCH_FILE) {
                    throw new FsCrawlerPluginException("File [" + remotePath + "] does not exist on SSH server");
                }
                throw new FsCrawlerPluginException(
                        "Failed to access file [" + remotePath + "] via SSH: " + e.getMessage(), e);
            } catch (IOException e) {
                throw new FsCrawlerPluginException(
                        "Failed to access file [" + remotePath + "] via SSH: " + e.getMessage(), e);
            }
        }

        // ========== REST API methods ==========

        @Override
        public InputStream readFile() throws FsCrawlerPluginException {
            logger.debug("Reading SSH file from [{}]", remotePath);
            try {
                return sftpClient.read(remotePath);
            } catch (IOException e) {
                throw new FsCrawlerPluginException(
                        "Failed to read file [" + remotePath + "] via SSH: " + e.getMessage(), e);
            }
        }

        // ========== Crawling methods ==========

        @Override
        public void openConnection() throws FsCrawlerPluginException {
            logger.debug("Opening SSH connection");

            sshClient = createSshClient();
            sftpClient = createSftpClient(openSshSession(sshClient, username, password, pemPath, hostname, port));
        }

        @Override
        public void closeConnection() throws FsCrawlerPluginException {
            logger.debug("Closing SSH connection");
            if (sshClient != null) {
                logger.trace("Closing SSH Client");
                try {
                    sshClient.close();
                } catch (IOException e) {
                    throw new FsCrawlerPluginException("Can not close connection", e);
                }
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
        public Collection<FileAbstractModel> getFiles(String dir) throws FsCrawlerPluginException {
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
                throw new FsCrawlerPluginException("can not list files from " + dir, e);
            }
        }

        @Override
        public InputStream getInputStream(FileAbstractModel file) throws FsCrawlerPluginException {
            logger.trace("Getting input stream for [{}]", file.getFullpath());
            try {
                return sftpClient.read(file.getFullpath());
            } catch (IOException e) {
                throw new FsCrawlerPluginException("Can not get input stream for " + file.getFullpath(), e);
            }
        }

        @Override
        public void closeInputStream(InputStream inputStream) throws FsCrawlerPluginException {
            logger.trace("Closing input stream");
            try {
                inputStream.close();
            } catch (IOException e) {
                throw new FsCrawlerPluginException("Can not stream", e);
            }
        }

        /** Convert an SFTP DirEntry to a FileAbstractModel. */
        private FileAbstractModel toFileAbstractModel(String path, SftpClient.DirEntry file) {
            logger.trace("Transform ssh file/dir [{}/{}] to a FileAbstractModel", path, file.getFilename());
            return new FileAbstractModel(
                    file.getFilename(),
                    !file.getAttributes().isDirectory(),
                    file.getAttributes().getModifyTime().toInstant(),
                    // Creation date not available
                    null,
                    file.getAttributes().getAccessTime().toInstant(),
                    FilenameUtils.getExtension(file.getFilename()),
                    path,
                    path.equals("/")
                            ? path.concat(file.getFilename())
                            : path.concat("/").concat(file.getFilename()),
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

        private ClientSession openSshSession(
                SshClient sshClient, String username, String password, String pemPath, String hostname, int port)
                throws FsCrawlerPluginException {
            try {
                logger.debug("Opening SSH connection to {}@{}", username, hostname);

                ClientSession session =
                        sshClient.connect(username, hostname, port).verify().getSession();

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
            } catch (Exception e) {
                throw new FsCrawlerPluginException(
                        "Failed to open SSH session to " + username + "@" + hostname + ":" + port, e);
            }
        }

        private SftpClient createSftpClient(ClientSession session) throws FsCrawlerPluginException {
            try {
                return SftpClientFactory.instance().createSftpClient(session);
            } catch (IOException e) {
                throw new FsCrawlerPluginException("Can not create sftp session", e);
            }
        }
    }
}
