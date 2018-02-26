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
import fr.pilato.elasticsearch.crawler.fs.beans.FileModel;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientManager;
import fr.pilato.elasticsearch.crawler.fs.crawler.FsParserAbstract;
import fr.pilato.elasticsearch.crawler.fs.crawler.Plugin;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.Server;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Vector;
import java.util.stream.Collectors;

@Plugin(name = FsParserSsh.NAME)
public class FsParserSsh extends FsParserAbstract {

    public static final String NAME = "ssh";
    private static final Logger logger = LogManager.getLogger(FsParserSsh.class);
    private ChannelSftp sftp;

    public FsParserSsh(FsSettings fsSettings, Path config, ElasticsearchClientManager esClientManager, Integer loop) {
        super(fsSettings, config, esClientManager, loop);
    }

    @Override
    public void openConnection() throws Exception {
        sftp = openSSHConnection(fsSettings.getServer());
    }

    @Override
    public void validate() {
        // Check that the SSH directory we want to crawl exists
        try {
            sftp.ls(fsSettings.getFs().getUrl());
        } catch (Exception e) {
            throw new RuntimeException(fsSettings.getFs().getUrl() + " doesn't exists.", e);
        }
    }

    @Override
    public void close() {
        try {
            sftp.getSession().disconnect();
        } catch (JSchException ignored) {
            // If we have no existing session, no need to close it
        }
        sftp.disconnect();
    }

    @SuppressWarnings("unchecked")
    public Collection<FileModel> getFiles(String dir) throws Exception {
        logger.debug("Listing local files from {}", dir);
        Vector<ChannelSftp.LsEntry> ls;

        ls = sftp.ls(dir);
        if (ls == null) return null;

        Collection<FileModel> result = new ArrayList<>(ls.size());
        // Iterate other files
        // We ignore here all files like . and ..
        result.addAll(ls.stream().filter(file -> !".".equals(file.getFilename()) &&
                !"..".equals(file.getFilename()))
                .map(file -> {
                    FileModel model = new FileModel();
                    model.name = file.getFilename();
                    model.directory = file.getAttrs().isDir();
                    model.file = !model.directory;
                    // We are using here the local TimeZone as a reference. If the remote system is under another TZ, this might cause issues
                    model.lastModifiedDate = LocalDateTime.ofInstant(Instant.ofEpochMilli(file.getAttrs().getMTime()*1000L), ZoneId.systemDefault());
                    model.path = dir;
                    model.fullpath = model.path.concat("/").concat(model.name);
                    model.size = file.getAttrs().getSize();
                    model.owner = Integer.toString(file.getAttrs().getUId());
                    model.group = Integer.toString(file.getAttrs().getGId());
                    return model;
                })
                .collect(Collectors.toList()));

        logger.debug("{} local files found", result.size());
        return result;
    }

    @Override
    public InputStream getInputStream(FileModel file) throws Exception {
        return sftp.get(file.fullpath);
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
