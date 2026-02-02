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
package fr.pilato.elasticsearch.crawler.plugins.fs.ftp;

import fr.pilato.elasticsearch.crawler.fs.beans.FileAbstractModel;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerExtensionRemoteProviderAbstract;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPlugin;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPluginException;
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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.toOctalPermission;

public class FsFtpPlugin extends FsCrawlerPlugin {
    private static final Logger logger = LogManager.getLogger();

    @Override
    protected String getName() {
        return "fs-ftp";
    }

    @Extension
    public static class FsCrawlerExtensionFsProviderFtp extends FsCrawlerExtensionRemoteProviderAbstract {

        private static final String ALTERNATIVE_ENCODING = "GBK";
        private static final Comparator<FTPFile> FTP_FILE_COMPARATOR = Comparator.comparing(
                file -> {
                    var timestamp = file.getTimestamp();
                    return timestamp != null ? LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(timestamp.getTimeInMillis()), ZoneId.systemDefault()) : null;
                },
                Comparator.nullsLast(Comparator.naturalOrder()));

        private FTPClient ftp;
        private boolean isUtf8 = false;

        // FTP-specific fields
        private FTPFile fileInfo;

        private final Predicate<FTPFile> isNotSymLink = file -> {
            if (fsSettings != null && fsSettings.getFs().isFollowSymlinks()) return true;
            return !file.isSymbolicLink();
        };

        @Override
        public String getType() {
            return "ftp";
        }

        // ========== Protocol-specific settings ==========

        @Override
        protected long getFilesize() {
            return fileInfo != null ? fileInfo.getSize() : 0;
        }

