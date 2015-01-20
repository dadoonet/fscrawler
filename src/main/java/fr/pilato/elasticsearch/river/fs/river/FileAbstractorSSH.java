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

package fr.pilato.elasticsearch.river.fs.river;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Vector;

public class FileAbstractorSSH extends FileAbstractor<ChannelSftp.LsEntry> {

    private ChannelSftp sftp;

    public FileAbstractorSSH(FsRiverFeedDefinition fsDef) throws Exception {
        super(fsDef);
    }

    @Override
    public FileAbstractModel toFileAbstractModel(String path, ChannelSftp.LsEntry file) {
        FileAbstractModel model = new FileAbstractModel();
        model.name = file.getFilename();
        model.directory = file.getAttrs().isDir();
        model.file = !model.directory;
        model.lastModifiedDate = file.getAttrs().getMTime();
        model.path = path;
        model.fullpath = model.path.concat("/").concat(model.name);
        return model;
    }

    @Override
    public InputStream getInputStream(FileAbstractModel file) throws Exception {
        return sftp.get(file.fullpath);
    }

    @Override
    public Collection<FileAbstractModel> getFiles(String dir) throws Exception {
        if (logger.isDebugEnabled()) logger.debug("Listing local files from {}", dir);
        Vector<ChannelSftp.LsEntry> ls = null;

        ls = sftp.ls(dir);
        if (ls == null) return null;

        Collection<FileAbstractModel> result = new ArrayList<FileAbstractModel>(ls.size());
        // Iterate other files
        for (ChannelSftp.LsEntry file : ls) {
            // We ignore here all files like . and ..
            if (!".".equals(file.getFilename()) &&
                    !"..".equals(file.getFilename())) {
                result.add(toFileAbstractModel(dir, file));
            }
        }

        if (logger.isDebugEnabled()) logger.debug("{} local files found", result.size());
        return result;
    }

    @Override
    public void open() throws Exception {
        sftp = openSSHConnection(fsDef);
    }

    @Override
    public void close() throws Exception {
        sftp.disconnect();
    }

    public ChannelSftp openSSHConnection(FsRiverFeedDefinition fsdef) throws Exception {
        if (logger.isDebugEnabled()) logger.debug("Opening SSH connection to {}@{}", fsdef.getUsername(),
                fsdef.getServer());

        JSch jsch = new JSch();
        Session session = jsch.getSession(fsdef.getUsername(), fsdef.getServer(), fsdef.getPort());
        java.util.Properties config = new java.util.Properties();
        config.put("StrictHostKeyChecking", "no");
        if (fsdef.getPemFilePath() != null) {
            jsch.addIdentity(fsdef.getPemFilePath());
        }
        session.setConfig(config);
        if (fsdef.getPassword() != null) {
            session.setPassword(fsdef.getPassword());
        }
        session.connect();

        //Open a new session for SFTP.
        Channel channel = session.openChannel("sftp");
        channel.connect();

        //checking SSH client connection.
        if (!channel.isConnected()) {
            logger.warn("Cannot connect with SSH to {}@{}", fsdef.getUsername(),
                    fsdef.getServer());
            throw new RuntimeException("Can not connect to " + fsdef.getUsername() + "@" + fsdef.getServer());
        }
        if (logger.isDebugEnabled()) logger.debug("SSH connection successful");
        return (ChannelSftp) channel;
    }
}
