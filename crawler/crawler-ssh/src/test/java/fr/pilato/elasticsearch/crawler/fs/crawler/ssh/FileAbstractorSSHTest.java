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
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyPairResourceWriter;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.config.keys.AuthorizedKeysAuthenticator;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.server.SftpFileSystemAccessor;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.apache.sshd.sftp.server.SftpSubsystemProxy;
import org.assertj.core.groups.Tuple;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class FileAbstractorSSHTest extends AbstractFSCrawlerTestCase {
    private static final Logger logger = LogManager.getLogger();
    private static final String SSH_USERNAME = "USERNAME";
    private static final String SSH_PASSWORD = "PASSWORD";
    private SshServer sshd = null;
    Path testDir = rootTmpDir.resolve("test-ssh");

    @Before
    public void setup() throws IOException, NoSuchAlgorithmException {
        if (Files.notExists(testDir)) {
            Files.createDirectory(testDir);
        }
        // Add some fake files to the test-ssh directory
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

        // Create /testfile.txt
        addFakeFile(testDir, "testfile.txt", "I'm a test file");

        // Create /nested
        addFakeDir(testDir, "nested");
        // Create /nested/foo.txt
        addFakeFile(testDir.resolve("nested"), "foo.txt", "文件名不支持中文");
        // Create /nested/bar.txt
        addFakeFile(testDir.resolve("nested"), "bar.txt", "bar file");

        // Create nested/buzz
        Path buzzDir = addFakeDir(testDir.resolve("nested"), "buzz");
        // Create /nested/buzz/hello.txt
        addFakeFile(buzzDir, "hello.txt", "hello");
        // Create /nested/buzz/world.txt
        addFakeFile(buzzDir, "world.txt", "world");

        // Create /permission
        Path permissionDir = addFakeDir(testDir, "permission");
        // Create /permission/all.txt with all permissions
        Path allFile = permissionDir.resolve("all.txt");
        if (Files.notExists(allFile)) {
            Files.writeString(allFile, "123");
        }
        allFile.toFile().setReadable(true, false);
        allFile.toFile().setWritable(true, false);
        allFile.toFile().setExecutable(true, false);
        // Create /permission/none.txt with no permissions
        Path noneFile = permissionDir.resolve("none.txt");
        if (Files.notExists(noneFile)) {
            Files.writeString(noneFile, "456");
        }
        noneFile.toFile().setReadable(false, false);
        noneFile.toFile().setWritable(false, false);
        noneFile.toFile().setExecutable(false, false);

        // Create "/subdir_with_space "
        Path subdirWithSpace = addFakeDir(testDir, "subdir_with_space ");
        // Create "/subdir_with_space /hello.txt"
        addFakeFile(subdirWithSpace, "hello.txt", "File in dir with space at the end");
        // Create "/subdir_with_space /world.txt"
        addFakeFile(subdirWithSpace, "world.txt", "File in dir with space at the end");

        /*
        // Create "/chérie"
        Path dirWithUtf8InName = addFakeDir(testDir, "chérie");
        // Create "/chérie/hello.txt"
        addFakeFile(dirWithUtf8InName, "hello.txt", "hello");
        // Create "/chérie/world.txt"
        addFakeFile(dirWithUtf8InName, "world.txt", "world");
         */

        SftpSubsystemFactory factory = new SftpSubsystemFactory.Builder()
                .withFileSystemAccessor(new SftpFileSystemAccessor() {
                    @Override
                    public Path resolveLocalFilePath(SftpSubsystemProxy subsystem, Path rootDir, String remotePath) throws InvalidPathException {
                        String path = remotePath;
                        if (remotePath.startsWith("/")) {
                            path = remotePath.substring(1);
                        }
                        return testDir.resolve(path);
                    }
                })
                .build();

        // Generate the key files for our SSH tests
        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        saveKeyPair(rootTmpDir, keyPair);

        sshd = SshServer.setUpDefaultServer();
        sshd.setHost("localhost");
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(rootTmpDir.resolve("host.ser")));
        sshd.setPasswordAuthenticator((username, password, session) ->
                SSH_USERNAME.equals(username) && SSH_PASSWORD.equals(password));
        sshd.setPublickeyAuthenticator(new AuthorizedKeysAuthenticator(rootTmpDir.resolve("public.key")));

        sshd.setSubsystemFactories(Collections.singletonList(factory));
        sshd.start();

        logger.info(" -> Started fake SSHD service on {}:{}", sshd.getHost(), sshd.getPort());
    }

    private void saveKeyPair(Path path, KeyPair keyPair) {
        OpenSSHKeyPairResourceWriter writer = new OpenSSHKeyPairResourceWriter();

        // Store Public Key.
        try (FileOutputStream fos = new FileOutputStream(path.resolve("public.key").toFile())) {
            writer.writePublicKey(keyPair.getPublic(), "Public Key for tests", fos);
        } catch (GeneralSecurityException | IOException e) {
            logger.error("Failed to save public key", e);
        }

        // Store Private Key.
        try (FileOutputStream fos = new FileOutputStream(path.resolve("private.key").toFile())) {
            writer.writePrivateKey(keyPair, "Private Key for tests", null, fos);
        } catch (GeneralSecurityException | IOException e) {
            logger.error("Failed to save public key", e);
        }
    }

    @After
    public void shutDown() throws IOException {
        if (sshd != null) {
            sshd.stop(true);
            sshd.close();
            logger.info(" -> Stopped fake SSHD service on {}:{}", sshd.getHost(), sshd.getPort());
        }
    }

    @Test
    public void sshClient() throws Exception {
        // Test with login / password
        try (FsCrawlerSshClient client = new FsCrawlerSshClient(SSH_USERNAME, SSH_PASSWORD, null,
                sshd.getHost(),sshd.getPort())) {
            client.open();
            SftpClient.Attributes stat = client.getSftpClient().stat("/");
            assertThat(stat.isDirectory()).isTrue();
        }

        // Test with PEM file
        try (FsCrawlerSshClient client = new FsCrawlerSshClient(SSH_USERNAME, null,
                rootTmpDir.resolve("private.key").toString(),
                sshd.getHost(),sshd.getPort())) {
            client.open();
            SftpClient.Attributes stat = client.getSftpClient().stat("/");
            assertThat(stat.isDirectory()).isTrue();
        }
    }

    @Test
    public void fileAbstractorSSH() throws Exception {
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getServer().setHostname(sshd.getHost());
        fsSettings.getServer().setPort(sshd.getPort());
        fsSettings.getServer().setUsername(SSH_USERNAME);
        fsSettings.getServer().setPassword(SSH_PASSWORD);

        try (FileAbstractor<?> fileAbstractor = new FileAbstractorSSH(fsSettings)) {
            fileAbstractor.open();
            assertThat(fileAbstractor.exists("/ThisPathDoesNotExist")).isFalse();

            testFilesInDir(fileAbstractor, "/ThisPathDoesNotExist");
            testFilesInDir(fileAbstractor, "/",
                    tuple(false, true, "nested", "", "/", "/nested", 0L, 16877, "0", "0"),
                    tuple(false, true,"permission", "", "/", "/permission", 0L, 16877, "0", "0"),
                    tuple(false, true,"subdir_with_space ", "", "/", "/subdir_with_space ", 0L, 16877, "0", "0"),
                    tuple(true, false, "testfile.txt", "txt", "/", "/testfile.txt", 15L, 33188, "0", "0"));
            testFilesInDir(fileAbstractor, "/nested",
                    tuple(false, true,"buzz", "", "/nested", "/nested/buzz", 0L, 16877, "0", "0"),
                    tuple(true, false, "foo.txt", "txt", "/nested", "/nested/foo.txt", 24L, 33188, "0", "0"),
                    tuple(true, false, "bar.txt", "txt", "/nested", "/nested/bar.txt", 8L, 33188, "0", "0"));
            testFilesInDir(fileAbstractor, "/permission",
                    tuple(true, false, "all.txt", "txt", "/permission", "/permission/all.txt", 3L, 33279, "0", "0"),
                    tuple(true, false, "none.txt", "txt", "/permission", "/permission/none.txt", 3L, 32768, "0", "0"));
            testFilesInDir(fileAbstractor, "/subdir_with_space ",
                    tuple(true, false, "hello.txt", "txt", "/subdir_with_space ", "/subdir_with_space /hello.txt", 33L, 33188, "0", "0"),
                    tuple(true, false, "world.txt", "txt", "/subdir_with_space ", "/subdir_with_space /world.txt", 33L, 33188, "0", "0"));
        }
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

    private void addFakeFile(Path dir, String filename, String content) throws IOException {
        Path testFile = dir.resolve(filename);
        if (Files.notExists(testFile)) {
            Files.writeString(testFile, content);
        }
    }

    private Path addFakeDir(Path dir, String subDirname) throws IOException {
        Path testDir = dir.resolve(subDirname);
        if (Files.notExists(testDir)) {
            Files.createDirectory(testDir);
        }
        return testDir;
    }
}