        @Override
        protected void doValidateFile() throws FsCrawlerPluginException {
            try {
                // Get file info to validate it exists
                String ftpPath = encodePathForFtp(remotePath);
                FTPFile[] files = ftp.listFiles(ftpPath);
                if (files == null || files.length == 0) {
                    throw new FsCrawlerPluginException("File [" + remotePath + "] does not exist on FTP server");
                }
                fileInfo = files[0];
                if (fileInfo.isDirectory()) {
                    throw new FsCrawlerPluginException("Path [" + remotePath + "] is a directory, not a file");
                }
            } catch (IOException e) {
                throw new FsCrawlerPluginException("Failed to access file [" + remotePath + "] via FTP: " + e.getMessage(), e);
            }
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
                throw new FsCrawlerPluginException("IOException caught while reading FTP file [" + remotePath + "]: " + e.getMessage(), e);
            }
        }

        /**
         * Wrapper InputStream that ensures FTP completePendingCommand() is called when the stream is closed.
         * This is required by the FTP protocol after using retrieveFileStream().
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

        /**
         * Encode the path with the appropriate encoding for FTP.
         */
        private String encodePathForFtp(String path) throws UnsupportedEncodingException {
            if (isUtf8) {
                return new String(path.getBytes(StandardCharsets.UTF_8), FTP.DEFAULT_CONTROL_ENCODING);
            } else {
                return new String(path.getBytes(ALTERNATIVE_ENCODING), FTP.DEFAULT_CONTROL_ENCODING);
            }
        }

        // ========== Crawling methods ==========

        @Override
        public void openConnection() throws FsCrawlerPluginException {
            try {
                ftp = new FTPClient();
                if (logger.isTraceEnabled() || logger.isDebugEnabled()) {
                    ftp.addProtocolCommandListener(
                            new PrintCommandListener(
                                    new PrintWriter(IoBuilder.forLogger(logger).buildOutputStream())));
                }
                // Send a safe command (NOOP) over the control connection to reset the router's idle timer
                ftp.setControlKeepAliveTimeout(Duration.ofSeconds(300));

                String effectiveHostname = getEffectiveHostname();
                int effectivePort = getEffectivePort();
                String effectiveUsername = getEffectiveUsername();
                String effectivePassword = getEffectivePassword();

                logger.debug("Opening FTP connection to {}@{}", effectiveUsername, effectiveHostname);

                ftp.connect(effectiveHostname, effectivePort);

                // Check FTP client connection
                int reply = ftp.getReplyCode();
                if (!FTPReply.isPositiveCompletion(reply)) {
                    ftp.disconnect();
                    logger.warn("Cannot connect with FTP to {}@{}", effectiveUsername, effectiveHostname);
                    throw new FsCrawlerPluginException("Can not connect to " + effectiveUsername + "@" + effectiveHostname);
                }

                if (!ftp.login(effectiveUsername, effectivePassword)) {
                    ftp.disconnect();
                    throw new FsCrawlerPluginException("Please check ftp user or password");
                }

                int utf8Reply = ftp.sendCommand("OPTS UTF8", "ON");
                if (FTPReply.isPositiveCompletion(utf8Reply)) {
                    isUtf8 = true;
                }
                ftp.setFileType(FTPClient.BINARY_FILE_TYPE);
                ftp.enterLocalPassiveMode();

                logger.debug("FTP connection successful");
            } catch (IOException e) {
                throw new FsCrawlerPluginException("IOException caught while opening FTP connection: " + e.getMessage(), e);
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
                String dir;
                if (isUtf8) {
                    dir = new String(directory.getBytes(StandardCharsets.UTF_8), FTP.DEFAULT_CONTROL_ENCODING);
                } else {
                    dir = new String(directory.getBytes(ALTERNATIVE_ENCODING), FTP.DEFAULT_CONTROL_ENCODING);
                }
                return ftp.changeWorkingDirectory(dir);
            } catch (IOException e) {
                return false;
            }
        }

        @Override
        public Collection<FileAbstractModel> getFiles(String dir) throws FsCrawlerPluginException {
            try {
                logger.debug("Listing files from {}", dir);
                String ftpDir = new String(dir.getBytes(ALTERNATIVE_ENCODING), FTP.DEFAULT_CONTROL_ENCODING);
                if (isUtf8) {
                    ftpDir = new String(dir.getBytes(StandardCharsets.UTF_8), FTP.DEFAULT_CONTROL_ENCODING);
                }

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
                throw new FsCrawlerPluginException("IOException caught while listing FTP directory [" + dir + "]: " + e.getMessage(), e);
            }
        }

        @Override
        public InputStream getInputStream(FileAbstractModel file) throws FsCrawlerPluginException {
            try {
                String fullPath = file.getFullpath();
                if (isUtf8) {
                    fullPath = new String(fullPath.getBytes(StandardCharsets.UTF_8), FTP.DEFAULT_CONTROL_ENCODING);
                } else {
                    fullPath = new String(fullPath.getBytes(ALTERNATIVE_ENCODING), FTP.DEFAULT_CONTROL_ENCODING);
                }

                InputStream inputStream = ftp.retrieveFileStream(fullPath);
                if (inputStream != null) {
                    return inputStream;
                } else {
                    throw new FsCrawlerPluginException(String.format("FTP client can not retrieve stream for [%s]", file.getFullpath()));
                }
            } catch (IOException e) {
                throw new FsCrawlerPluginException("IOException caught while getting FTP file stream for [" + file.getFullpath() + "]: " + e.getMessage(), e);
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

        /**
         * Convert an FTPFile to a FileAbstractModel.
         */
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
                    // Using local TimeZone as reference
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(file.getTimestamp().getTimeInMillis()), ZoneId.systemDefault()),
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

        /**
         * Determines FTPFile permissions.
         */
        private static int getFilePermissions(final FTPFile file) {
            try {
                int user = toOctalPermission(
                        file.hasPermission(FTPFile.USER_ACCESS, FTPFile.READ_PERMISSION),
                        file.hasPermission(FTPFile.USER_ACCESS, FTPFile.WRITE_PERMISSION),
                        file.hasPermission(FTPFile.USER_ACCESS, FTPFile.EXECUTE_PERMISSION));
                int group = toOctalPermission(
                        file.hasPermission(FTPFile.GROUP_ACCESS, FTPFile.READ_PERMISSION),
                        file.hasPermission(FTPFile.GROUP_ACCESS, FTPFile.WRITE_PERMISSION),
                        file.hasPermission(FTPFile.GROUP_ACCESS, FTPFile.EXECUTE_PERMISSION));
                int others = toOctalPermission(
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
