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
package fr.pilato.elasticsearch.crawler.fs.framework;

import com.carrotsearch.randomizedtesting.RandomizedTest;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.assertj.core.api.Assertions;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

public class FsCrawlerUtilTest extends AbstractFSCrawlerTestCase {
    private static final Logger logger = LogManager.getLogger();
    private static File file;

    @BeforeClass
    public static void createTmpFile() throws IOException {
        Path path = rootTmpDir.resolve("test-group.txt");
        if (!OsValidator.WINDOWS) {
            Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rwx------");
            FileAttribute<Set<PosixFilePermission>> fileAttributes = PosixFilePermissions.asFileAttribute(permissions);
            Files.createFile(path, fileAttributes);
        } else {
            Files.createFile(path);
        }
        file = path.toFile();
    }

    @Test
    public void ownerName() {
        String ownerName = FsCrawlerUtil.getOwnerName(file);
        Assertions.assertThat(ownerName).isNotEmpty();
    }

    @Test
    public void groups() {
        Assume.assumeFalse("This test can not run on Windows.", OsValidator.WINDOWS);
        String groupName = FsCrawlerUtil.getGroupName(file);
        Assertions.assertThat(groupName).isNotEmpty();
    }

    @Test
    public void permissions() {
        Assume.assumeFalse("This test can not run on Windows.", OsValidator.WINDOWS);
        int permissions = FsCrawlerUtil.getFilePermissions(file);
        Assertions.assertThat(permissions).isEqualTo(700);
    }

    @Test
    public void aclEntries() {
        List<FileAcl> aclEntries = FsCrawlerUtil.getFileAcls(file.toPath());
        if (OsValidator.WINDOWS) {
            Assertions.assertThat(aclEntries)
                    .as("ACL entries should exist on Windows")
                    .isNotEmpty();
        } else {
            Assertions.assertThat(aclEntries)
                    .as("ACL entries should be empty when ACL view is not supported")
                    .isEmpty();
        }
    }

    @Test
    public void aclHashConsistency() {
        FileAcl aclOne = new FileAcl();
        aclOne.setPrincipal("user");
        aclOne.setType("ALLOW");
        aclOne.setPermissions(List.of("READ_DATA"));
        aclOne.setFlags(List.of("FILE_INHERIT"));

        FileAcl aclTwo = new FileAcl();
        aclTwo.setPrincipal("user");
        aclTwo.setType("ALLOW");
        aclTwo.setPermissions(List.of("READ_DATA"));
        aclTwo.setFlags(List.of("FILE_INHERIT"));

        String hash1 = FsCrawlerUtil.computeAclHash(List.of(aclOne));
        String hash2 = FsCrawlerUtil.computeAclHash(List.of(aclTwo));
        Assertions.assertThat(hash1).isEqualTo(hash2);

        aclTwo.setPermissions(List.of("WRITE_DATA"));
        String hash3 = FsCrawlerUtil.computeAclHash(List.of(aclTwo));
        Assertions.assertThat(hash3).isNotEqualTo(hash1);
    }

