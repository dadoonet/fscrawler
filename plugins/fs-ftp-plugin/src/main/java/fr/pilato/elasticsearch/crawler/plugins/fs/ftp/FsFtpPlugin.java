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
package fr.pilato.elasticsearch.crawler.plugins.fs.ftp;

import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.beans.FileAbstractModel;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil;
import fr.pilato.elasticsearch.crawler.fs.settings.ProviderSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.Server;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerExtensionFsProviderAbstract;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPlugin;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPluginException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.net.PrintCommandListener;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.io.IoBuilder;
import org.pf4j.Extension;

public class FsFtpPlugin extends FsCrawlerPlugin {
    private static final Logger logger = LogManager.getLogger();

    @Override
    protected String getName() {
        return "fs-ftp";
    }

    @Extension
    public static class FsCrawlerExtensionFsProviderFtp extends FsCrawlerExtensionFsProviderAbstract {
        /** Default FTP port when {@code fs.providers.ftp.port} is omitted. */
        public static final int DEFAULT_PORT = 21;

        /** Default FTP username when {@code fs.providers.ftp.username} is omitted. */
        public static final String DEFAULT_USERNAME = "anonymous";

        private static final String ALTERNATIVE_ENCODING = "GBK";
        private static final Comparator<FTPFile> FTP_FILE_COMPARATOR = Comparator.comparing(
                file -> {
                    var timestamp = file.getTimestamp();
                    return timestamp != null ? Instant.ofEpochMilli(timestamp.getTimeInMillis()) : null;
                },
                Comparator.nullsLast(Comparator.naturalOrder()));

        private FTPClient ftp;
        private boolean isUtf8 = false;
        private FTPFile fileInfo;

        private String remotePath;
        private String hostname;
        private int port;
        private String username;
        private String password;

        private final Predicate<FTPFile> isNotSymLink = file -> {
            if (fsSettings != null && fsSettings.getFs().isFollowSymlinks()) return true;
            return !file.isSymbolicLink();
        };

        @Override
        public String getType() {
            return "ftp";
        }

        @Override
        public boolean supportsCrawling() {
            return true;
        }

        @Override
        @SuppressWarnings("removal")
        protected void parseSettings() {
            ProviderSettings settings = ProviderSettings.of(getType(), fsSettings, restTypeSettings());
            Server server = fsSettings != null ? fsSettings.getServer() : null;
            hostname = settings.string("hostname", server != null ? server.getHostname() : null);
            port = settings.integer(
                    "port", DEFAULT_PORT, server != null && server.getPort() > 0 ? server.getPort() : null);
            username = settings.string("username", server != null ? server.getUsername() : null, DEFAULT_USERNAME);
            password = settings.secret("password", server != null ? server.getPassword() : null);
            remotePath = settings.overlayString("path");
            settings.deprecationWarnings().forEach(logger::warn);
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
            doc.getFile().setFilesize(fileInfo != null ? fileInfo.getSize() : 0);

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
            return "ftp://" + hostname + ":" + port + fullPath;
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
                // Get file info to validate it exists
                // Use mlistFile() which returns info about the path itself (like SSH stat())
                // rather than listFiles() which returns directory contents when given a directory
                String ftpPath = encodePathForFtp(remotePath);
                fileInfo = ftp.mlistFile(ftpPath);

                if (fileInfo == null) {
                    // mlistFile() may not be supported by all FTP servers (requires MLST command)
                    // Fall back to listing the parent directory and finding the entry
                    fileInfo = getFileInfoFromParentListing();
                }

                if (fileInfo == null) {
                    throw new FsCrawlerPluginException("File [" + remotePath + "] does not exist on FTP server");
                }

                if (fileInfo.isDirectory()) {
                    throw new FsCrawlerPluginException("Path [" + remotePath + "] is a directory, not a file");
                }
            } catch (IOException e) {
                throw new FsCrawlerPluginException(
                        "Failed to access file [" + remotePath + "] via FTP: " + e.getMessage(), e);
            }
        }

        /**
         * Get file info by listing the parent directory and finding the matching entry. This is a fallback when
         * mlistFile() is not supported by the FTP server.
         *
         * @return the FTPFile info, or null if not found
         * @throws IOException if an I/O error occurs
         */
        private FTPFile getFileInfoFromParentListing() throws IOException {
            // Extract parent directory and filename
            String parentDir;
            String filename;
            int lastSlash = remotePath.lastIndexOf('/');
            if (lastSlash <= 0) {
                parentDir = "/";
                filename = lastSlash == 0 ? remotePath.substring(1) : remotePath;
            } else {
                parentDir = remotePath.substring(0, lastSlash);
                filename = remotePath.substring(lastSlash + 1);
            }

            // List the parent directory
            String encodedParentDir = encodePathForFtp(parentDir);
            FTPFile[] files = ftp.listFiles(encodedParentDir);

            if (files == null) {
                return null;
            }

            // Find the entry matching our filename
            for (FTPFile file : files) {
                if (filename.equals(file.getName())) {
                    return file;
                }
            }

            return null;
        }

