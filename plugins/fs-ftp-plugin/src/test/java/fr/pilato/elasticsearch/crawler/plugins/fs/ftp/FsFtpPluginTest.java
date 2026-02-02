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
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerExtensionFsCrawler;
import org.assertj.core.groups.Tuple;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockftpserver.fake.FakeFtpServer;
import org.mockftpserver.fake.UserAccount;
import org.mockftpserver.fake.filesystem.DirectoryEntry;
import org.mockftpserver.fake.filesystem.FileEntry;
import org.mockftpserver.fake.filesystem.FileSystem;
import org.mockftpserver.fake.filesystem.Permissions;
import org.mockftpserver.fake.filesystem.UnixFakeFileSystem;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class FsFtpPluginTest extends AbstractFSCrawlerTestCase {
    private FakeFtpServer fakeFtpServer;
    private final String user = "user";
    private final String pass = "pass";

    @Before
    public void setup() {
        fakeFtpServer = new FakeFtpServer();
        fakeFtpServer.setServerControlPort(0);
        fakeFtpServer.addUserAccount(new UserAccount(user, pass, "/"));

        // Create a fake filesystem with the following structure:
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
        fileSystem.add(new DirectoryEntry(nestedDir));
        fileSystem.add(new FileEntry(nestedDir + "/foo.txt", "文件名不支持中文"));
        fileSystem.add(new FileEntry(nestedDir + "/bar.txt", "bar file"));

        fileSystem.add(new DirectoryEntry(nestedDir + "/buzz"));
        fileSystem.add(new FileEntry(nestedDir + "/buzz/hello.txt", "hello"));
        fileSystem.add(new FileEntry(nestedDir + "/buzz/world.txt", "world"));

        String permissionDir = "/permission";
        fileSystem.add(new DirectoryEntry(permissionDir));
        FileEntry fileAllPermissions = new FileEntry(permissionDir + "/all.txt", "123");
        fileAllPermissions.setPermissions(Permissions.ALL);
        fileSystem.add(fileAllPermissions);
        FileEntry fileNonePermissions = new FileEntry(permissionDir + "/none.txt", "456");
        fileNonePermissions.setPermissions(Permissions.NONE);
        fileSystem.add(fileNonePermissions);

        String dirWithSpaceInName = "/subdir_with_space ";
        fileSystem.add(new DirectoryEntry(dirWithSpaceInName));
        fileSystem.add(new FileEntry(dirWithSpaceInName + "/hello.txt", "File in dir with space at the end"));
        fileSystem.add(new FileEntry(dirWithSpaceInName + "/world.txt", "File in dir with space at the end"));

        fakeFtpServer.setFileSystem(fileSystem);
        fakeFtpServer.start();
    }

    @After
    public void shutDown() {
        fakeFtpServer.stop();
    }

    @Test
    public void ftpPlugin() throws Exception {
        int port = fakeFtpServer.getServerControlPort();
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getServer().setHostname("localhost");
        fsSettings.getServer().setUsername(user);
        fsSettings.getServer().setPassword(pass);
        fsSettings.getServer().setPort(port);

        FsFtpPlugin.FsCrawlerExtensionFsProviderFtp ftpPlugin = new FsFtpPlugin.FsCrawlerExtensionFsProviderFtp();
        ftpPlugin.start(fsSettings, "{}");

        try {
            ftpPlugin.openConnection();

            testFilesInDir(ftpPlugin, "/ThisPathDoesNotExist");
            testFilesInDir(ftpPlugin, "/",
                    tuple(false, true, "nested", "", "/", "/nested", 0L, 777, "none", "none"),
                    tuple(false, true,"permission", "", "/", "/permission", 0L, 777, "none", "none"),
                    tuple(false, true,"subdir_with_space ", "", "/", "/subdir_with_space ", 0L, 777, "none", "none"),
                    tuple(true, false, "testfile.txt", "txt", "/", "/testfile.txt", 15L, 777, "none", "none"));
            testFilesInDir(ftpPlugin, "/nested",
                    tuple(false, true,"buzz", "", "/nested", "/nested/buzz", 0L, 777, "none", "none"),
                    tuple(true, false, "foo.txt", "txt", "/nested", "/nested/foo.txt", 24L, 777, "none", "none"),
                    tuple(true, false, "bar.txt", "txt", "/nested", "/nested/bar.txt", 8L, 777, "none", "none"));
            testFilesInDir(ftpPlugin, "/permission",
                    tuple(true, false, "all.txt", "txt", "/permission", "/permission/all.txt", 3L, 777, "none", "none"),
                    tuple(true, false, "none.txt", "txt", "/permission", "/permission/none.txt", 3L, 0, "none", "none"));
            testFilesInDir(ftpPlugin, "/subdir_with_space ",
                    tuple(true, false, "hello.txt", "txt", "/subdir_with_space ", "/subdir_with_space /hello.txt", 33L, 777, "none", "none"),
                    tuple(true, false, "world.txt", "txt", "/subdir_with_space ", "/subdir_with_space /world.txt", 33L, 777, "none", "none"));
        } finally {
            ftpPlugin.closeConnection();
        }
    }

    @Test
    public void getType() {
        FsFtpPlugin.FsCrawlerExtensionFsProviderFtp ftpPlugin = new FsFtpPlugin.FsCrawlerExtensionFsProviderFtp();
        assertThat(ftpPlugin.getType()).isEqualTo("ftp");
    }

    private void testFilesInDir(FsCrawlerExtensionFsCrawler plugin, String path, Tuple... values) throws Exception {
        assertThat(plugin.exists(path)).isEqualTo(values.length > 0);
        Collection<FileAbstractModel> files = plugin.getFiles(path);
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
