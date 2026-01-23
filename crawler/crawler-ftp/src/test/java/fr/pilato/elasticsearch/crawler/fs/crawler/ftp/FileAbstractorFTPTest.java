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

package fr.pilato.elasticsearch.crawler.fs.crawler.ftp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import fr.pilato.elasticsearch.crawler.fs.crawler.FileAbstractModel;
import fr.pilato.elasticsearch.crawler.fs.crawler.FileAbstractor;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.assertj.core.groups.Tuple;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockftpserver.fake.FakeFtpServer;
import org.mockftpserver.fake.UserAccount;
import org.mockftpserver.fake.filesystem.DirectoryEntry;
import org.mockftpserver.fake.filesystem.FileEntry;
import org.mockftpserver.fake.filesystem.FileSystem;
import org.mockftpserver.fake.filesystem.Permissions;
import org.mockftpserver.fake.filesystem.UnixFakeFileSystem;

public class FileAbstractorFTPTest extends AbstractFSCrawlerTestCase {
    private static final Logger logger = LogManager.getLogger();
    private FakeFtpServer fakeFtpServer;
    private final String user = "user";
    private final String pass = "pass";

    @Before
    public void setup() {
        fakeFtpServer = new FakeFtpServer();
        fakeFtpServer.setServerControlPort(0);
        fakeFtpServer.addUserAccount(new UserAccount(user, pass, "/"));

        // We are going to fake a whole filesystem with the following directory structure:
        /*
        /testfile.txt
        /nested
            /foo.txt
            /bar.txt
            /buzz
                /hello.txt
                /world.txt
        /permission
            /all.txt (with all permissions)
            /none.txt (with no permissions)
        /subdir_with_space
            /hello.txt
            /world.txt
         */
        FileSystem fileSystem = new UnixFakeFileSystem();

        // Create /testfile.txt
        fileSystem.add(new FileEntry("/testfile.txt", "I'm a test file"));

        String nestedDir = "/nested";
        // Create /nested
        fileSystem.add(new DirectoryEntry(nestedDir));
        // Create /nested/foo.txt
        fileSystem.add(new FileEntry(nestedDir + "/foo.txt", "文件名不支持中文"));
        // Create /nested/bar.txt
        fileSystem.add(new FileEntry(nestedDir + "/bar.txt", "bar file"));

        // Create /nested/buzz
        fileSystem.add(new DirectoryEntry(nestedDir + "/buzz"));
        // Create /nested/buzz/hello.txt
        fileSystem.add(new FileEntry(nestedDir + "/buzz/hello.txt", "hello"));
        // Create /nested/buzz/world.txt
        fileSystem.add(new FileEntry(nestedDir + "/buzz/world.txt", "world"));

        String permissionDir = "/permission";
        // Create /permission
        fileSystem.add(new DirectoryEntry(permissionDir));
        // Create /permission/all.txt with all permissions
        FileEntry fileAllPermissions = new FileEntry(permissionDir + "/all.txt", "123");
        fileAllPermissions.setPermissions(Permissions.ALL);
        fileSystem.add(fileAllPermissions);
        // Create /permission/none.txt with no permissions
        FileEntry fileNonePermissions = new FileEntry(permissionDir + "/none.txt", "456");
        fileNonePermissions.setPermissions(Permissions.NONE);
        fileSystem.add(fileNonePermissions);

        String dirWithSpaceInName = "/subdir_with_space ";
        // Create "/subdir_with_space "
        fileSystem.add(new DirectoryEntry(dirWithSpaceInName));
        // Create "/subdir_with_space /hello.txt"
        fileSystem.add(new FileEntry(dirWithSpaceInName + "/hello.txt", "File in dir with space at the end"));
        // Create "/subdir_with_space /world.txt"
        fileSystem.add(new FileEntry(dirWithSpaceInName + "/world.txt", "File in dir with space at the end"));

        // Apparently FakeFtpServer doesn't support utf-8 for filenames
        /*
        String dirWithUtf8InName = "/chérie";
        // Create "/chérie"
        fileSystem.add(new DirectoryEntry(dirWithSpaceInName));
        // Create "/chérie/hello.txt"
        fileSystem.add(new FileEntry(dirWithUtf8InName + "/hello.txt", "hello"));
        // Create "/chérie/world.txt"
        fileSystem.add(new FileEntry(dirWithUtf8InName + "/world.txt", "world"));
         */

        fakeFtpServer.setFileSystem(fileSystem);
        fakeFtpServer.start();
    }

    @After
    public void shutDown() {
        fakeFtpServer.stop();
    }