        // ========== REST API methods ==========

        @Override
        public InputStream readFile() throws FsCrawlerPluginException {
            try {
                logger.debug("Reading FTP file from [{}]", remotePath);
                String ftpPath = encodePathForFtp(remotePath);
                InputStream inputStream = ftp.retrieveFileStream(ftpPath);
                if (inputStream != null) {
                    // Wrap the stream to ensure completePendingCommand() is called when closed
                    return new FtpInputStream(inputStream, ftp);
                } else {
                    throw new FsCrawlerPluginException("FTP client cannot retrieve stream for [" + remotePath + "]");
                }
            } catch (IOException e) {
                throw new FsCrawlerPluginException(
                        "IOException caught while reading FTP file [" + remotePath + "]: " + e.getMessage(), e);
            }
        }

        /**
         * Wrapper InputStream that ensures FTP completePendingCommand() is called when the stream is closed. This is
         * required by the FTP protocol after using retrieveFileStream().
         */
        private static class FtpInputStream extends FilterInputStream {
            private final FTPClient ftpClient;

            FtpInputStream(InputStream in, FTPClient ftpClient) {
                super(in);
                this.ftpClient = ftpClient;
            }

            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    // Must call completePendingCommand() after closing the stream
                    // to properly complete the FTP data transfer
                    try {
                        ftpClient.completePendingCommand();
                    } catch (IOException e) {
                        // Log but don't throw - the stream is already closed
                        LogManager.getLogger(FtpInputStream.class).warn("Failed to complete FTP pending command", e);
                    }
                }
            }
        }

        /** Encode the path with the appropriate encoding for FTP. */
        private String encodePathForFtp(String path) throws UnsupportedEncodingException {
            if (isUtf8) {
                return new String(path.getBytes(StandardCharsets.UTF_8), FTP.DEFAULT_CONTROL_ENCODING);
            } else {
                return new String(path.getBytes(ALTERNATIVE_ENCODING), FTP.DEFAULT_CONTROL_ENCODING);
            }
        }

        // ========== Crawling methods ==========

        @Override
        @SuppressWarnings(
                "java:S5332") // FTP is insecure by nature; this plugin exists precisely to support FTP servers
        public void openConnection() throws FsCrawlerPluginException {
            try {
                ftp = new FTPClient();
                if (logger.isTraceEnabled() || logger.isDebugEnabled()) {
                    ftp.addProtocolCommandListener(new PrintCommandListener(
                            new PrintWriter(IoBuilder.forLogger(logger).buildOutputStream())));
                }
                // Send a safe command (NOOP) over the control connection to reset the router's idle timer
                ftp.setControlKeepAliveTimeout(Duration.ofSeconds(300));

                logger.debug("Opening FTP connection to {}@{}", username, hostname);

                ftp.connect(hostname, port);

                // Check FTP client connection
                int reply = ftp.getReplyCode();
                if (!FTPReply.isPositiveCompletion(reply)) {
                    ftp.disconnect();
                    logger.warn("Cannot connect with FTP to {}@{}", username, hostname);
                    throw new FsCrawlerPluginException("Can not connect to " + username + "@" + hostname);
                }

                if (!ftp.login(username, password)) {
                    ftp.disconnect();
                    throw new FsCrawlerPluginException("Please check ftp user or password");
                }

                int utf8Reply = ftp.sendCommand("OPTS UTF8", "ON");
                if (FTPReply.isPositiveCompletion(utf8Reply)) {
                    isUtf8 = true;
                }
                ftp.setFileType(FTP.BINARY_FILE_TYPE);
                ftp.enterLocalPassiveMode();

                logger.debug("FTP connection successful");
            } catch (IOException e) {
                throw new FsCrawlerPluginException(
                        "IOException caught while opening FTP connection: " + e.getMessage(), e);
            }
        }

        @Override
        public void closeConnection() throws FsCrawlerPluginException {
            try {
                if (ftp != null && ftp.isConnected()) {
                    ftp.logout();
                    ftp.disconnect();
                }
            } catch (IOException e) {
                logger.warn("Error during FTP logout: {}", e.getMessage());
            }
        }

        @Override
        public boolean exists(String directory) {
            try {
                logger.debug("Checking dir existence: {}", directory);
                String dir = encodePathForFtp(directory);
                return ftp.changeWorkingDirectory(dir);
            } catch (IOException e) {
                return false;
            }
        }

        @Override
        public Collection<FileAbstractModel> getFiles(String dir) throws FsCrawlerPluginException {
            try {
                logger.debug("Listing files from {}", dir);
                String ftpDir = encodePathForFtp(dir);

                FTPFile[] ftpFiles = ftp.listFiles(ftpDir);
                if (ftpFiles == null || ftpFiles.length == 0) {
                    logger.debug("No files found [{}]. Returning an empty array.", ftpDir);
                    return Collections.emptyList();
                }

                List<FTPFile> files = Arrays.stream(ftpFiles)
                        .filter(isNotSymLink)
                        .sorted(FTP_FILE_COMPARATOR.reversed())
                        .toList();

                Collection<FileAbstractModel> result = new ArrayList<>(files.size());
                // Iterate other files, ignoring . and ..
                result.addAll(files.stream()
                        .filter(file -> !".".equals(file.getName()) && !"..".equals(file.getName()))
                        .map(file -> toFileAbstractModel(dir, file))
                        .toList());

                logger.debug("{} files found", result.size());
                return result;
            } catch (IOException e) {
                throw new FsCrawlerPluginException(
                        "IOException caught while listing FTP directory [" + dir + "]: " + e.getMessage(), e);
            }
        }

        @Override
        public InputStream getInputStream(FileAbstractModel file) throws FsCrawlerPluginException {
            try {
                String fullPath = encodePathForFtp(file.getFullpath());

                InputStream inputStream = ftp.retrieveFileStream(fullPath);
                if (inputStream != null) {
                    return inputStream;
                } else {
                    throw new FsCrawlerPluginException(
                            String.format("FTP client can not retrieve stream for [%s]", file.getFullpath()));
                }
            } catch (IOException e) {
                throw new FsCrawlerPluginException(
                        "IOException caught while getting FTP file stream for [" + file.getFullpath() + "]: "
                                + e.getMessage(),
                        e);
            }
        }

        @Override
        public void closeInputStream(InputStream inputStream) throws FsCrawlerPluginException {
            try {
                inputStream.close();
                // This is necessary if we want to retrieve multiple streams one by one
                ftp.completePendingCommand();
            } catch (IOException e) {
                throw new FsCrawlerPluginException("Error while closing FTP stream: " + e.getMessage(), e);
            }
        }

        /** Convert an FTPFile to a FileAbstractModel. */
        private FileAbstractModel toFileAbstractModel(String path, FTPFile file) {
            String filename = file.getName();
            String extension = FilenameUtils.getExtension(filename);

            String toEncoding = ALTERNATIVE_ENCODING;
            if (isUtf8) {
                toEncoding = StandardCharsets.UTF_8.displayName();
            }
            try {
                filename = new String(filename.getBytes(FTP.DEFAULT_CONTROL_ENCODING), toEncoding);
            } catch (UnsupportedEncodingException e) {
                logger.error("Error during encoding: {}", e.getMessage());
            }

            return new FileAbstractModel(
                    filename,
                    file.isFile(),
                    Instant.ofEpochMilli(file.getTimestamp().getTimeInMillis()),
                    // Creation date not available
                    null,
                    // Access date not available
                    null,
                    extension,
                    path,
                    path.equals("/") ? path.concat(filename) : path.concat("/").concat(filename),
                    file.getSize(),
                    file.getUser(),
                    file.getGroup(),
                    getFilePermissions(file),
                    Collections.emptyList(),
                    null);
        }

        /** Determines FTPFile permissions. */
        private static int getFilePermissions(final FTPFile file) {
            try {
                int user = FsCrawlerUtil.toOctalPermission(
                        file.hasPermission(FTPFile.USER_ACCESS, FTPFile.READ_PERMISSION),
                        file.hasPermission(FTPFile.USER_ACCESS, FTPFile.WRITE_PERMISSION),
                        file.hasPermission(FTPFile.USER_ACCESS, FTPFile.EXECUTE_PERMISSION));
                int group = FsCrawlerUtil.toOctalPermission(
                        file.hasPermission(FTPFile.GROUP_ACCESS, FTPFile.READ_PERMISSION),
                        file.hasPermission(FTPFile.GROUP_ACCESS, FTPFile.WRITE_PERMISSION),
                        file.hasPermission(FTPFile.GROUP_ACCESS, FTPFile.EXECUTE_PERMISSION));
                int others = FsCrawlerUtil.toOctalPermission(
                        file.hasPermission(FTPFile.WORLD_ACCESS, FTPFile.READ_PERMISSION),
                        file.hasPermission(FTPFile.WORLD_ACCESS, FTPFile.WRITE_PERMISSION),
                        file.hasPermission(FTPFile.WORLD_ACCESS, FTPFile.EXECUTE_PERMISSION));

                return user * 100 + group * 10 + others;
            } catch (Exception e) {
                logger.warn("Failed to determine 'permissions' of {}: {}", file, e.getMessage());
                return -1;
            }
        }
    }
}