    @Test
    public void isFileSizeUnderLimit() {
        Assertions.assertThat(FsCrawlerUtil.isFileSizeUnderLimit(ByteSizeValue.parseBytesSizeValue("1mb"), 1))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isFileSizeUnderLimit(ByteSizeValue.parseBytesSizeValue("1mb"), 1048576))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isFileSizeUnderLimit(
                        ByteSizeValue.parseBytesSizeValue("1mb"),
                        new ByteSizeValue(RandomizedTest.randomIntBetween(2, 100), ByteSizeUnit.MB).getBytes()))
                .isFalse();
    }

    @Test
    public void extractMajorVersion() {
        Assertions.assertThat(FsCrawlerUtil.extractMajorVersion("7.2.0")).isEqualTo(7);
        Assertions.assertThat(FsCrawlerUtil.extractMajorVersion("8.17.1")).isEqualTo(8);
        Assertions.assertThat(FsCrawlerUtil.extractMajorVersion("10.1.0")).isEqualTo(10);
    }

    @Test
    public void extractMinorVersion() {
        Assertions.assertThat(FsCrawlerUtil.extractMinorVersion("7.2.0")).isEqualTo(2);
        Assertions.assertThat(FsCrawlerUtil.extractMinorVersion("8.17.1")).isEqualTo(17);
        Assertions.assertThat(FsCrawlerUtil.extractMinorVersion("10.1.0")).isEqualTo(1);
    }

    @Test
    public void getRealPathNameWindows() {
        testRealPath("/C:", "test-windows.txt", "/C:/test-windows.txt");
        testRealPath("/C:/", "test-windows.txt", "/C:/test-windows.txt");
        testRealPath("/C:/dir", "test-windows.txt", "/C:/dir/test-windows.txt");
        testRealPath("/C:/dir/", "test-windows.txt", "/C:/dir/test-windows.txt");

        testRealPath("C:/", "test-windows.txt", "C:/test-windows.txt");
        testRealPath("C:/dir", "test-windows.txt", "C:/dir/test-windows.txt");
        testRealPath("C:/dir/", "test-windows.txt", "C:/dir/test-windows.txt");
    }

    @Test
    public void getRealPathNameWindowsBackslash() {
        testRealPath("C:", "test-windows.txt", "C:\\test-windows.txt");
        testRealPath("C:\\", "test-windows.txt", "C:\\test-windows.txt");
        testRealPath("C:\\dir", "test-windows.txt", "C:\\dir\\test-windows.txt");
        testRealPath("C:\\dir\\", "test-windows.txt", "C:\\dir\\test-windows.txt");
    }

    @Test
    public void getRealPathNameWindowsNetworkPath() {
        testRealPath("\\\\SOMEONE", "test-smb.txt", "\\\\SOMEONE\\test-smb.txt");
        testRealPath("\\\\SOMEONE\\", "test-smb.txt", "\\\\SOMEONE\\test-smb.txt");
        testRealPath("\\\\SOMEONE\\share", "test-smb.txt", "\\\\SOMEONE\\share\\test-smb.txt");
        testRealPath("\\\\SOMEONE\\share\\", "test-smb.txt", "\\\\SOMEONE\\share\\test-smb.txt");
    }

    @Test
    public void getRealPathNameLinuxForLocalOrFtp() {
        // Local Linux / FTP
        testRealPath("/", "test-linux.txt", "/test-linux.txt");
        testRealPath("/dir", "test-linux.txt", "/dir/test-linux.txt");
        testRealPath("/dir/", "test-linux.txt", "/dir/test-linux.txt");
    }

    @Test
    public void getRealPathNameLinuxForSmb() {
        // SMB
        testRealPath("//SOMEONE", "test-smb.txt", "//SOMEONE/test-smb.txt");
        testRealPath("//SOMEONE/", "test-smb.txt", "//SOMEONE/test-smb.txt");
        testRealPath("//SOMEONE/share", "test-smb.txt", "//SOMEONE/share/test-smb.txt");
        testRealPath("//SOMEONE/share/", "test-smb.txt", "//SOMEONE/share/test-smb.txt");
    }

    private void testRealPath(String dirname, String filename, String expectedPath) {
        Assertions.assertThat(FsCrawlerUtil.computeRealPathName(dirname, filename))
                .isEqualTo(expectedPath);
    }

    @Test
    public void computePathLinuxFromRoot() {
        // Local Linux / FTP
        testVirtualPath("/", "/", "/");
        testVirtualPath("/", "/dir", "/dir");
        testVirtualPath("/", "/dir/subdir", "/dir/subdir");
        testVirtualPath("/", "/file.txt", "/file.txt");
        testVirtualPath("/", "/dir/file.txt", "/dir/file.txt");
        testVirtualPath("/", "/dir/subdir/file.txt", "/dir/subdir/file.txt");
    }

    @Test
    public void computePathLinuxFromDir() {
        testVirtualPath("/tmp", "/tmp", "/");
        testVirtualPath("/tmp", "/tmp/dir", "/dir");
        testVirtualPath("/tmp", "/tmp/dir/subdir", "/dir/subdir");
        testVirtualPath("/tmp", "/tmp/file.txt", "/file.txt");
        testVirtualPath("/tmp", "/tmp/dir/file.txt", "/dir/file.txt");
        testVirtualPath("/tmp", "/tmp/dir/subdir/file.txt", "/dir/subdir/file.txt");
    }

    @Test
    public void computePathLinuxSmb() {
        // SMB
        testVirtualPath("//SOMEONE/share", "//SOMEONE/share", "/");
        testVirtualPath("//SOMEONE/share", "//SOMEONE/share/dir", "/dir");
        testVirtualPath("//SOMEONE/share", "//SOMEONE/share/dir/subdir", "/dir/subdir");
        testVirtualPath("//SOMEONE/share", "//SOMEONE/share/file.txt", "/file.txt");
        testVirtualPath("//SOMEONE/share", "//SOMEONE/share/dir/file.txt", "/dir/file.txt");
        testVirtualPath("//SOMEONE/share", "//SOMEONE/share/dir/subdir/file.txt", "/dir/subdir/file.txt");
    }

    @Test
    public void computePathWindowsFromRoot() {
        testVirtualPath("C:", "C:", "\\");
        testVirtualPath("C:", "C:\\dir", "\\dir");
        testVirtualPath("C:", "C:\\dir\\subdir", "\\dir\\subdir");
        testVirtualPath("C:", "C:\\file.txt", "\\file.txt");
        testVirtualPath("C:", "C:\\dir\\file.txt", "\\dir\\file.txt");
        testVirtualPath("C:", "C:\\dir\\subdir\\file.txt", "\\dir\\subdir\\file.txt");
    }

    @Test
    public void computePathWindowsFromDirBackslash() {
        testVirtualPath("C:\\tmp", "C:\\tmp", "\\");
        testVirtualPath("C:\\tmp", "C:\\tmp\\dir", "\\dir");
        testVirtualPath("C:\\tmp", "C:\\tmp\\dir\\subdir", "\\dir\\subdir");
        testVirtualPath("C:\\tmp", "C:\\tmp\\file.txt", "\\file.txt");
        testVirtualPath("C:\\tmp", "C:\\tmp\\dir\\file.txt", "\\dir\\file.txt");
        testVirtualPath("C:\\tmp", "C:\\tmp\\dir\\subdir\\file.txt", "\\dir\\subdir\\file.txt");
    }

    @Test
    public void computePathWindowsFromDirSlash() {
        testVirtualPath("C:/tmp", "C:/tmp", "/");
        testVirtualPath("C:/tmp", "C:/tmp/dir", "/dir");
        testVirtualPath("C:/tmp", "C:/tmp/dir/subdir", "/dir/subdir");
        testVirtualPath("C:/tmp", "C:/tmp/file.txt", "/file.txt");
        testVirtualPath("C:/tmp", "C:/tmp/dir/file.txt", "/dir/file.txt");
        testVirtualPath("C:/tmp", "C:/tmp/dir/subdir/file.txt", "/dir/subdir/file.txt");
    }

    @Test
    public void computePathWindowsNetworkDrive() {
        testVirtualPath("/C:/tmp", "/C:/tmp", "/");
        testVirtualPath("/C:/tmp", "/C:/tmp/dir", "/dir");
        testVirtualPath("/C:/tmp", "/C:/tmp/dir/subdir", "/dir/subdir");
        testVirtualPath("/C:/tmp", "/C:/tmp/file.txt", "/file.txt");
        testVirtualPath("/C:/tmp", "/C:/tmp/dir/file.txt", "/dir/file.txt");
        testVirtualPath("/C:/tmp", "/C:/tmp/dir/subdir/file.txt", "/dir/subdir/file.txt");
    }

    @Test
    public void computePathWindowsRemoteServer() {
        testVirtualPath("\\\\SOMEONE\\share", "\\\\SOMEONE\\share", "\\");
        testVirtualPath("\\\\SOMEONE\\share", "\\\\SOMEONE\\share\\dir", "\\dir");
        testVirtualPath("\\\\SOMEONE\\share", "\\\\SOMEONE\\share\\dir\\subdir", "\\dir\\subdir");
        testVirtualPath("\\\\SOMEONE\\share", "\\\\SOMEONE\\share\\file.txt", "\\file.txt");
        testVirtualPath("\\\\SOMEONE\\share", "\\\\SOMEONE\\share\\dir\\file.txt", "\\dir\\file.txt");
        testVirtualPath("\\\\SOMEONE\\share", "\\\\SOMEONE\\share\\dir\\subdir\\file.txt", "\\dir\\subdir\\file.txt");
    }

    private void testVirtualPath(String rootPath, String realPath, String expectedPath) {
        Assertions.assertThat(FsCrawlerUtil.computeVirtualPathName(rootPath, realPath))
                .isEqualTo(expectedPath);
    }

    @Test
    public void getFileExtension() {
        Assertions.assertThat(FsCrawlerUtil.getFileExtension(new File("foo.bar")))
                .isEqualTo("bar");
        Assertions.assertThat(FsCrawlerUtil.getFileExtension(new File("foo"))).isEmpty();
        Assertions.assertThat(FsCrawlerUtil.getFileExtension(new File("foo.bar.baz")))
                .isEqualTo("baz");
    }

    @Test
    public void localDateToDate() {
        LocalDateTime now = LocalDateTime.now();
        Date date = FsCrawlerUtil.localDateTimeToDate(now);
        logger.info(
                "Current Time [{}] in [{}] is actually [{}]",
                now,
                TimeZone.getDefault().getDisplayName(),
                date);
        Assertions.assertThat(date).isNotNull();
    }

    /**
     * Test for getPathSeparator with various path formats. This is related to <a
     * href="https://github.com/dadoonet/fscrawler/issues/2134">#2134</a>
     */
    @Test
    public void getPathSeparator() {
        // Linux-style paths
        Assertions.assertThat(FsCrawlerUtil.getPathSeparator("/tmp/dir")).isEqualTo("/");
        Assertions.assertThat(FsCrawlerUtil.getPathSeparator("/")).isEqualTo("/");
        Assertions.assertThat(FsCrawlerUtil.getPathSeparator("//SOMEONE/share")).isEqualTo("/");

        // Windows-style paths with backslashes
        Assertions.assertThat(FsCrawlerUtil.getPathSeparator("C:\\tmp\\dir")).isEqualTo("\\");
        Assertions.assertThat(FsCrawlerUtil.getPathSeparator("C:\\")).isEqualTo("\\");
        Assertions.assertThat(FsCrawlerUtil.getPathSeparator("\\\\SOMEONE\\share"))
                .isEqualTo("\\");

        // Windows-style paths with forward slashes (common user mistake on Windows)
        // These should return "/" because they contain "/" but not "\"
        Assertions.assertThat(FsCrawlerUtil.getPathSeparator("C:/tmp/dir")).isEqualTo("/");
        Assertions.assertThat(FsCrawlerUtil.getPathSeparator("C:/")).isEqualTo("/");

        // Edge case: just a drive letter with colon but no slashes
        // Should return "\" because it contains ":" but no "/"
        Assertions.assertThat(FsCrawlerUtil.getPathSeparator("C:")).isEqualTo("\\");
    }

    @Test
    public void humanReadableDuration() {
        Assertions.assertThat(FsCrawlerUtil.durationToString(Duration.ZERO)).isEqualTo("0s");
        Assertions.assertThat(FsCrawlerUtil.durationToString(null)).isNull();
        Assertions.assertThat(FsCrawlerUtil.durationToString(Duration.ofSeconds(59)))
                .isEqualTo("59s");
        Assertions.assertThat(FsCrawlerUtil.durationToString(Duration.ofSeconds(60)))
                .isEqualTo("1m");
        Assertions.assertThat(FsCrawlerUtil.durationToString(Duration.ofSeconds(61)))
                .isEqualTo("1m1s");
        Assertions.assertThat(FsCrawlerUtil.durationToString(Duration.ofMinutes(59)))
                .isEqualTo("59m");
        Assertions.assertThat(FsCrawlerUtil.durationToString(Duration.ofMinutes(60)))
                .isEqualTo("1h");
        Assertions.assertThat(FsCrawlerUtil.durationToString(Duration.ofMinutes(61)))
                .isEqualTo("1h1m");
        Assertions.assertThat(FsCrawlerUtil.durationToString(
                        Duration.ofMillis(RandomizedTest.randomLongBetween(0, 999999999L))))
                .isNotEmpty();
    }

    @Test
    public void wait_for() {
        int duration = RandomizedTest.randomIntBetween(50, 100);
        LocalDateTime now = LocalDateTime.now();
        FsCrawlerUtil.waitFor(Duration.ofMillis(duration));
        LocalDateTime afterWait1 = LocalDateTime.now();
        Assertions.assertThat(Duration.between(now, afterWait1).toMillis()).isGreaterThanOrEqualTo(duration);
        FsCrawlerUtil.waitFor(Duration.ofMillis(100));
        LocalDateTime afterWait2 = LocalDateTime.now();
        Assertions.assertThat(Duration.between(now, afterWait2).toMillis()).isGreaterThanOrEqualTo(100);
        FsCrawlerUtil.waitFor(Duration.ofSeconds(1));
        LocalDateTime afterWait3 = LocalDateTime.now();
        Assertions.assertThat(Duration.between(now, afterWait3).toSeconds()).isGreaterThanOrEqualTo(1);
    }
}
