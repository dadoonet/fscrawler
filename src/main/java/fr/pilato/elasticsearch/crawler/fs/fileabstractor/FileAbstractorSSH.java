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

package fr.pilato.elasticsearch.crawler.fs.fileabstractor;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.Server;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Vector;
import java.util.stream.Collectors;

public class FileAbstractorSSH extends FileAbstractor<ChannelSftp.LsEntry> {

    private ChannelSftp sftp;

    public FileAbstractorSSH(FsSettings fsSettings) {
        super(fsSettings);
    }

    @Override
    public FileAbstractModel toFileAbstractModel(String path, ChannelSftp.LsEntry file) {
        FileAbstractModel model = new FileAbstractModel();
        model.name = file.getFilename();
        model.directory = file.getAttrs().isDir();
        model.file = !model.directory;
        model.lastModifiedDate = Instant.ofEpochMilli(file.getAttrs().getMTime());
        model.path = path;
        model.fullpath = model.path.concat("/").concat(model.name);
        model.size = file.getAttrs().getSize();
        model.owner = Integer.toString(file.getAttrs().getUId());
        model.group = Integer.toString(file.getAttrs().getGId());
        return model;
    }

    @Override
    public InputStream getInputStream(FileAbstractModel file) throws Exception {
        return sftp.get(file.fullpath);
    }

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
    public boolean exists(String dir) throws Exception {
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
        sftp.getSession().disconnect();
        sftp.disconnect();
    }

    public ChannelSftp openSSHConnection(Server server) throws Exception {
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
        session.connect();

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