    @Test
    public void fileAbstractorFTP() throws Exception {
        String path = "/";
        int port = fakeFtpServer.getServerControlPort();
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getServer().setHostname("localhost");
        fsSettings.getServer().setUsername(user);
        fsSettings.getServer().setPassword(pass);
        fsSettings.getServer().setPort(port);

        try (FileAbstractor<?> fileAbstractor = new FileAbstractorFTP(fsSettings)) {
            fileAbstractor.open();
            testFilesInDir(fileAbstractor, "/ThisPathDoesNotExist");
            testFilesInDir(fileAbstractor, path,
                    tuple(false, true, "nested", "", "/", "/nested", 0L, 777, "none", "none"),
                    tuple(false, true,"permission", "", "/", "/permission", 0L, 777, "none", "none"),
                    tuple(false, true,"subdir_with_space ", "", "/", "/subdir_with_space ", 0L, 777, "none", "none"),
                    tuple(true, false, "testfile.txt", "txt", "/", "/testfile.txt", 15L, 777, "none", "none"));
            testFilesInDir(fileAbstractor, "/nested",
                    tuple(false, true,"buzz", "", "/nested", "/nested/buzz", 0L, 777, "none", "none"),
                    tuple(true, false, "foo.txt", "txt", "/nested", "/nested/foo.txt", 24L, 777, "none", "none"),
                    tuple(true, false, "bar.txt", "txt", "/nested", "/nested/bar.txt", 8L, 777, "none", "none"));
            testFilesInDir(fileAbstractor, "/permission",
                    tuple(true, false, "all.txt", "txt", "/permission", "/permission/all.txt", 3L, 777, "none", "none"),
                    tuple(true, false, "none.txt", "txt", "/permission", "/permission/none.txt", 3L, 0, "none", "none"));
            testFilesInDir(fileAbstractor, "/subdir_with_space ",
                    tuple(true, false, "hello.txt", "txt", "/subdir_with_space ", "/subdir_with_space /hello.txt", 33L, 777, "none", "none"),
                    tuple(true, false, "world.txt", "txt", "/subdir_with_space ", "/subdir_with_space /world.txt", 33L, 777, "none", "none"));
        }
    }

    /**
     * FakeFtpServer doesn't support utf-8
     * You have to adapt this test to your own system
     * So this test is disabled by default
     */
    @Test @Ignore
    public void testConnectToFTPServer() throws Exception {
        String path = "/中文目录";
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getServer().setHostname("192.168.18.207");
        fsSettings.getServer().setUsername("helsonxiao");
        fsSettings.getServer().setPassword("123456");
        fsSettings.getServer().setPort(21);

        FileAbstractorFTP ftp = new FileAbstractorFTP(fsSettings);
        ftp.open();
        boolean exists = ftp.exists(path);
        assertThat(exists).isTrue();
        Collection<FileAbstractModel> files = ftp.getFiles(path);
        logger.debug("Found {} files", files.size());

        for (FileAbstractModel file : files) {
            if (file.isDirectory()) {
                Collection<FileAbstractModel> subDirFiles = ftp.getFiles(file.getFullpath());
                logger.debug("Found {} files in sub dir", subDirFiles.size());
                for (FileAbstractModel subDirFile : subDirFiles) {
                    if (subDirFile.isFile()) {
                        InputStream inputStream = ftp.getInputStream(subDirFile);
                        String content = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
                        logger.debug("[{}] - {}: {}", file.getName(), subDirFile.getName(), content);
                        ftp.closeInputStream(inputStream);
                    }
                }
            } else {
                InputStream inputStream = ftp.getInputStream(file);
                String content = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
                logger.debug(" - {}: {}", file.getName(), content);
                ftp.closeInputStream(inputStream);
            }
        }

        ftp.close();
    }

    private void testFilesInDir(FileAbstractor<?> fileAbstractor, String path, Tuple... values) throws Exception {
        assertThat(fileAbstractor.exists(path)).isEqualTo(values.length > 0);
        Collection<FileAbstractModel> files = fileAbstractor.getFiles(path);
        assertThat(files).hasSize(values.length);
        assertThat(files).extracting(
                        FileAbstractModel::isFile,
                        FileAbstractModel::isDirectory,
                        FileAbstractModel::getName,
                        FileAbstractModel::getExtension,
                        FileAbstractModel::getPath,
                        FileAbstractModel::getFullpath,
                        FileAbstractModel::getSize,
                        FileAbstractModel::getPermissions,
                        FileAbstractModel::getGroup,
                        FileAbstractModel::getOwner)
                .containsExactlyInAnyOrder(values);
    }
}
