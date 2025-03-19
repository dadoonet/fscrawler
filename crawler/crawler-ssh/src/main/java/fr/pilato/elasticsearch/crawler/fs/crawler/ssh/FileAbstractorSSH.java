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

import fr.pilato.elasticsearch.crawler.fs.crawler.FileAbstractModel;
import fr.pilato.elasticsearch.crawler.fs.crawler.FileAbstractor;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;

import java.io.IOException;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.sshd.sftp.client.SftpClient;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Comparator;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class FileAbstractorSSH extends FileAbstractor<SftpClient.DirEntry> {
    private static final Logger logger = LogManager.getLogger();

    private final FsCrawlerSshClient fsCrawlerSshClient;
    private final static Predicate<SftpClient.DirEntry> IS_DOT = file ->
            !".".equals(file.getFilename()) &&
            !"..".equals(file.getFilename());
    private final static Comparator<SftpClient.DirEntry> SFTP_FILE_COMPARATOR = Comparator.comparing(
            file -> LocalDateTime.ofInstant(file.getAttributes().getModifyTime().toInstant(), ZoneId.systemDefault()));

    public FileAbstractorSSH(FsSettings fsSettings) {
        super(fsSettings);
        fsCrawlerSshClient = new FsCrawlerSshClient(
                fsSettings.getServer().getUsername(),
                fsSettings.getServer().getPassword(),
                fsSettings.getServer().getPemPath(),
                fsSettings.getServer().getHostname(),
                fsSettings.getServer().getPort()
        );
    }

    @Override
    public FileAbstractModel toFileAbstractModel(String path, SftpClient.DirEntry file) {
        logger.trace("Transform ssh file/dir [{}/{}] to a FileAbstractModel", path, file.getFilename());
        return new FileAbstractModel(
                file.getFilename(),
                !file.getAttributes().isDirectory(),
                // We are using here the local TimeZone as a reference. If the remote system is under another TZ, this might cause issues
                LocalDateTime.ofInstant(file.getAttributes().getModifyTime().toInstant(), ZoneId.systemDefault()),
                // We don't have the creation date
                null,
                // We are using here the local TimeZone as a reference. If the remote system is under another TZ, this might cause issues
                LocalDateTime.ofInstant(file.getAttributes().getAccessTime().toInstant(), ZoneId.systemDefault()),
                FilenameUtils.getExtension(file.getFilename()),
                path,
                path.equals("/") ? path.concat(file.getFilename()) : path.concat("/").concat(file.getFilename()),
                file.getAttributes().getSize(),
                Integer.toString(file.getAttributes().getUserId()),
                Integer.toString(file.getAttributes().getGroupId()),
                file.getAttributes().getPermissions());
    }

    @Override
    public InputStream getInputStream(FileAbstractModel file) throws Exception {
        logger.trace("Getting input stream for [{}]", file.getFullpath());
        return fsCrawlerSshClient.getSftpClient().read(file.getFullpath());
    }

    @Override
    public void closeInputStream(InputStream inputStream) throws IOException {
        logger.trace("Closing input stream");
        inputStream.close();
    }

    @Override
    public Collection<FileAbstractModel> getFiles(String dir) throws Exception {
        logger.debug("Listing local files from [{}]", dir);

        Iterable<SftpClient.DirEntry> ls;

        ls = fsCrawlerSshClient.getSftpClient().readDir(dir);

        /*
         Iterate other files
         We ignore here all files like "." and ".."
        */
        Collection<FileAbstractModel> result = StreamSupport.stream(ls.spliterator(), false)
                .filter(IS_DOT)
                .sorted(SFTP_FILE_COMPARATOR.reversed())
                .map(file -> toFileAbstractModel(dir, file))
                .collect(Collectors.toList());

        logger.trace("{} local files found", result.size());
        return result;
    }

    @Override
    public boolean exists(String dir) {
        logger.trace("Checking if ssh file/dir [{}] exists", dir);
        try {
            fsCrawlerSshClient.getSftpClient().stat(dir);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    @Override
    public void open() throws Exception {
        logger.trace("Opening fsCrawlerSshClient");
        fsCrawlerSshClient.open();
    }

    @Override
    public void close() throws Exception {
        logger.trace("Closing fsCrawlerSshClient");
        fsCrawlerSshClient.close();
    }
}
