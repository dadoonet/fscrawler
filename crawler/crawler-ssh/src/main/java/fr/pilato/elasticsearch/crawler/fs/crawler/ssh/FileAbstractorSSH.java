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

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import fr.pilato.elasticsearch.crawler.fs.crawler.FileAbstractModel;
import fr.pilato.elasticsearch.crawler.fs.crawler.FileAbstractor;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.Server;
import java.io.IOException;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Vector;
import java.util.stream.Collectors;

public class FileAbstractorSSH extends FileAbstractor<ChannelSftp.LsEntry> {
    private final Logger logger = LogManager.getLogger(FileAbstractorSSH.class);

    private ChannelSftp sftp;

    public FileAbstractorSSH(FsSettings fsSettings) {
        super(fsSettings);
    }

    @Override
    public FileAbstractModel toFileAbstractModel(String path, ChannelSftp.LsEntry file) {
        return new FileAbstractModel(
                file.getFilename(),
                !file.getAttrs().isDir(),
                // We are using here the local TimeZone as a reference. If the remote system is under another TZ, this might cause issues
                LocalDateTime.ofInstant(Instant.ofEpochMilli(file.getAttrs().getMTime()*1000L), ZoneId.systemDefault()),
                // We don't have the creation date
                null,
                // We are using here the local TimeZone as a reference. If the remote system is under another TZ, this might cause issues
                LocalDateTime.ofInstant(Instant.ofEpochMilli(file.getAttrs().getATime()*1000L), ZoneId.systemDefault()),
                FilenameUtils.getExtension(file.getFilename()),
                path,
                path.equals("/") ? path.concat(file.getFilename()) : path.concat("/").concat(file.getFilename()),
                file.getAttrs().getSize(),
                Integer.toString(file.getAttrs().getUId()),
                Integer.toString(file.getAttrs().getGId()),
                file.getAttrs().getPermissions());
    }

    @Override
    public InputStream getInputStream(FileAbstractModel file) throws Exception {
        return sftp.get(file.getFullpath());
    }

    @Override
    public void closeInputStream(InputStream inputStream) throws IOException {
        inputStream.close();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Collection<FileAbstractModel> getFiles(String dir) throws Exception {
        logger.debug("Listing local files from {}", dir);
        Vector<ChannelSftp.LsEntry> ls;

        ls = sftp.ls(dir);
        if (ls == null) return null;

        Collection<FileAbstractModel> result = new ArrayList<>(ls.size());
        // Iterate other files
        // We ignore here all files like . and ..
        result.addAll(ls.stream().filter(file -> !".".equals(file.getFilename()) &&
                !"..".equals(file.getFilename()))
                .map(file -> toFileAbstractModel(dir, file))
                .collect(Collectors.toList()));

        logger.debug("{} local files found", result.size());
        return result;
    }

    @Override
    public boolean exists(String dir) {
        try {
            sftp.ls(dir);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    @Override
    public void open() throws Exception {
        sftp = openSSHConnection(fsSettings.getServer());
    }

    @Override
    public void close() throws Exception {
        if (sftp != null) {
            sftp.getSession().disconnect();
            sftp.disconnect();
        }
    }

    private ChannelSftp openSSHConnection(Server server) throws Exception {
        logger.debug("Opening SSH connection to {}@{}", server.getUsername(), server.getHostname());

        JSch jsch = new JSch();
        Session session = jsch.getSession(server.getUsername(), server.getHostname(), server.getPort());
        java.util.Properties config = new java.util.Properties();
        config.put("StrictHostKeyChecking", "no");
        if (server.getPemPath() != null) {
            jsch.addIdentity(server.getPemPath());
        }
        session.setConfig(config);
        if (server.getPassword() != null) {
            session.setPassword(server.getPassword());
        }

        try {
            session.connect();
        } catch (JSchException e) {
            logger.warn("Cannot connect with SSH to {}@{}: {}", server.getUsername(),
                    server.getHostname(), e.getMessage());
            throw e;
        }

        //Open a new session for SFTP.
        Channel channel = session.openChannel("sftp");
        channel.connect();

        //checking SSH client connection.
        if (!channel.isConnected()) {
            logger.warn("Cannot connect with SSH to {}@{}", server.getUsername(),
                    server.getHostname());
            throw new RuntimeException("Can not connect to " + server.getUsername() + "@" + server.getHostname());
        }
        logger.debug("SSH connection successful");
        return (ChannelSftp) channel;
    }
}
